package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.SecureEndpoint;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.Properties;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Timer;
import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Broker {
    public Broker() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException {
    }

    private record Client(InetSocketAddress addr, long endOfLease) {

        @Override
            public boolean equals(Object other) {
                if (other == this) {
                    return true;
                }
                if (!(other instanceof Client(InetSocketAddress addr1, long eol))) {
                    return false;
                }
                return addr.equals(addr1);
            }
        }

        private static class LockingTimerTask extends TimerTask {
            private final ReadWriteLock lock;
            private final Runnable r;

            public LockingTimerTask(ReadWriteLock lock, Runnable r) {
                this.lock = lock;
                this.r = r;
            }

            @Override
            public void run() {
                lock.writeLock().lock();
                try {
                    r.run();
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }

        private static final long LEASE_TIME = 3000;
    private final Endpoint endpoint = new SecureEndpoint(new Endpoint(Properties.PORT));
    private final ClientCollection<Client> clients = new ClientCollection<>();
    private final AtomicInteger client_counter = new AtomicInteger();
    Thread stopRequestThread = new Thread(() -> {
        JOptionPane.showMessageDialog(null, "Press OK button to stop the broker");
        running = false;
    });
    private volatile boolean running = true;
    private final Timer timer = new Timer();

    private void startCheckLeaseTask(final ReadWriteLock lock) {
        timer.schedule(new LockingTimerTask(lock, () -> {
            System.out.println("Checking leases...");
            long now = System.currentTimeMillis();
            for (int i = 0; i < clients.size(); i++) {
                final Client client = clients.getClient(i);
                final String id = clients.getId(i);
                if (client.endOfLease < now) {
                    System.out.printf("Lease expired for %s%n", client.addr);
                    deregister(new DeregisterRequest(id));
                } else {
                    System.out.printf("Lease (%d) for %s is still valid%n", client.endOfLease, client.addr);
                }
            }
        }), LEASE_TIME, LEASE_TIME * 2);
    }

    private void handoff(HandoffRequest r, Message msg) {
        final int index = clients.indexOf(new Client(msg.getSender(), 0));
        if (index < 0) {
            System.out.printf("Handoff: Client %s not found%n", r.getFish().getTankId());
            return;
        }
        InetSocketAddress target = null;
        switch (r.getFish().getDirection()) {
            case Direction.LEFT:
                target = clients.getLeftNeighorOf(index).addr;
                break;
            case Direction.RIGHT:
                target = clients.getRightNeighorOf(index).addr;
                break;
            default:
                System.out.printf("Received unknown direction: %s%n", r.getFish().getDirection());
                break;
        }
        System.out.printf("sending handoff from %s to %s%n", msg.getSender(), target);
        endpoint.send(target, r);
    }

    private void deregister(DeregisterRequest r) {
        final String client_id = r.getId();
        final int index = clients.indexOf(client_id);
        if ( index < 0) {
            System.out.printf("Deregister: Client %s not found%n", client_id);
            return;
        }
        final Client leftNeighbour = clients.getLeftNeighorOf(index);
        final Client rightNeighbour = clients.getRightNeighorOf(index);

        endpoint.send(leftNeighbour.addr, new NeighbourUpdate(Direction.RIGHT, rightNeighbour.addr));
        endpoint.send(rightNeighbour.addr, new NeighbourUpdate(Direction.LEFT, leftNeighbour.addr));

        clients.remove(index);
    }

    private void register(Message msg) {
        long now = System.currentTimeMillis();
        final Client client = new Client(msg.getSender(), now + LEASE_TIME);
        // handle re-register with updated lease
        final int index = clients.indexOf(client);
        String client_id = "";
        if (index >= 0) {
            System.out.printf("Re-register: Client %s found, updating lease time%n", client_id);
            client_id = clients.getId(index);
            clients.remove(index);
        } else {
            client_id = String.format("client%d", client_counter.addAndGet(1));
        }
        clients.add(client_id, client);

        final int client_index = clients.indexOf(client_id);
        final Client leftNeighbour = clients.getLeftNeighorOf(client_index);
        final Client rightNeighbour = clients.getRightNeighorOf(client_index);

        endpoint.send(client.addr, new NeighbourUpdate(Direction.LEFT, leftNeighbour.addr));
        endpoint.send(client.addr, new NeighbourUpdate(Direction.RIGHT, rightNeighbour.addr));

        endpoint.send(leftNeighbour.addr, new NeighbourUpdate(Direction.RIGHT, client.addr));
        endpoint.send(rightNeighbour.addr, new NeighbourUpdate(Direction.LEFT, client.addr));

        // after the updateNeighbour messages have been sent, we can send the register response
        // so that the first fish is only spawned when the neighbours are ready
        endpoint.send(client.addr, new RegisterResponse(client_id, LEASE_TIME));

        if (clients.size() == 1) {
            endpoint.send(client.addr, new Token());
        }
    }

    private void brokerAsync() {
        final ReadWriteLock lock = new ReentrantReadWriteLock();
        try (ExecutorService service = Executors.newFixedThreadPool(8)) {
            stopRequestThread.start();
            startCheckLeaseTask(lock);
            while (running) {
                final Message msg = endpoint.nonBlockingReceive();
                if (msg == null) {
                    continue;
                }
                if (msg.getPayload() instanceof PoisonPill) {
                    running = false;
                    stopRequestThread.interrupt();
                    System.out.println("received PoisonPill, running = false");
                }
                service.execute(new BrokerTask(msg, lock));
            }
            service.shutdown();
        }
    }

    private final class BrokerTask implements Runnable {
        private final Message msg;
        private final ReadWriteLock lock;

        public BrokerTask(Message msg, ReadWriteLock lock) {
            this.msg = msg;
            this.lock = lock;
        }

        @Override
        public void run() {
            switch (msg.getPayload()) {
                case RegisterRequest ignored: {
                    lock.writeLock().lock();
                    register(msg);
                    lock.writeLock().unlock();
                    break;
                }
                case DeregisterRequest r: {
                    lock.writeLock().lock();
                    deregister(r);
                    lock.writeLock().unlock();
                    break;
                }
                case HandoffRequest r: {
                    lock.readLock().lock();
                    handoff(r, msg);
                    lock.readLock().unlock();
                    break;
                }
                case NameResolutionRequest r: {
                    Client c = clients.getClientById(r.getTankId());
                    if (c == null) {
                        System.out.printf("NameResolution: Client %s not found%n", r.getTankId());
                        return;
                    }
                    System.out.printf("NameResolution: Client %s found at %s%n", r.getTankId(), c.addr);
                    endpoint.send(msg.getSender(), new NameResolutionResponse(c.addr, r.getRequestId()));
                }
                default:
                    System.out.printf("Received unknown message: %s%n", msg.getPayload());
                    break;
            }
        }
    }

    public static void main(String[] args) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException {
        new Broker().brokerAsync();
    }
}
