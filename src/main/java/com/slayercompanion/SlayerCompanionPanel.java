package com.slayercompanion;

import com.slayercompanion.data.SlayerTask;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The task dossier and the points plan. Compact by default: headings in capitals with one short item per line, and the
 * sentences behind them in tooltips. Detailed shows every line as a sentence. The scene overlay stays compact either way.
 * Headings and labels are RuneLite's orange and items grey, coloured only where the colour means something.
 * <p>
 * A refresh describes the sidebar as rows. While the rows keep their shape, only their text changes in place, so a kill
 * updates the kills left, the rate and the gear counts without a rebuild. A rebuild puts the scroll position back, and no
 * text can scroll the sidebar by itself.
 */
public class SlayerCompanionPanel extends PluginPanel
{
	private static final int TEXT_WIDTH = 185;
	/**
	 * Compact items fill the width the sidebar leaves inside its borders while its scroll bar shows: 242 less the 7 pixel
	 * scroll bar, the panel's 6 pixel borders and the compact 4 pixel sides.
	 */
	static final int COMPACT_WIDTH = 215;
	private static final Border DETAILED_BORDER = BorderFactory.createEmptyBorder(10, 10, 10, 10);
	private static final Border COMPACT_BORDER = BorderFactory.createEmptyBorder(10, 4, 10, 4);
	/** Text width of a row in the open place list, inside the box's width. */
	private static final int ROW_TEXT_WIDTH = 150;
	/** Space above each compact heading. */
	private static final int SECTION_GAP = 8;
	/** A tooltip longer than this many characters wraps at {@link #TOOLTIP_WIDTH} pixels. */
	private static final int TOOLTIP_WRAP = 60;
	private static final int TOOLTIP_WIDTH = 280;
	/** Every heading, the task's name and the detailed sidebar's labels: RuneLite's own accent. */
	static final Color HEADING_COLOR = ColorScheme.BRAND_ORANGE;
	/** Items and sentences, unless their colour means something. */
	static final Color TEXT_COLOR = ColorScheme.TEXT_COLOR;
	/** Gear that is all there, purchases to make now and blocks to keep. */
	static final Color GREEN = new Color(80, 220, 100);
	/** Purchases to skip and blocks to lift. */
	static final Color SKIP_COLOR = new Color(255, 120, 120);
	/** Missing gear, in the sidebar and on the scene overlay. */
	static final Color MISSING_COLOR = new Color(255, 80, 80);
	/** An item to bring from the bank, or a recommended one. */
	static final Color BRING_COLOR = Color.YELLOW;
	/** In the GEAR heading's tooltip until the bank has been seen, since until then only carried and worn items count. */
	static final String BANK_HINT = "Open your bank once so the gear check can see what is in it.";
	/** {@link #MISSING_COLOR} for the place list's HTML rows, where a locked place says what it needs. */
	static final String NEEDS_HTML_COLOR = String.format("#%02x%02x%02x", MISSING_COLOR.getRed(), MISSING_COLOR.getGreen(),
			MISSING_COLOR.getBlue());

	static final String KRYSTILIA_NOTE = "Krystilia's tasks only count in the Wilderness.";
	static final String WILDERNESS_ITEM = "Wilderness only";
	static final String NO_ROUTE_ITEM = "No route";
	/** The detailed sidebar's elemental weakness when the monsters have none. */
	static final String NO_ELEMENTAL = "None";
	private static final String NO_TASK = "No active Slayer task detected.";
	private static final String NOT_IN_DOSSIER = "This task is not in the dossier yet. Streak and points still show.";
	private static final String NO_MAP_POINT = "No verified map point for this area yet.";
	private static final String NOTHING_AFFORDABLE = "Nothing affordable above your reserve yet.";
	private static final String NO_CACHE = "The game's Slayer tables could not be read, so the plan cannot judge what to buy. Shop progress uses the"
			+ " built-in list.";

	private final SlayerCompanionPlugin plugin;
	private final SlayerCompanionConfig config;
	private final JPanel content = new JPanel();
	private final RefreshGate refreshGate = new RefreshGate();
	/** The rows on screen, each with its component. */
	private List<Row> shown = Collections.emptyList();
	/** Runs a refresh that waited for an open dropdown. */
	private final PopupMenuListener popupWatcher = new PopupMenuListener()
	{
		@Override
		public void popupMenuWillBecomeVisible(PopupMenuEvent e)
		{
		}

		@Override
		public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
		{
			// The list is still on screen during this call, so the held refresh runs on the next turn.
			if (refreshGate.popupClosed()) SwingUtilities.invokeLater(SlayerCompanionPanel.this::refresh);
		}

		@Override
		public void popupMenuCanceled(PopupMenuEvent e)
		{
		}
	};

	@Inject
	SlayerCompanionPanel(SlayerCompanionPlugin plugin, SlayerCompanionConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);
		refresh();
	}

	void refresh()
	{
		// A rebuild would close a dropdown the player has open, so the refresh waits until the list closes.
		if (!refreshGate.shouldRebuild(popupOpen(content))) return;
		final Rows rows = new Rows();
		final boolean detailed = config.sidebarDetail() == SidebarDetail.DETAILED;
		if (detailed)
		{
			detailed(rows);
		}
		else
		{
			compact(rows);
		}
		if (!updateInPlace(rows.list))
		{
			content.setBorder(detailed ? DETAILED_BORDER : COMPACT_BORDER);
			rebuild(rows.list);
		}
	}

	// ---- rows ----

	private enum Kind
	{
		LABEL,
		/** A compact heading: bold text that wraps, so a long task name is never cut. */
		HEADING,
		TEXT,
		GAP,
		CONTROL
	}

	/** One line of the sidebar, and once it is on screen the component that shows it. */
	private static final class Row
	{
		final Kind kind;
		/** Rows of one kind with the same key are the same line, so a refresh can change the one on screen in place. */
		final String key;
		final String text;
		final Color color;
		final String tooltip;
		/** A text's width, a gap's height. */
		final int size;
		final Supplier<JComponent> control;
		JComponent component;

		Row(Kind kind, String key, String text, Color color, String tooltip, int size, Supplier<JComponent> control)
		{
			this.kind = kind;
			this.key = key;
			this.text = text;
			this.color = color;
			this.tooltip = tooltip;
			this.size = size;
			this.control = control;
		}

		boolean sameLine(Row other)
		{
			return kind == other.kind && key.equals(other.key);
		}
	}

	/** The rows of one refresh, in order. */
	private static final class Rows
	{
		final List<Row> list = new ArrayList<>();

		/** A bold line. Its text is its key, so a new heading means a rebuild. */
		void label(String text, Color color, String tooltip)
		{
			list.add(new Row(Kind.LABEL, text, text, color, tooltip, 0, null));
		}

		/** Wrapping text. Its text can change in place. */
		void text(String text, Color color, int width, String tooltip)
		{
			list.add(new Row(Kind.TEXT, "text " + width, text, color, tooltip, width, null));
		}

		void gap(int height)
		{
			list.add(new Row(Kind.GAP, "gap " + height, null, null, null, height, null));
		}

		/** A dropdown or buttons. The key holds everything they show, so any change rebuilds them. */
		void control(String key, Supplier<JComponent> control)
		{
			list.add(new Row(Kind.CONTROL, key, null, null, null, 0, control));
		}

		/** The detailed sidebar's title. */
		void title(String text)
		{
			label(text, HEADING_COLOR, null);
			gap(8);
		}

		/** The detailed sidebar's label and sentence, left out when there is nothing to say. */
		void field(String label, String value, Color valueColor)
		{
			if (value == null || value.isEmpty()) return;
			label(label, HEADING_COLOR, null);
			text(value, valueColor, TEXT_WIDTH, null);
			gap(8);
		}

		/** A compact heading in capitals. Its text is its key, so a new heading means a rebuild. */
		void heading(String text, String tooltip)
		{
			if (!list.isEmpty()) gap(SECTION_GAP);
			list.add(new Row(Kind.HEADING, text, text, HEADING_COLOR, tooltip, COMPACT_WIDTH, null));
		}

		/** A compact item, left out when there is nothing to say. */
		void item(String text, Color color, String tooltip)
		{
			if (text == null || text.isEmpty()) return;
			text("- " + text, color, COMPACT_WIDTH, tooltip);
		}
	}

	/** Shows the new rows in the components on screen. False when the rows have another shape, which takes a rebuild. */
	private boolean updateInPlace(List<Row> next)
	{
		if (next.size() != shown.size()) return false;
		for (int i = 0; i < next.size(); i++)
		{
			if (!next.get(i).sameLine(shown.get(i))) return false;
		}
		boolean resized = false;
		for (int i = 0; i < next.size(); i++)
		{
			final Row old = shown.get(i);
			final Row row = next.get(i);
			row.component = old.component;
			if (!Objects.equals(old.tooltip, row.tooltip)) row.component.setToolTipText(tooltip(row.tooltip));
			if (!Objects.equals(old.color, row.color)) row.component.setForeground(row.color);
			if (row.kind == Kind.TEXT && !old.text.equals(row.text))
			{
				final JTextArea text = (JTextArea) row.component;
				text.setText(row.text);
				resized |= fit(text, row.size);
			}
		}
		shown = next;
		if (resized) content.revalidate();
		return true;
	}

	private void rebuild(List<Row> next)
	{
		final JScrollPane scrollPane = getScrollPane();
		final int scrolled = scrollPane == null ? 0 : scrollPane.getVerticalScrollBar().getValue();
		content.removeAll();
		for (Row row : next)
		{
			row.component = build(row);
			content.add(row.component);
		}
		shown = next;
		content.revalidate();
		content.repaint();
		keepScroll(scrollPane, scrolled);
	}

	/** A rebuild leaves the sidebar where the player scrolled it, as far as the new height allows. */
	private static void keepScroll(JScrollPane scrollPane, int scrolled)
	{
		if (scrollPane == null) return;
		// Lays the new rows out now, so the position is back before anything is painted. Without a window this does nothing.
		scrollPane.validate();
		scrollPane.getVerticalScrollBar().setValue(scrolled);
	}

	private JComponent build(Row row)
	{
		switch (row.kind)
		{
			case LABEL:
			{
				final JLabel label = new JLabel(row.text);
				label.setForeground(row.color);
				label.setFont(label.getFont().deriveFont(Font.BOLD));
				label.setAlignmentX(Component.LEFT_ALIGNMENT);
				label.setToolTipText(tooltip(row.tooltip));
				return label;
			}
			case HEADING:
			case TEXT:
			{
				final Font font = new JLabel().getFont().deriveFont(row.kind == Kind.HEADING ? Font.BOLD : Font.PLAIN);
				final JTextArea text = wrappingText(row.text, row.color, font, row.size);
				text.setAlignmentX(Component.LEFT_ALIGNMENT);
				text.setToolTipText(tooltip(row.tooltip));
				return text;
			}
			case GAP:
				return (JComponent) Box.createRigidArea(new Dimension(0, row.size));
			default:
				return row.control.get();
		}
	}

	/** Plain text as a tooltip: its line breaks kept, wrapped at {@link #TOOLTIP_WIDTH} pixels when long. Null without text. */
	static String tooltip(String text)
	{
		if (blank(text)) return null;
		final String html = escapeHtml(text.trim()).replace("\n", "<br>");
		return text.trim().length() > TOOLTIP_WRAP ? "<html><body width='" + TOOLTIP_WIDTH + "'>" + html + "</body></html>"
				: "<html>" + html + "</html>";
	}

	// ---- detailed ----

	/** Every line as a sentence. */
	private void detailed(Rows rows)
	{
		final SlayerTask task = plugin.getCurrentTask();
		rows.title(task == null ? "Slayer Sensei" : task.getTask());
		if (plugin.getTaskName() == null)
		{
			rows.field("Status", NO_TASK, TEXT_COLOR);
		}
		else if (task == null)
		{
			rows.field("Task", plugin.getTaskName(), TEXT_COLOR);
			rows.field("Guide", NOT_IN_DOSSIER, TEXT_COLOR);
		}
		else
		{
			rows.field("Progress", progress(), TEXT_COLOR);
			rows.field(streakHeading(plugin.getStreakKind()), value(plugin.getTaskStreak()) + " / " + value(plugin.getSlayerPoints()), TEXT_COLOR);
			rows.field("Assigned by", plugin.assignedBy(task), TEXT_COLOR);
			rows.field("Verdict", SlayerTaskText.verdict(task, config.goal(), plugin.getTaskMaster()), TEXT_COLOR);
			final Destination destination = plugin.getDestination();
			final TaskPlaces places = plugin.getPlaces();
			// Konar's area, and a place the player picked, replace the dossier's general advice. Unknown facts
			// for them are left out rather than borrowed from the dossier, which may describe another place.
			final boolean assigned = destination != null && destination.getOrigin() == Destination.Origin.ASSIGNED;
			addWhereToGo(rows, task, destination, places, assigned);
			rows.field("Combat area", combatLine(destination, places, task), TEXT_COLOR);
			rows.field("Style", task.getStyle(), TEXT_COLOR);
			rows.field("Kill style", task.getKillStyle(), TEXT_COLOR);
			rows.field("Weakness", task.getWeakness(), TEXT_COLOR);
			rows.field("Elemental weakness", elementalLine(task), TEXT_COLOR);
			rows.field("Enemy attacks", task.getMonsterAttackStyle(), TEXT_COLOR);
			rows.field("Prayer", task.getProtectionPrayer(), TEXT_COLOR);
			rows.field("Required items", task.getRequiredItems(), TEXT_COLOR);
			addGearCheck(rows, plugin.getGearResult());
			rows.field("Cannon", cannonLine(destination, places, task), TEXT_COLOR);
			rows.field("Superior", superiorText(task), TEXT_COLOR);
			rows.field("Task rate", plugin.getProgressRate(), TEXT_COLOR);
			if (config.showUnlockAdvisor()) rows.field("Advisor", plugin.getUnlockAdvice(), TEXT_COLOR);
			rows.field("Notable drops", task.getNotableDrops(), TEXT_COLOR);
			rows.field("Data", dataLine(task), TEXT_COLOR);
		}
		addPointsPlan(rows, plugin.getPointsPlan());
	}

	static String streakHeading(SlayerAssigner.Streak streak)
	{
		switch (streak)
		{
			case WILDERNESS:
				return "Wilderness streak / points";
			case MORTIMER:
				return "Mortimer streak / points";
			default:
				return "Streak / points";
		}
	}

	private static boolean popupOpen(Container container)
	{
		for (Component c : container.getComponents())
		{
			if (c instanceof JComboBox && ((JComboBox<?>) c).isPopupVisible()) return true;
			if (c instanceof Container && popupOpen((Container) c)) return true;
		}
		return false;
	}

	/** Takes the value once, since the client thread replaces it every tick and may clear it between reads. */
	private static void addGearCheck(Rows rows, SlayerGearCheck.Result gear)
	{
		if (gear == null) return;
		rows.field("Live gear check", gear.getLine(), gearColor(gear.getStatus()));
	}

	/**
	 * Red for a missing mandatory item, yellow for one to bring from the bank or a recommended one, green when everything
	 * is there. The red is lighter than pure red, which is hard to read on the sidebar's grey and on the scene overlay.
	 */
	static Color gearColor(SlayerGearCheck.Status status)
	{
		switch (status)
		{
			case MISSING:
				return MISSING_COLOR;
			case BRING:
				return BRING_COLOR;
			default:
				return GREEN;
		}
	}

	private void addPointsPlan(Rows rows, SlayerPlanBuilder.View view)
	{
		if (!config.showPointsPlanner() || view == null) return;
		final SlayerPointsPlanner.Plan plan = view.getPlan();
		rows.gap(6);
		rows.title("Points plan (" + view.getMasterName() + ")");
		addGoalSelector(rows, view.getGoal());
		addRoutine(rows, view);
		addBestMaster(rows, view);
		rows.field("Points", SlayerPlanText.points(plan, view.getMaster().assigner(), view.getMasterName(), view.getGoal()), TEXT_COLOR);
		rows.field("Shop progress", shopProgress(plan), TEXT_COLOR);
		addLines(rows, "Buy now", plan.getBuyNow(), GREEN);
		if (plan.getBuyNow().isEmpty() && !plan.getSaveFor().isEmpty())
		{
			rows.field("Buy now", NOTHING_AFFORDABLE, TEXT_COLOR);
		}
		addLines(rows, "Save for", plan.getSaveFor(), TEXT_COLOR);
		addLines(rows, "Unlocks with levels", plan.getLater(), TEXT_COLOR);
		addLines(rows, SlayerPlanText.skipHeading(view.getGoal()), plan.getAvoid(), SKIP_COLOR);
		rows.field(view.getMasterName() + " block list", blockList(view.getBlocked(), view.getEmptySlots(), view.getLockedSlots(),
				view.getBlockedWeightPercent()), TEXT_COLOR);
		rows.field(SlayerPlanText.blockHeading(view), SlayerPlanText.blockAdvice(view), TEXT_COLOR);
		// On the Slayer points routine the filler tasks' list at Turael's spot, labelled apart from the milestone tasks'
		final SlayerPlanBuilder.Fillers fillers = view.getFillers();
		if (fillers != null)
		{
			rows.field(fillers.getNpc() + " block list", blockList(fillers.getBlocked(), fillers.getEmptySlots(), fillers.getLockedSlots(),
					fillers.getBlockedWeightPercent()), TEXT_COLOR);
			rows.field(SlayerPlanText.FILLER_BLOCK_HEADING, SlayerPlanText.fillerBlockAdvice(view), TEXT_COLOR);
		}
		for (String note : view.getNotes())
		{
			rows.field("Note", note, TEXT_COLOR);
		}
		if (plan.getCosmeticLeft() > 0)
		{
			rows.field("Cosmetic", cosmetic(plan), TEXT_COLOR);
		}
		if (!view.isFromCache())
		{
			rows.field("Data", NO_CACHE, TEXT_COLOR);
		}
	}

	/**
	 * One-click goal switch. The box gets its value before the listener is attached, so building it never fires a
	 * change. The plugin writes the config.
	 */
	private void addGoalSelector(Rows rows, SlayerGoal goal)
	{
		rows.label("Goal", HEADING_COLOR, null);
		rows.gap(3);
		rows.control("goal " + goal, () -> goalBox(goal, TEXT_WIDTH));
		rows.gap(8);
	}

	private JComboBox<SlayerGoal> goalBox(SlayerGoal goal, int width)
	{
		final JComboBox<SlayerGoal> box = new JComboBox<>(SlayerGoal.values());
		box.setSelectedItem(goal);
		box.setFocusable(false);
		box.setToolTipText("What the points plan works toward");
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		final Dimension size = new Dimension(width, 26);
		box.setPreferredSize(size);
		box.setMaximumSize(size);
		box.addItemListener(e ->
		{
			if (e.getStateChange() == ItemEvent.SELECTED) plugin.setGoal((SlayerGoal) e.getItem());
		});
		// Every dropdown in the sidebar, the goal box included, reports when its list closes
		box.addPopupMenuListener(popupWatcher);
		return box;
	}

	/** The Slayer points routine: the task in progress, who to visit next with a path there, and the next milestone. */
	private void addRoutine(Rows rows, SlayerPlanBuilder.View view)
	{
		final SlayerPointsRoutine.Advice advice = view.getRoutine();
		if (advice == null) return;
		rows.field("Current task", advice.getCurrent(), TEXT_COLOR);
		rows.field("Next task", advice.getNext(), TEXT_COLOR);
		addMasterRoute(rows, view, false);
		rows.field("Note", advice.getCaveat(), TEXT_COLOR);
		rows.field("Next milestone", advice.getMilestone(), TEXT_COLOR);
		rows.field("Streak", advice.getWarning(), TEXT_COLOR);
	}

	/** For XP and profit, where the routine sits for Slayer points: the best master, a reason, the runner-up and a path. */
	private void addBestMaster(Rows rows, SlayerPlanBuilder.View view)
	{
		final String heading = SlayerPlanText.masterHeading(view.getGoal());
		if (heading == null) return;
		rows.field(heading, SlayerPlanText.masterAdvice(view), TEXT_COLOR);
		addMasterRoute(rows, view, false);
	}

	/**
	 * The plan's path to the master the goal says to visit next. Without a task its route buttons are not shown, so Clear
	 * path comes here, and so does the hint when Shortest Path is missing or off.
	 */
	private void addMasterRoute(Rows rows, SlayerPlanBuilder.View view, boolean compact)
	{
		if (!config.pathMode().showsButtons()) return;
		final MasterRoute.Target target = MasterRoute.target(view);
		if (target == null) return;
		final boolean taskRoute = plugin.getTaskName() != null && plugin.getCurrentTask() != null;
		final PluginNotices.Notice notice = PluginNotices.shortestPath(plugin.getShortestPathStatus(), config.pathMode(),
				plugin.isPathTurnOnFailed());
		if (notice != null)
		{
			// The task's route already says it
			if (taskRoute) return;
			if (compact) compactNotice(rows, notice, plugin::turnOnShortestPath);
			else detailedNotice(rows, "Route", notice, plugin::turnOnShortestPath);
			return;
		}
		final boolean owned = plugin.ownsRoute();
		final int width = compact ? COMPACT_WIDTH : TEXT_WIDTH;
		final String key = "master " + width + " " + target + " " + !taskRoute + " " + owned;
		if (!compact)
		{
			rows.label("Route", HEADING_COLOR, null);
			rows.gap(3);
		}
		else
		{
			rows.gap(4);
		}
		rows.control(key, () -> masterButtons(target, !taskRoute, owned, width));
		if (!compact) rows.gap(8);
	}

	/** The path to the master, and Clear path below it when the task's buttons are not shown. */
	private JPanel masterButtons(MasterRoute.Target target, boolean clear, boolean owned, int width)
	{
		final JButton path = button(target.getLabel(), target.isRoutable(), target.getTooltip());
		path.addActionListener(e -> plugin.requestMasterRouteFromSidebar());
		final JPanel row = new JPanel(new GridLayout(clear ? 2 : 1, 1, 0, 4));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(path);
		if (clear) row.add(clearButton(owned));
		final Dimension size = new Dimension(width, clear ? 2 * 26 + 4 : 26);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	private static String blockList(List<String> blocked, int emptySlots, String lockedSlots, int blockedWeightPercent)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(blocked.isEmpty() ? "Nothing blocked" : String.join(", ", blocked));
		if (emptySlots > 0)
		{
			sb.append(". ").append(emptySlots).append(emptySlots == 1 ? " open slot is" : " open slots are").append(" empty");
		}
		if (lockedSlots != null)
		{
			sb.append(". ").append(lockedSlots);
		}
		if (blockedWeightPercent >= 0)
		{
			sb.append(". Blocks remove ").append(blockedWeightPercent).append("% of the weight of tasks you have unlocked");
		}
		return sb.toString();
	}

	private static void addLines(Rows rows, String label, List<SlayerPointsPlanner.Line> lines, Color color)
	{
		if (lines.isEmpty()) return;
		final StringBuilder sb = new StringBuilder();
		for (SlayerPointsPlanner.Line line : lines)
		{
			if (sb.length() > 0) sb.append("\n\n");
			sb.append(line.getName());
			if (line.getCost() >= 0) sb.append(" (").append(line.getCost()).append(")");
			sb.append(": ").append(line.getDetail());
		}
		rows.field(label, sb.toString(), color);
	}

	private void addWhereToGo(Rows rows, SlayerTask task, Destination destination, TaskPlaces places, boolean assigned)
	{
		if (assigned)
		{
			rows.field("Where to go", assignedText(destination), TEXT_COLOR);
			rows.field("Area note", destination.getNote(), TEXT_COLOR);
			rows.field("World map", destination.hasEntrance() ? "Marker: " + destination.markerLabel() : NO_MAP_POINT, TEXT_COLOR);
		}
		else
		{
			final TaskPlaces.Show show = TaskPlaces.show(places);
			final boolean wilderness = places != null && places.isWilderness();
			if (show == TaskPlaces.Show.PICKER)
			{
				addPlacePicker(rows, places);
			}
			else if (show == TaskPlaces.Show.ONE_PLACE)
			{
				rows.field("Where to go", places.getPlaces().get(0).getLabel(), TEXT_COLOR);
			}
			else
			{
				// No place list for this task: the dossier's own advice, as before. For Krystilia it says first
				// that only the Wilderness counts, since no Wilderness place is verified to mark or route to.
				rows.field("Where to go", whereToGoText(wilderness, task.getBestLocation()), TEXT_COLOR);
			}
			if (show != TaskPlaces.Show.DOSSIER)
			{
				rows.field("Access", places.accessLine(), accessColor(places));
			}
			if (wilderness && show != TaskPlaces.Show.DOSSIER)
			{
				rows.field("Wilderness only", KRYSTILIA_NOTE, TEXT_COLOR);
			}
			if (show != TaskPlaces.Show.DOSSIER && destination != null)
			{
				rows.field("Area note", destination.getNote(), TEXT_COLOR);
			}
			if (destination != null && destination.hasEntrance())
			{
				rows.field("World map", "Marker: " + destination.markerLabel(), TEXT_COLOR);
			}
		}
		addRouteControls(rows, destination, places);
	}

	/** The dossier's own text, after the Wilderness rule for a Krystilia task. */
	static String whereToGoText(boolean wilderness, String dossierText)
	{
		if (!wilderness) return dossierText;
		return dossierText == null || dossierText.trim().isEmpty() ? KRYSTILIA_NOTE : KRYSTILIA_NOTE + " " + dossierText.trim();
	}

	/** The places for a task whose master leaves the place open, with the line under the picker. */
	private void addPlacePicker(Rows rows, TaskPlaces places)
	{
		rows.label("Where to go", HEADING_COLOR, null);
		rows.gap(3);
		rows.control(placeKey(places, TEXT_WIDTH), () -> placeBox(places, TEXT_WIDTH));
		final String summary = places.summary();
		if (summary != null)
		{
			rows.gap(3);
			rows.text(summary, TEXT_COLOR, TEXT_WIDTH, null);
		}
		rows.gap(8);
	}

	/** Everything the place box shows, so a change rebuilds it. */
	private static String placeKey(TaskPlaces places, int width)
	{
		final List<String> keys = new ArrayList<>();
		for (TaskPlaces.Place place : places.getPlaces())
		{
			// A place that locks or opens, or takes the recommendation, shows differently in the list
			keys.add(place.getKey() + (place.isRecommended() ? " recommended" : "") + (place.isLocked() ? " " + place.getAccess().needs() : ""));
		}
		return "place " + width + " " + places.getTaskId() + " " + places.getChosenKey() + " " + places.isWilderness() + " " + keys;
	}

	/** Red when the place in use is locked, as for missing gear, and grey for a note alone. */
	static Color accessColor(TaskPlaces places)
	{
		final TaskPlaces.Place chosen = places == null ? null : places.chosen();
		return chosen != null && chosen.isLocked() ? MISSING_COLOR : TEXT_COLOR;
	}

	/**
	 * The places, the recommended one first. The box gets its value before the listener is attached, so a rebuild never
	 * fires a change, and picking the place already in use does nothing. The plugin stores the pick on the client thread.
	 */
	private JComboBox<TaskPlaces.Place> placeBox(TaskPlaces places, int width)
	{
		final JComboBox<TaskPlaces.Place> box = new JComboBox<>(places.getPlaces().toArray(new TaskPlaces.Place[0]));
		box.setRenderer(new PlaceRenderer());
		box.setSelectedItem(places.chosen());
		box.setFocusable(false);
		box.setMaximumRowCount(12);
		// Long names are cut short in the closed box, so the tooltip names the place in full, and says what it needs.
		final String access = places.accessLine();
		box.setToolTipText(places.chosen().getLabel() + ". " + (access == null ? "" : access + " ")
				+ "Pick where to do this task. The pick is remembered for the next time you get it.");
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		final Dimension size = new Dimension(width, 26);
		box.setPreferredSize(size);
		box.setMaximumSize(size);
		final int taskId = places.getTaskId();
		final String inUse = places.getChosenKey();
		box.addItemListener(e ->
		{
			if (e.getStateChange() != ItemEvent.SELECTED) return;
			final String key = ((TaskPlaces.Place) e.getItem()).getKey();
			if (!key.equals(inUse)) plugin.choosePlace(taskId, key);
		});
		box.addPopupMenuListener(popupWatcher);
		return box;
	}

	/**
	 * The closed box shows the place's name. Each row of the open list adds what a locked place needs, whether it is
	 * recommended and its facts, and its tooltip says what the place needs and its access note.
	 */
	private static final class PlaceRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
		{
			super.getListCellRendererComponent(list, value, index, selected, focused);
			if (value instanceof TaskPlaces.Place)
			{
				final TaskPlaces.Place place = (TaskPlaces.Place) value;
				setText(index < 0 ? place.getLabel() : rowHtml(place));
				setToolTipText(index < 0 ? null : tooltip(place.getAccess().sentence()));
			}
			return this;
		}
	}

	/** A width attribute wraps lines at that many pixels. A CSS width is scaled up and would not wrap. */
	static String rowHtml(TaskPlaces.Place place)
	{
		final String note = TaskPlaces.rowNote(place);
		final String needs = place.getAccess().needs();
		final StringBuilder html = new StringBuilder("<html><body width='").append(ROW_TEXT_WIDTH).append("'>")
				.append(escapeHtml(place.getLabel()));
		if (needs != null || note != null) html.append("<br>");
		if (needs != null) html.append("<font color='").append(NEEDS_HTML_COLOR).append("'>").append(escapeHtml(needs)).append(".</font>");
		if (needs != null && note != null) html.append(' ');
		if (note != null) html.append("<font color='#a0a0a0'>").append(escapeHtml(note)).append("</font>");
		return html.append("</body></html>").toString();
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Shortest Path buttons, or the notice that says it is optional when it cannot draw. */
	private void addRouteControls(Rows rows, Destination destination, TaskPlaces places)
	{
		if (!config.pathMode().showsButtons()) return;
		final PluginNotices.Notice notice = PluginNotices.shortestPath(plugin.getShortestPathStatus(), config.pathMode(),
				plugin.isPathTurnOnFailed());
		if (notice != null)
		{
			detailedNotice(rows, "Route", notice, plugin::turnOnShortestPath);
			return;
		}
		final String reason = noRouteReason(destination, places);
		final boolean owned = plugin.ownsRoute();
		rows.label("Route", HEADING_COLOR, null);
		rows.gap(3);
		rows.control(routeKey(false, destination, reason, owned), () -> routeButtons(destination, reason, owned, false));
		if (reason != null)
		{
			rows.gap(3);
			rows.text(reason, TEXT_COLOR, TEXT_WIDTH, null);
		}
		rows.gap(8);
	}

	/** Everything the route buttons show, so a change rebuilds them. */
	private static String routeKey(boolean oneRow, Destination destination, String reason, boolean owned)
	{
		return "route " + oneRow + " " + owned + " " + (reason != null ? reason : destination.routeDescription());
	}

	/**
	 * Path to task and Clear path. The detailed sidebar gives each its own row, so each label has the whole width and is
	 * never cut short. The compact one puts them side by side.
	 */
	private JPanel routeButtons(Destination destination, String reason, boolean owned, boolean oneRow)
	{
		final JButton path = button("Path to task", reason == null,
				reason == null ? "Draw the route to " + destination.routeDescription() + " with Shortest Path" : reason);
		path.addActionListener(e -> plugin.requestRouteFromSidebar());
		final JPanel row = new JPanel(oneRow ? new GridLayout(1, 2, 4, 0) : new GridLayout(2, 1, 0, 4));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(path);
		row.add(clearButton(owned));
		final Dimension size = oneRow ? new Dimension(COMPACT_WIDTH, 26) : new Dimension(TEXT_WIDTH, 2 * 26 + 4);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	static String noRouteReason(Destination destination)
	{
		return noRouteReason(destination, null);
	}

	static String noRouteReason(Destination destination, TaskPlaces places)
	{
		if (destination == null && places != null && places.isWilderness())
		{
			return "No verified place in the Wilderness for this task yet.";
		}
		return destination == null ? "No verified destination for this task yet." : destination.noRouteReason();
	}

	/** Clears the route Slayer Sensei drew, to the task or to a master. */
	private JButton clearButton(boolean owned)
	{
		final JButton clear = button("Clear path", owned,
				owned ? "Clear the route Slayer Sensei drew" : "Clears only a route Slayer Sensei drew");
		clear.addActionListener(e -> plugin.clearRouteFromSidebar());
		return clear;
	}

	private static JButton button(String text, boolean enabled, String tooltip)
	{
		final JButton button = new JButton(text);
		button.setEnabled(enabled);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		return button;
	}

	// ---- compact ----

	/** Headings in capitals with one short item per line. The sentences behind them are in the tooltips. */
	private void compact(Rows rows)
	{
		final SlayerTask task = plugin.getCurrentTask();
		if (plugin.getTaskName() == null)
		{
			rows.heading("SLAYER SENSEI", null);
			rows.item("No task", TEXT_COLOR, NO_TASK);
		}
		else if (task == null)
		{
			rows.heading(upper(plugin.getTaskName()), null);
			rows.item("Not in the dossier yet", TEXT_COLOR, NOT_IN_DOSSIER);
		}
		else
		{
			compactTask(rows, task);
		}
		compactPlan(rows, plugin.getPointsPlan());
	}

	private void compactTask(Rows rows, SlayerTask task)
	{
		// The game's name for the task. Some dossier entries carry a longer one, such as hydras with the Alchemical Hydra.
		rows.heading(upper(plugin.getTaskName()), fields("Assigned by", plugin.assignedBy(task), "Notable drops", task.getNotableDrops(),
				"Data", dataLine(task)));
		rows.item(progressItem(plugin.getTaskAmount(), plugin.getTaskInitialAmount()), TEXT_COLOR, null);
		rows.item(streakItem(plugin.getStreakKind(), plugin.getTaskStreak(), plugin.getSlayerPoints()), TEXT_COLOR, null);

		rows.heading("ADVICE", null);
		if (config.showUnlockAdvisor())
		{
			final String advice = plugin.getUnlockAdvice();
			for (String item : plugin.getUnlockAdviceItems())
			{
				rows.item(item, TEXT_COLOR, advice);
			}
		}
		rows.item(SlayerTaskText.compactVerdict(task, config.goal(), plugin.getTaskMaster()), TEXT_COLOR,
				SlayerTaskText.verdict(task, config.goal(), plugin.getTaskMaster()));

		// What to sort out before leaving, so it comes before the way there. Read once, since the client thread replaces
		// it every tick. A task that needs and recommends nothing has no section.
		final SlayerGearCheck.Result gear = plugin.getGearResult();
		final String required = task.getRequiredItems();
		final boolean gearShown = gear != null;
		if (gearShown)
		{
			rows.heading("GEAR", lines(fields("Required items", required), gear.isBankSeen() ? null : BANK_HINT));
			for (String item : gear.getMissing())
			{
				rows.item(item, gearColor(SlayerGearCheck.Status.MISSING), gear.getLine());
			}
			for (String item : gear.getBring())
			{
				rows.item(item, gearColor(SlayerGearCheck.Status.BRING), gear.getLine());
			}
			rows.item(gear.getReadyItem(), gearColor(SlayerGearCheck.Status.READY), gear.getLine());
		}

		final Destination destination = plugin.getDestination();
		final TaskPlaces places = plugin.getPlaces();
		compactWhere(rows, task, destination, places);

		final String cannon = cannonLine(destination, places, task);
		final String combat = combatLine(destination, places, task);
		final String prayer = SlayerTaskText.prayerItem(task.getProtectionPrayer());
		// Without a gear section the dossier's required items go with the fight's details
		rows.heading("FIGHT", fields("Style", task.getStyle(), "Kill style", task.getKillStyle(), "Weakness", task.getWeakness(),
				"Elemental weakness", elementalLine(task), "Enemy attacks", task.getMonsterAttackStyle(), "Prayer", task.getProtectionPrayer(),
				"Combat area", combat, "Cannon", cannon, "Superior", superiorText(task), "Required items", gearShown ? null : required));
		rows.item(prayer, TEXT_COLOR, task.getProtectionPrayer());
		rows.item(SlayerTaskText.styleItem(task.getStyle(), task.getKillStyle()), TEXT_COLOR, task.getKillStyle());
		rows.item(SlayerTaskText.weaknessItem(task.getWeakness()), TEXT_COLOR, task.getWeakness());
		rows.item(SlayerTaskText.elementalItem(task.getElementalWeakness()), TEXT_COLOR, task.getElementalWeakness());
		// The prayer already says how it attacks
		if (prayer == null) rows.item(SlayerTaskText.attacksItem(task.getMonsterAttackStyle()), TEXT_COLOR, task.getMonsterAttackStyle());
		rows.item(SlayerTaskText.cannonItem(cannon, combat), TEXT_COLOR, fields("Cannon", cannon, "Combat area", combat));
		if (task.isHasSuperior()) rows.item(SlayerTaskText.SUPERIOR_ITEM, TEXT_COLOR, superiorText(task));

		final String rate = plugin.getProgressRateItem();
		if (rate != null)
		{
			rows.heading("RATE", null);
			rows.item(rate, TEXT_COLOR, plugin.getProgressRate());
		}
	}

	/** Konar's area, the place picker or the place, then the route buttons. Notes and the map marker are in the tooltip. */
	private void compactWhere(Rows rows, SlayerTask task, Destination destination, TaskPlaces places)
	{
		if (destination != null && destination.getOrigin() == Destination.Origin.ASSIGNED)
		{
			rows.heading("WHERE", fields("Area note", destination.getNote(), "World map",
					destination.hasEntrance() ? "Marker: " + destination.markerLabel() : NO_MAP_POINT));
			rows.item(assignedItem(destination), TEXT_COLOR, assignedText(destination));
		}
		else
		{
			final TaskPlaces.Show show = TaskPlaces.show(places);
			final boolean wilderness = places != null && places.isWilderness();
			rows.heading("WHERE", fields("Area note", show != TaskPlaces.Show.DOSSIER && destination != null ? destination.getNote() : null,
					"World map", destination != null && destination.hasEntrance() ? "Marker: " + destination.markerLabel() : null,
					"Access", show != TaskPlaces.Show.DOSSIER ? places.accessLine() : null));
			if (show == TaskPlaces.Show.PICKER)
			{
				rows.gap(3);
				rows.control(placeKey(places, COMPACT_WIDTH), () -> placeBox(places, COMPACT_WIDTH));
				rows.gap(2);
				rows.item(places.compactSummary(), TEXT_COLOR, places.summary());
			}
			else if (show == TaskPlaces.Show.ONE_PLACE)
			{
				rows.item(places.getPlaces().get(0).getLabel(), TEXT_COLOR, null);
			}
			if (show != TaskPlaces.Show.DOSSIER && places.chosen() != null)
			{
				// What the place in use needs. A note alone stays in the heading's tooltip.
				rows.item(places.chosen().getAccess().needs(), MISSING_COLOR, places.accessLine());
			}
			else
			{
				rows.item(dossierPlaceItem(wilderness, destination, task.getBestLocation()), TEXT_COLOR,
						whereToGoText(wilderness, task.getBestLocation()));
			}
			if (wilderness && show != TaskPlaces.Show.DOSSIER) rows.item(WILDERNESS_ITEM, TEXT_COLOR, KRYSTILIA_NOTE);
		}
		compactRoute(rows, destination, places);
	}

	/** Path to task and Clear path side by side, why there is no route, or the notice when Shortest Path cannot draw. */
	private void compactRoute(Rows rows, Destination destination, TaskPlaces places)
	{
		if (!config.pathMode().showsButtons()) return;
		final PluginNotices.Notice notice = PluginNotices.shortestPath(plugin.getShortestPathStatus(), config.pathMode(),
				plugin.isPathTurnOnFailed());
		if (notice != null)
		{
			compactNotice(rows, notice, plugin::turnOnShortestPath);
			return;
		}
		final String reason = noRouteReason(destination, places);
		final boolean owned = plugin.ownsRoute();
		rows.gap(4);
		rows.control(routeKey(true, destination, reason, owned), () -> routeButtons(destination, reason, owned, true));
		if (reason != null) rows.item(NO_ROUTE_ITEM, TEXT_COLOR, reason);
	}

	private void compactPlan(Rows rows, SlayerPlanBuilder.View view)
	{
		if (!config.showPointsPlanner() || view == null) return;
		final SlayerPointsPlanner.Plan plan = view.getPlan();
		final SlayerGoal goal = view.getGoal();
		final String title = "Points plan (" + view.getMasterName() + ")";
		rows.gap(SECTION_GAP);
		rows.control("plan " + goal + " " + title, () -> planRow(goal, title));

		final SlayerPointsRoutine.Advice routine = view.getRoutine();
		if (routine != null)
		{
			rows.heading(routine.getHeading(), lines(routine.getCurrent(), routine.getNext(), routine.getCaveat(), routine.getMilestone(),
					routine.getWarning()));
			for (String item : routine.getItems())
			{
				rows.item(item, TEXT_COLOR, null);
			}
			addMasterRoute(rows, view, true);
		}
		final List<String> masters = SlayerPlanText.masterItems(view);
		if (!masters.isEmpty())
		{
			rows.heading(SlayerPlanText.BEST_MASTER_HEADING, fields(SlayerPlanText.masterHeading(goal), SlayerPlanText.masterAdvice(view)));
			for (String item : masters)
			{
				rows.item(item, TEXT_COLOR, null);
			}
			addMasterRoute(rows, view, true);
		}

		rows.heading("POINTS", null);
		rows.item(SlayerPlanText.pointsItem(plan), TEXT_COLOR, lines(SlayerPlanText.points(plan, view.getMaster().assigner(), view.getMasterName(), goal),
				fields("Shop progress", shopProgress(plan)), plan.getCosmeticLeft() > 0 ? cosmetic(plan) : null));
		compactLines(rows, "BUY", null, plan.getBuyNow(), GREEN);
		if (plan.getBuyNow().isEmpty() && !plan.getSaveFor().isEmpty())
		{
			rows.heading("BUY", null);
			rows.item("Nothing affordable yet", TEXT_COLOR, NOTHING_AFFORDABLE);
		}
		compactLines(rows, "SAVE FOR", null, plan.getSaveFor(), TEXT_COLOR);
		compactLines(rows, "LATER", "Unlocks with levels", plan.getLater(), TEXT_COLOR);
		compactLines(rows, "SKIP", SlayerPlanText.skipHeading(goal), plan.getAvoid(), SKIP_COLOR);

		compactBlocks(rows, view.getMasterName(), view.getBlocked(), view.getBlockAdvice(), lines(blockList(view.getBlocked(), view.getEmptySlots(),
				view.getLockedSlots(), view.getBlockedWeightPercent()), SlayerPlanText.blockAdvice(view)));
		final SlayerPlanBuilder.Fillers fillers = view.getFillers();
		if (fillers != null)
		{
			compactBlocks(rows, fillers.getNpc(), fillers.getBlocked(), fillers.getBlockAdvice(), lines(blockList(fillers.getBlocked(),
					fillers.getEmptySlots(), fillers.getLockedSlots(), fillers.getBlockedWeightPercent()), SlayerPlanText.fillerBlockAdvice(view)));
		}

		if (!view.getNoteItems().isEmpty() || !view.isFromCache())
		{
			rows.heading("NOTES", null);
			for (int i = 0; i < view.getNoteItems().size(); i++)
			{
				rows.item(view.getNoteItems().get(i), TEXT_COLOR, view.getNotes().get(i));
			}
			if (!view.isFromCache()) rows.item("Slayer tables not read", TEXT_COLOR, NO_CACHE);
		}
	}

	/** "PLAN" with the goal box beside it. */
	private JPanel planRow(SlayerGoal goal, String tooltip)
	{
		final JLabel label = new JLabel("PLAN");
		label.setForeground(HEADING_COLOR);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setToolTipText(tooltip);
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label, BorderLayout.WEST);
		row.add(goalBox(goal, COMPACT_WIDTH), BorderLayout.CENTER);
		final Dimension size = new Dimension(COMPACT_WIDTH, 26);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	/** A heading over plan lines, each with its reason in the tooltip. */
	private static void compactLines(Rows rows, String heading, String tooltip, List<SlayerPointsPlanner.Line> lines, Color color)
	{
		if (lines.isEmpty()) return;
		rows.heading(heading, tooltip);
		for (SlayerPointsPlanner.Line line : lines)
		{
			rows.item(line.getLabel(), color, line.getDetail());
		}
	}

	/**
	 * A master's block list in one section: the blocks to keep, the ones to add and the ones to lift. The block advice is
	 * in the heading's tooltip and each item's reason or cost in its own.
	 */
	private static void compactBlocks(Rows rows, String npc, List<String> blocked, SlayerBlockAdvisor.Advice advice, String tooltip)
	{
		rows.heading(SlayerPlanText.blockListHeading(npc), tooltip);
		for (SlayerPlanText.BlockItem item : SlayerPlanText.blockListItems(npc, blocked, advice))
		{
			rows.item(item.getText(), blockColor(item.getKind()), item.getTooltip());
		}
	}

	/** Green for a block to keep, the skips' red for one to lift, grey for the rest. */
	static Color blockColor(SlayerPlanText.BlockItem.Kind kind)
	{
		switch (kind)
		{
			case SET:
				return GREEN;
			case UNBLOCK:
				return SKIP_COLOR;
			default:
				return TEXT_COLOR;
		}
	}

	/** "112 of 150 left", or "112 left" without the amount assigned. Null without the kills left. */
	static String progressItem(String left, String assigned)
	{
		if (blank(left)) return null;
		return blank(assigned) ? left + " left" : left + " of " + assigned + " left";
	}

	/** "Streak 47, 521 pts", the streak named for Krystilia and Mortimer as {@link #streakHeading} names it. */
	static String streakItem(SlayerAssigner.Streak streak, String count, String points)
	{
		final String name = streak == SlayerAssigner.Streak.WILDERNESS ? "Wilderness streak "
				: streak == SlayerAssigner.Streak.MORTIMER ? "Mortimer streak " : "Streak ";
		return name + value(count) + ", " + value(points) + " pts";
	}

	/** "Konar: Catacombs of Kourend" */
	static String assignedItem(Destination destination)
	{
		return "Konar: " + destination.getLabel();
	}

	private static String assignedText(Destination destination)
	{
		return "Konar assigned: " + destination.getLabel() + ". Kills elsewhere do not count.";
	}

	/** The compact place without a place list: Krystilia's rule, the dossier's place, or the name its text starts with. */
	static String dossierPlaceItem(boolean wilderness, Destination destination, String dossierText)
	{
		if (wilderness) return WILDERNESS_ITEM;
		return destination != null ? destination.getLabel() : SlayerTaskText.placeItem(dossierText);
	}

	// ---- notices about Shortest Path, the optional plugin that draws the routes ----

	/** A notice in the compact sidebar: a short item with the sentence on it, and its button. */
	private void compactNotice(Rows rows, PluginNotices.Notice notice, Runnable turnOn)
	{
		rows.item(notice.getItem(), notice.isFailed() ? MISSING_COLOR : TEXT_COLOR, notice.getText());
		if (notice.getButton() == null) return;
		rows.gap(4);
		rows.control(noticeKey(notice, COMPACT_WIDTH), () -> noticeButton(notice, turnOn, COMPACT_WIDTH));
	}

	/** A notice in the detailed sidebar: the label, the sentence and the button. */
	private void detailedNotice(Rows rows, String label, PluginNotices.Notice notice, Runnable turnOn)
	{
		rows.label(label, HEADING_COLOR, null);
		rows.text(notice.getText(), notice.isFailed() ? MISSING_COLOR : TEXT_COLOR, TEXT_WIDTH, null);
		if (notice.getButton() != null)
		{
			rows.gap(3);
			rows.control(noticeKey(notice, TEXT_WIDTH), () -> noticeButton(notice, turnOn, TEXT_WIDTH));
		}
		rows.gap(8);
	}

	/** Everything the button shows, so a change rebuilds it. */
	private static String noticeKey(PluginNotices.Notice notice, int width)
	{
		return "notice " + width + " " + notice.getButton() + " " + notice.isFailed();
	}

	/**
	 * The button that turns the plugin on. Only a press switches a plugin on, and the press arrives on the Swing thread,
	 * where RuneLite's plugin list starts plugins too.
	 */
	private static JPanel noticeButton(PluginNotices.Notice notice, Runnable turnOn, int width)
	{
		final JButton button = button(notice.getButton(), true, notice.getText());
		button.addActionListener(e -> turnOn.run());
		final JPanel row = new JPanel(new GridLayout(1, 1));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(button);
		final Dimension size = new Dimension(width, 26);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	// ---- shared text ----

	private static String cannonLine(Destination destination, TaskPlaces places, SlayerTask task)
	{
		return TaskPlaces.factLine(destination, places, destination == null ? null : destination.getCannon(), task.getCannon());
	}

	private static String combatLine(Destination destination, TaskPlaces places, SlayerTask task)
	{
		return TaskPlaces.factLine(destination, places, destination == null ? null : destination.getCombat(), task.getMultiOrSingle());
	}

	/**
	 * The elemental weakness, or {@link #NO_ELEMENTAL} when the monsters have none. Null when it is not known, so a task
	 * the dossier does not cover, whose weakness says so, has no line.
	 */
	static String elementalLine(SlayerTask task)
	{
		final String elemental = task.getElementalWeakness();
		if (elemental == null) return null;
		return elemental.trim().isEmpty() ? NO_ELEMENTAL : elemental.trim();
	}

	private static String superiorText(SlayerTask task)
	{
		return task.isHasSuperior() ? "Possible with Bigger and Badder" : "No superior for this task";
	}

	private String dataLine(SlayerTask task)
	{
		return plugin.dataLabel(task);
	}

	private static String shopProgress(SlayerPointsPlanner.Plan plan)
	{
		return plan.getOwned() + " of " + plan.getTotal() + " rewards bought";
	}

	private static String cosmetic(SlayerPointsPlanner.Plan plan)
	{
		return plan.getCosmeticLeft() + " slayer helm recolours left. Buy these last.";
	}

	/** "Label: value" lines from label and value pairs, leaving out the empty values. Null when all are empty. */
	private static String fields(String... pairs)
	{
		final List<String> lines = new ArrayList<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			if (!blank(pairs[i + 1])) lines.add(pairs[i] + ": " + pairs[i + 1].trim());
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	/** The texts that are not empty, one per line. Null when all are empty. */
	private static String lines(String... texts)
	{
		final List<String> lines = new ArrayList<>();
		for (String text : texts)
		{
			if (!blank(text)) lines.add(text.trim());
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	/** Wrapping text that never scrolls the sidebar. A text area's caret scrolls to the text whenever it is set. */
	private static final class SidebarText extends JTextArea
	{
		SidebarText(String text)
		{
			super(text);
		}

		@Override
		public void scrollRectToVisible(Rectangle rect)
		{
			// The sidebar stays where the player left it
		}
	}

	private static JTextArea wrappingText(String value, Color color, Font font, int width)
	{
		final JTextArea text = new SidebarText(value);
		text.setEditable(false);
		text.setFocusable(false);
		text.setOpaque(false);
		text.setBorder(null);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(font);
		text.setForeground(color);
		fit(text, width);
		return text;
	}

	/** Sizes wrapping text to its lines at {@code width}. Whether its height changed. */
	private static boolean fit(JTextArea text, int width)
	{
		final int before = text.isPreferredSizeSet() ? text.getPreferredSize().height : -1;
		text.setMinimumSize(null);
		text.setPreferredSize(null);
		text.setMaximumSize(null);
		text.setSize(new Dimension(width, Short.MAX_VALUE));
		final Dimension wrapped = new Dimension(width, text.getPreferredSize().height);
		text.setMinimumSize(wrapped);
		text.setPreferredSize(wrapped);
		text.setMaximumSize(wrapped);
		text.setSize(wrapped);
		return wrapped.height != before;
	}

	private String progress()
	{
		return value(plugin.getTaskAmount()) + " left / " + value(plugin.getTaskInitialAmount()) + " assigned";
	}

	private static String value(String value)
	{
		return value == null || value.isEmpty() ? "?" : value;
	}

	private static boolean blank(String text)
	{
		return text == null || text.trim().isEmpty();
	}

	private static String upper(String text)
	{
		return text.toUpperCase(Locale.ROOT);
	}
}
