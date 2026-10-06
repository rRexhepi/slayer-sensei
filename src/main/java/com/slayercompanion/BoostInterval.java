package com.slayercompanion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Which milestone tasks the Slayer points routine takes to the master who pays most. Every other task goes
 * to Turael or Aya, who pay nothing but move the streak. Stored in config by constant name.
 */
@AllArgsConstructor
public enum BoostInterval
{
	EVERY_10TH("Every 10th task", 10),
	EVERY_50TH("Every 50th task", 50);

	private final String displayName;
	@Getter
	private final int every;

	/** Whether the routine takes task {@code taskNumber} of the streak to a high master. */
	boolean isBoostTask(int taskNumber)
	{
		return taskNumber > 0 && taskNumber % every == 0;
	}

	/** The first boost task after {@code taskNumber}. */
	int nextBoostTask(int taskNumber)
	{
		return (Math.max(0, taskNumber) / every + 1) * every;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
