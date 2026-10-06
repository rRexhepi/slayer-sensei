package com.slayercompanion;

import lombok.AllArgsConstructor;

/** How much the sidebar says. Stored in config by constant name, so the names must not change. */
@AllArgsConstructor
public enum SidebarDetail
{
	/** Headings with one short item per line. The reasons are in tooltips. */
	COMPACT("Compact"),
	/** Every line as a sentence. */
	DETAILED("Detailed");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
