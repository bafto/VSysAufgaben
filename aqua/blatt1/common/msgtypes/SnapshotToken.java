package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class SnapshotToken implements Serializable {
    private final int state;

    public SnapshotToken(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }
}
