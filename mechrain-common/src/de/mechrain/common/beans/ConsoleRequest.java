package de.mechrain.common.beans;

public class ConsoleRequest implements ICliBean {
	
	private static final long serialVersionUID = 7309872534066016698L;
	
	private String request;
	private String[] suggestions;

	public String getRequest() {
		return request;
	}

	public void setRequest(String request) {
		this.request = request;
	}

	public String[] getSuggestions() {
		return suggestions;
	}

	public void setSuggestions(final String[] suggestions) {
		this.suggestions = suggestions;
	}

}
