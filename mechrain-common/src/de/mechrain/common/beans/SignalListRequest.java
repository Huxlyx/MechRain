package de.mechrain.common.beans;

/** 
 * CLI request for a snapshot of all registered signals (see {@link SignalListResponse}). 
 * */
public class SignalListRequest implements ICliBean {

	private static final long serialVersionUID = 1L;

	public static final SignalListRequest INSTANCE = new SignalListRequest();
}
