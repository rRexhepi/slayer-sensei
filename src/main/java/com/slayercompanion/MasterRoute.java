package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Where the sidebar's "Path to" button in the plan sends Shortest Path: the master the goal says to visit next. Pure.
 * <ul>
 * <li>Slayer points: the master the routine names for the next task. A milestone task goes to the master who pays most
 * for it. Every other task, and a milestone no usable master pays for yet, goes to Turael's spot, or to Spria when she
 * gives tasks, so Shortest Path picks whichever is closer.</li>
 * <li>XP and profit: the best ranked master.</li>
 * </ul>
 * The next task follows the streak, so this works with or without a task in progress.
 */
final class MasterRoute
{
	/**
	 * The tile each master stands on, from the OSRS Wiki's NPC infobox maps (read 5 October 2026). Each was checked against
	 * Shortest Path 1.20.8's collision map and transport tables with tools/wiki-extract (WalkableCheck and
	 * ReachabilityCheck). A master who replaces another stands on the same spot. Krystilia's own tile is blocked in that
	 * map, so the tile south of it is used. Shortest Path has no way into Wyrmscraig Cavern, so Mortimer has no tile.
	 */
	private static final Map<SlayerAssigner, WorldPoint> SPOTS = new EnumMap<>(SlayerAssigner.class);

	static
	{
		// Turael, and Aya after While Guthix Sleeps, in Burthorpe
		SPOTS.put(SlayerAssigner.TURAEL, new WorldPoint(2931, 3536, 0));
		SPOTS.put(SlayerAssigner.SPRIA, new WorldPoint(3091, 3267, 0));
		// Mazchna, and Achtryn while he is away, in Canifis
		SPOTS.put(SlayerAssigner.MAZCHNA, new WorldPoint(3511, 3509, 0));
		SPOTS.put(SlayerAssigner.VANNAKA, new WorldPoint(3147, 9914, 0));
		SPOTS.put(SlayerAssigner.CHAELDAR, new WorldPoint(2444, 4431, 0));
		SPOTS.put(SlayerAssigner.KONAR, new WorldPoint(1309, 3785, 0));
		// Nieve, or Steve after Monkey Madness II, in the Tree Gnome Stronghold
		SPOTS.put(SlayerAssigner.NIEVE, new WorldPoint(2432, 3423, 0));
		// Duradel, and Kuradal after While Guthix Sleeps, upstairs in Shilo Village
		SPOTS.put(SlayerAssigner.DURADEL, new WorldPoint(2869, 2982, 1));
		// The wiki's tile, 3109 3516, is blocked in Shortest Path's map. This is the tile south of it.
		SPOTS.put(SlayerAssigner.KRYSTILIA, new WorldPoint(3109, 3515, 0));
	}

	/** Where the button goes. */
	@Value
	static class Target
	{
		/** The button's label, "Path to Aya or Spria", or "No path to Mortimer" when Shortest Path cannot get there. */
		String label;
		/** The tiles to route to, the closest one wins. Empty when Shortest Path cannot reach the master. */
		List<WorldPoint> points;
		/** The button's tooltip: where the route goes, or why there is none. */
		String tooltip;

		boolean isRoutable()
		{
			return !points.isEmpty();
		}
	}

	private MasterRoute()
	{
	}

	/** The tile Shortest Path routes to for a master, or null when it cannot reach the master. */
	static WorldPoint spot(SlayerAssigner master)
	{
		return master == null ? null : SPOTS.get(master);
	}

	/** Where the plan's goal sends the player next, or null when the plan names no master. */
	static Target target(SlayerPlanBuilder.View view)
	{
		if (view == null) return null;
		if (view.getGoal() == SlayerGoal.POINTS)
		{
			final SlayerPointsRoutine.Advice routine = view.getRoutine();
			if (routine == null) return null;
			if (!routine.getRanking().isEmpty())
			{
				final SlayerPointsRoutine.Ranked best = routine.getRanking().get(0);
				return to(Collections.singletonList(best.getMaster()), Collections.singletonList(best.getNpc()));
			}
			final List<SlayerAssigner> masters = new ArrayList<>();
			final List<String> npcs = new ArrayList<>();
			masters.add(SlayerAssigner.TURAEL);
			npcs.add(npc(view, SlayerAssigner.TURAEL));
			final SlayerMasterAccess.Option spria = SlayerMasterAccess.find(view.getMasters(), SlayerAssigner.SPRIA);
			if (spria != null && spria.isUsable())
			{
				masters.add(SlayerAssigner.SPRIA);
				npcs.add(spria.getNpc());
			}
			return to(masters, npcs);
		}
		final List<SlayerMasterRanker.Ranked> ranking = view.getRanking();
		if (ranking.isEmpty()) return null;
		return to(Collections.singletonList(ranking.get(0).getMaster()), Collections.singletonList(ranking.get(0).getNpc()));
	}

	/** A route to any of {@code masters}, named by the NPC at each spot. Masters Shortest Path cannot reach are left out. */
	private static Target to(List<SlayerAssigner> masters, List<String> npcs)
	{
		final List<WorldPoint> points = new ArrayList<>();
		final List<String> reachable = new ArrayList<>();
		final List<String> places = new ArrayList<>();
		for (int i = 0; i < masters.size(); i++)
		{
			final WorldPoint spot = spot(masters.get(i));
			if (spot == null) continue;
			points.add(spot);
			reachable.add(npcs.get(i));
			places.add(npcs.get(i) + " in " + masters.get(i).getLocation());
		}
		if (points.isEmpty())
		{
			return new Target("No path to " + String.join(" or ", npcs), Collections.<WorldPoint>emptyList(),
					"Shortest Path cannot reach " + String.join(" or ", npcs) + " in " + masters.get(0).getLocation() + ".");
		}
		return new Target("Path to " + String.join(" or ", reachable), Collections.unmodifiableList(points),
				"Draw the route to " + String.join(" or ", places) + " with Shortest Path"
						+ (points.size() > 1 ? ". It picks the closer one." : ""));
	}

	private static String npc(SlayerPlanBuilder.View view, SlayerAssigner master)
	{
		final SlayerMasterAccess.Option option = SlayerMasterAccess.find(view.getMasters(), master);
		return option == null ? master.getDisplayName() : option.getNpc();
	}
}
