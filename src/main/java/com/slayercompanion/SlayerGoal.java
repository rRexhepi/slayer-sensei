package com.slayercompanion;

import lombok.AllArgsConstructor;

/**
 * What the player wants from Slayer. The points plan is built for it, and the Slayer points goal adds the
 * next-task routine. Stored in config by constant name, so the names must not change.
 */
@AllArgsConstructor
public enum SlayerGoal
{
	XP("XP"),
	PROFIT("Profit"),
	POINTS("Slayer points");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
