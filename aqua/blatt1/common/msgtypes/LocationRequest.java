package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class LocationRequest implements Serializable {
    private final String id;

    public LocationRequest(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
