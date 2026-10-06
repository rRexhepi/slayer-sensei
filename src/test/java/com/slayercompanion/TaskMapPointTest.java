package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;

public class TaskMapPointTest
{
	private static final WorldPoint CATACOMBS = new WorldPoint(1636, 3673, 0);

	/** The plugin's one colour, as on its sidebar headings, the panel's title and its toolbar icon. */
	@Test
	public void thePinAndTheEdgeArrowsAreRuneLitesOrange()
	{
		assertEquals(ColorScheme.BRAND_ORANGE, TaskMapPoint.ORANGE);
		assertTrue(drawsIn(TaskMapPoint.PIN, ColorScheme.BRAND_ORANGE));
		for (BufferedImage arrow : TaskMapPoint.ARROWS)
		{
			assertTrue(drawsIn(arrow, ColorScheme.BRAND_ORANGE));
		}
	}

	private static boolean drawsIn(BufferedImage image, Color color)
	{
		for (int x = 0; x < image.getWidth(); x++)
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				if (image.getRGB(x, y) == color.getRGB()) return true;
			}
		}
		return false;
	}

	@Test
	public void directionsGoClockwiseFromNorth()
	{
		assertEquals(0, TaskMapPoint.direction(0, 0, 0, 100));
		assertEquals(1, TaskMapPoint.direction(0, 0, 100, 100));
		assertEquals(2, TaskMapPoint.direction(0, 0, 100, 0));
		assertEquals(3, TaskMapPoint.direction(0, 0, 100, -100));
		assertEquals(4, TaskMapPoint.direction(0, 0, 0, -100));
		assertEquals(5, TaskMapPoint.direction(0, 0, -100, -100));
		assertEquals(6, TaskMapPoint.direction(0, 0, -100, 0));
		assertEquals(7, TaskMapPoint.direction(0, 0, -100, 100));
	}

	@Test
	public void directionsSnapToTheNearestEighth()
	{
		assertEquals(0, TaskMapPoint.direction(0, 0, 10, 100));
		assertEquals(0, TaskMapPoint.direction(0, 0, -10, 100));
		assertEquals(2, TaskMapPoint.direction(0, 0, 100, 30));
		assertEquals("Mount Karuulm is west of the Grand Exchange", 6,
				TaskMapPoint.direction(3164, 3487, 1308, 3807));
		assertEquals(0, TaskMapPoint.direction(5, 5, 5, 5));
	}

	@Test
	public void theMarkerSnapsToTheEdgeAndJumpsOnClick()
	{
		final TaskMapPoint point = new TaskMapPoint(CATACOMBS, "Slayer task: Fire giants");
		assertTrue(point.isSnapToEdge());
		assertTrue(point.isJumpOnClick());
		assertEquals(CATACOMBS, point.getTarget());
		assertEquals("a jump-on-click point needs a name for its Focus on menu entry", TaskMapPoint.NAME, point.getName());
		assertEquals("Slayer task: Fire giants", point.getTooltip());
	}

	@Test
	public void onScreenItIsAPinWithItsTipOnThePoint()
	{
		final TaskMapPoint point = new TaskMapPoint(CATACOMBS, "tip");
		assertSame(TaskMapPoint.PIN, point.getImage());
		assertEquals(TaskMapPoint.PIN_TIP, point.getImagePoint());
		assertEquals(TaskMapPoint.PIN.getHeight() - 1, point.getImagePoint().getY());
	}

	@Test
	public void atTheEdgeItBecomesAnArrowAimedAtTheDestination()
	{
		final TaskMapPoint point = new TaskMapPoint(CATACOMBS, "tip");
		// Map centred far to the east, so the destination is to the west.
		point.aimFrom(CATACOMBS.getX() + 500, CATACOMBS.getY());
		point.setCurrentlyEdgeSnapped(true);
		point.onEdgeSnap();
		assertSame(TaskMapPoint.ARROWS[6], point.getImage());
		assertNull("the arrow is centred on the edge position", point.getImagePoint());

		// Panning so the destination is now to the south re-aims the arrow while snapped.
		point.aimFrom(CATACOMBS.getX(), CATACOMBS.getY() + 500);
		assertSame(TaskMapPoint.ARROWS[4], point.getImage());

		point.setCurrentlyEdgeSnapped(false);
		point.onEdgeUnsnap();
		assertSame(TaskMapPoint.PIN, point.getImage());
		assertEquals(TaskMapPoint.PIN_TIP, point.getImagePoint());
	}

	@Test
	public void aimingWhileOnScreenKeepsThePin()
	{
		final TaskMapPoint point = new TaskMapPoint(CATACOMBS, "tip");
		point.aimFrom(CATACOMBS.getX() + 500, CATACOMBS.getY());
		assertSame(TaskMapPoint.PIN, point.getImage());
		assertEquals(6, point.getDirection());
	}

	@Test
	public void everyDirectionHasItsOwnArrow()
	{
		assertEquals(8, TaskMapPoint.ARROWS.length);
		for (int i = 1; i < TaskMapPoint.ARROWS.length; i++)
		{
			assertNotSame(TaskMapPoint.ARROWS[0], TaskMapPoint.ARROWS[i]);
		}
	}

	@Test
	public void theTooltipNamesTheTaskAndKonarsArea()
	{
		final Destination assigned = new Destination(Destination.Origin.ASSIGNED, "neypotzli", 50, "Neypotzli",
				new WorldPoint(1435, 3131, 0), "Cam Torum entrance", true, null, null, null, java.util.Collections.emptyList());
		assertEquals("Slayer task: Wyrms<br>Konar assigned: Neypotzli<br>Marker: Cam Torum entrance",
				SlayerCompanionPlugin.mapTooltip("Wyrms", assigned));
		final Destination recommended = new Destination(Destination.Origin.RECOMMENDED, "chasm_of_fire", 0, "Chasm of Fire",
				new WorldPoint(1432, 3670, 0), null, true, null, null, null, java.util.Collections.emptyList());
		assertEquals("Slayer task: Black demons<br>Chasm of Fire", SlayerCompanionPlugin.mapTooltip("Black demons", recommended));
	}
}
