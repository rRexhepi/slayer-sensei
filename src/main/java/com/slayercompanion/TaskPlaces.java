package com.slayercompanion;

import com.slayercompanion.data.SlayerLocation;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskLocations;
import com.slayercompanion.data.WildernessPlaces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * The places the sidebar offers for the current task and the one in use. Masters other than Konar leave
 * the place to the player, so the task's list from /slayer_areas.json becomes a picker and the pick is
 * remembered per task in the plugin config. Krystilia's tasks only count in the Wilderness, so they offer
 * only her Wilderness list and remember their pick apart. Every place says whether the account can enter it
 * ({@link PlaceAccess}), and the first one it can enter is recommended. The decisions here are pure and unit
 * tested. {@link Destination#resolve} turns the chosen key into the destination.
 */
@Value
class TaskPlaces
{
	/** Config keys "place_" plus the task id, in the plugin's config group, hold the remembered pick. */
	static final String CONFIG_KEY_PREFIX = "place_";
	/** The same for a pick among Krystilia's Wilderness places. */
	static final String WILDERNESS_CONFIG_KEY_PREFIX = "wildernessPlace_";

	enum Show
	{
		/** Two or more places: a picker. */
		PICKER,
		/** Exactly one place: its name as text. */
		ONE_PLACE,
		/** No known place: the dossier's own text. */
		DOSSIER
	}

	@Value
	static class Place
	{
		String key;
		String label;
		boolean recommended;
		/** Short facts that help choose, such as "Cannon not allowed. Multicombat.", or null when none are known. */
		String facts;
		/** Whether the account can enter the place, and what it lacks when not. */
		PlaceAccess.Verdict access;

		Place(String key, String label, boolean recommended, String facts, PlaceAccess.Verdict access)
		{
			this.key = key;
			this.label = label;
			this.recommended = recommended;
			this.facts = facts;
			this.access = access == null ? PlaceAccess.Verdict.OPEN : access;
		}

		/** A place nothing is known to lock. */
		Place(String key, String label, boolean recommended, String facts)
		{
			this(key, label, recommended, facts, PlaceAccess.Verdict.OPEN);
		}

		boolean isLocked()
		{
			return access.isLocked();
		}
	}

	int taskId;
	/** The recommended place first, then the data file's order. */
	List<Place> places;
	/** Key of the place in use, or null when there are no places. */
	String chosenKey;
	/** Krystilia's task: the places are her Wilderness list, and kills elsewhere do not count. */
	boolean wilderness;

	TaskPlaces(int taskId, List<Place> places, String chosenKey, boolean wilderness)
	{
		this.taskId = taskId;
		this.places = places;
		this.chosenKey = chosenKey;
		this.wilderness = wilderness;
	}

	TaskPlaces(int taskId, List<Place> places, String chosenKey)
	{
		this(taskId, places, chosenKey, false);
	}

	/**
	 * Whether the player may pick the place for this assignment. Konar names the area herself, so her
	 * tasks keep it, even one without an area. Krystilia's tasks get a pick among her Wilderness places.
	 *
	 * @param assignedAreaId value of the SLAYER_AREA varp
	 * @param masterId value of the SLAYER_MASTER varbit, 0 when unknown
	 */
	static boolean pickable(int assignedAreaId, int masterId)
	{
		return assignedAreaId <= 0 && masterId != SlayerAssigner.KONAR.getId();
	}

	/** Whether the assignment only counts in the Wilderness: Krystilia's tasks. */
	static boolean wildernessOnly(int masterId)
	{
		return masterId == SlayerAssigner.KRYSTILIA.getId();
	}

	/**
	 * The places for one of Krystilia's tasks, the recommended one first, each with its facts for this task.
	 * Only her Wilderness list counts. A task without one offers nothing, so nothing is marked or routed.
	 */
	static List<Place> offeredInWilderness(int taskId, SlayerLocations data)
	{
		return offeredInWilderness(taskId, data, PlaceAccess.Account.UNKNOWN);
	}

	/** As above, with whether the account can enter each place. The first place it can enter is recommended. */
	static List<Place> offeredInWilderness(int taskId, SlayerLocations data, PlaceAccess.Account account)
	{
		final WildernessPlaces list = data.forWildernessTask(taskId);
		if (list == null || list.getPlaces().isEmpty())
		{
			return Collections.emptyList();
		}
		final String recommended = Destination.recommendedWildernessKey(list);
		final List<Place> places = new ArrayList<>();
		for (WildernessPlaces.Place wild : list.getPlaces())
		{
			final SlayerLocation location = data.byKey(wild.getKey());
			if (location == null) continue;
			final String label = wild.getLabel() != null && !wild.getLabel().trim().isEmpty()
					? wild.getLabel().trim() : Destination.label(location);
			final Place place = new Place(wild.getKey(), label, wild.getKey().equals(recommended), facts(location, taskId),
					PlaceAccess.check(PlaceAccess.of(location, taskId), account));
			if (place.isRecommended()) places.add(0, place);
			else places.add(place);
		}
		return Collections.unmodifiableList(firstOpenRecommended(places));
	}

	/** Every place the data lists for the task, the recommended one first, each with its facts for this task. */
	static List<Place> offered(int taskId, SlayerTask dossier, SlayerLocations data)
	{
		return offered(taskId, dossier, data, PlaceAccess.Account.UNKNOWN);
	}

	/** As above, with whether the account can enter each place. The first place it can enter is recommended. */
	static List<Place> offered(int taskId, SlayerTask dossier, SlayerLocations data, PlaceAccess.Account account)
	{
		final TaskLocations list = data.forTask(taskId);
		if (list == null || list.getLocations() == null || list.getLocations().isEmpty())
		{
			return Collections.emptyList();
		}
		final String recommended = Destination.recommendedKey(taskId, dossier, data);
		final List<Place> places = new ArrayList<>();
		for (String key : list.getLocations())
		{
			final SlayerLocation location = data.byKey(key);
			if (location == null) continue;
			final Place place = new Place(key, Destination.label(location), key.equals(recommended), facts(location, taskId),
					PlaceAccess.check(PlaceAccess.of(location, taskId), account));
			if (place.isRecommended()) places.add(0, place);
			else places.add(place);
		}
		return Collections.unmodifiableList(firstOpenRecommended(places));
	}

	/**
	 * The recommended place is the first place in display order the account can enter. It moves to the front and the
	 * place before it loses the recommendation. When every place is locked the order stays as it is.
	 */
	static List<Place> firstOpenRecommended(List<Place> places)
	{
		if (places.isEmpty() || !places.get(0).isLocked()) return places;
		int open = -1;
		for (int i = 1; i < places.size() && open < 0; i++)
		{
			if (!places.get(i).isLocked()) open = i;
		}
		if (open < 0) return places;
		final Place first = places.get(open);
		final List<Place> out = new ArrayList<>();
		out.add(new Place(first.getKey(), first.getLabel(), true, first.getFacts(), first.getAccess()));
		for (int i = 0; i < places.size(); i++)
		{
			final Place place = places.get(i);
			if (i == open) continue;
			out.add(place.isRecommended() ? new Place(place.getKey(), place.getLabel(), false, place.getFacts(), place.getAccess()) : place);
		}
		return out;
	}

	/**
	 * The quests the places of a task name, Krystilia's Wilderness list for hers. Reading a quest state runs a client
	 * script, so the plugin reads only these.
	 */
	static Set<String> quests(int taskId, boolean wilderness, SlayerLocations data)
	{
		final List<SlayerLocation> locations = new ArrayList<>();
		if (wilderness)
		{
			final WildernessPlaces list = data.forWildernessTask(taskId);
			if (list != null)
			{
				for (WildernessPlaces.Place place : list.getPlaces())
				{
					if (data.byKey(place.getKey()) != null) locations.add(data.byKey(place.getKey()));
				}
			}
		}
		else
		{
			final TaskLocations list = data.forTask(taskId);
			if (list != null && list.getLocations() != null)
			{
				for (String key : list.getLocations())
				{
					if (data.byKey(key) != null) locations.add(data.byKey(key));
				}
			}
		}
		return PlaceAccess.quests(locations, taskId);
	}

	/** A place's facts for one task. The task's own facts win over the place-wide ones. */
	private static String facts(SlayerLocation location, int taskId)
	{
		final SlayerLocation.TaskEntry task = Destination.taskEntry(location, taskId);
		final String cannon = task != null && task.getCannon() != null ? task.getCannon() : location.getCannon();
		final String combat = task != null && task.getCombat() != null ? task.getCombat() : location.getCombat();
		return facts(cannon, combat);
	}

	/** The remembered pick while the task still offers it, else the recommended place. */
	static String chosen(List<Place> places, String storedKey)
	{
		if (places.isEmpty()) return null;
		for (Place place : places)
		{
			if (place.getKey().equals(storedKey)) return storedKey;
		}
		return places.get(0).getKey();
	}

	static Show show(TaskPlaces places)
	{
		if (places == null || places.getPlaces().isEmpty()) return Show.DOSSIER;
		return places.getPlaces().size() == 1 ? Show.ONE_PLACE : Show.PICKER;
	}

	boolean offers(String key)
	{
		return place(key) != null;
	}

	/** The place in use, or null without places. */
	Place chosen()
	{
		return place(chosenKey);
	}

	private Place place(String key)
	{
		for (Place place : places)
		{
			if (place.getKey().equals(key)) return place;
		}
		return null;
	}

	/** The line under the picker: whether the place in use is the recommended one, and its facts. */
	String summary()
	{
		final Place chosen = chosen();
		if (chosen == null) return null;
		if (chosen.isRecommended())
		{
			return chosen.getFacts() == null ? "Recommended." : "Recommended. " + chosen.getFacts();
		}
		final String recommended = places.get(0).isRecommended() ? "Recommended: " + places.get(0).getLabel() + "." : null;
		if (chosen.getFacts() == null) return recommended;
		return recommended == null ? chosen.getFacts() : chosen.getFacts() + " " + recommended;
	}

	/**
	 * The compact sidebar's item under the picker: "Recommended", or "Recommended: " and the recommended place while
	 * another is in use. Null without a recommended place. {@link #summary} adds the facts.
	 */
	String compactSummary()
	{
		final Place chosen = chosen();
		if (chosen == null) return null;
		if (chosen.isRecommended()) return "Recommended";
		return places.get(0).isRecommended() ? "Recommended: " + places.get(0).getLabel() : null;
	}

	/** The second line of a place in the picker's list, or null when there is nothing to add. */
	static String rowNote(Place place)
	{
		if (!place.isRecommended()) return place.getFacts();
		return place.getFacts() == null ? "Recommended." : "Recommended. " + place.getFacts();
	}

	/**
	 * What the place in use needs and its access note, as the sidebar's line under the place, such as
	 * "Needs Priest in Peril." Null when the account can enter it and no note applies.
	 */
	String accessLine()
	{
		final Place chosen = chosen();
		return chosen == null ? null : chosen.getAccess().sentence();
	}

	static String facts(String cannon, String combat)
	{
		final StringBuilder sb = new StringBuilder();
		final String c = SlayerLocations.cannonValue(cannon);
		if (c != null) sb.append("yes".equals(c) ? "Cannon allowed." : "Cannon not allowed.");
		final String m = SlayerLocations.combatValue(combat);
		if (m != null)
		{
			if (sb.length() > 0) sb.append(' ');
			sb.append("multi".equals(m) ? "Multicombat." : "single".equals(m) ? "Single combat." : "Partly multicombat.");
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**
	 * The sidebar's Cannon or Combat area line. Konar's area shows only its own fact. With a place list, the
	 * chosen place's fact shows when known. The dossier's line describes the recommended place, so it stands
	 * in for that place only, and a place the player picked shows no line rather than borrowed advice.
	 * Krystilia's Wilderness places never borrow it either, since the dossier may describe a place outside
	 * the Wilderness. Without a place list, the dossier's line stays as before.
	 */
	static String factLine(Destination destination, TaskPlaces places, String placeFact, String dossierLine)
	{
		if (destination != null && destination.getOrigin() == Destination.Origin.ASSIGNED) return placeFact;
		if (show(places) == Show.DOSSIER || destination == null) return dossierLine;
		if (placeFact != null || places.isWilderness()) return placeFact;
		return destination.getOrigin() == Destination.Origin.RECOMMENDED ? dossierLine : null;
	}

	static String configKey(int taskId)
	{
		return configKey(taskId, false);
	}

	static String configKey(int taskId, boolean wilderness)
	{
		return (wilderness ? WILDERNESS_CONFIG_KEY_PREFIX : CONFIG_KEY_PREFIX) + taskId;
	}

	/** The task id of a remembered-place config key, either kind, or -1 for any other key. */
	static int taskIdOfConfigKey(String key)
	{
		if (key == null) return -1;
		final String prefix = key.startsWith(CONFIG_KEY_PREFIX) ? CONFIG_KEY_PREFIX
				: key.startsWith(WILDERNESS_CONFIG_KEY_PREFIX) ? WILDERNESS_CONFIG_KEY_PREFIX : null;
		if (prefix == null) return -1;
		try
		{
			final int taskId = Integer.parseInt(key.substring(prefix.length()));
			return taskId > 0 ? taskId : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
