package com.slayercompanion;

import lombok.AllArgsConstructor;

/** How Slayer Sensei asks the Shortest Path plugin to draw the route to the task. */
@AllArgsConstructor
public enum PathMode
{
	OFF("Off"),
	BUTTON("Sidebar button only"),
	NEW_TASK("New task and button");

	private final String displayName;

	boolean showsButtons()
	{
		return this != OFF;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
