package com.slayercompanion;

import com.slayercompanion.data.SlayerLocation;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskLocations;
import com.slayercompanion.data.WildernessPlaces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Where the current task should be done. {@link #resolve} is the single place that decides it, and the
 * sidebar's "Where to go", the world map marker and the Shortest Path target all read the result.
 * Facts that are unknown stay null so the UI can leave the line out instead of borrowing advice that
 * was written for some other place.
 */
@Value
public class Destination
{
	public enum Origin
	{
		/** Konar fixed the area. Kills elsewhere do not count. */
		ASSIGNED,
		/**
		 * A place other than the data's recommendation, for a task without an assigned area: the player's pick, or the
		 * first place the account can enter when the recommended one is locked ({@link TaskPlaces}).
		 */
		PREFERRED,
		/** The data file's or the dossier's recommendation. */
		RECOMMENDED
	}

	/** The surface map ends below this y. Underground and instanced areas lie above it. */
	static final int SURFACE_MAX_Y = 4160;

	Origin origin;
	/** Location key in the data file, or null when the file does not cover this place. */
	String key;
	/** Game area id when Konar assigned the area, otherwise 0. */
	int areaId;
	String label;
	/**
	 * Point for the world map marker: the place's verified entrance, or for a place without one, its first
	 * kill spot on the surface map. Null when no verified point exists.
	 */
	WorldPoint entrance;
	String entranceLabel;
	/** False when Shortest Path cannot reach the entrance, so it is never sent as a route target. */
	boolean entranceRoutable;
	String cannon;
	String combat;
	String note;
	/** Verified spawn points for this task at this place. Empty when none are known. */
	List<WorldPoint> killSpots;

	public boolean hasEntrance()
	{
		return entrance != null;
	}

	/** What the world map marker points at. */
	public String markerLabel()
	{
		return entranceLabel != null ? entranceLabel : label;
	}

	/** Kill spots when known, else the entrance. Empty when neither is verified and reachable. */
	public List<WorldPoint> routeTargets()
	{
		if (!killSpots.isEmpty()) return killSpots;
		return entrance == null || !entranceRoutable ? Collections.emptyList() : Collections.singletonList(entrance);
	}

	public String routeDescription()
	{
		if (!killSpots.isEmpty()) return "the " + label + " kill spots";
		return entrance == null || !entranceRoutable ? null : markerLabel();
	}

	/** Why there is no route, or null when there is one. */
	public String noRouteReason()
	{
		if (!routeTargets().isEmpty()) return null;
		if (entrance != null) return "Shortest Path cannot route to " + label + ".";
		return "No verified point for " + label + " yet.";
	}

	/**
	 * Decides the destination for the current task.
	 *
	 * @param taskId value of the SLAYER_TARGET varp, or 0 when unknown
	 * @param dossier the task's dossier entry, used for the recommendation when the data file has none
	 * @param assignedAreaId value of the SLAYER_AREA varp, 0 when the master did not assign an area
	 * @param gameAreaName the assigned area's name from the game's SlayerArea table, used when the data
	 *        file does not cover the area yet
	 * @param preferredKey location key of the place in use: the player's pick, or the first place the account can
	 *        enter when the recommended one is locked. Null for the recommendation. It counts only while the
	 *        task's list holds it, so a key from older data falls back to the recommendation without fuss.
	 * @return the destination, or null when no place is known at all
	 */
	static Destination resolve(int taskId, SlayerTask dossier, int assignedAreaId, String gameAreaName,
			String preferredKey, SlayerLocations data)
	{
		return resolve(taskId, dossier, assignedAreaId, gameAreaName, preferredKey, data, false);
	}

	/**
	 * As above, for a task that only counts in the Wilderness when {@code wilderness} is set (Krystilia's).
	 * Such a task resolves only to a place from its Wilderness list, with only the points checked to lie in
	 * the Wilderness, and to nothing at all without one: never to the general recommendation, which can
	 * send the player where kills do not count.
	 */
	static Destination resolve(int taskId, SlayerTask dossier, int assignedAreaId, String gameAreaName,
			String preferredKey, SlayerLocations data, boolean wilderness)
	{
		if (wilderness && assignedAreaId <= 0)
		{
			return resolveWilderness(taskId, preferredKey, data);
		}
		if (assignedAreaId > 0)
		{
			final SlayerLocation location = data.byAreaId(assignedAreaId);
			if (location != null)
			{
				return fromLocation(Origin.ASSIGNED, location, assignedAreaId, taskId);
			}
			final String label = notBlank(gameAreaName) ? gameAreaName.trim() : "area " + assignedAreaId;
			return new Destination(Origin.ASSIGNED, null, assignedAreaId, label, null, null, false, null, null, null,
					Collections.emptyList());
		}
		final String recommendedKey = recommendedKey(taskId, dossier, data);
		final TaskLocations list = data.forTask(taskId);
		if (preferredKey != null && !preferredKey.equals(recommendedKey) && list != null && list.getLocations() != null
				&& list.getLocations().contains(preferredKey))
		{
			return fromLocation(Origin.PREFERRED, data.byKey(preferredKey), 0, taskId);
		}
		final SlayerLocation recommended = data.byKey(recommendedKey);
		if (recommended != null)
		{
			return fromLocation(Origin.RECOMMENDED, recommended, 0, taskId);
		}
		final TaskDestination generic = TaskDestination.forTask(dossier);
		if (generic != null)
		{
			return new Destination(Origin.RECOMMENDED, generic.getKey(), 0, generic.getLabel(), generic.getPoint(),
					null, true, null, null, null, Collections.emptyList());
		}
		return null;
	}

	/**
	 * The place to suggest when the master leaves it open: the data file's recommendation, else the
	 * dossier's place when the task lists it, else the first listed place. A task without a list gets the
	 * dossier's place, which may lie outside the data file. Null when nothing is known.
	 */
	static String recommendedKey(int taskId, SlayerTask dossier, SlayerLocations data)
	{
		final TaskLocations list = data.forTask(taskId);
		final TaskDestination generic = TaskDestination.forTask(dossier);
		if (list == null || list.getLocations() == null || list.getLocations().isEmpty())
		{
			return generic == null ? null : generic.getKey();
		}
		if (list.getRecommended() != null) return list.getRecommended();
		if (generic != null && list.getLocations().contains(generic.getKey())) return generic.getKey();
		return list.getLocations().get(0);
	}

	private static Destination resolveWilderness(int taskId, String preferredKey, SlayerLocations data)
	{
		final WildernessPlaces list = data.forWildernessTask(taskId);
		if (list == null || list.getPlaces().isEmpty()) return null;
		final String recommendedKey = recommendedWildernessKey(list);
		WildernessPlaces.Place chosen = null;
		for (WildernessPlaces.Place place : list.getPlaces())
		{
			if (place.getKey().equals(preferredKey)) chosen = place;
		}
		final boolean preferred = chosen != null && !chosen.getKey().equals(recommendedKey);
		if (chosen == null)
		{
			for (WildernessPlaces.Place place : list.getPlaces())
			{
				if (place.getKey().equals(recommendedKey)) chosen = place;
			}
		}
		final SlayerLocation location = data.byKey(chosen.getKey());
		final SlayerLocation.TaskEntry task = taskEntry(location, taskId);
		final String cannon = task != null && task.getCannon() != null
				? cannonText(task.getCannon(), task.getCannonNote())
				: cannonText(location.getCannon(), location.getCannonNote());
		final String combat = combatText(task != null && task.getCombat() != null ? task.getCombat() : location.getCombat());
		final String note = joinNotes(location.getNote(), task == null ? null : task.getNote());
		final List<WorldPoint> spots = points(chosen.getSpots());
		final WorldPoint entrance = SlayerLocations.validPoint(chosen.getEntrance()) ? point(chosen.getEntrance()) : null;
		final String label = notBlank(chosen.getLabel()) ? chosen.getLabel().trim() : label(location);
		final Origin origin = preferred ? Origin.PREFERRED : Origin.RECOMMENDED;
		if (entrance == null)
		{
			return new Destination(origin, chosen.getKey(), 0, label, surfacePoint(spots), null, true, cannon, combat, note, spots);
		}
		return new Destination(origin, chosen.getKey(), 0, label, entrance, blankToNull(chosen.getEntranceLabel()),
				!Boolean.FALSE.equals(chosen.getEntranceRoutable()), cannon, combat, note, spots);
	}

	/** The recommended place of a Krystilia list, else its first place. */
	static String recommendedWildernessKey(WildernessPlaces list)
	{
		if (list.getRecommended() != null) return list.getRecommended();
		return list.getPlaces().isEmpty() ? null : list.getPlaces().get(0).getKey();
	}

	private static Destination fromLocation(Origin origin, SlayerLocation location, int areaId, int taskId)
	{
		final SlayerLocation.TaskEntry task = taskEntry(location, taskId);
		final String cannon = task != null && task.getCannon() != null
				? cannonText(task.getCannon(), task.getCannonNote())
				: cannonText(location.getCannon(), location.getCannonNote());
		final String combat = combatText(task != null && task.getCombat() != null ? task.getCombat() : location.getCombat());
		final String note = joinNotes(location.getNote(), task == null ? null : task.getNote());
		final List<WorldPoint> spots = task == null ? Collections.emptyList() : points(task.getSpots());
		final WorldPoint entrance = SlayerLocations.validPoint(location.getEntrance()) ? point(location.getEntrance()) : null;
		if (entrance == null)
		{
			// Places found only through spawn lists often have no entrance. A kill spot on the surface map is
			// a verified point too, and Shortest Path can reach it by construction.
			return new Destination(origin, location.getKey(), areaId, label(location), surfacePoint(spots), null, true,
					cannon, combat, note, spots);
		}
		return new Destination(origin, location.getKey(), areaId, label(location), entrance,
				blankToNull(location.getEntranceLabel()), !Boolean.FALSE.equals(location.getEntranceRoutable()),
				cannon, combat, note, spots);
	}

	/** The first point on the surface map, or null. */
	static WorldPoint surfacePoint(List<WorldPoint> points)
	{
		for (WorldPoint p : points)
		{
			if (p.getPlane() == 0 && p.getY() < SURFACE_MAX_Y) return p;
		}
		return null;
	}

	static String label(SlayerLocation location)
	{
		return notBlank(location.getLabel()) ? location.getLabel().trim() : location.getKey();
	}

	static SlayerLocation.TaskEntry taskEntry(SlayerLocation location, int taskId)
	{
		if (location.getTasks() == null || taskId <= 0) return null;
		for (SlayerLocation.TaskEntry task : location.getTasks())
		{
			if (task != null && task.getTaskId() == taskId) return task;
		}
		return null;
	}

	static String cannonText(String value, String note)
	{
		final String v = SlayerLocations.cannonValue(value);
		if (v == null) return null;
		final String text = "yes".equals(v) ? "Allowed." : "Not allowed.";
		return notBlank(note) ? text + " " + note.trim() : text;
	}

	static String combatText(String value)
	{
		final String v = SlayerLocations.combatValue(value);
		if (v == null) return null;
		switch (v)
		{
			case "multi": return "Multicombat";
			case "single": return "Single combat";
			default: return "Mixed. Some parts are multicombat.";
		}
	}

	private static String joinNotes(String locationNote, String taskNote)
	{
		if (notBlank(locationNote) && notBlank(taskNote)) return locationNote.trim() + " " + taskNote.trim();
		if (notBlank(taskNote)) return taskNote.trim();
		return notBlank(locationNote) ? locationNote.trim() : null;
	}

	private static List<WorldPoint> points(int[][] spots)
	{
		if (spots == null || spots.length == 0) return Collections.emptyList();
		final List<WorldPoint> points = new ArrayList<>();
		for (int[] spot : spots)
		{
			if (SlayerLocations.validPoint(spot)) points.add(point(spot));
		}
		return Collections.unmodifiableList(points);
	}

	private static WorldPoint point(int[] p)
	{
		return new WorldPoint(p[0], p[1], p[2]);
	}

	private static boolean notBlank(String value)
	{
		return value != null && !value.trim().isEmpty();
	}

	private static String blankToNull(String value)
	{
		return notBlank(value) ? value.trim() : null;
	}
}
