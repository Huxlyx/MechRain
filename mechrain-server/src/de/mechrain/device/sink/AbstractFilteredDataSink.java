package de.mechrain.device.sink;

import java.util.List;
import java.util.stream.Collectors;

import de.mechrain.protocol.MRP;

/**
 * Abstract base class for data sinks that apply a filter to incoming data.
 */
public abstract class AbstractFilteredDataSink extends AbstractDataSink {
    
	private static final long serialVersionUID = -5083665113119106450L;
	
	protected final List<MRP> filter;
	
	protected AbstractFilteredDataSink(final List<MRP> filter) {
		this.filter = filter;
	}

	@Override
	public List<String> getFilterNames() {
		if (filter == null) {
			return null;
		}
		return filter.stream().map(MRP::name).collect(Collectors.toList());
	}
}
