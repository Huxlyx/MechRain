package de.mechrain.signal;

import java.io.Serializable;

/**
 * Base class for {@link ISignal} implementations, providing the common ID field.
 */
public abstract class AbstractSignal implements ISignal, Serializable {

	private static final long serialVersionUID = 1L;

	private int id;

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void setId(final int id) {
		this.id = id;
	}
}
