package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

public class ShortestPathBridgeTest
{
	private static final WorldPoint A = new WorldPoint(1650, 10070, 0);
	private static final WorldPoint B = new WorldPoint(1660, 10080, 0);

	@Test
	public void oneTargetIsSentAsAWorldPoint()
	{
		final Map<String, Object> data = ShortestPathBridge.pathData(Collections.singletonList(A));
		assertEquals(Collections.singleton("target"), data.keySet());
		assertEquals(A, data.get("target"));
	}

	@Test
	public void severalTargetsAreSentAsASetSoShortestPathPicksTheClosest()
	{
		final Map<String, Object> data = ShortestPathBridge.pathData(Arrays.asList(A, B, A));
		assertTrue(data.get("target") instanceof Set);
		assertEquals(new LinkedHashSet<>(Arrays.asList(A, B)), data.get("target"));
		assertFalse("no start, so Shortest Path uses the player's location", data.containsKey("start"));
		assertFalse("never override the user's Shortest Path settings", data.containsKey("config"));
	}

	@Test
	public void duplicatesOfOnePointCollapseToAWorldPoint()
	{
		assertEquals(A, ShortestPathBridge.pathData(Arrays.asList(A, A)).get("target"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void aRouteWithoutTargetsIsABug()
	{
		ShortestPathBridge.pathData(Collections.emptyList());
	}

	@Test
	public void routeRequestsFromAnyPluginAreRecognised()
	{
		assertTrue(ShortestPathBridge.isRouteRequest(new PluginMessage("shortestpath", "path")));
		assertTrue(ShortestPathBridge.isRouteRequest(new PluginMessage("shortestpath", "clear")));
		assertFalse("Shortest Path's own transport report is not a target change",
				ShortestPathBridge.isRouteRequest(new PluginMessage("shortestpath", "transports")));
		assertFalse(ShortestPathBridge.isRouteRequest(new PluginMessage("questhelper", "path")));
	}

	@Test
	public void shortestPathMenuEntriesThatChangeTheTargetAreRecognised()
	{
		assertTrue(ShortestPathBridge.changesTarget("Set", "<col=ff9040>Target</col>"));
		assertTrue(ShortestPathBridge.changesTarget("Set", "<col=ff9040>Target</col> <col=ff9040>2</col>"));
		assertTrue(ShortestPathBridge.changesTarget("Clear", "<col=ff9040>Path</col>"));
		assertTrue(ShortestPathBridge.changesTarget("Find closest", "<col=ff9040>Bank</col>"));
		assertFalse("a new start keeps the target", ShortestPathBridge.changesTarget("Set", "<col=ff9040>Start</col>"));
		assertFalse(ShortestPathBridge.changesTarget("Walk here", ""));
		assertFalse(ShortestPathBridge.changesTarget("Clear", "<col=ff9040>Inventory</col>"));
		assertFalse(ShortestPathBridge.changesTarget(null, null));
	}

	@PluginDescriptor(name = "Shortest Path")
	private static final class RenamedShortestPath extends Plugin
	{
	}

	@PluginDescriptor(name = "Shortest Clue")
	private static final class OtherPlugin extends Plugin
	{
	}

	@Test
	public void theShortestPathPluginIsFoundByItsDescriptor()
	{
		assertTrue(ShortestPathBridge.isShortestPath(new RenamedShortestPath()));
		assertFalse(ShortestPathBridge.isShortestPath(new OtherPlugin()));
		assertFalse(ShortestPathBridge.isShortestPath(null));
	}
}
