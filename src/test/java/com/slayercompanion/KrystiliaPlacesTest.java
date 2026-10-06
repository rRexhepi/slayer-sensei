package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Krystilia's tasks only count in the Wilderness, so they are never marked or routed anywhere else. */
public class KrystiliaPlacesTest
{
	private static final int ABYSSAL_DEMONS = 42;
	private static final int BLACK_DEMONS = 30;
	private static final int BANDITS = 102;
	private static final int ENTS = 101;

	/**
	 * Abyssal demons are recommended at the Catacombs in general, which must never reach a Krystilia task.
	 * Edgeville Dungeon has its own entrance and spots outside the Wilderness, and her list keeps only the
	 * Wilderness spot. Ents have a verified place without any point.
	 */
	private static final String JSON = "{\"locations\": ["
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"areaId\": 1,"
			+ " \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\", \"cannon\": \"no\", \"cannonSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 42, \"spots\": [[1674, 10056, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"key\": \"wilderness_slayer_cave\", \"label\": \"Wilderness Slayer Cave\", \"entrance\": [3292, 3746, 0],"
			+ " \"entranceSource\": \"test\", \"tasks\": [{\"taskId\": 42, \"spots\": [[3343, 10163, 0]], \"spotsSource\": \"test\","
			+ " \"cannon\": \"yes\", \"cannonSource\": \"test\", \"combat\": \"multi\", \"combatSource\": \"test\"},"
			+ " {\"taskId\": 30, \"spots\": [[3359, 10114, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"key\": \"edgeville_dungeon\", \"label\": \"Edgeville Dungeon\", \"entrance\": [3096, 3469, 0], \"entranceSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 30, \"spots\": [[3095, 9899, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"key\": \"forests_north_and_east_of_the_chaos_temple\", \"label\": \"Forests north and east of the Chaos Temple\"}"
			+ "], \"taskLocations\": ["
			+ "{\"taskId\": 42, \"locations\": [\"catacombs_of_kourend\", \"wilderness_slayer_cave\"], \"recommended\": \"catacombs_of_kourend\"},"
			+ "{\"taskId\": 30, \"locations\": [\"edgeville_dungeon\", \"wilderness_slayer_cave\"]}"
			+ "], \"wildernessTaskLocations\": ["
			+ "{\"taskId\": 42, \"source\": \"test\", \"recommended\": \"wilderness_slayer_cave\", \"places\": ["
			+ "  {\"key\": \"wilderness_slayer_cave\", \"label\": \"Wilderness Slayer Cave\", \"entrance\": [3292, 3746, 0],"
			+ "   \"entranceSource\": \"test\", \"spots\": [[3343, 10163, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"taskId\": 30, \"source\": \"test\", \"places\": ["
			+ "  {\"key\": \"edgeville_dungeon\", \"label\": \"Edgeville Dungeon (Wilderness part)\","
			+ "   \"spots\": [[3092, 9958, 0]], \"spotsSource\": \"test\"},"
			+ "  {\"key\": \"wilderness_slayer_cave\", \"label\": \"Wilderness Slayer Cave\", \"entrance\": [3292, 3746, 0],"
			+ "   \"entranceSource\": \"test\", \"spots\": [[3359, 10114, 0]], \"spotsSource\": \"test\"}]},"
			+ "{\"taskId\": 101, \"source\": \"test\", \"places\": ["
			+ "  {\"key\": \"forests_north_and_east_of_the_chaos_temple\", \"label\": \"Forests north and east of the Chaos Temple\"}]}"
			+ "]}";

	private final SlayerLocations data = new SlayerLocations(new Gson(), new StringReader(JSON));

	private static SlayerTask dossier(String bestLocation)
	{
		final SlayerTask task = new SlayerTask();
		task.setTask("Test task");
		task.setBestLocation(bestLocation);
		return task;
	}

	@Test
	public void aKrystiliaTaskNeverFallsBackToThePlaceOtherMastersUse()
	{
		final Destination general = Destination.resolve(ABYSSAL_DEMONS, dossier("Catacombs of Kourend"), 0, null, null, data);
		assertEquals("catacombs_of_kourend", general.getKey());
		final Destination hers = Destination.resolve(ABYSSAL_DEMONS, dossier("Catacombs of Kourend"), 0, null, null, data, true);
		assertEquals("wilderness_slayer_cave", hers.getKey());
		assertEquals(new WorldPoint(3292, 3746, 0), hers.getEntrance());
		assertEquals(Collections.singletonList(new WorldPoint(3343, 10163, 0)), hers.routeTargets());
		assertEquals("Allowed.", hers.getCannon());
		final Destination pickedElsewhere = Destination.resolve(ABYSSAL_DEMONS, null, 0, null, "catacombs_of_kourend", data, true);
		assertEquals("a pick outside her list never counts", "wilderness_slayer_cave", pickedElsewhere.getKey());
	}

	@Test
	public void withoutAVerifiedWildernessPlaceNothingIsMarkedOrRouted()
	{
		assertNull("the dossier names a place, but not one in the Wilderness",
				Destination.resolve(BANDITS, dossier("Catacombs of Kourend"), 0, null, null, data, true));
		assertTrue(TaskPlaces.offeredInWilderness(BANDITS, data).isEmpty());
		final TaskPlaces none = new TaskPlaces(BANDITS, Collections.emptyList(), null, true);
		assertEquals(TaskPlaces.Show.DOSSIER, TaskPlaces.show(none));
		assertEquals("No verified place in the Wilderness for this task yet.", SlayerCompanionPanel.noRouteReason(null, none));
		assertEquals("Krystilia's tasks only count in the Wilderness. Wilderness Bandit Camp.",
				SlayerCompanionPanel.whereToGoText(true, "Wilderness Bandit Camp."));
		assertEquals("Krystilia's tasks only count in the Wilderness.", SlayerCompanionPanel.whereToGoText(true, null));
		assertEquals("other masters keep the dossier text", "Wilderness Bandit Camp.",
				SlayerCompanionPanel.whereToGoText(false, "Wilderness Bandit Camp."));
	}

	@Test
	public void onlyTheWildernessPointsOfAPlaceAreUsed()
	{
		final Destination d = Destination.resolve(BLACK_DEMONS, null, 0, null, null, data, true);
		assertEquals("edgeville_dungeon", d.getKey());
		assertEquals("Edgeville Dungeon (Wilderness part)", d.getLabel());
		assertFalse("the dungeon's own entrance is in Edgeville, outside the Wilderness", d.hasEntrance());
		assertEquals("not the spot in the part outside the Wilderness", Collections.singletonList(new WorldPoint(3092, 9958, 0)),
				d.routeTargets());
	}

	@Test
	public void herPlacesMakeAPickerWithTheRecommendedOneFirst()
	{
		final List<TaskPlaces.Place> places = TaskPlaces.offeredInWilderness(BLACK_DEMONS, data);
		assertEquals(Arrays.asList("edgeville_dungeon", "wilderness_slayer_cave"),
				Arrays.asList(places.get(0).getKey(), places.get(1).getKey()));
		assertTrue("without a recommendation the first place leads", places.get(0).isRecommended());
		assertEquals("Edgeville Dungeon (Wilderness part)", places.get(0).getLabel());
		final String chosen = TaskPlaces.chosen(places, "wilderness_slayer_cave");
		final Destination d = Destination.resolve(BLACK_DEMONS, null, 0, null, chosen, data, true);
		assertEquals("wilderness_slayer_cave", d.getKey());
		assertEquals(Destination.Origin.PREFERRED, d.getOrigin());
		assertEquals(Collections.singletonList(new WorldPoint(3359, 10114, 0)), d.routeTargets());
	}

	@Test
	public void aVerifiedPlaceWithoutPointsIsNamedButNeitherMarkedNorRouted()
	{
		final Destination d = Destination.resolve(ENTS, null, 0, null, null, data, true);
		assertEquals("Forests north and east of the Chaos Temple", d.getLabel());
		assertFalse(d.hasEntrance());
		assertTrue(d.routeTargets().isEmpty());
		assertEquals("No verified point for Forests north and east of the Chaos Temple yet.", SlayerCompanionPanel.noRouteReason(d));
	}

	@Test
	public void herPlacesNeverBorrowTheDossierFacts()
	{
		final TaskPlaces hers = new TaskPlaces(BLACK_DEMONS, TaskPlaces.offeredInWilderness(BLACK_DEMONS, data), "edgeville_dungeon", true);
		final Destination d = Destination.resolve(BLACK_DEMONS, null, 0, null, "edgeville_dungeon", data, true);
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
		assertNull("the dossier may describe a place outside the Wilderness", TaskPlaces.factLine(d, hers, null, "Dossier."));
		assertEquals("Known.", TaskPlaces.factLine(d, hers, "Known.", "Dossier."));
		final TaskPlaces none = new TaskPlaces(BANDITS, Collections.emptyList(), null, true);
		assertEquals("no Wilderness place: the dossier's advice as before", "Dossier.",
				TaskPlaces.factLine(null, none, null, "Dossier."));
	}

	@Test
	public void otherMastersKeepTheirOwnList()
	{
		final Destination d = Destination.resolve(BLACK_DEMONS, null, 0, null, null, data);
		assertEquals("edgeville_dungeon", d.getKey());
		assertEquals("Edgeville Dungeon", d.getLabel());
		assertEquals(new WorldPoint(3096, 3469, 0), d.getEntrance());
	}
}
