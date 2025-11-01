package de.mechrain.cmdline.beans;

public class RemoveTaskRequest implements ICliBean {

	private static final long serialVersionUID = 987654321098765432L;
	
	public final int id;

	public RemoveTaskRequest(int id) {
		this.id = id;
	}

}
