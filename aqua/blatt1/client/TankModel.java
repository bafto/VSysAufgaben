package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.SnapshotMarker;
import aqua.blatt1.common.msgtypes.SnapshotToken;

public class TankModel extends Observable implements Iterable<FishModel> {


	private static enum RecordingState {
		IDLE, LEFT, RIGHT, BOTH
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

	private Integer localState = null;
	private RecordingState recordingState = RecordingState.IDLE;
	private SnapshotToken snapshotToken = null;
	private volatile boolean isSnapshotInitiator = false;

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

	public synchronized void receiveSnapshotToken(SnapshotToken t) {
		this.snapshotToken = new SnapshotToken(localState + t.getState());
		this.localState = null;
		if (isSnapshotInitiator) {
			isSnapshotInitiator = false;
			return;
		}
		forwarder.handoverSnapshotToken(leftNeighbour, this.snapshotToken);
	}

	public synchronized SnapshotToken getSnapshotToken() {
		return this.snapshotToken;
	}

	public synchronized void setSnapshotInitiator(boolean b) {
		this.isSnapshotInitiator = b;
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

	private synchronized void initializeLocalState() {
		localState = (int)fishies.stream().filter(Predicate.not(FishModel::isDeparting)).count();
	}

	public synchronized void initiateSnapshot() {
		initializeLocalState();
		recordingState = RecordingState.BOTH;
		forwarder.sendSnapshotMarker(leftNeighbour);
		forwarder.sendSnapshotMarker(rightNeighbour);
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
		}
	}

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
		switch (fish.getDirection()) {
			case LEFT:
				if (recordingState == RecordingState.BOTH || recordingState == RecordingState.RIGHT) {
					localState++;
				}
				break;
			case RIGHT:
				if (recordingState == RecordingState.BOTH || recordingState == RecordingState.LEFT) {
					localState++;
				}
				break;
		}
	}

	synchronized void receiveSnapshotMarker(InetSocketAddress sender) {
		System.out.println("Received snapshot marker " + recordingState);
		if (!sender.equals(leftNeighbour) && !sender.equals(rightNeighbour)) {
			System.out.println("Received snapshot marker from unknown sender");
			return;
		}

		if (recordingState == RecordingState.IDLE) {
			initiateSnapshot();
			if (sender.equals(leftNeighbour)) {
				recordingState = RecordingState.RIGHT;
			}
			if (sender.equals(rightNeighbour)) {
				recordingState = RecordingState.LEFT;
			}
		} else {
			if (sender.equals(leftNeighbour)) {
				switch (recordingState) {
					case BOTH:
						recordingState = RecordingState.RIGHT;
						break;
					case LEFT:
						recordingState = RecordingState.IDLE;
						break;
				}
			}
			if (sender.equals(rightNeighbour)) {
				switch (recordingState) {
					case BOTH:
						recordingState = RecordingState.LEFT;
						break;
					case RIGHT:
						recordingState = RecordingState.IDLE;
						break;
				}
			}

			if (recordingState == RecordingState.IDLE) {
				System.out.println("Snapshot completed");
				System.out.printf("Local state: %d%n", localState);
				if (isSnapshotInitiator) {
					forwarder.handoverSnapshotToken(leftNeighbour, new SnapshotToken(localState));
				}
			}
		}
		System.out.println("Recording state: " + recordingState);
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

}
