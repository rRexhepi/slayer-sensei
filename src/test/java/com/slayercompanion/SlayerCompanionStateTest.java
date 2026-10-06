package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.GameState;
import org.junit.Test;

/**
 * Regression coverage for per-character isolation of the task tracker.
 */
public class SlayerCompanionStateTest
{
	@Test
	public void switchingCharacterDropsThePreviousTracking()
	{
		// The progress rate, ETA and cached dossier belong to one character. A change of task name alone
		// does not tell two accounts apart when both are on a task with the same name.
		assertTrue(SlayerCompanionPlugin.shouldResetForProfile("profile-a", "profile-b"));
		assertTrue(SlayerCompanionPlugin.shouldResetForProfile(null, "profile-a"));
		assertTrue(SlayerCompanionPlugin.shouldResetForProfile("profile-a", null));
	}

	@Test
	public void loggingBackIntoTheSameCharacterKeepsTracking()
	{
		assertFalse(SlayerCompanionPlugin.shouldResetForProfile("profile-a", "profile-a"));
		assertFalse(SlayerCompanionPlugin.shouldResetForProfile(null, null));
	}

	@Test
	public void logoutAndHopEndTheTrackedSession()
	{
		assertTrue(SlayerCompanionPlugin.endsSession(GameState.LOGIN_SCREEN));
		assertTrue(SlayerCompanionPlugin.endsSession(GameState.HOPPING));
	}

	@Test
	public void loadingAndPlayingDoNotEndTheTrackedSession()
	{
		// A loading screen between regions is not an account change; resetting there would throw
		// away a legitimate progress-rate sample every time the player crossed a region boundary.
		assertFalse(SlayerCompanionPlugin.endsSession(GameState.LOGGED_IN));
		assertFalse(SlayerCompanionPlugin.endsSession(GameState.LOADING));
		assertFalse(SlayerCompanionPlugin.endsSession(GameState.LOGGING_IN));
	}

	/**
	 * A game tick also arrives during a loading screen, on every teleport, cave entrance and region boundary. Reading it
	 * as no task would reset the rate, restart the new-task window, drop the plan and rebuild the marker and the sidebar.
	 * Such a tick reads nothing and changes nothing, and neither does a read that fails.
	 */
	@Test
	public void aTickWhileNotLoggedInOrWithAFailedReadKeepsTheTask()
	{
		for (GameState state : GameState.values())
		{
			if (state == GameState.LOGGED_IN) continue;
			final SlayerCompanionPlugin.TaskTick tick = SlayerCompanionPlugin.taskTick(state, () ->
			{
				throw new AssertionError("read while " + state);
			});
			assertTrue(state.name(), tick.isSkip());
		}
		assertTrue("a failed read", SlayerCompanionPlugin.taskTick(GameState.LOGGED_IN, () ->
		{
			throw new IllegalStateException("table not readable");
		}).isSkip());
		// Logged in, the read decides, and no kills left is no task at once
		final SlayerTaskReader.Task task = new SlayerTaskReader.Task("Abyssal Demons", 112, 150);
		assertEquals(new SlayerCompanionPlugin.TaskTick(false, task), SlayerCompanionPlugin.taskTick(GameState.LOGGED_IN, () -> task));
		assertEquals(new SlayerCompanionPlugin.TaskTick(false, null), SlayerCompanionPlugin.taskTick(GameState.LOGGED_IN, () -> null));
	}

	@Test
	public void progressIsCountedFromDropsOfTheTaskCounter()
	{
		// These are counter-progress units, not literal NPC deaths: bracelet effects may make one
		// physical kill consume two units or none.
		assertEquals(1, SlayerCompanionPlugin.progressRecorded(10, 9));
		assertEquals(3, SlayerCompanionPlugin.progressRecorded(10, 7));
		assertEquals(30, SlayerCompanionPlugin.progressRecorded(50, 20));
	}

	@Test
	public void nonProgressCounterMovementsAreIgnored()
	{
		// First observation after login or a task change.
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(-1, 50));
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(0, 0));
		// No change, and a new assignment raising the count.
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(10, 10));
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(10, 133));
		// Zero is ambiguous between completion, cancel and block. Suppressing terminal drops makes
		// even a small cancel truthful rather than reporting fake combat progress.
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(5, 0));
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(1, 0));
		assertEquals(0, SlayerCompanionPlugin.progressRecorded(100, 69));
	}

	@Test
	public void aChangedOriginalAmountRebaselinesEvenWhenTaskNameIsTheSame()
	{
		assertTrue(SlayerCompanionPlugin.assignmentChanged(150, 130));
		assertTrue(SlayerCompanionPlugin.assignmentChanged(150, 0));
		assertFalse(SlayerCompanionPlugin.assignmentChanged(150, 150));
		assertFalse(SlayerCompanionPlugin.assignmentChanged(-1, 150));
	}

	@Test
	public void krystiliaAndMortimerTasksShowTheirOwnStreak()
	{
		assertEquals(SlayerAssigner.Streak.WILDERNESS, SlayerCompanionPlugin.streakFor(7));
		assertEquals(SlayerAssigner.Streak.MORTIMER, SlayerCompanionPlugin.streakFor(10));
		assertEquals(SlayerAssigner.Streak.MAIN, SlayerCompanionPlugin.streakFor(6));
		assertEquals(SlayerAssigner.Streak.MAIN, SlayerCompanionPlugin.streakFor(8));
		assertEquals("no task or an unknown master", SlayerAssigner.Streak.MAIN, SlayerCompanionPlugin.streakFor(0));
		assertEquals("Wilderness streak / points", SlayerCompanionPanel.streakHeading(SlayerAssigner.Streak.WILDERNESS));
		assertEquals("Mortimer streak / points", SlayerCompanionPanel.streakHeading(SlayerAssigner.Streak.MORTIMER));
		assertEquals("Streak / points", SlayerCompanionPanel.streakHeading(SlayerAssigner.Streak.MAIN));
	}

	@Test
	public void theTaskRateAsALineAndAsACompactItem()
	{
		assertEquals("Shows once the task count first goes down.", SlayerCompanionPlugin.rateText(0, 0, 150, false));
		assertEquals("After the first kill", SlayerCompanionPlugin.rateText(0, 0, 150, true));
		assertEquals("Learning the rate. 2 done so far.", SlayerCompanionPlugin.rateText(2, 600, 148, false));
		assertEquals("Learning, 2 done", SlayerCompanionPlugin.rateText(2, 600, 148, true));
		assertEquals("Learning, 5 done", SlayerCompanionPlugin.rateText(5, 59, 145, true));
		// 31 in six minutes is 310 an hour, so 62 left take 12 minutes
		assertEquals("310 an hour. About 12m left.", SlayerCompanionPlugin.rateText(31, 360, 62, false));
		assertEquals("310 an hour, 12m left", SlayerCompanionPlugin.rateText(31, 360, 62, true));
		assertEquals("60 an hour, 2h 30m left", SlayerCompanionPlugin.rateText(60, 3600, 150, true));
	}

	@Test
	public void startupSeedsOnlyAnAlreadyLoggedInProfile()
	{
		assertEquals("profile-a", SlayerCompanionPlugin.initialProfileKey(GameState.LOGGED_IN, "profile-a"));
		assertEquals(null, SlayerCompanionPlugin.initialProfileKey(GameState.LOGIN_SCREEN, "profile-a"));
	}

	/**
	 * When the scene panel shows: after a new task, within three minutes of the last kill, or always when the player
	 * asked for that. It needs no other plugin for this.
	 */
	@Test
	public void thePanelShowsAfterANewTaskAndWhileKillingOrAlways()
	{
		assertEquals("three minutes of game ticks", 180.0, SlayerCompanionPlugin.WORKING_TICKS * 0.6, 0.001);
		// The new-task window, before any kill
		assertTrue(SlayerCompanionPlugin.overlayShows(false, 150, 200, -1));
		assertFalse(SlayerCompanionPlugin.overlayShows(false, 201, 200, -1));
		// Working on the task
		final int lastKill = 1000;
		assertTrue(SlayerCompanionPlugin.overlayShows(false, lastKill, 200, lastKill));
		assertTrue(SlayerCompanionPlugin.overlayShows(false, lastKill + SlayerCompanionPlugin.WORKING_TICKS, 200, lastKill));
		assertFalse("gone on a bank trip", SlayerCompanionPlugin.overlayShows(false, lastKill + SlayerCompanionPlugin.WORKING_TICKS + 1,
				200, lastKill));
		// Always show
		assertTrue(SlayerCompanionPlugin.overlayShows(true, 99_999, 200, -1));
	}
}
