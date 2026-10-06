package com.slayercompanion;

import com.google.gson.Gson;
import com.slayercompanion.data.Access;
import com.slayercompanion.data.SlayerLocation;
import com.slayercompanion.data.TaskLocations;
import com.slayercompanion.data.WildernessPlaces;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads /slayer_areas.json: the places where Slayer tasks can be done and, per task, the ordered list
 * of those places, plus Krystilia's lists of Wilderness places. A malformed point or fact is dropped with
 * a warning instead of failing the plugin. {@link #problems} lists every such issue so a test can keep the
 * bundled file clean.
 */
@Singleton
@Slf4j
public class SlayerLocations
{
	static final String RESOURCE = "/slayer_areas.json";

	private final Map<String, SlayerLocation> byKey = new LinkedHashMap<>();
	private final Map<Integer, SlayerLocation> byAreaId = new HashMap<>();
	private final Map<Integer, TaskLocations> byTask = new HashMap<>();
	private final Map<Integer, WildernessPlaces> byWildernessTask = new HashMap<>();

	@Inject
	SlayerLocations(Gson gson)
	{
		try (InputStream in = SlayerLocations.class.getResourceAsStream(RESOURCE))
		{
			if (in != null)
			{
				load(gson, new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		catch (Exception e)
		{
			log.warn("slayer-sensei: failed to load {}", RESOURCE, e);
		}
		log.debug("slayer-sensei: loaded {} slayer locations, {} task lists", byKey.size(), byTask.size());
	}

	/** For tests: loads the given JSON instead of the bundled resource. */
	SlayerLocations(Gson gson, Reader reader)
	{
		load(gson, reader);
	}

	SlayerLocation byKey(String key)
	{
		return key == null ? null : byKey.get(key);
	}

	SlayerLocation byAreaId(int areaId)
	{
		return areaId <= 0 ? null : byAreaId.get(areaId);
	}

	TaskLocations forTask(int taskId)
	{
		return byTask.get(taskId);
	}

	/** Where Krystilia's task can be done in the Wilderness, or null when no place is verified. */
	WildernessPlaces forWildernessTask(int taskId)
	{
		return byWildernessTask.get(taskId);
	}

	Collection<WildernessPlaces> wildernessLists()
	{
		return Collections.unmodifiableCollection(byWildernessTask.values());
	}

	Collection<SlayerLocation> all()
	{
		return Collections.unmodifiableCollection(byKey.values());
	}

	Collection<TaskLocations> taskLists()
	{
		return Collections.unmodifiableCollection(byTask.values());
	}

	private void load(Gson gson, Reader reader)
	{
		final LocationFile file = gson.fromJson(reader, LocationFile.class);
		if (file == null) return;
		if (file.locations != null)
		{
			for (SlayerLocation location : file.locations)
			{
				addLocation(location);
			}
		}
		if (file.taskLocations != null)
		{
			for (TaskLocations list : file.taskLocations)
			{
				addTaskList(list);
			}
		}
		if (file.wildernessTaskLocations != null)
		{
			for (WildernessPlaces list : file.wildernessTaskLocations)
			{
				addWildernessList(list);
			}
		}
	}

	private void addLocation(SlayerLocation location)
	{
		if (location == null || blank(location.getKey()))
		{
			log.warn("slayer-sensei: skipping a slayer location without a key");
			return;
		}
		if (byKey.containsKey(location.getKey()))
		{
			log.warn("slayer-sensei: duplicate slayer location {}, keeping the first", location.getKey());
			return;
		}
		for (String problem : problems(location))
		{
			log.warn("slayer-sensei: slayer location {}: {}", location.getKey(), problem);
		}
		sanitize(location);
		if (location.getAreaId() > 0 && byAreaId.containsKey(location.getAreaId()))
		{
			log.warn("slayer-sensei: area {} is claimed by two locations, keeping {}", location.getAreaId(),
					byAreaId.get(location.getAreaId()).getKey());
			location.setAreaId(0);
		}
		byKey.put(location.getKey(), location);
		if (location.getAreaId() > 0) byAreaId.put(location.getAreaId(), location);
	}

	private void addTaskList(TaskLocations list)
	{
		if (list == null || list.getTaskId() <= 0 || byTask.containsKey(list.getTaskId())) return;
		final List<String> keys = new ArrayList<>();
		if (list.getLocations() != null)
		{
			for (String key : list.getLocations())
			{
				if (byKey.containsKey(key) && !keys.contains(key)) keys.add(key);
				else log.warn("slayer-sensei: task {} lists unknown or repeated location {}", list.getTaskId(), key);
			}
		}
		list.setLocations(Collections.unmodifiableList(keys));
		if (list.getRecommended() != null && !keys.contains(list.getRecommended()))
		{
			log.warn("slayer-sensei: task {} recommends {}, which is not in its list", list.getTaskId(), list.getRecommended());
			list.setRecommended(null);
		}
		byTask.put(list.getTaskId(), list);
	}

	private void addWildernessList(WildernessPlaces list)
	{
		if (list == null || list.getTaskId() <= 0 || byWildernessTask.containsKey(list.getTaskId())) return;
		for (String problem : problems(list))
		{
			log.warn("slayer-sensei: Wilderness list for task {}: {}", list.getTaskId(), problem);
		}
		final List<WildernessPlaces.Place> places = new ArrayList<>();
		if (list.getPlaces() != null)
		{
			for (WildernessPlaces.Place place : list.getPlaces())
			{
				if (place == null || !byKey.containsKey(place.getKey()) || places.stream().anyMatch(p -> p.getKey().equals(place.getKey())))
				{
					log.warn("slayer-sensei: Krystilia task {} lists an unknown or repeated place", list.getTaskId());
					continue;
				}
				if (place.getEntrance() != null && !validPoint(place.getEntrance())) place.setEntrance(null);
				final List<int[]> spots = new ArrayList<>();
				if (place.getSpots() != null)
				{
					for (int[] spot : place.getSpots())
					{
						if (validPoint(spot)) spots.add(spot);
					}
				}
				place.setSpots(spots.toArray(new int[0][]));
				places.add(place);
			}
		}
		list.setPlaces(Collections.unmodifiableList(places));
		if (list.getRecommended() != null && places.stream().noneMatch(p -> p.getKey().equals(list.getRecommended())))
		{
			list.setRecommended(null);
		}
		byWildernessTask.put(list.getTaskId(), list);
	}

	/** Every point in a Wilderness list that would be ignored or has no source. */
	static List<String> problems(WildernessPlaces list)
	{
		final List<String> problems = new ArrayList<>();
		if (blank(list.getSource())) problems.add("no source");
		if (list.getPlaces() == null) return problems;
		for (WildernessPlaces.Place place : list.getPlaces())
		{
			final String name = place == null ? "?" : place.getKey();
			if (place == null || blank(place.getKey()))
			{
				problems.add(name + ": no key");
				continue;
			}
			if (place.getEntrance() != null)
			{
				if (!validPoint(place.getEntrance())) problems.add(name + ": invalid entrance point");
				if (blank(place.getEntranceSource())) problems.add(name + ": entrance without a source");
			}
			if (place.getEntranceRoutable() != null && blank(place.getEntranceRoutableSource()))
			{
				problems.add(name + ": entranceRoutable without a source");
			}
			if (place.getSpots() != null)
			{
				for (int[] spot : place.getSpots())
				{
					if (!validPoint(spot)) problems.add(name + ": invalid spot");
				}
				if (place.getSpots().length > 0 && blank(place.getSpotsSource())) problems.add(name + ": spots without a source");
			}
		}
		return problems;
	}

	/** Every reason a value in this location would be ignored, plus facts and points without a source. */
	static List<String> problems(SlayerLocation location)
	{
		final List<String> problems = new ArrayList<>();
		if (blank(location.getLabel())) problems.add("no label");
		if (location.getEntrance() != null)
		{
			if (!validPoint(location.getEntrance())) problems.add("invalid entrance point");
			if (blank(location.getEntranceSource())) problems.add("entrance without a source");
		}
		if (location.getEntranceRoutable() != null && blank(location.getEntranceRoutableSource()))
		{
			problems.add("entranceRoutable without a source");
		}
		checkFacts(problems, "location", location.getCannon(), location.getCannonSource(), location.getCombat(),
				location.getCombatSource(), location.getNote(), location.getNoteSource());
		checkAccess(problems, "location", location.getAccess());
		if (location.getTasks() != null)
		{
			for (SlayerLocation.TaskEntry task : location.getTasks())
			{
				final String name = "task " + (task == null ? "?" : task.getTaskId());
				if (task == null || task.getTaskId() <= 0)
				{
					problems.add(name + ": no task id");
					continue;
				}
				if (task.getSpots() != null)
				{
					for (int[] spot : task.getSpots())
					{
						if (!validPoint(spot)) problems.add(name + ": invalid spot");
					}
					if (task.getSpots().length > 0 && blank(task.getSpotsSource())) problems.add(name + ": spots without a source");
				}
				checkFacts(problems, name, task.getCannon(), task.getCannonSource(), task.getCombat(),
						task.getCombatSource(), task.getNote(), task.getNoteSource());
				checkAccess(problems, name, task.getAccess());
			}
		}
		return problems;
	}

	/** An access entry must need something or carry a note, name its source and ask for real levels only. */
	private static void checkAccess(List<String> problems, String scope, Access access)
	{
		if (access == null) return;
		final boolean locks = access.getQuestsFinished() != null && !access.getQuestsFinished().isEmpty()
				|| access.getQuestsStarted() != null && !access.getQuestsStarted().isEmpty()
				|| access.getSkills() != null && !access.getSkills().isEmpty();
		if (!locks && blank(access.getNote())) problems.add(scope + ": access that needs nothing");
		if (blank(access.getSource())) problems.add(scope + ": access without a source");
		if (access.getSkills() != null)
		{
			for (Map.Entry<String, Integer> skill : access.getSkills().entrySet())
			{
				if (skill.getValue() == null || skill.getValue() < 1 || skill.getValue() > 99)
				{
					problems.add(scope + ": " + skill.getKey() + " level must be 1 to 99");
				}
			}
		}
	}

	private static void checkFacts(List<String> problems, String scope, String cannon, String cannonSource,
			String combat, String combatSource, String note, String noteSource)
	{
		if (cannon != null && cannonValue(cannon) == null) problems.add(scope + ": cannon must be yes or no");
		if (cannon != null && blank(cannonSource)) problems.add(scope + ": cannon without a source");
		if (combat != null && combatValue(combat) == null) problems.add(scope + ": combat must be multi, single or mixed");
		if (combat != null && blank(combatSource)) problems.add(scope + ": combat without a source");
		if (!blank(note) && blank(noteSource)) problems.add(scope + ": note without a source");
	}

	/** Drops values the plugin would otherwise have to second-guess at render time. */
	private static void sanitize(SlayerLocation location)
	{
		if (location.getEntrance() != null && !validPoint(location.getEntrance())) location.setEntrance(null);
		location.setCannon(cannonValue(location.getCannon()));
		location.setCombat(combatValue(location.getCombat()));
		final List<SlayerLocation.TaskEntry> tasks = new ArrayList<>();
		if (location.getTasks() != null)
		{
			for (SlayerLocation.TaskEntry task : location.getTasks())
			{
				if (task == null || task.getTaskId() <= 0) continue;
				final List<int[]> spots = new ArrayList<>();
				if (task.getSpots() != null)
				{
					for (int[] spot : task.getSpots())
					{
						if (validPoint(spot)) spots.add(spot);
					}
				}
				task.setSpots(spots.toArray(new int[0][]));
				task.setCannon(cannonValue(task.getCannon()));
				task.setCombat(combatValue(task.getCombat()));
				tasks.add(task);
			}
		}
		location.setTasks(Collections.unmodifiableList(tasks));
	}

	static boolean validPoint(int[] point)
	{
		return point != null && point.length == 3 && point[0] > 0 && point[1] > 0 && point[2] >= 0 && point[2] <= 3;
	}

	static String cannonValue(String value)
	{
		if (value == null) return null;
		final String v = value.trim().toLowerCase();
		return "yes".equals(v) || "no".equals(v) ? v : null;
	}

	static String combatValue(String value)
	{
		if (value == null) return null;
		final String v = value.trim().toLowerCase();
		return "multi".equals(v) || "single".equals(v) || "mixed".equals(v) ? v : null;
	}

	private static boolean blank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	private static final class LocationFile
	{
		private List<SlayerLocation> locations;
		private List<TaskLocations> taskLocations;
		private List<WildernessPlaces> wildernessTaskLocations;
	}
}
