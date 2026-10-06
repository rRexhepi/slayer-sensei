package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Which notice the sidebar shows about Shortest Path, its words and its button. */
public class PluginNoticesTest
{
	@Test
	public void shortestPathIsOptionalAndOnlyAButtonTurnsItOn()
	{
		final PluginNotices.Notice off = PluginNotices.shortestPath(ShortestPathBridge.Status.DISABLED, PathMode.NEW_TASK, false);
		assertEquals("Optional, draws the route", off.getItem());
		assertEquals("Shortest Path is optional. It draws the route to your task and to your next master. It is installed but off.",
			off.getText());
		assertEquals("Turn on Shortest Path", off.getButton());
		assertFalse(off.isFailed());
		// Not installed: one line, no install button and no link
		final PluginNotices.Notice missing = PluginNotices.shortestPath(ShortestPathBridge.Status.MISSING, PathMode.NEW_TASK, false);
		assertEquals("Optional: Shortest Path, Plugin Hub", missing.getItem());
		assertEquals("Shortest Path is optional. It draws the route to your task and to your next master. You can install it from"
			+ " the Plugin Hub.", missing.getText());
		assertNull(missing.getButton());
		assertFalse(missing.getText().contains("http"));
	}

	@Test
	public void aFailedPressSaysSoAndKeepsTheButton()
	{
		final PluginNotices.Notice failed = PluginNotices.shortestPath(ShortestPathBridge.Status.DISABLED, PathMode.BUTTON, true);
		assertEquals("Could not turn it on", failed.getItem());
		assertEquals("Shortest Path is optional. It draws the route to your task and to your next master. The button could not turn"
			+ " it on. Turn it on in RuneLite's plugin list instead.", failed.getText());
		assertTrue(failed.isFailed());
		assertEquals("Turn on Shortest Path", failed.getButton());
	}

	@Test
	public void nothingIsSaidAboutShortestPathWhenItRunsOrRoutesAreOff()
	{
		for (ShortestPathBridge.Status status : ShortestPathBridge.Status.values())
		{
			assertNull(status.name(), PluginNotices.shortestPath(status, PathMode.OFF, false));
			assertNull(status.name(), PluginNotices.shortestPath(status, PathMode.OFF, true));
		}
		for (PathMode mode : PathMode.values())
		{
			assertNull(mode.name(), PluginNotices.shortestPath(ShortestPathBridge.Status.ACTIVE, mode, false));
		}
	}

	/** Compact items fit on a line, and no user-facing text uses dashes or semicolons. */
	@Test
	public void theItemsAreShort()
	{
		final List<PluginNotices.Notice> notices = new ArrayList<>();
		for (ShortestPathBridge.Status status : ShortestPathBridge.Status.values())
		{
			for (PathMode mode : PathMode.values())
			{
				notices.add(PluginNotices.shortestPath(status, mode, false));
				notices.add(PluginNotices.shortestPath(status, mode, true));
			}
		}
		for (PluginNotices.Notice notice : notices)
		{
			if (notice == null) continue;
			assertTrue(notice.getItem(), notice.getItem().length() <= 35);
		}
	}
}
