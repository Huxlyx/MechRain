package de.mechrain.cmdline.beans;

public class SetDescriptionRequest implements ICliBean {

	private static final long serialVersionUID = 6518022329117041920L;
	
	public final String description;
	
	public SetDescriptionRequest(final String description) {
		this.description = description;
	}
}
