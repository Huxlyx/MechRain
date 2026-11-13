package de.mechrain.common.beans;

/**
 * CLI request to enter interactive signal creation. The server prompts for
 * signal type and type-specific fields via {@link ConsoleRequest}/{@link ConsoleResponse}.
 */
public class AddSignalRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public static final AddSignalRequest INSTANCE = new AddSignalRequest();
}
