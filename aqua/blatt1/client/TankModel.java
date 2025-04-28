package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.LocationRequest;
import aqua.blatt1.common.msgtypes.SnapshotToken;

import javax.swing.*;

public class TankModel extends Observable implements Iterable<FishModel> {
	private enum RecordingState {
		IDLE, LEFT, RIGHT, BOTH
	}

	private enum FishLocation {
		HERE, LEFT, RIGHT
	}

	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	protected final ClientCommunicator.ClientForwarder forwarder;

	protected InetSocketAddress leftNeighbour = null;
	protected InetSocketAddress rightNeighbour = null;

	private volatile boolean token = false;
    private static final int TOKEN_TIMEOUT = 3000;

	private Integer snapshotState = null;
	private RecordingState recordingState = RecordingState.IDLE;
	private Function<Integer, Void> snapshotCollectionCallback;

	private final Map<String, FishLocation> fishLocations = new ConcurrentHashMap<>();

	public TankModel(ClientCommunicator.ClientForwarder forwarder) {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.forwarder = forwarder;
	}

	public synchronized void setLeftNeighbour(InetSocketAddress leftNeighbour) {
		this.leftNeighbour = leftNeighbour;
	}

	public synchronized void setRightNeighbour(InetSocketAddress rightNeighbour) {
		this.rightNeighbour = rightNeighbour;
	}

	private int calculateLocalState() {
		return (int)fishies.stream().filter(Predicate.not(FishModel::isDeparting)).count();
	}

	public synchronized void initiateSnapshot(Function<Integer, Void> snapshotCollectionCallback) {
		System.out.println("Initiating snapshot for Client " + id);
		this.recordingState = RecordingState.BOTH;
		this.snapshotState = calculateLocalState();
		this.snapshotCollectionCallback = snapshotCollectionCallback;
		forwarder.sendSnapshotMarker(leftNeighbour);
		forwarder.sendSnapshotMarker(rightNeighbour);
	}

	public void receiveSnapshotToken(SnapshotToken t) {
		if (snapshotState == null) {
			snapshotCollectionCallback.apply(t.getState());
			snapshotCollectionCallback = null;
			return;
		}
		forwarder.sendSnapshotToken(leftNeighbour, t.getState() + snapshotState);
		this.snapshotState = null;
	}

	public void receiveSnapshotMarker(InetSocketAddress sender) {
		System.out.println("Received snapshot marker from " + sender + " for Client " + id);
		switch (recordingState) {
		case IDLE:
			System.out.println("starting snapshot for Client " + id);
			snapshotState = calculateLocalState();
			if (sender.equals(leftNeighbour)) {
				recordingState = RecordingState.RIGHT;
			} else if (sender.equals(rightNeighbour)) {
				recordingState = RecordingState.LEFT;
			}
			forwarder.sendSnapshotMarker(leftNeighbour);
			forwarder.sendSnapshotMarker(rightNeighbour);
			break;
		case LEFT:
			if (!sender.equals(leftNeighbour)) {
				break;
			}
			recordingState = RecordingState.IDLE;
			break;
		case RIGHT:
			if (!sender.equals(rightNeighbour)) {
				break;
			}
			recordingState = RecordingState.IDLE;
			break;
		case BOTH:
			if (sender.equals(leftNeighbour)) {
				recordingState = RecordingState.RIGHT;
			} else if (sender.equals(rightNeighbour)) {
				recordingState = RecordingState.LEFT;
			}
			break;
		}

		if (recordingState == RecordingState.IDLE) {
			System.out.println(String.format("Snapshot beendet für Client %s, Local State: ", id) + snapshotState);
			if (snapshotCollectionCallback != null) {
				forwarder.sendSnapshotToken(leftNeighbour, snapshotState);
				this.snapshotState = null;
			}
		}
	}

	public synchronized void receiveToken() {
		this.token = true;
        Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (hasToken()) {
					token = false;
					forwarder.handoverToken(leftNeighbour);
				}
			}
		}, TOKEN_TIMEOUT);
	}

	public synchronized boolean hasToken() {
		return this.token;
	}

	synchronized void onRegistration(String id) {
		this.id = id;
		newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
	}

	public synchronized void newFish(int x, int y) {
		if (fishies.size() < MAX_FISHIES) {
			x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
			y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

			FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
					rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

			fishies.add(fish);
			fishLocations.put(fish.getId(), FishLocation.HERE);
		}
	}

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
		fishLocations.put(fish.getId(), FishLocation.HERE);

		switch (recordingState) {
		case IDLE:
			break;
		case LEFT:
			if (fish.getDirection() == Direction.RIGHT) {
				snapshotState++;
			}
			break;
		case RIGHT:
			if (fish.getDirection() == Direction.LEFT) {
				snapshotState++;
			}
			break;
		case BOTH:
			snapshotState++;
			break;
		}
	}

	public String getId() {
		return id;
	}

	public synchronized int getFishCounter() {
		return fishCounter;
	}

	public synchronized Iterator<FishModel> iterator() {
		return fishies.iterator();
	}

	private synchronized void updateFishies() {
		for (Iterator<FishModel> it = iterator(); it.hasNext();) {
			FishModel fish = it.next();

			fish.update();

			if (fish.hitsEdge()) {
				if (hasToken()) {
				forwarder.handOff(fish,
						fish.getDirection() == Direction.LEFT ? leftNeighbour : rightNeighbour);
				fishLocations.put(fish.getId(),
						fish.getDirection() == Direction.LEFT ? FishLocation.LEFT : FishLocation.RIGHT);
				} else {
					fish.reverse();
				}
			}

			if (fish.disappears())
				it.remove();
		}
	}

	private synchronized void update() {
		updateFishies();
		setChanged();
		notifyObservers();
	}

	protected void run() {
		forwarder.register();

		try {
			while (!Thread.currentThread().isInterrupted()) {
				update();
				TimeUnit.MILLISECONDS.sleep(10);
			}
		} catch (InterruptedException consumed) {
			// allow method to terminate
		}
	}

	public synchronized void finish() {
		forwarder.deregister(id);
		if (hasToken()) {
			forwarder.handoverToken(leftNeighbour);
		}
	}

	public synchronized void receiveLocationResponse(final LocationRequest r) {
		locateFishGlobally(r.getId());
	}

	public synchronized void locateFishGlobally(final String id) {
		// search left neighbour if the fish was never here
		FishLocation loc = fishLocations.getOrDefault(id, FishLocation.LEFT);

		if (loc == FishLocation.HERE) {
			for (FishModel fish : fishies) {
				if (fish.getId().equals(id)) {
					fish.toggle();
					return;
				}
			}
		} else {
			forwarder.sendLocationRequest(id,
					loc == FishLocation.LEFT ? leftNeighbour : rightNeighbour);
		}
	}
}
