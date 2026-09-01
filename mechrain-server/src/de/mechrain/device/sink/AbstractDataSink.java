package de.mechrain.device.sink;

import java.util.List;

public abstract class AbstractDataSink implements IDataSink {
	
	private int id;
	private Integer signalId;
	
	@Override
	public int getId() {
		return id;
	}
	
	@Override
	public void setId(final int id) {
		this.id = id;
	}

	@Override
	public Integer getSignalId() {
		return signalId;
	}

	@Override
	public void setSignalId(final Integer signalId) {
		this.signalId = signalId;
	}

	@Override
	public List<String> getFilterNames() {
		return null;
	}

	private static final long serialVersionUID = 884828949282878085L;

}
