package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.Properties;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Broker {
    private record Client(InetSocketAddress addr) {

        @Override
            public boolean equals(Object other) {
                if (other == this) {
                    return true;
                }
                if (!(other instanceof Client(InetSocketAddress addr1))) {
                    return false;
                }
            return addr.equals(addr1);
            }
        }

    private final Endpoint endpoint = new Endpoint(Properties.PORT);
    private final ClientCollection<Client> clients = new ClientCollection<>();
    private final AtomicInteger client_counter = new AtomicInteger();
    Thread stopRequestThread = new Thread(() -> {
        JOptionPane.showMessageDialog(null, "Press OK button to stop the broker");
        running = false;
    });
    private volatile boolean running = true;

    private void handoff(HandoffRequest r, Message msg) {
        final int index = clients.indexOf(new Client(msg.getSender()));
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
        final String client_id = String.format("client%d", client_counter.addAndGet(1));
        final Client client = new Client(msg.getSender());
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
        endpoint.send(client.addr, new RegisterResponse(client_id));

        if (clients.size() == 1) {
            endpoint.send(client.addr, new Token());
        }
    }

    private void brokerAsync() {
        final ReadWriteLock lock = new ReentrantReadWriteLock();
        try (ExecutorService service = Executors.newFixedThreadPool(8)) {
            stopRequestThread.start();
            while (running) {
                final Message msg = endpoint.nonBlockingReceive();
                if (msg == null) {
                    continue;
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
                case PoisonPill ignored: {
                    running = false;
                    stopRequestThread.interrupt();
                    System.out.println("received PoisonPill, running = false");
                    break;
                }
                default:
                    System.out.printf("Received unknown message: %s%n", msg.getPayload());
                    break;
            }
        }
    }

    public static void main(String[] args) {
        new Broker().brokerAsync();
    }
}
