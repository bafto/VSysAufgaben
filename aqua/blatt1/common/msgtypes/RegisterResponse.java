package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final String id;
	private final long leaseTime;

	public RegisterResponse(String id, long leaseTime) {
		this.id = id;
		this.leaseTime = leaseTime;
	}

	public String getId() {
		return id;
	}
	public long getLeaseTime() {
		return leaseTime;
	}

}
