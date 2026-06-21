package de.mechrain.common;

/**
 * Single source of truth for the CLI ↔ Server protocol version.
 * Increment this constant whenever a breaking change is made to the CLI bean set.
 */
public final class ProtocolVersion {

	public static final int PROTOCOL_VERSION = 2;

	private ProtocolVersion() {}
}
