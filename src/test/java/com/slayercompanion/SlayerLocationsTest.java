package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slayercompanion.data.Access;
import com.slayercompanion.data.SlayerLocation;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskLocations;
import com.slayercompanion.data.WildernessPlaces;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.client.game.AgilityShortcut;
import net.runelite.client.plugins.npcunaggroarea.NpcAggroAreaPlugin;
import org.junit.Test;

/** Keeps the bundled /slayer_areas.json complete, sourced and consistent with the rest of the plugin. */
public class SlayerLocationsTest
{
	/**
	 * Konar's allowed areas per task id, from the game cache dump
	 * (tools/cache-dump/out/slayer_tables.json, konarTaskAreas, 2026-10-04). 111 pairs.
	 */
	private static final String KONAR_PAIRS = "123:50,51,52 122:49 34:44,45,55 43:26,43 74:36,32 84:35 88:30,13,42 "
			+ "36:26 45:26,42 25:1,27,6,34,47,51 72:33 106:32 79:2,31,1 26:1,5,27,41 66:42,28 27:1,27,6,37 "
			+ "50:1,26,51 18:21,22,23,24,25,56 41:1,9,2 49:1,20 46:9 94:10 29:3,4,5,1,47 95:19 52:1,9,42 "
			+ "90:16,17,18,39,40 31:3,7,6,2,1 35:14,1,15,43 111:3,50,54,56 112:3 113:3 92:13 "
			+ "16:3,2,1,8,5,47,48 53:11,12 48:2,9,10,1,42,46 30:4,6,5,1 42:1,9,38 127:1,5,47,29";

	/**
	 * Every task in the pools of Turael, Spria, Mazchna, Vannaka, Chaeldar, Nieve and Duradel, from the same
	 * cache dump (masters[].tasks[], 2026-10-04). Boss tasks (98) are left out: the boss decides the place.
	 */
	private static final String OTHER_MASTER_TASKS = "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 "
			+ "25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 55 56 57 61 62 63 "
			+ "64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 83 84 85 86 87 88 89 90 92 94 95 96 106 111 112 121 "
			+ "122 123 124 125 126 127 128 129 130";

	/** Regression floor for those tasks having two or more places to pick from. */
	private static final int MIN_TASKS_WITH_A_CHOICE = 70;

	/** Krystilia's pool from the same cache dump (masters[] id 7), without the boss task. */
	private static final String KRYSTILIA_TASKS = "42 49 52 50 120 119 10 17 14 48 110 28 107 104 24 103 27 102 101 11 100 "
			+ "99 4 7 13 54 79 15 89 91 31 30 94 19 16 29";

	/** Regression floor for her tasks with a Wilderness place that can be marked or routed to. */
	private static final int MIN_KRYSTILIA_TASKS_WITH_A_POINT = 30;

	/** Konar areas without a verified surface point. The UI says so and draws no marker. */
	private static final Set<Integer> AREAS_WITHOUT_ENTRANCE = new HashSet<>(Arrays.asList(
			49 // Poison Waste Dungeon: the wiki only describes the entrance in words
	));

	/**
	 * Regression floor for kill spots across Konar's 111 (task, area) pairs. A spot must be a listed wiki spawn
	 * tile that Shortest Path 1.20.7 can both stand on and reach. The other pairs route to the entrance.
	 */
	private static final int MIN_PAIRS_WITH_KILL_SPOTS = 67;

	private final Gson gson = new Gson();
	private final SlayerLocations data = new SlayerLocations(gson);
	private final SlayerDataset dataset = new SlayerDataset(gson);

	private JsonObject raw()
	{
		return gson.fromJson(new InputStreamReader(
				SlayerLocations.class.getResourceAsStream(SlayerLocations.RESOURCE), StandardCharsets.UTF_8), JsonObject.class);
	}

	@Test
	public void everyPointAndFactHasAValidValueAndASource()
	{
		final List<String> problems = new ArrayList<>();
		final Set<String> keys = new HashSet<>();
		final Set<Integer> areaIds = new HashSet<>();
		final JsonArray locations = raw().getAsJsonArray("locations");
		for (JsonElement element : locations)
		{
			final SlayerLocation location = gson.fromJson(element, SlayerLocation.class);
			for (String problem : SlayerLocations.problems(location))
			{
				problems.add(location.getKey() + ": " + problem);
			}
			if (!keys.add(location.getKey())) problems.add("duplicate key " + location.getKey());
			if (location.getAreaId() > 0 && !areaIds.add(location.getAreaId())) problems.add("duplicate area " + location.getAreaId());
			if (location.getTasks() != null)
			{
				for (SlayerLocation.TaskEntry task : location.getTasks())
				{
					if (task.getSpots() != null && task.getSpots().length > 6) problems.add(location.getKey() + ": too many spots");
				}
			}
		}
		assertTrue("Problems in slayer_areas.json: " + problems, problems.isEmpty());
		assertEquals("every location loads", locations.size(), data.all().size());
	}

	@Test
	public void gameAreaRowsMatchRuneLiteNames() throws Exception
	{
		for (SlayerLocation location : data.all())
		{
			if (location.getAreaId() <= 0) continue;
			final Field row = DBTableID.SlayerArea.Row.class.getField(location.getRow());
			assertNotNull(location.getKey(), row);
		}
		assertEquals("one location per SlayerArea row in the RuneLite API", DBTableID.SlayerArea.Row.class.getFields().length,
				data.all().stream().filter(l -> l.getAreaId() > 0).count());
	}

	@Test
	public void everyKonarAreaIsLabelledAndHasAVerifiedEntrance()
	{
		final List<String> missing = new ArrayList<>();
		int pairs = 0;
		int withSpots = 0;
		for (String pair : KONAR_PAIRS.split(" "))
		{
			final int taskId = Integer.parseInt(pair.substring(0, pair.indexOf(':')));
			for (String area : pair.substring(pair.indexOf(':') + 1).split(","))
			{
				final int areaId = Integer.parseInt(area);
				pairs++;
				final SlayerLocation location = data.byAreaId(areaId);
				if (location == null)
				{
					missing.add("area " + areaId);
					continue;
				}
				final Destination d = Destination.resolve(taskId, null, areaId, null, null, data);
				assertEquals(Destination.Origin.ASSIGNED, d.getOrigin());
				if (d.getEntrance() == null && !AREAS_WITHOUT_ENTRANCE.contains(areaId))
				{
					missing.add(location.getLabel() + " entrance");
				}
				if (!d.getKillSpots().isEmpty()) withSpots++;
			}
		}
		assertEquals(111, pairs);
		assertTrue("Konar areas without data: " + missing, missing.isEmpty());
		assertTrue("kill spot coverage fell to " + withSpots, withSpots >= MIN_PAIRS_WITH_KILL_SPOTS);
	}

	@Test
	public void anAreaListedWithoutAnEntranceReallyHasNone()
	{
		for (int areaId : AREAS_WITHOUT_ENTRANCE)
		{
			assertTrue("update AREAS_WITHOUT_ENTRANCE, area " + areaId + " now has a point",
					data.byAreaId(areaId).getEntrance() == null);
		}
	}

	@Test
	public void theDossierDestinationsAgreeWithTheData()
	{
		final String[] bestLocations = {"Catacombs of Kourend", "God Wars Dungeon", "Jormungand's Prison",
				"Taverley Dungeon", "Mos Le'Harmless Cave", "Kraken Cove", "Lighthouse", "Iorwerth Dungeon",
				"Karuulm Slayer Dungeon", "Fremennik Slayer Dungeon", "Kalphite Cave", "Ancient Cavern",
				"Smoke Devil Dungeon", "Brimhaven Dungeon", "Asgarnian Ice Dungeon", "Slayer Tower",
				"Stronghold Slayer Cave", "Chasm of Fire", "Mor Ul Rek", "Wyvern Cave"};
		for (String best : bestLocations)
		{
			final SlayerTask task = new SlayerTask();
			task.setBestLocation(best);
			final TaskDestination destination = TaskDestination.forTask(task);
			assertNotNull(best, destination);
			final SlayerLocation location = data.byKey(destination.getKey());
			assertNotNull("no data location for " + destination.getKey(), location);
			final int[] e = location.getEntrance();
			assertEquals(destination.getKey(), destination.getPoint(), new WorldPoint(e[0], e[1], e[2]));
		}
	}

	@Test
	public void everyListRecommendsOneOfItsPlacesFirst()
	{
		int fromDossier = 0;
		for (TaskLocations list : data.taskLists())
		{
			assertNotNull(list.getTask() + " has no recommended place", list.getRecommended());
			assertEquals("the recommendation is listed first", list.getRecommended(), list.getLocations().get(0));
			// When the plugin's own mapping of the dossier text names a listed place, the data must agree.
			final TaskDestination destination = TaskDestination.forTask(dataset.lookup(list.getTask()));
			if (destination != null && list.getLocations().contains(destination.getKey()))
			{
				assertEquals(list.getTask(), destination.getKey(), list.getRecommended());
				fromDossier++;
			}
		}
		assertTrue(fromDossier > 20);
	}

	/**
	 * The Wilderness Slayer Cave, which lies east of RuneLite's underground Wilderness area (x 2944 to 3263). The box
	 * is the four map squares it lies in, regions 13469, 13470, 13725 and 13726: x 3328 to 3455, and y 10048 to 10175.
	 * Those rows are Wilderness levels 17 to 32 underground, at 8 rows a level from 9920, and the wiki puts the cave's
	 * monsters "between level 17 to 32 Wilderness" (Wilderness Slayer Cave, read 4 October 2026). RuneLite's four
	 * crevice shortcuts in the cave (AgilityShortcut WILDERNESS_SLAYER_CAVE_CREVICE_*) lie inside it.
	 */
	static final WorldArea WILDERNESS_SLAYER_CAVE = new WorldArea(3328, 10048, 128, 128, 0);

	@Test
	public void theCaveBoxHoldsRuneLitesShortcutsInTheCave() throws Exception
	{
		final WorldArea[] wilderness = {runeliteArea("WILDERNESS_ABOVE_GROUND"), runeliteArea("WILDERNESS_UNDERGROUND")};
		int crevices = 0;
		for (AgilityShortcut shortcut : AgilityShortcut.values())
		{
			if (!shortcut.name().startsWith("WILDERNESS_SLAYER_CAVE")) continue;
			crevices++;
			assertTrue(shortcut.name(), shortcut.getWorldLocation().isInArea2D(WILDERNESS_SLAYER_CAVE));
			// The box is needed: RuneLite's own Wilderness areas leave the cave out
			assertTrue(shortcut.name(), !shortcut.getWorldLocation().isInArea2D(wilderness));
		}
		assertEquals(4, crevices);
		// A spot moved out of the cave is caught
		assertTrue(!new WorldPoint(1643, 10085, 0).isInArea2D(WILDERNESS_SLAYER_CAVE));
		// The cave's spots for the other masters lie in it too
		final SlayerLocation cave = data.byKey("wilderness_slayer_cave");
		int spots = 0;
		for (SlayerLocation.TaskEntry task : cave.getTasks())
		{
			for (int[] spot : task.getSpots() == null ? new int[0][] : task.getSpots())
			{
				assertTrue(task.getTask() + " " + Arrays.toString(spot), point(spot).isInArea2D(WILDERNESS_SLAYER_CAVE));
				spots++;
			}
		}
		assertTrue(spots >= 10);
	}

	/**
	 * Every point in Krystilia's lists must lie in the Wilderness as RuneLite itself defines it, and every spot in the
	 * Wilderness Slayer Cave in the cave's own box ({@link #WILDERNESS_SLAYER_CAVE}).
	 */
	@Test
	public void everyPointInKrystiliasListsIsInTheWilderness() throws Exception
	{
		final WorldArea[] wilderness = {runeliteArea("WILDERNESS_ABOVE_GROUND"), runeliteArea("WILDERNESS_UNDERGROUND")};
		final List<String> problems = new ArrayList<>();
		final JsonArray lists = raw().getAsJsonArray("wildernessTaskLocations");
		for (JsonElement element : lists)
		{
			final WildernessPlaces list = gson.fromJson(element, WildernessPlaces.class);
			for (String problem : SlayerLocations.problems(list))
			{
				problems.add(list.getTask() + ": " + problem);
			}
			for (WildernessPlaces.Place place : list.getPlaces())
			{
				if (data.byKey(place.getKey()) == null) problems.add(list.getTask() + " lists unknown place " + place.getKey());
				if (place.getEntrance() != null && !point(place.getEntrance()).isInArea2D(wilderness))
				{
					problems.add(list.getTask() + " at " + place.getKey() + ": marker outside the Wilderness");
				}
				for (int[] spot : place.getSpots() == null ? new int[0][] : place.getSpots())
				{
					final boolean inside = place.getKey().equals("wilderness_slayer_cave")
							? point(spot).isInArea2D(WILDERNESS_SLAYER_CAVE) : point(spot).isInArea2D(wilderness);
					if (!inside)
					{
						problems.add(list.getTask() + " at " + place.getKey() + ": spot " + Arrays.toString(spot) + " outside the Wilderness");
					}
				}
			}
		}
		assertTrue("Krystilia lists: " + problems, problems.isEmpty());
		assertEquals("every list loads", lists.size(), data.wildernessLists().size());
	}

	@Test
	public void everyTaskKrystiliaAssignsHasAWildernessList()
	{
		final List<String> missing = new ArrayList<>();
		int withPoint = 0;
		for (String id : KRYSTILIA_TASKS.split(" "))
		{
			final WildernessPlaces list = data.forWildernessTask(Integer.parseInt(id));
			if (list == null || list.getPlaces().isEmpty())
			{
				missing.add(id);
				continue;
			}
			assertEquals("the recommendation is listed first", Destination.recommendedWildernessKey(list),
					list.getPlaces().get(0).getKey());
			for (WildernessPlaces.Place place : list.getPlaces())
			{
				if (place.getEntrance() != null || place.getSpots().length > 0)
				{
					withPoint++;
					break;
				}
			}
		}
		assertTrue("Krystilia tasks without a Wilderness list: " + missing, missing.isEmpty());
		assertTrue("her tasks with a marked or routable place fell to " + withPoint, withPoint >= MIN_KRYSTILIA_TASKS_WITH_A_POINT);
	}

	private static WorldArea runeliteArea(String name) throws Exception
	{
		final Field field = NpcAggroAreaPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return (WorldArea) field.get(null);
	}

	private static WorldPoint point(int[] p)
	{
		return new WorldPoint(p[0], p[1], p[2]);
	}

	@Test
	public void everyTaskTheOtherMastersAssignHasAPlace()
	{
		final List<String> missing = new ArrayList<>();
		int twoOrMore = 0;
		for (String id : OTHER_MASTER_TASKS.split(" "))
		{
			final int taskId = Integer.parseInt(id);
			final TaskLocations list = data.forTask(taskId);
			if (list == null || list.getLocations().isEmpty())
			{
				missing.add(id);
				continue;
			}
			if (list.getLocations().size() >= 2) twoOrMore++;
		}
		assertTrue("tasks without a place: " + missing, missing.isEmpty());
		assertTrue("tasks with a choice fell to " + twoOrMore, twoOrMore >= MIN_TASKS_WITH_A_CHOICE);
	}

	/** The plugin checks quests and skills by RuneLite's constant names, so the data may use only those. */
	@Test
	public void everyQuestAndSkillTheDataNamesIsARuneLiteConstant()
	{
		final List<String> unknown = new ArrayList<>();
		int named = 0;
		for (SlayerLocation location : data.all())
		{
			final List<Access> access = new ArrayList<>();
			access.add(location.getAccess());
			for (SlayerLocation.TaskEntry task : location.getTasks())
			{
				access.add(task.getAccess());
			}
			for (Access a : access)
			{
				if (a == null) continue;
				final List<String> quests = new ArrayList<>();
				if (a.getQuestsFinished() != null) quests.addAll(a.getQuestsFinished());
				if (a.getQuestsStarted() != null) quests.addAll(a.getQuestsStarted());
				for (String quest : quests)
				{
					named++;
					if (Arrays.stream(Quest.values()).noneMatch(q -> q.name().equals(quest))) unknown.add(location.getKey() + ": quest " + quest);
				}
				if (a.getSkills() == null) continue;
				for (String skill : a.getSkills().keySet())
				{
					named++;
					if (Arrays.stream(Skill.values()).noneMatch(s -> s.name().equals(skill))) unknown.add(location.getKey() + ": skill " + skill);
				}
			}
		}
		assertTrue("not RuneLite constants: " + unknown, unknown.isEmpty());
		assertTrue("the data names quests and skills to check", named > 100);
	}

	@Test
	public void accessMustNeedSomethingAndNameItsSource()
	{
		final SlayerLocation place = new SlayerLocation();
		place.setKey("test");
		place.setLabel("Test");
		final Access access = new Access();
		place.setAccess(access);
		assertEquals(Arrays.asList("location: access that needs nothing", "location: access without a source"),
				SlayerLocations.problems(place));
		access.setQuestsFinished(Collections.singletonList("PRIEST_IN_PERIL"));
		access.setSkills(Collections.singletonMap("SAILING", 120));
		access.setSource("OSRS Wiki");
		assertEquals(Collections.singletonList("location: SAILING level must be 1 to 99"), SlayerLocations.problems(place));
		access.setSkills(null);
		assertTrue(SlayerLocations.problems(place).isEmpty());
	}

	/** Places the bundled data must keep locked behind their quest, from the OSRS Wiki's own words. */
	@Test
	public void wellKnownGatesAreInTheData()
	{
		final PlaceAccess.Account fresh = PlaceAccess.Account.read(Arrays.asList("PRIEST_IN_PERIL", "LOST_CITY",
				"SONG_OF_THE_ELVES", "DRAGON_SLAYER_II", "BONE_VOYAGE"), quest -> net.runelite.api.QuestState.NOT_STARTED, null);
		assertEquals("Needs Priest in Peril", PlaceAccess.check(PlaceAccess.of(data.byKey("slayer_tower"), 41), fresh).needs());
		assertEquals("Needs Lost City", PlaceAccess.check(PlaceAccess.of(data.byKey("zanaris"), 74), fresh).needs());
		assertEquals("Needs Song of the Elves", PlaceAccess.check(PlaceAccess.of(data.byKey("iorwerth_dungeon"), 66), fresh).needs());
		assertEquals("Needs Bone Voyage", PlaceAccess.check(PlaceAccess.of(data.byKey("fossil_island"), 106), fresh).needs());
		// Only the dragons in the Myths' Guild basement need Dragon Slayer II, not the ogresses in the cove's open side
		assertEquals("Needs Dragon Slayer II", PlaceAccess.check(PlaceAccess.of(data.byKey("myths_guild_dungeon"), 27), fresh).needs());
		assertFalse(PlaceAccess.check(PlaceAccess.of(data.byKey("myths_guild_dungeon"), 20), fresh).isLocked());
		assertFalse("the Catacombs need nothing", PlaceAccess.check(PlaceAccess.of(data.byKey("catacombs_of_kourend"), 41), fresh).isLocked());
	}

	@Test
	public void everyTaskListNamesKnownPlaces()
	{
		for (TaskLocations list : data.taskLists())
		{
			assertTrue(list.getTask(), !list.getLocations().isEmpty());
			for (String key : list.getLocations())
			{
				assertNotNull(list.getTask() + " lists " + key, data.byKey(key));
			}
		}
	}
}
