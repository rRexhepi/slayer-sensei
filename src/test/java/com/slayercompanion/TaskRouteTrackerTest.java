package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.TaskRouteTracker.Action;
import com.slayercompanion.TaskRouteTracker.Assignment;
import org.junit.Test;

public class TaskRouteTrackerTest
{
	private static final Assignment HELLHOUNDS = new Assignment("profile-a", "Hellhounds", 31, 0, 6, 150);
	private static final Assignment HELLHOUNDS_CATACOMBS = new Assignment("profile-a", "Hellhounds", 31, 0, 1, 150);
	private static final Assignment DRAKES = new Assignment("profile-a", "Drakes", 112, 0, 3, 90);

	private final TaskRouteTracker tracker = new TaskRouteTracker();

	/** Feeds the same observation for several ticks and returns the last decision that was not NONE. */
	private Action feed(Assignment assignment, boolean autoPath, int ticks)
	{
		Action result = Action.NONE;
		for (int i = 0; i < ticks; i++)
		{
			final Action action = tracker.observe(assignment, autoPath);
			if (action != Action.NONE)
			{
				assertEquals("only one decision per change", Action.NONE, result);
				result = action;
			}
		}
		return result;
	}

	private void settle(Assignment assignment)
	{
		tracker.sessionStarted();
		assertEquals(Action.NONE, feed(assignment, true, TaskRouteTracker.SETTLE_TICKS + 1));
	}

	@Test
	public void loggingInWithATaskInProgressNeverDrawsARoute()
	{
		tracker.sessionStarted();
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 50));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void varpsArrivingLateAfterLoginStillCountAsTheBaseline()
	{
		tracker.sessionStarted();
		// No task for the first ticks while the varps load, then the task that was already running.
		assertEquals(Action.NONE, feed(null, true, 2));
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 20));
	}

	@Test
	public void aNewAssignmentDuringPlayDrawsOneRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 10));
		assertTrue(tracker.ownsRoute());
		assertEquals("the same assignment never asks twice", Action.NONE, feed(HELLHOUNDS, true, 30));
	}

	@Test
	public void aNewAssignmentWithoutAutoPathDrawsNothing()
	{
		settle(null);
		assertEquals(Action.NONE, feed(HELLHOUNDS, false, 10));
		assertFalse(tracker.ownsRoute());
	}

	/** The name is read from the game on the same tick as the varps, so a new task counts on the tick it appears. */
	@Test
	public void aNewAssignmentCountsOnItsFirstTickAndOnlyOnce()
	{
		settle(DRAKES);
		assertEquals(Action.PATH, tracker.observe(HELLHOUNDS, true));
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 10));
		// A task taken while another was in progress is new too, at once
		assertEquals(Action.PATH, tracker.observe(DRAKES, true));
	}

	@Test
	public void completingTheTaskClearsOurRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		assertEquals(Action.CLEAR, feed(null, true, 3));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void aSkipToAnotherTaskReplacesOurRouteWithTheNewOne()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		assertEquals(Action.PATH, feed(DRAKES, true, 3));
		assertTrue(tracker.ownsRoute());
	}

	@Test
	public void aChangedAreaForTheSameTaskIsANewAssignment()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		assertEquals(Action.CLEAR, feed(HELLHOUNDS_CATACOMBS, false, 3));
	}

	@Test
	public void aRouteTheUserTookOverIsNeverCleared()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		tracker.routeTakenOver();
		assertEquals(Action.NONE, feed(null, true, 5));
	}

	@Test
	public void aRouteWeNeverDrewIsNeverCleared()
	{
		settle(HELLHOUNDS);
		assertEquals(Action.NONE, feed(null, true, 5));
		assertEquals(Action.NONE, feed(DRAKES, false, 5));
	}

	@Test
	public void theButtonRouteIsClearedWhenTheTaskEnds()
	{
		settle(HELLHOUNDS);
		tracker.routeRequested(HELLHOUNDS);
		assertTrue(tracker.ownsRoute());
		assertEquals(Action.CLEAR, feed(null, false, 3));
	}

	@Test
	public void ourOwnClearDropsOwnership()
	{
		settle(HELLHOUNDS);
		tracker.routeRequested(HELLHOUNDS);
		tracker.routeCleared();
		assertEquals(Action.NONE, feed(null, true, 5));
	}

	@Test
	public void aHopOnTheWayKeepsTheRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		tracker.sessionStarted();
		assertEquals(Action.NONE, feed(null, true, 1));
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 20));
		assertTrue(tracker.ownsRoute());
	}

	@Test
	public void loggingBackInAfterTheTaskChangedElsewhereClearsTheOldRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		tracker.sessionStarted();
		// Finished on another device, so a different task is already running at login. No new route.
		assertEquals(Action.CLEAR, feed(DRAKES, true, 20));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void anotherCharacterLoggingInClearsTheRouteOfTheFirst()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		tracker.sessionStarted();
		final Assignment sameTaskOtherCharacter = new Assignment("profile-b", "Hellhounds", 31, 0, 6, 150);
		assertEquals(Action.CLEAR, feed(sameTaskOtherCharacter, true, 20));
	}

	@Test
	public void pickingAnotherPlaceMovesOurRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		assertEquals(Action.PATH, tracker.placeChanged(true));
		assertTrue(tracker.ownsRoute());
		assertEquals("the same assignment, so no second route", Action.NONE, feed(HELLHOUNDS, true, 10));
		assertEquals("still ours to clear when the task ends", Action.CLEAR, feed(null, true, 3));
	}

	@Test
	public void pickingAPlaceShortestPathCannotReachClearsOurRoute()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		assertEquals(Action.CLEAR, tracker.placeChanged(false));
		assertFalse(tracker.ownsRoute());
		assertEquals(Action.NONE, feed(null, true, 3));
	}

	@Test
	public void pickingAPlaceWithoutARouteOfOursDrawsNothing()
	{
		settle(HELLHOUNDS);
		assertEquals(Action.NONE, tracker.placeChanged(true));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void aRouteTheUserTookOverIsNotMovedByAPick()
	{
		settle(null);
		assertEquals(Action.PATH, feed(HELLHOUNDS, true, 3));
		tracker.routeTakenOver();
		assertEquals(Action.NONE, tracker.placeChanged(true));
	}

	// ---- the route to the next master ----

	@Test
	public void finishingTheTaskKeepsTheRouteToTheNextMaster()
	{
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		assertTrue(tracker.ownsRoute());
		assertTrue(tracker.ownsMasterRoute());
		// The task is done on the way. That is when the route is needed, so it stays.
		assertEquals(Action.NONE, feed(null, true, 10));
		assertTrue(tracker.ownsMasterRoute());
	}

	@Test
	public void theNextTaskTakesOverInNewTaskMode()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		assertEquals(Action.PATH, feed(DRAKES, true, 3));
		assertTrue("the route to the task is ours", tracker.ownsRoute());
		assertFalse(tracker.ownsMasterRoute());
		assertEquals("and goes when that task ends", Action.CLEAR, feed(null, true, 3));
	}

	@Test
	public void otherwiseTheNextTaskClearsTheRouteToTheMaster()
	{
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		assertEquals(Action.NONE, feed(null, false, 3));
		assertEquals(Action.CLEAR, feed(DRAKES, false, 3));
		assertFalse(tracker.ownsRoute());
		// A skip to a new task while the old one was in progress is a new assignment too
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		assertEquals(Action.CLEAR, feed(DRAKES, false, 3));
	}

	@Test
	public void clearPathClearsTheRouteToTheMaster()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		tracker.routeCleared();
		assertFalse(tracker.ownsRoute());
		assertEquals(Action.NONE, feed(DRAKES, false, 5));
	}

	@Test
	public void aRouteToTheMasterTheUserTookOverIsNeverCleared()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		tracker.routeTakenOver();
		assertFalse(tracker.ownsRoute());
		assertEquals(Action.NONE, feed(DRAKES, false, 5));
	}

	@Test
	public void aPickedPlaceDoesNotMoveTheRouteToTheMaster()
	{
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		assertEquals(Action.NONE, tracker.placeChanged(true));
		assertTrue(tracker.ownsMasterRoute());
	}

	@Test
	public void aTaskButtonRouteReplacesTheRouteToTheMaster()
	{
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		tracker.routeRequested(HELLHOUNDS);
		assertFalse(tracker.ownsMasterRoute());
		assertEquals("the task route goes with its task", Action.CLEAR, feed(null, false, 3));
	}

	@Test
	public void aHopBetweenTasksKeepsTheRouteToTheMaster()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		// A hop starts the session without a character, and the login names it
		tracker.sessionStarted();
		tracker.sessionStarted("profile-a");
		assertEquals(Action.NONE, feed(null, true, 20));
		assertTrue(tracker.ownsMasterRoute());
	}

	@Test
	public void aHopOnTheSameTaskKeepsTheRouteToTheMaster()
	{
		settle(HELLHOUNDS);
		tracker.masterRouteRequested("profile-a", HELLHOUNDS);
		tracker.sessionStarted("profile-a");
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 20));
		assertTrue(tracker.ownsMasterRoute());
	}

	@Test
	public void anotherCharacterLoggingInClearsTheRouteToTheMaster()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		tracker.sessionStarted("profile-b");
		assertEquals(Action.CLEAR, feed(null, true, 20));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void aTaskTakenElsewhereClearsTheRouteToTheMasterAtLogin()
	{
		settle(null);
		tracker.masterRouteRequested("profile-a", null);
		tracker.sessionStarted("profile-a");
		// A task already in progress at login never gets a route of its own
		assertEquals(Action.CLEAR, feed(DRAKES, true, 20));
		assertFalse(tracker.ownsRoute());
	}

	@Test
	public void aTaskReceivedDuringTheSettleWindowIsTreatedAsTheBaseline()
	{
		tracker.sessionStarted();
		assertEquals(Action.NONE, feed(null, true, 2));
		assertEquals(Action.NONE, feed(HELLHOUNDS, true, 10));
		assertFalse(tracker.ownsRoute());
	}
}
