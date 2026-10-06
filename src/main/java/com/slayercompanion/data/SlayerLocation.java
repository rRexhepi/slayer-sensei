package com.slayercompanion.data;

import java.util.List;
import lombok.Data;

/**
 * One place where Slayer tasks can be done, from /slayer_areas.json. {@code key} is the stable id the
 * plugin and the data file use. {@code areaId} is set only for places the game itself knows as a
 * Slayer area (the SLAYER_AREA varp and the SlayerArea table's COL_AREA_ID), which is how Konar's
 * assignments are matched. Points are {@code [x, y, plane]} in real game coordinates. Every point and
 * fact carries its source so the file can be audited and corrected without touching code.
 */
@Data
public class SlayerLocation
{
	private String key;
	private String label;
	/** Game area id, or 0 when the game has no SlayerArea row for this place. */
	private int areaId;
	/** DBTableID.SlayerArea.Row constant name, for auditing only. */
	private String row;
	/** Surface point for the world map marker. Absent when no verified point exists. */
	private int[] entrance;
	/** What the entrance point is, when that differs from the place itself. */
	private String entranceLabel;
	private String entranceSource;
	/** False when Shortest Path has no way to reach the entrance, so it is shown on the map but never routed to. */
	private Boolean entranceRoutable;
	private String entranceRoutableSource;
	/** "yes" or "no". Absent when unknown. */
	private String cannon;
	private String cannonNote;
	private String cannonSource;
	/** "multi", "single" or "mixed". Absent when unknown. */
	private String combat;
	private String combatSource;
	private String note;
	private String noteSource;
	/** What every task here needs to get in. Absent when the wiki shows no gate or the gate is not settled. */
	private Access access;
	/** Tasks that can be done here, with kill spots and facts that override the place-wide ones. */
	private List<TaskEntry> tasks;

	@Data
	public static class TaskEntry
	{
		/** Value of the SLAYER_TARGET varp (SlayerTask COL_ID). */
		private int taskId;
		/** Readable task name, for auditing only. */
		private String task;
		/** Spawn points usable as pathfinding targets. */
		private int[][] spots;
		private String spotsSource;
		private String cannon;
		private String cannonNote;
		private String cannonSource;
		private String combat;
		private String combatSource;
		private String note;
		private String noteSource;
		/** What this task needs here on top of the place's own access, when only its monsters lie further in. */
		private Access access;
	}
}
