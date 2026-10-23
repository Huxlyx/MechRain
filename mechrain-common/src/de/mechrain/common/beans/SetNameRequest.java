package de.mechrain.common.beans;

public class SetNameRequest implements ICliBean {

	private static final long serialVersionUID = 4189026471553812906L;
	
	public final String name;
	
	public SetNameRequest(final String name) {
		this.name = name;
	}
}
