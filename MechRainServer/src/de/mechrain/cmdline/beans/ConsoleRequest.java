package de.mechrain.cmdline.beans;

public class ConsoleRequest implements ICliBean {
	
	private static final long serialVersionUID = -1292473863546144221L;
	
	private String request;

	public String getRequest() {
		return request;
	}

	public void setRequest(String request) {
		this.request = request;
	}

}
