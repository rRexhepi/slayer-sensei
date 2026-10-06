package com.slayercompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshGateTest
{
	private final RefreshGate gate = new RefreshGate();

	@Test
	public void withNoListOpenTheSidebarRebuildsAtOnce()
	{
		assertTrue(gate.shouldRebuild(false));
		assertFalse("nothing was held back", gate.popupClosed());
	}

	@Test
	public void aRefreshWhileAListIsOpenWaitsAndRunsOnceWhenItCloses()
	{
		assertFalse(gate.shouldRebuild(true));
		assertFalse("a kill on every tick still means one rebuild", gate.shouldRebuild(true));
		assertTrue(gate.popupClosed());
		assertFalse(gate.popupClosed());
	}

	@Test
	public void closingAListWithNothingHeldBackDoesNotRebuild()
	{
		assertFalse(gate.popupClosed());
	}

	@Test
	public void aRebuildAfterTheListClosedClearsTheHold()
	{
		assertFalse(gate.shouldRebuild(true));
		assertTrue(gate.shouldRebuild(false));
		assertFalse(gate.popupClosed());
	}
}
