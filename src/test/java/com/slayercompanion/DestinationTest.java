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
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class DestinationTest
{
	private static final int HELLHOUNDS = 31;
	private static final int DRAKES = 112;
	private static final int TAVERLEY_AREA = 6;
	private static final int KARUULM_AREA = 3;
	private static final int UNKNOWN_AREA = 99;

	private static final String JSON = "{\"locations\": ["
			+ "{\"key\": \"taverley_dungeon\", \"label\": \"Taverley Dungeon\", \"areaId\": 6,"
			+ " \"entrance\": [2883, 3397, 0], \"entranceSource\": \"test\","
			+ " \"cannon\": \"yes\", \"cannonSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 31, \"spots\": [[2860, 9840, 0], [2870, 9845, 0]], \"spotsSource\": \"test\","
			+ "   \"combat\": \"single\", \"combatSource\": \"test\", \"note\": \"Bring a dusty key.\", \"noteSource\": \"test\"}]},"
			+ "{\"key\": \"karuulm_slayer_dungeon\", \"label\": \"Karuulm Slayer Dungeon\", \"areaId\": 3,"
			+ " \"entrance\": [1308, 3807, 0], \"entranceSource\": \"test\","
			+ " \"cannon\": \"yes\", \"cannonSource\": \"test\","
			+ " \"tasks\": [{\"taskId\": 111, \"cannon\": \"no\", \"cannonNote\": \"Wyrm area.\", \"cannonSource\": \"test\"}]},"
			+ "{\"key\": \"neypotzli\", \"label\": \"Neypotzli\", \"areaId\": 50,"
			+ " \"entrance\": [1435, 3131, 0], \"entranceLabel\": \"Cam Torum entrance\", \"entranceSource\": \"test\"},"
			+ "{\"key\": \"the_abyss\", \"label\": \"The Abyss\", \"areaId\": 38},"
			+ "{\"key\": \"wyrmscraig\", \"label\": \"Wyrmscraig\", \"areaId\": 56, \"entrance\": [2571, 2240, 0],"
			+ " \"entranceSource\": \"test\", \"entranceRoutable\": false},"
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"areaId\": 1,"
			+ " \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\"}"
			+ "], \"taskLocations\": ["
			+ "{\"taskId\": 31, \"locations\": [\"taverley_dungeon\", \"karuulm_slayer_dungeon\"]}"
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
	public void konarsAreaWinsOverTheDossier()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, dossier("Catacombs of Kourend (no cannon)"),
				TAVERLEY_AREA, null, null, data);
		assertEquals(Destination.Origin.ASSIGNED, d.getOrigin());
		assertEquals("Taverley Dungeon", d.getLabel());
		assertEquals(TAVERLEY_AREA, d.getAreaId());
		assertEquals(new WorldPoint(2883, 3397, 0), d.getEntrance());
	}

	@Test
	public void killSpotsAreTheRouteTargetWhenKnown()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, null, TAVERLEY_AREA, null, null, data);
		assertEquals(Arrays.asList(new WorldPoint(2860, 9840, 0), new WorldPoint(2870, 9845, 0)), d.routeTargets());
	}

	@Test
	public void theEntranceIsTheRouteTargetWithoutKillSpots()
	{
		final Destination d = Destination.resolve(DRAKES, null, KARUULM_AREA, null, null, data);
		assertTrue(d.getKillSpots().isEmpty());
		assertEquals(Collections.singletonList(new WorldPoint(1308, 3807, 0)), d.routeTargets());
	}

	@Test
	public void taskFactsOverrideAreaFacts()
	{
		final Destination wyrms = Destination.resolve(111, null, KARUULM_AREA, null, null, data);
		assertEquals("Not allowed. Wyrm area.", wyrms.getCannon());
		final Destination drakes = Destination.resolve(DRAKES, null, KARUULM_AREA, null, null, data);
		assertEquals("Allowed.", drakes.getCannon());
		final Destination hellhounds = Destination.resolve(HELLHOUNDS, null, TAVERLEY_AREA, null, null, data);
		assertEquals("Single combat", hellhounds.getCombat());
		assertEquals("Bring a dusty key.", hellhounds.getNote());
	}

	@Test
	public void unknownFactsStayUnknown()
	{
		final Destination d = Destination.resolve(DRAKES, null, KARUULM_AREA, null, null, data);
		assertNull("combat is not known for Karuulm and must not be borrowed", d.getCombat());
		assertNull(d.getNote());
	}

	@Test
	public void anAreaWithoutAVerifiedPointHasNoMarkerAndNoRoute()
	{
		final Destination d = Destination.resolve(42, dossier("Catacombs of Kourend"), 38, null, null, data);
		assertEquals(Destination.Origin.ASSIGNED, d.getOrigin());
		assertEquals("The Abyss", d.getLabel());
		assertFalse("the dossier's place must not stand in for Konar's area", d.hasEntrance());
		assertTrue(d.routeTargets().isEmpty());
	}

	@Test
	public void anAreaMissingFromTheDataUsesTheGameName()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, dossier("Taverley Dungeon"), UNKNOWN_AREA,
				"Somewhere New", null, data);
		assertEquals(Destination.Origin.ASSIGNED, d.getOrigin());
		assertEquals("Somewhere New", d.getLabel());
		assertNull(d.getEntrance());
		assertTrue(d.routeTargets().isEmpty());
		assertEquals("area 99", Destination.resolve(HELLHOUNDS, null, UNKNOWN_AREA, null, null, data).getLabel());
	}

	@Test
	public void theEntranceLabelNamesWhatTheMarkerPointsAt()
	{
		final Destination d = Destination.resolve(111, null, 50, null, null, data);
		assertEquals("Neypotzli", d.getLabel());
		assertEquals("Cam Torum entrance", d.markerLabel());
	}

	@Test
	public void withoutAnAreaOrAListTheDossierRecommendationDecidesAsBefore()
	{
		final Destination d = Destination.resolve(DRAKES, dossier("Catacombs of Kourend (multi)"), 0, null, null, data);
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
		assertEquals("catacombs_of_kourend", d.getKey());
		assertEquals(new WorldPoint(1636, 3673, 0), d.getEntrance());
		assertEquals(0, d.getAreaId());
	}

	@Test
	public void aDossierPlaceOutsideTheDataStillGetsItsVerifiedPoint()
	{
		final Destination d = Destination.resolve(DRAKES, dossier("Chasm of Fire"), 0, null, null, data);
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
		assertEquals("chasm_of_fire", d.getKey());
		assertEquals("Chasm of Fire", d.getLabel());
		assertEquals(new WorldPoint(1432, 3670, 0), d.getEntrance());
	}

	@Test
	public void aListWithoutARecommendationLeadsWithTheDossierPlaceWhenItIsListed()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, dossier("Karuulm Slayer Dungeon"), 0, null, null, data);
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
		assertEquals("karuulm_slayer_dungeon", d.getKey());
	}

	@Test
	public void otherwiseTheFirstListedPlaceIsRecommended()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, dossier("Catacombs of Kourend"), 0, null, null, data);
		assertEquals("the dossier's place is not on this task's list", "taverley_dungeon", d.getKey());
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
		assertEquals("taverley_dungeon", Destination.resolve(HELLHOUNDS, null, 0, null, null, data).getKey());
	}

	@Test
	public void thePlayersPickWinsWhenNoAreaIsAssigned()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, dossier("Catacombs of Kourend"), 0, null,
				"karuulm_slayer_dungeon", data);
		assertEquals(Destination.Origin.PREFERRED, d.getOrigin());
		assertEquals("Karuulm Slayer Dungeon", d.getLabel());
		final Destination konar = Destination.resolve(HELLHOUNDS, null, TAVERLEY_AREA, null, "karuulm_slayer_dungeon", data);
		assertEquals("Konar's area is not negotiable", Destination.Origin.ASSIGNED, konar.getOrigin());
	}

	@Test
	public void pickingTheRecommendedPlaceKeepsItRecommended()
	{
		final Destination d = Destination.resolve(HELLHOUNDS, null, 0, null, "taverley_dungeon", data);
		assertEquals(Destination.Origin.RECOMMENDED, d.getOrigin());
	}

	@Test
	public void aPickTheTaskDoesNotListFallsBackQuietly()
	{
		assertEquals("taverley_dungeon", Destination.resolve(HELLHOUNDS, null, 0, null, "catacombs_of_kourend", data).getKey());
		assertEquals("taverley_dungeon", Destination.resolve(HELLHOUNDS, null, 0, null, "removed_from_the_data", data).getKey());
	}

	@Test
	public void noKnownPlaceMeansNoDestination()
	{
		assertNull(Destination.resolve(DRAKES, dossier("Somewhere the dossier invented"), 0, null, null, data));
		assertNull(Destination.resolve(DRAKES, null, 0, null, null, data));
	}

	@Test
	public void thePathButtonExplainsWhyItIsDisabled()
	{
		assertEquals("No verified destination for this task yet.", SlayerCompanionPanel.noRouteReason(null));
		final Destination abyss = Destination.resolve(42, null, 38, null, null, data);
		assertEquals("No verified point for The Abyss yet.", SlayerCompanionPanel.noRouteReason(abyss));
		assertNull(SlayerCompanionPanel.noRouteReason(Destination.resolve(HELLHOUNDS, null, TAVERLEY_AREA, null, null, data)));
	}

	@Test
	public void anEntranceShortestPathCannotReachIsMarkedButNeverRouted()
	{
		final Destination island = Destination.resolve(111, null, 56, null, null, data);
		assertTrue("the map marker still shows", island.hasEntrance());
		assertTrue(island.routeTargets().isEmpty());
		assertEquals("Shortest Path cannot route to Wyrmscraig.", SlayerCompanionPanel.noRouteReason(island));
	}

	@Test
	public void taskListsKeepOnlyKnownLocationsInOrder()
	{
		assertEquals(Arrays.asList("taverley_dungeon", "karuulm_slayer_dungeon"), data.forTask(HELLHOUNDS).getLocations());
	}
}
