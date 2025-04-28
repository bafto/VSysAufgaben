package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {
    private final InetSocketAddress address;
    private final String requestId;

    public NameResolutionResponse(InetSocketAddress address, String requestId) {
        this.address = address;
        this.requestId = requestId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public String getRequestId() {
        return requestId;
    }
}
