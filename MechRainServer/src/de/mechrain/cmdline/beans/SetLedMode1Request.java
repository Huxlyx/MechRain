package de.mechrain.cmdline.beans;

public class SetLedMode1Request implements ICliBean {

	private static final long serialVersionUID = -6312363198916L;
	
	public final int mode;
	
	public SetLedMode1Request(final int mode) {
		this.mode = mode;
	}
}
