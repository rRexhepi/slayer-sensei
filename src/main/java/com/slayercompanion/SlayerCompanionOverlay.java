package com.slayercompanion;

import com.slayercompanion.data.SlayerTask;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The panel over the game for the task in progress: the task's name, the kills left, Konar's area, a line for each
 * item that is missing or worth bringing, the streak and the points. The gear lines have their own setting, and
 * {@link SlayerCompanionPlugin#shouldShowOverlay} decides when the panel shows.
 */
public class SlayerCompanionOverlay extends OverlayPanel
{
	private final SlayerCompanionPlugin plugin;
	private final SlayerCompanionConfig config;

	/** One gear line: the compact sidebar's words in the sidebar's colour. */
	@Value
	static class GearLine
	{
		String text;
		Color color;
	}

	@Inject
	private SlayerCompanionOverlay(SlayerCompanionPlugin plugin, SlayerCompanionConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPriority(PRIORITY_LOW);
		setPosition(config.panelPosition());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPanel())
		{
			return null;
		}
		if (!plugin.shouldShowOverlay())
		{
			return null;
		}
		setPosition(config.panelPosition());

		final List<String> texts = new ArrayList<>();
		final SlayerTask t = plugin.getCurrentTask();
		if (t == null)
		{
			if (plugin.getTaskName() == null)
			{
				return null;
			}
			// Have a task the dataset doesn't cover yet.
			addTitle(plugin.getTaskName(), texts);
			addLine("No dossier for this task yet", Color.GRAY, texts);
			addGearLines(texts);
			return render(graphics, texts);
		}

		addTitle(t.getTask(), texts);
		addLine("Status", hasValue(plugin.getTaskAmount()) ? plugin.getTaskAmount() + " left" : "active", Color.WHITE, texts);
		final Destination destination = plugin.getDestination();
		if (destination != null && destination.getOrigin() == Destination.Origin.ASSIGNED)
		{
			addLine("Area", destination.getLabel(), new Color(120, 200, 255), texts);
		}
		addGearLines(texts);
		addLine("Streak", hasValue(plugin.getTaskStreak()) ? plugin.getTaskStreak() : "?", new Color(255, 215, 0), texts);
		addLine("Points", hasValue(plugin.getSlayerPoints()) ? plugin.getSlayerPoints() : "?", new Color(120, 200, 255), texts);

		return render(graphics, texts);
	}

	/** The panel's narrowest and widest size. */
	static final int MIN_WIDTH = 150;
	static final int MAX_WIDTH = 240;
	/** The panel's border on both sides of a line, plus a little room, so the widest line does not wrap. */
	private static final int SIDE_ROOM = 12;

	/** Sizes the panel to its widest line in the game's font, within the bounds, then draws it. */
	private Dimension render(Graphics2D graphics, List<String> texts)
	{
		int widest = 0;
		for (String text : texts)
		{
			widest = Math.max(widest, graphics.getFontMetrics().stringWidth(text));
		}
		panelComponent.setPreferredSize(new Dimension(panelWidth(widest), 0));
		return super.render(graphics);
	}

	/** The panel's width for its widest line: wide enough that the line does not wrap, within the bounds. */
	static int panelWidth(int widestLine)
	{
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widestLine + SIDE_ROOM));
	}

	private void addTitle(String text, List<String> texts)
	{
		panelComponent.getChildren().add(TitleComponent.builder().text(text).color(ColorScheme.BRAND_ORANGE).build());
		texts.add(text);
	}

	/** What is missing and what to bring, from the gear check the sidebar shows. Read once, as the client thread replaces it. */
	private void addGearLines(List<String> texts)
	{
		if (!config.showPanelGear())
		{
			return;
		}
		for (GearLine line : gearLines(plugin.getGearResult()))
		{
			addLine(line.getText(), line.getColor(), texts);
		}
	}

	/**
	 * A red line for each missing mandatory item and a yellow one for each item to bring, from the bank or recommended.
	 * Empty without either.
	 */
	static List<GearLine> gearLines(SlayerGearCheck.Result gear)
	{
		if (gear == null)
		{
			return Collections.emptyList();
		}
		final List<GearLine> lines = new ArrayList<>();
		for (String item : gear.getMissing())
		{
			lines.add(new GearLine(item, SlayerCompanionPanel.gearColor(SlayerGearCheck.Status.MISSING)));
		}
		for (String item : gear.getBring())
		{
			lines.add(new GearLine(item, SlayerCompanionPanel.gearColor(SlayerGearCheck.Status.BRING)));
		}
		return lines;
	}

	private void addLine(String label, String value, Color color, List<String> texts)
	{
		if (value == null || value.isEmpty())
		{
			return;
		}
		addLine(label + ": " + value, color, texts);
	}

	private void addLine(String text, Color color, List<String> texts)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(color).build());
		texts.add(text);
	}

	private static boolean hasValue(String value)
	{
		return value != null && !value.isEmpty();
	}

}
