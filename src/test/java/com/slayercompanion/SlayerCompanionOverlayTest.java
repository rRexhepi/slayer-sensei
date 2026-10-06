package com.slayercompanion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The scene panel's width follows its widest line, so lines such as "Status: 138 left" do not wrap in the game's font. */
public class SlayerCompanionOverlayTest
{
	@Test
	public void thePanelIsAsWideAsItsWidestLineWithinBounds()
	{
		// Short lines leave the panel at its narrowest
		assertEquals(SlayerCompanionOverlay.MIN_WIDTH, SlayerCompanionOverlay.panelWidth(0));
		assertEquals(SlayerCompanionOverlay.MIN_WIDTH, SlayerCompanionOverlay.panelWidth(100));
		// A wider line widens the panel by its width plus the room on both sides
		assertEquals(192, SlayerCompanionOverlay.panelWidth(180));
		assertEquals(SlayerCompanionOverlay.MAX_WIDTH, SlayerCompanionOverlay.panelWidth(228));
		// Wider still, and the panel stops growing so a long line wraps instead
		assertEquals(SlayerCompanionOverlay.MAX_WIDTH, SlayerCompanionOverlay.panelWidth(400));
	}
}
