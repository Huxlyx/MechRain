package de.mechrain.common.beans;

public class ServerInfoResponse implements ICliBean {

	private static final long serialVersionUID = 2L;

	private String version;
	private int protocolVersion;

	public ServerInfoResponse() {}

	public ServerInfoResponse(final String version, final int protocolVersion) {
		this.version = version;
		this.protocolVersion = protocolVersion;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(final int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}
}
