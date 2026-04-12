package de.mechrain.device.sink;

import java.util.List;

public abstract class AbstractDataSink implements IDataSink {
	
	private int id;
	
	@Override
	public int getId() {
		return id;
	}
	
	@Override
	public void setId(final int id) {
		this.id = id;
	}

	@Override
	public List<String> getFilterNames() {
		return null;
	}

	private static final long serialVersionUID = 884828949282878085L;

}
