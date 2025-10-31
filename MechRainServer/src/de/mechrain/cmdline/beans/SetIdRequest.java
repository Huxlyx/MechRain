package de.mechrain.cmdline.beans;

public class SetIdRequest implements ICliBean {

	private static final long serialVersionUID = -6352537122149098916L;
	
	public final int newId;
	
	public SetIdRequest(final int newId) {
		this.newId = newId;
	}

}
