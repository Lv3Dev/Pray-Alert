package com.prayalert.model;

public enum DurationMode
{
	UNTIL_ANIMATION_ENDS("Until animation ends"),
	FOR_TICKS("For N ticks");

	private final String label;

	DurationMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
