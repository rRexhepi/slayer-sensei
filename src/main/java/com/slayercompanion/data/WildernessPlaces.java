package com.slayercompanion.data;

import java.util.List;
import lombok.Data;

/**
 * Where one of Krystilia's tasks can be done, from /slayer_areas.json. Her tasks only count in the
 * Wilderness, so this list holds only places her wiki page names, and only points checked to lie in the
 * Wilderness: the marker point and kill spots here replace the place's own, which may reach outside it.
 * Label, facts and notes still come from the place in {@code locations[]}.
 */
@Data
public class WildernessPlaces
{
	/** Value of the SLAYER_TARGET varp (SlayerTask COL_ID). */
	private int taskId;
	/** Readable task name, for auditing only. */
	private String task;
	/** In display order, the recommended one first. */
	private List<Place> places;
	/** Key of the recommended place. When absent, the first listed place. */
	private String recommended;
	private String source;

	@Data
	public static class Place
	{
		/** Key of the place in locations[]. */
		private String key;
		/** The name Krystilia's wiki page uses, which can be narrower than the place, such as "South of the Lava Maze". */
		private String label;
		/** Marker point in the Wilderness. Absent when none is verified. */
		private int[] entrance;
		private String entranceLabel;
		private String entranceSource;
		/** False when Shortest Path cannot reach the entrance, so it is shown on the map but never routed to. */
		private Boolean entranceRoutable;
		private String entranceRoutableSource;
		/** Kill spots in the Wilderness, usable as pathfinding targets. */
		private int[][] spots;
		private String spotsSource;
	}
}
