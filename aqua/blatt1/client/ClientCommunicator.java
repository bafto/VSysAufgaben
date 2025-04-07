package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
	private final Endpoint endpoint;

	public ClientCommunicator() {
		endpoint = new Endpoint();
	}

	public class ClientForwarder {
		private final InetSocketAddress broker;

		private ClientForwarder() {
			this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
		}

		public void register() {
			endpoint.send(broker, new RegisterRequest());
		}

		public void deregister(String id) {
			endpoint.send(broker, new DeregisterRequest(id));
		}

		public void handOff(FishModel fish, InetSocketAddress client) {
			if (client == null) {
				return;
			}
			System.out.println("Handing off fish to " + client);
			endpoint.send(client, new HandoffRequest(fish));
		}

		public void handoverToken(InetSocketAddress leftNeighbour) {
			if (leftNeighbour == null) {
				return;
			}
			endpoint.send(leftNeighbour, new Token());
		}

		public void sendSnapshotMarker(InetSocketAddress client) {
			if (client == null) {
				return;
			}
			System.out.println("sending snapshot marker to " + client);
			endpoint.send(client, new SnapshotMarker());
		}

		public void sendSnapshotToken(InetSocketAddress leftNeighbour, int sn) {
			if (leftNeighbour == null) {
				return;
			}
			System.out.println("sending snapshot token to " + leftNeighbour);
			endpoint.send(leftNeighbour, new SnapshotToken(sn));
		}
	}

	public class ClientReceiver extends Thread {
		private final TankModel tankModel;

		private ClientReceiver(TankModel tankModel) {
			this.tankModel = tankModel;
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Message msg = endpoint.blockingReceive();

				if (msg.getPayload() instanceof RegisterResponse)
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighbourUpdate u) {
					switch (u.getDirection()) {
						case Direction.LEFT:
							System.out.println("Received neighbour update left: " + u.getNewNeighbour().toString());
							tankModel.setLeftNeighbour(u.getNewNeighbour());
							break;
						case Direction.RIGHT:
							System.out.println("Received neighbour update right: " + u.getNewNeighbour().toString());
							tankModel.setRightNeighbour(u.getNewNeighbour());
							break;
					}
				}

				if (msg.getPayload() instanceof Token) {
					tankModel.receiveToken();
				}

				if (msg.getPayload() instanceof SnapshotMarker) {
					tankModel.receiveSnapshotMarker(msg.getSender());
				}

				if (msg.getPayload() instanceof SnapshotToken t) {
					tankModel.receiveSnapshotToken(t);
				}
			}
			System.out.println("Receiver stopped.");
		}
	}

	public ClientForwarder newClientForwarder() {
		return new ClientForwarder();
	}

	public ClientReceiver newClientReceiver(TankModel tankModel) {
		return new ClientReceiver(tankModel);
	}

}
