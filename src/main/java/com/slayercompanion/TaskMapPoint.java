package com.slayercompanion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

/**
 * World map marker for the task destination. On screen it is a pin whose tip sits on the point. When the
 * point is off screen RuneLite snaps the marker to the edge of the map view and calls
 * {@link #onEdgeSnap}, and the pin becomes an orange arrow aimed at the destination. Clicking either form
 * focuses the map on the destination through RuneLite's own jumpOnClick handling.
 */
class TaskMapPoint extends WorldMapPoint
{
	/** RuneLite's own accent, the plugin's colour in the sidebar, on the panel and on its toolbar icon. */
	static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	private static final Color DARK = new Color(45, 45, 45, 240);
	static final String NAME = "Slayer task";

	static final BufferedImage PIN = drawPin();
	/** Tip of the pin, relative to the image's top left corner. */
	static final Point PIN_TIP = new Point(PIN.getWidth() / 2, PIN.getHeight() - 1);
	/** One arrow per compass direction, clockwise from north. */
	static final BufferedImage[] ARROWS = drawArrows();

	private int direction;

	TaskMapPoint(WorldPoint point, String tooltip)
	{
		super(point, PIN);
		setTarget(point);
		setName(NAME);
		setTooltip(tooltip);
		setSnapToEdge(true);
		setJumpOnClick(true);
		setImagePoint(PIN_TIP);
	}

	@Override
	public void onEdgeSnap()
	{
		setImage(ARROWS[direction]);
		setImagePoint(null);
	}

	@Override
	public void onEdgeUnsnap()
	{
		setImage(PIN);
		setImagePoint(PIN_TIP);
	}

	/** Aims the edge arrow from the centre of the map view. Cheap enough to call every frame. */
	void aimFrom(int mapCenterX, int mapCenterY)
	{
		final int d = direction(mapCenterX, mapCenterY, getWorldPoint().getX(), getWorldPoint().getY());
		if (d == direction)
		{
			return;
		}
		direction = d;
		if (isCurrentlyEdgeSnapped())
		{
			setImage(ARROWS[d]);
		}
	}

	int getDirection()
	{
		return direction;
	}

	/**
	 * Compass direction from one world position to another, as 0 to 7 clockwise from north. World y grows
	 * northwards, which is up on the map.
	 */
	static int direction(int fromX, int fromY, int toX, int toY)
	{
		if (fromX == toX && fromY == toY)
		{
			return 0;
		}
		final double degrees = Math.toDegrees(Math.atan2(toX - fromX, toY - fromY));
		return (int) Math.round((degrees + 360.0) % 360.0 / 45.0) % 8;
	}

	private static BufferedImage drawPin()
	{
		final int width = 20;
		final int height = 28;
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final Polygon point = new Polygon(new int[]{4, 16, width / 2}, new int[]{13, 13, height - 1}, 3);
		g.setColor(DARK);
		g.fillPolygon(point);
		g.setColor(ORANGE);
		g.setStroke(new BasicStroke(1.5f));
		g.drawPolygon(point);
		g.setColor(DARK);
		g.fillOval(0, 0, 19, 19);
		g.setColor(ORANGE);
		g.drawOval(1, 1, 17, 17);
		g.setFont(new Font("SansSerif", Font.BOLD, 12));
		g.drawString("S", 6, 14);
		g.dispose();
		return image;
	}

	private static BufferedImage[] drawArrows()
	{
		final BufferedImage[] arrows = new BufferedImage[8];
		for (int i = 0; i < arrows.length; i++)
		{
			arrows[i] = drawArrow(i * 45);
		}
		return arrows;
	}

	/** An orange arrow pointing up, turned clockwise by the given angle. */
	private static BufferedImage drawArrow(int degrees)
	{
		final int size = 26;
		final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setTransform(AffineTransform.getRotateInstance(Math.toRadians(degrees), size / 2.0, size / 2.0));
		final Polygon arrow = new Polygon(
				new int[]{13, 23, 17, 17, 9, 9, 3},
				new int[]{2, 13, 13, 23, 23, 13, 13}, 7);
		g.setColor(ORANGE);
		g.fillPolygon(arrow);
		g.setColor(DARK);
		g.setStroke(new BasicStroke(1.5f));
		g.drawPolygon(arrow);
		g.dispose();
		return image;
	}
}
