package de.mechrain.common.beans;

/**
 * Sent by the CLI immediately after receiving {@link ServerInfoResponse},
 * advertising the client's protocol version so the server can detect mismatches.
 */
public class HandshakeRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	private int protocolVersion;

	public HandshakeRequest() {}

	public HandshakeRequest(final int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(final int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}
}
