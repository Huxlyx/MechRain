package de.mechrain.cmdline.beans;

public class SetLedAllRgbRequest implements ICliBean {

	private static final long serialVersionUID = -6312363198916L;
	
	public final int r;
	public final int g;
	public final int b;
	
	public SetLedAllRgbRequest(final int r, final int g, final int b) {
		this.r = r;
		this.g = g;
		this.b = b;
	}
}
