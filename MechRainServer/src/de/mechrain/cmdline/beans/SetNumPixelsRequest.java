package de.mechrain.cmdline.beans;

public class SetNumPixelsRequest implements ICliBean {

	private static final long serialVersionUID = -63525123198916L;
	
	public final int numPixels;
	
	public SetNumPixelsRequest(final int numPixels) {
		this.numPixels = numPixels;
	}

}
