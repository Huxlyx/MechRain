package de.mechrain.common.beans;

public class ServerInfoResponse implements ICliBean {

	private static final long serialVersionUID = 1L;

	private String version;

	public ServerInfoResponse() {}

	public ServerInfoResponse(final String version) {
		this.version = version;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}
}
