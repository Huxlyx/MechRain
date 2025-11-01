package de.mechrain.cmdline.beans;

public class RemoveSinkRequest implements ICliBean {

	private static final long serialVersionUID = 1234567890123456789L;
	
	public final int id;

	public RemoveSinkRequest(int id) {
		this.id = id;
	}
}
