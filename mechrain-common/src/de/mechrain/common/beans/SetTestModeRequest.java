package de.mechrain.common.beans;

/**
 * Request to enable or disable the device's test mode, which changes the discovery
 * broadcast/response strings it uses (e.g. so it can be discovered by a test server
 * without interfering with production hardware on the same network).
 */
public class SetTestModeRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public final boolean enabled;

	public SetTestModeRequest(final boolean enabled) {
		this.enabled = enabled;
	}

}
