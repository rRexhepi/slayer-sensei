package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class TaskPlacesTest
{
	private static final int SPECTRES = 41;
	private static final int BANSHEES = 38;
	private static final int COWS = 6;
	private static final int CAVE_KRAKEN = 92;
	private static final int NIEVE = 6;
	private static final int KONAR = 8;
	private static final int KRYSTILIA = 7;
	private static final int MORTIMER = 10;

	private static final String JSON = "{\"locations\": ["
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"areaId\": 1,"
			+ " \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\", \"cannon\": \"no\", \"cannonSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 41, \"combat\": \"multi\", \"combatSource\": \"test\"}]},"
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\", \"areaId\": 9,"
			+ " \"entrance\": [3416, 3535, 0], \"entranceSource\": \"test\", \"cannon\": \"no\", \"cannonSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 41, \"combat\": \"single\", \"combatSource\": \"test\"}]},"
			+ "{\"key\": \"stronghold_slayer_dungeon\", \"label\": \"Stronghold Slayer Dungeon\", \"areaId\": 2,"
			+ " \"entrance\": [2427, 3424, 0], \"entranceSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 41, \"cannon\": \"yes\", \"cannonSource\": \"test\", \"combat\": \"single\", \"combatSource\": \"test\"}]},"
			+ "{\"key\": \"deepfin_mine\", \"label\": \"Deepfin Mine\"},"
			+ "{\"key\": \"lumbridge_east_farm\", \"label\": \"Lumbridge East Farm\","
			+ " \"tasks\": [{\"taskId\": 6, \"spots\": [[3258, 3277, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"key\": \"cow_cave\", \"label\": \"Cow Cave\","
			+ " \"tasks\": [{\"taskId\": 6, \"spots\": [[3001, 9801, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"key\": \"kraken_cove\", \"label\": \"Kraken Cove\", \"areaId\": 13, \"entrance\": [2277, 3611, 0], \"entranceSource\": \"test\"}"
			+ "], \"taskLocations\": ["
			+ "{\"taskId\": 41, \"locations\": [\"catacombs_of_kourend\", \"slayer_tower\", \"stronghold_slayer_dungeon\", \"deepfin_mine\"],"
			+ " \"recommended\": \"catacombs_of_kourend\"},"
			+ "{\"taskId\": 38, \"locations\": [\"catacombs_of_kourend\", \"slayer_tower\"]},"
			+ "{\"taskId\": 6, \"locations\": [\"lumbridge_east_farm\", \"cow_cave\"]},"
			+ "{\"taskId\": 92, \"locations\": [\"kraken_cove\"]}"
			+ "]}";

	private final SlayerLocations data = new SlayerLocations(new Gson(), new StringReader(JSON));

	private static SlayerTask dossier(String bestLocation)
	{
		final SlayerTask task = new SlayerTask();
		task.setTask("Test task");
		task.setBestLocation(bestLocation);
		return task;
	}

	private static List<String> keys(List<TaskPlaces.Place> places)
	{
		final List<String> keys = new ArrayList<>();
		for (TaskPlaces.Place place : places)
		{
			keys.add(place.getKey());
		}
		return keys;
	}

	@Test
	public void theRecommendedPlaceComesFirstAndCarriesItsFacts()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals(java.util.Arrays.asList("catacombs_of_kourend", "slayer_tower", "stronghold_slayer_dungeon", "deepfin_mine"),
				keys(places));
		assertTrue(places.get(0).isRecommended());
		assertFalse(places.get(1).isRecommended());
		assertEquals("Cannon not allowed. Multicombat.", places.get(0).getFacts());
		assertEquals("the task's own facts win over the place's", "Cannon allowed. Single combat.", places.get(2).getFacts());
		assertNull("unknown facts stay unknown", places.get(3).getFacts());
	}

	@Test
	public void withoutARecommendationInTheDataTheDossierPlaceLeadsWhenTheTaskListsIt()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(BANSHEES, dossier("Slayer Tower ground floor"), data);
		assertEquals(java.util.Arrays.asList("slayer_tower", "catacombs_of_kourend"), keys(places));
		assertTrue(places.get(0).isRecommended());
	}

	@Test
	public void otherwiseTheFirstListedPlaceLeads()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(COWS, dossier("A field nobody lists"), data);
		assertEquals(java.util.Arrays.asList("lumbridge_east_farm", "cow_cave"), keys(places));
		assertTrue(places.get(0).isRecommended());
		assertTrue("a task the data does not cover offers nothing", TaskPlaces.offered(999, null, data).isEmpty());
	}

	@Test
	public void aStoredPickIsUsedWhileTheTaskStillOffersIt()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals("stronghold_slayer_dungeon", TaskPlaces.chosen(places, "stronghold_slayer_dungeon"));
	}

	@Test
	public void aStaleOrMissingPickFallsBackToTheRecommendation()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals("catacombs_of_kourend", TaskPlaces.chosen(places, "a_place_the_data_dropped"));
		assertEquals("kraken_cove exists, but not for this task", "catacombs_of_kourend", TaskPlaces.chosen(places, "kraken_cove"));
		assertEquals("catacombs_of_kourend", TaskPlaces.chosen(places, null));
		assertNull(TaskPlaces.chosen(Collections.emptyList(), "slayer_tower"));
	}

	@Test
	public void theDestinationFollowsTheChosenPlace()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		for (TaskPlaces.Place place : places)
		{
			final String chosen = TaskPlaces.chosen(places, place.getKey());
			final Destination d = Destination.resolve(SPECTRES, null, 0, null, chosen, data);
			assertEquals(place.getKey(), d.getKey());
			assertEquals(place.isRecommended() ? Destination.Origin.RECOMMENDED : Destination.Origin.PREFERRED, d.getOrigin());
		}
		final Destination stale = Destination.resolve(SPECTRES, null, 0, null, "kraken_cove", data);
		assertEquals("a key the task does not list is ignored", "catacombs_of_kourend", stale.getKey());
		assertEquals(Destination.Origin.RECOMMENDED, stale.getOrigin());
	}

	@Test
	public void onlyMastersThatLeaveThePlaceOpenGetAPicker()
	{
		assertTrue(TaskPlaces.pickable(0, NIEVE));
		assertTrue(TaskPlaces.pickable(0, MORTIMER));
		assertTrue("an unknown master with no area still leaves the place open", TaskPlaces.pickable(0, 0));
		assertFalse("an assigned area is never negotiable", TaskPlaces.pickable(5, NIEVE));
		assertFalse("Konar keeps working as before", TaskPlaces.pickable(0, KONAR));
		assertFalse(TaskPlaces.pickable(5, KONAR));
		assertTrue("Krystilia picks among her Wilderness places", TaskPlaces.pickable(0, KRYSTILIA));
		assertTrue(TaskPlaces.wildernessOnly(KRYSTILIA));
		assertFalse(TaskPlaces.wildernessOnly(NIEVE));
		assertFalse(TaskPlaces.wildernessOnly(0));
	}

	@Test
	public void twoOrMorePlacesMakeAPickerOneIsTextNoneKeepsTheDossier()
	{
		assertEquals(TaskPlaces.Show.DOSSIER, TaskPlaces.show(null));
		assertEquals(TaskPlaces.Show.DOSSIER, TaskPlaces.show(new TaskPlaces(1, Collections.emptyList(), null)));
		final List<TaskPlaces.Place> one = TaskPlaces.offered(CAVE_KRAKEN, null, data);
		assertEquals(TaskPlaces.Show.ONE_PLACE, TaskPlaces.show(new TaskPlaces(CAVE_KRAKEN, one, "kraken_cove")));
		final List<TaskPlaces.Place> four = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals(TaskPlaces.Show.PICKER, TaskPlaces.show(new TaskPlaces(SPECTRES, four, "catacombs_of_kourend")));
	}

	@Test
	public void theLineUnderThePickerSaysWhatIsRecommended()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals("Recommended. Cannon not allowed. Multicombat.",
				new TaskPlaces(SPECTRES, places, "catacombs_of_kourend").summary());
		assertEquals("Cannon allowed. Single combat. Recommended: Catacombs of Kourend.",
				new TaskPlaces(SPECTRES, places, "stronghold_slayer_dungeon").summary());
		assertEquals("Recommended: Catacombs of Kourend.", new TaskPlaces(SPECTRES, places, "deepfin_mine").summary());
	}

	@Test
	public void theCompactLineSaysWhetherThePickIsTheRecommendedPlace()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals("Recommended", new TaskPlaces(SPECTRES, places, "catacombs_of_kourend").compactSummary());
		// The facts are in the tooltip, and under FIGHT
		assertEquals("Recommended: Catacombs of Kourend", new TaskPlaces(SPECTRES, places, "stronghold_slayer_dungeon").compactSummary());
		final List<TaskPlaces.Place> none = Arrays.asList(new TaskPlaces.Place("a", "A", false, null), new TaskPlaces.Place("b", "B", false, null));
		assertNull(new TaskPlaces(SPECTRES, none, "b").compactSummary());
		assertNull(new TaskPlaces(SPECTRES, Collections.<TaskPlaces.Place>emptyList(), null).compactSummary());
	}

	@Test
	public void eachListRowNotesTheRecommendationAndTheFacts()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		assertEquals("Recommended. Cannon not allowed. Multicombat.", TaskPlaces.rowNote(places.get(0)));
		assertEquals("Cannon not allowed. Single combat.", TaskPlaces.rowNote(places.get(1)));
		assertNull(TaskPlaces.rowNote(places.get(3)));
		assertEquals("Partly multicombat.", TaskPlaces.facts(null, "mixed"));
		assertNull(TaskPlaces.facts("maybe", null));
	}

	@Test
	public void cannonAndCombatLinesShowThePlaceInUse()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, data);
		final TaskPlaces picker = new TaskPlaces(SPECTRES, places, "catacombs_of_kourend");
		final Destination recommended = Destination.resolve(SPECTRES, null, 0, null, null, data);
		final Destination picked = Destination.resolve(SPECTRES, null, 0, null, "deepfin_mine", data);
		final Destination konar = Destination.resolve(SPECTRES, null, 9, null, null, data);
		assertEquals("a known fact wins", "Known.", TaskPlaces.factLine(recommended, picker, "Known.", "Dossier."));
		assertEquals("the dossier describes the recommended place", "Dossier.",
				TaskPlaces.factLine(recommended, picker, null, "Dossier."));
		assertNull("never borrowed for another place", TaskPlaces.factLine(picked, picker, null, "Dossier."));
		assertNull("Konar's area as before", TaskPlaces.factLine(konar, picker, null, "Dossier."));
		assertEquals("no list, as before", "Dossier.", TaskPlaces.factLine(recommended, null, "Known.", "Dossier."));
		assertEquals("Dossier.", TaskPlaces.factLine(null, null, null, "Dossier."));
	}

	@Test
	public void listRowsShowTheNameOverItsNoteAsEscapedHtml()
	{
		final TaskPlaces.Place place = new TaskPlaces.Place("x", "Bob & <Co>", true, "Cannon allowed.");
		assertEquals("<html><body width='150'>Bob &amp; &lt;Co&gt;<br><font color='#a0a0a0'>Recommended. "
				+ "Cannon allowed.</font></body></html>", SlayerCompanionPanel.rowHtml(place));
		assertEquals("<html><body width='150'>Deepfin Mine</body></html>",
				SlayerCompanionPanel.rowHtml(new TaskPlaces.Place("deepfin_mine", "Deepfin Mine", false, null)));
	}

	@Test
	public void thePickIsStoredUnderOneConfigKeyPerTask()
	{
		assertEquals("place_41", TaskPlaces.configKey(SPECTRES));
		assertEquals(SPECTRES, TaskPlaces.taskIdOfConfigKey("place_41"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey("pathMode"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey("place_"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey("place_x"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey("place_-3"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey(null));
		assertEquals("Krystilia's pick is kept apart", "wildernessPlace_41", TaskPlaces.configKey(SPECTRES, true));
		assertEquals(SPECTRES, TaskPlaces.taskIdOfConfigKey("wildernessPlace_41"));
		assertEquals(-1, TaskPlaces.taskIdOfConfigKey("wildernessPlace_"));
	}

	// ---- places the account cannot enter ----

	private static final int DARK_BEASTS = 66;
	private static final int BLACK_DEMONS = 30;

	/**
	 * Spectres: the data recommends the Slayer Tower, which needs Priest in Peril. Dark beasts: both places are locked.
	 * Krystilia's black demons: her list recommends a place that needs Sailing.
	 */
	private static final String ACCESS_JSON = "{\"locations\": ["
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\", \"entrance\": [3416, 3535, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsFinished\": [\"PRIEST_IN_PERIL\"], \"source\": \"test\"}},"
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\"},"
			+ "{\"key\": \"god_wars_dungeon\", \"label\": \"God Wars Dungeon\", \"entrance\": [2917, 3745, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"note\": \"Needs 60 Strength or 60 Agility.\", \"source\": \"test\"}},"
			+ "{\"key\": \"mourner_tunnels\", \"label\": \"Mourner Tunnels\", \"entrance\": [2542, 3327, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsStarted\": [\"MOURNINGS_END_PART_II\"], \"source\": \"test\"}},"
			+ "{\"key\": \"charred_dungeon\", \"label\": \"Charred Dungeon\", \"entrance\": [2637, 2416, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"skills\": {\"SAILING\": 60}, \"source\": \"test\"}},"
			+ "{\"key\": \"wilderness_slayer_cave\", \"label\": \"Wilderness Slayer Cave\", \"entrance\": [3292, 3746, 0], \"entranceSource\": \"test\"}"
			+ "], \"taskLocations\": ["
			+ "{\"taskId\": 41, \"locations\": [\"slayer_tower\", \"god_wars_dungeon\", \"catacombs_of_kourend\"], \"recommended\": \"slayer_tower\"},"
			+ "{\"taskId\": 66, \"locations\": [\"mourner_tunnels\", \"slayer_tower\"], \"recommended\": \"mourner_tunnels\"}"
			+ "], \"wildernessTaskLocations\": ["
			+ "{\"taskId\": 30, \"source\": \"test\", \"recommended\": \"charred_dungeon\", \"places\": ["
			+ "  {\"key\": \"charred_dungeon\", \"label\": \"Charred Dungeon\"},"
			+ "  {\"key\": \"wilderness_slayer_cave\", \"label\": \"Wilderness Slayer Cave\"}]}"
			+ "]}";

	private final SlayerLocations gated = new SlayerLocations(new Gson(), new StringReader(ACCESS_JSON));

	private static PlaceAccess.Account account(QuestState priestInPeril, QuestState mourningsEnd, int sailing)
	{
		final java.util.Map<String, QuestState> quests = new java.util.HashMap<>();
		quests.put("PRIEST_IN_PERIL", priestInPeril);
		quests.put("MOURNINGS_END_PART_II", mourningsEnd);
		final int[] levels = new int[Skill.values().length];
		Arrays.fill(levels, 50);
		levels[Skill.SAILING.ordinal()] = sailing;
		return new PlaceAccess.Account(quests, levels);
	}

	private static final PlaceAccess.Account NEW_ACCOUNT = account(QuestState.NOT_STARTED, QuestState.NOT_STARTED, 1);
	private static final PlaceAccess.Account QUESTED = account(QuestState.FINISHED, QuestState.IN_PROGRESS, 70);

	@Test
	public void theFirstPlaceTheAccountCanEnterIsRecommended()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, gated, NEW_ACCOUNT);
		assertEquals("the first open place moves to the front", Arrays.asList("god_wars_dungeon", "slayer_tower", "catacombs_of_kourend"),
				keys(places));
		assertTrue(places.get(0).isRecommended());
		assertFalse("a note never locks", places.get(0).isLocked());
		assertFalse("the locked place loses the recommendation", places.get(1).isRecommended());
		assertTrue(places.get(1).isLocked());
		assertEquals("Needs Priest in Peril", places.get(1).getAccess().needs());
		assertFalse(places.get(2).isLocked());

		// With the quest done, the data's recommendation leads again
		final List<TaskPlaces.Place> done = TaskPlaces.offered(SPECTRES, null, gated, QUESTED);
		assertEquals(Arrays.asList("slayer_tower", "god_wars_dungeon", "catacombs_of_kourend"), keys(done));
		assertTrue(done.get(0).isRecommended());
		assertFalse(done.get(0).isLocked());
		// Before anything is read, nothing is locked and the order is the data's
		assertEquals(keys(done), keys(TaskPlaces.offered(SPECTRES, null, gated)));
	}

	@Test
	public void whenEveryPlaceIsLockedTheOrderStaysAndTheFirstSaysWhatItNeeds()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(DARK_BEASTS, null, gated, NEW_ACCOUNT);
		assertEquals(Arrays.asList("mourner_tunnels", "slayer_tower"), keys(places));
		assertTrue(places.get(0).isRecommended());
		assertTrue(places.get(0).isLocked() && places.get(1).isLocked());
		final TaskPlaces picker = new TaskPlaces(DARK_BEASTS, places, TaskPlaces.chosen(places, null));
		assertEquals("mourner_tunnels", picker.getChosenKey());
		assertEquals("Needs Mourning's End Part II started.", picker.accessLine());
		// The marker and the route still go to the place in use
		final Destination d = Destination.resolve(DARK_BEASTS, null, 0, null, picker.getChosenKey(), gated);
		assertEquals("mourner_tunnels", d.getKey());
		assertEquals(new WorldPoint(2542, 3327, 0), d.getEntrance());
	}

	@Test
	public void aPlaceThePlayerPickedStaysPickedLockedOrNot()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offered(SPECTRES, null, gated, NEW_ACCOUNT);
		assertEquals("slayer_tower", TaskPlaces.chosen(places, "slayer_tower"));
		final TaskPlaces picker = new TaskPlaces(SPECTRES, places, "slayer_tower");
		assertTrue(picker.chosen().isLocked());
		assertEquals("Needs Priest in Peril.", picker.accessLine());
		final Destination d = Destination.resolve(SPECTRES, null, 0, null, "slayer_tower", gated);
		assertEquals("the marker and the route follow the pick", "slayer_tower", d.getKey());
		assertEquals(new WorldPoint(3416, 3535, 0), d.getEntrance());
		// Without a pick the place in use is the first open one, which the data did not recommend
		final String chosen = TaskPlaces.chosen(places, null);
		assertEquals("god_wars_dungeon", chosen);
		final Destination first = Destination.resolve(SPECTRES, null, 0, null, chosen, gated);
		assertEquals("god_wars_dungeon", first.getKey());
		assertEquals(Destination.Origin.PREFERRED, first.getOrigin());
		assertNull("the dossier's line describes the data's recommendation, not this place",
				TaskPlaces.factLine(first, new TaskPlaces(SPECTRES, places, chosen), null, "Dossier."));
		assertEquals("its note is shown", "Needs 60 Strength or 60 Agility.", new TaskPlaces(SPECTRES, places, chosen).accessLine());
		assertNull("an open place without a note says nothing", new TaskPlaces(SPECTRES, places, "catacombs_of_kourend").accessLine());
	}

	@Test
	public void krystiliasListRecommendsItsFirstOpenPlaceToo()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offeredInWilderness(BLACK_DEMONS, gated, NEW_ACCOUNT);
		assertEquals(Arrays.asList("wilderness_slayer_cave", "charred_dungeon"), keys(places));
		assertTrue(places.get(0).isRecommended());
		assertEquals("Needs 60 Sailing", places.get(1).getAccess().needs());
		assertEquals(Arrays.asList("charred_dungeon", "wilderness_slayer_cave"), keys(TaskPlaces.offeredInWilderness(BLACK_DEMONS, gated, QUESTED)));
	}

	@Test
	public void onlyTheQuestsOfTheTasksOwnPlacesAreNamedForReading()
	{
		assertEquals(Collections.singleton("PRIEST_IN_PERIL"), TaskPlaces.quests(SPECTRES, false, gated));
		assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("MOURNINGS_END_PART_II", "PRIEST_IN_PERIL")),
				TaskPlaces.quests(DARK_BEASTS, false, gated));
		assertTrue("Krystilia's list names a level, not a quest", TaskPlaces.quests(BLACK_DEMONS, true, gated).isEmpty());
		assertTrue("a task without places names nothing", TaskPlaces.quests(999, false, gated).isEmpty());
	}

	@Test
	public void aLockedRowSaysWhatItNeedsInRedBeforeItsNote()
	{
		final String red = "<font color='" + SlayerCompanionPanel.NEEDS_HTML_COLOR + "'>Needs Priest in Peril";
		assertEquals("#ff5050", SlayerCompanionPanel.NEEDS_HTML_COLOR);
		final TaskPlaces.Place tower = TaskPlaces.offered(SPECTRES, null, gated, NEW_ACCOUNT).get(1);
		final String row = SlayerCompanionPanel.rowHtml(tower);
		assertTrue(row, row.contains("Slayer Tower<br>" + red));
		final TaskPlaces.Place locked = new TaskPlaces.Place("x", "X", true, "Cannon allowed.", tower.getAccess());
		final String withNote = SlayerCompanionPanel.rowHtml(locked);
		assertTrue(withNote, withNote.contains(red));
		assertTrue("what it needs comes first", withNote.indexOf(red) < withNote.indexOf("<font color='#a0a0a0'>Recommended"));
		assertFalse("an open row has no red", SlayerCompanionPanel.rowHtml(TaskPlaces.offered(SPECTRES, null, gated, QUESTED).get(0))
				.contains(SlayerCompanionPanel.NEEDS_HTML_COLOR));
	}

	@Test
	public void aPlaceWithoutAnEntranceIsMarkedAtItsKillSpotOnTheSurface()
	{
		final Destination field = Destination.resolve(COWS, null, 0, null, null, data);
		assertEquals("lumbridge_east_farm", field.getKey());
		assertEquals(new WorldPoint(3258, 3277, 0), field.getEntrance());
		assertEquals("Lumbridge East Farm", field.markerLabel());
		assertEquals(Collections.singletonList(new WorldPoint(3258, 3277, 0)), field.routeTargets());

		final Destination cave = Destination.resolve(COWS, null, 0, null, "cow_cave", data);
		assertFalse("an underground spot would not show on the surface map", cave.hasEntrance());
		assertEquals("but it is still the route target", Collections.singletonList(new WorldPoint(3001, 9801, 0)),
				cave.routeTargets());
	}
}
