package de.mechrain.device.sink;

public abstract class AbstractDataSink implements IDataSink {
	
	private int id;
	
	@Override
	public int getId() {
		return id;
	}
	
	public void setId(final int id) {
		this.id = id;
	}

	private static final long serialVersionUID = 884828949282878085L;

}
