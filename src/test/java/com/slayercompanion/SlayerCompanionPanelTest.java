package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The sidebar in its scroll pane at the real sidebar size, without a window. A kill must change text in place, and
 * neither a kill nor a rebuild may move the scroll position. Every Swing call runs on the event thread, and each refresh
 * is laid out at once and then the event queue is drained, which is when a text's caret scrolls the pane.
 */
public class SlayerCompanionPanelTest
{
	static
	{
		// Nothing here needs a screen. This takes effect when no other test has started AWT yet.
		System.setProperty("java.awt.headless", "true");
	}

	private static final Gson GSON = new Gson();
	private static final SlayerDataset DATASET = new SlayerDataset(GSON);
	private static final SlayerLocations LOCATIONS = new SlayerLocations(GSON);
	/** RuneLite's sidebar: the panel's 225 pixels and room for the scroll bar. */
	private static final int SIDEBAR_WIDTH = 242;
	private static final int SIDEBAR_HEIGHT = 400;
	private static LookAndFeel before;

	@BeforeClass
	public static void runeLiteLook() throws Throwable
	{
		before = UIManager.getLookAndFeel();
		onEdt(RuneLiteLAF::setup);
	}

	@AfterClass
	public static void restoreLook() throws Throwable
	{
		onEdt(() -> UIManager.setLookAndFeel(before));
	}

	@Test
	public void aKillChangesTextInPlaceAndTheSidebarStaysPut() throws Throwable
	{
		for (SidebarDetail detail : SidebarDetail.values())
		{
			final Sidebar sidebar = Sidebar.konarTask(detail);
			final int middle = sidebar.scrollToMiddle();
			final List<Component> rows = sidebar.rows();
			sidebar.kill(111, 39);
			sidebar.refresh();
			assertEquals(detail + ": the sidebar stays where the player scrolled it", middle, sidebar.scrolled());
			assertEquals(detail + ": a kill builds nothing new", rows, sidebar.rows());
			assertTrue(sidebar.texts().toString(), sidebar.texts().contains(detail == SidebarDetail.COMPACT ? "- 111 of 150 left" : "111 left / 150 assigned"));
		}
	}

	@Test
	public void aKillUpdatesTheGearCountsInPlace() throws Throwable
	{
		final Sidebar sidebar = Sidebar.lizards();
		final int middle = sidebar.scrollToMiddle();
		final List<Component> rows = sidebar.rows();
		assertTrue(sidebar.texts().toString(), sidebar.texts().contains("- Missing: 14 ice coolers (26 of 40)"));
		sidebar.kill(39, 1);
		sidebar.refresh();
		assertTrue(sidebar.texts().toString(), sidebar.texts().contains("- Missing: 13 ice coolers (26 of 39)"));
		assertEquals(rows, sidebar.rows());
		assertEquals(middle, sidebar.scrolled());
	}

	@Test
	public void aRebuildKeepsTheScrollPosition() throws Throwable
	{
		for (SidebarDetail detail : SidebarDetail.values())
		{
			final Sidebar sidebar = Sidebar.konarTask(detail);
			final int middle = sidebar.scrollToMiddle();
			final Component first = sidebar.rows().get(0);
			// The advisor has no line now, so the rows change shape and the sidebar is built again
			sidebar.set("unlockAdvice", null);
			sidebar.set("unlockAdviceItems", Collections.emptyList());
			sidebar.refresh();
			assertNotSame(detail + ": rebuilt", first, sidebar.rows().get(0));
			assertEquals(detail + ": the sidebar stays where the player scrolled it", middle, sidebar.scrolled());
		}
	}

	@Test
	public void switchingTheDetailRebuildsWithoutMovingTheSidebar() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		final int middle = sidebar.scrollToMiddle();
		assertTrue(sidebar.texts().contains("ADVICE"));
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.refresh();
		assertTrue(sidebar.texts().contains("Verdict"));
		assertFalse(sidebar.texts().contains("ADVICE"));
		// The detailed sidebar is taller, so the same position is still there
		assertEquals(middle, sidebar.scrolled());
	}

	@Test
	public void noTextScrollsTheSidebarByItself() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		final int middle = sidebar.scrollToMiddle();
		onEdt(() ->
		{
			for (JTextComponent text : sidebar.textComponents())
			{
				text.setCaretPosition(text.getDocument().getLength());
				text.scrollRectToVisible(new Rectangle(0, 0, 10, 10));
			}
		});
		sidebar.settle();
		assertEquals(middle, sidebar.scrolled());
	}

	@Test
	public void compactItemsAreShortAndTheSentencesAreInTooltips() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		for (String text : sidebar.texts())
		{
			assertTrue(text, text.length() <= 40);
		}
		// The advisor's sentence is on its item
		final List<String> tips = new ArrayList<>();
		onEdt(() ->
		{
			for (JTextComponent text : sidebar.textComponents())
			{
				if (text.getText().equals("- Skip (30)")) tips.add(text.getToolTipText());
			}
		});
		assertEquals(Collections.singletonList("<html><body width='280'>Skip it for 30 points. The wiki's Slayer guide says to skip or block it for XP."
				+ " You have 521 points.</body></html>"), tips);
	}

	@Test
	public void gearComesBetweenAdviceAndWhereInTheColourOfItsState() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		// Abyssal demons without a demonbane weapon, and Malevolent Masquerade bought
		sidebar.gear(SlayerGearCheck.evaluate("Abyssal demons", 112, 1, Collections.<SlayerGearCheck.Held>emptyList(),
				varbit -> varbit == VarbitID.SLAYER_HELM_UNLOCKED ? 1 : 0));
		final List<String> texts = sidebar.texts();
		final int gear = texts.indexOf("GEAR");
		assertTrue(texts.toString(), texts.indexOf("ADVICE") < gear && gear < texts.indexOf("WHERE"));
		assertEquals(Arrays.asList("- Bring: demonbane weapon", "- Bring: Slayer helmet or black mask"), texts.subList(gear + 1, gear + 3));
		// Every heading is RuneLite's orange. The items carry the state.
		assertEquals(ColorScheme.BRAND_ORANGE, sidebar.color("GEAR"));
		assertEquals(Color.YELLOW, sidebar.color("- Bring: demonbane weapon"));
		assertEquals(SlayerCompanionPanel.tooltip("Bring a demonbane weapon, and a Slayer helmet or black mask"),
				sidebar.tooltip("- Bring: demonbane weapon"));

		// A light source missing is red, and with the gear there one green item
		sidebar.gear(SlayerGearCheck.evaluate("Cave bugs", 20, 0, Collections.<SlayerGearCheck.Held>emptyList(), varbit -> 0));
		assertEquals(ColorScheme.BRAND_ORANGE, sidebar.color("GEAR"));
		assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("- Missing: lit light source"));
		sidebar.gear(SlayerGearCheck.evaluate("Abyssal demons", 112, 1, Collections.singletonList(new SlayerGearCheck.Held(ItemID.ARCLIGHT,
				"Arclight", 1)), varbit -> 0));
		assertTrue(sidebar.texts().contains("- Ready: Arclight"));
		assertEquals(SlayerCompanionPanel.gearColor(SlayerGearCheck.Status.READY), sidebar.color("- Ready: Arclight"));
	}

	/** What the place needs shows as every need does: red items with the line as their tooltip, and the red line when detailed. */
	@Test
	public void whatThePlaceNeedsShowsLikeEveryNeed() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		final SlayerGearCheck.Result brimhaven = SlayerGearCheck.evaluate("Fire giants", 50, SlayerRequirements.BRIMHAVEN_AREA,
				SlayerRequirements.BRIMHAVEN_DUNGEON, Collections.<SlayerGearCheck.Held>emptyList(), null, varbit -> 0, true);
		sidebar.gear(brimhaven);
		final List<String> texts = sidebar.texts();
		final int gear = texts.indexOf("GEAR");
		assertEquals(Arrays.asList("- Missing: axe", "- Missing: 875 coins"), texts.subList(gear + 1, gear + 3));
		assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("- Missing: axe"));
		assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("- Missing: 875 coins"));
		assertEquals(SlayerCompanionPanel.tooltip(brimhaven.getLine()), sidebar.tooltip("- Missing: 875 coins"));
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.refresh();
		assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color(brimhaven.getLine()));
	}

	@Test
	public void aTaskThatNeedsAndRecommendsNothingHasNoGearSection() throws Throwable
	{
		// The dossier says nothing is required for abyssal demons, and that is not printed as an item
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		assertFalse(sidebar.texts().contains("GEAR"));
		assertFalse(sidebar.texts().contains("- Nothing required"));
		// The detailed sidebar keeps the line, with the colour of its state
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.gear(SlayerGearCheck.evaluate("Abyssal demons", 112, 1, Collections.<SlayerGearCheck.Held>emptyList(), varbit -> 0));
		assertTrue(sidebar.texts().contains("Bring a demonbane weapon"));
		assertEquals(Color.YELLOW, sidebar.color("Bring a demonbane weapon"));
	}

	@Test
	public void theGearTooltipSaysToOpenTheBankUntilItHasBeenSeen() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		sidebar.gear(SlayerGearCheck.evaluate("Gargoyles", 112, 0, Collections.<SlayerGearCheck.Held>emptyList(), varbit -> 0));
		assertTrue(sidebar.tooltip("GEAR"), sidebar.tooltip("GEAR").contains(SlayerCompanionPanel.BANK_HINT));
		assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("- Missing: rock or granite hammer"));
		// Once the bank has been seen the hint goes, and the hammer it holds is to be brought, in yellow
		sidebar.gear(SlayerGearCheck.evaluate("Gargoyles", 112, 0, Collections.<SlayerGearCheck.Held>emptyList(),
				BankGear.of(new Item[]{new Item(ItemID.SLAYER_ROCK_HAMMER, 1)}), varbit -> 0, true));
		final String tooltip = sidebar.tooltip("GEAR");
		assertFalse(tooltip, tooltip != null && tooltip.contains(SlayerCompanionPanel.BANK_HINT));
		assertEquals(Color.YELLOW, sidebar.color("- In bank: Rock hammer"));
		assertEquals(SlayerCompanionPanel.tooltip("Bring from your bank: Rock hammer"), sidebar.tooltip("- In bank: Rock hammer"));
		// The detailed line is yellow too
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.refresh();
		assertEquals(Color.YELLOW, sidebar.color("Bring from your bank: Rock hammer"));
	}

	@Test
	public void eachMastersBlocksAreOneSection() throws Throwable
	{
		final List<String> texts = Sidebar.konarTask(SidebarDetail.COMPACT).texts();
		int lists = 0;
		for (String text : texts)
		{
			assertFalse(text, text.startsWith("ADD AT ") || text.startsWith("UNBLOCK AT "));
			if (text.endsWith(" BLOCKLIST")) lists++;
		}
		assertTrue(texts.toString(), lists >= 1);
	}

	// ---- Shortest Path, the optional plugin ----

	@Test
	public void shortestPathOffOrMissingIsCalledOptionalWhereTheRouteButtonsGo() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		sidebar.set("shortestPathStatus", ShortestPathBridge.Status.DISABLED);
		sidebar.refresh();
		List<String> texts = sidebar.texts();
		// Under the place, with a button instead of Path to task
		assertEquals("- Optional, draws the route", texts.get(texts.indexOf("- Konar: Catacombs of Kourend") + 1));
		assertTrue(sidebar.buttons().contains("Turn on Shortest Path"));
		assertFalse(sidebar.buttons().contains("Path to task"));
		assertEquals(TEXT_COLOR, sidebar.color("- Optional, draws the route"));

		// Not installed: one line, no install button
		sidebar.set("shortestPathStatus", ShortestPathBridge.Status.MISSING);
		sidebar.refresh();
		texts = sidebar.texts();
		assertTrue(texts.contains("- Optional: Shortest Path, Plugin Hub"));
		assertFalse(sidebar.buttons().contains("Turn on Shortest Path"));
		// It fits on one line, as every compact item should
		assertEquals(sidebar.height("- 112 of 150 left"), sidebar.height("- Optional: Shortest Path, Plugin Hub"));

		// With routes off nothing is said about Shortest Path
		sidebar.pathMode = PathMode.OFF;
		sidebar.refresh();
		for (String text : sidebar.texts()) assertFalse(text, text.contains("Shortest Path") || text.contains("Optional"));
		assertFalse(sidebar.buttons().contains("Turn on Shortest Path"));

		// The detailed sidebar puts the sentence under Route, with the button
		sidebar.pathMode = PathMode.NEW_TASK;
		sidebar.set("shortestPathStatus", ShortestPathBridge.Status.DISABLED);
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.refresh();
		texts = sidebar.texts();
		final String why = PluginNotices.shortestPath(ShortestPathBridge.Status.DISABLED, PathMode.NEW_TASK, false).getText();
		assertEquals(why, texts.get(texts.indexOf("Route") + 1));
		assertTrue(sidebar.buttons().contains("Turn on Shortest Path"));
	}

	// ---- places the account cannot enter ----

	/** Spectres from Nieve: the Slayer Tower needs Priest in Peril, the other two places need nothing. */
	private static final String PICKER_JSON = "{\"locations\": ["
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\", \"entrance\": [3416, 3535, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsFinished\": [\"PRIEST_IN_PERIL\"], \"source\": \"test\"}},"
			+ "{\"key\": \"stronghold_slayer_dungeon\", \"label\": \"Stronghold Slayer Dungeon\", \"entrance\": [2427, 3424, 0], \"entranceSource\": \"test\"},"
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\"}"
			+ "], \"taskLocations\": [{\"taskId\": 41, \"locations\": [\"slayer_tower\", \"stronghold_slayer_dungeon\", \"catacombs_of_kourend\"],"
			+ " \"recommended\": \"slayer_tower\"}]}";

	@Test
	public void aLockedPlaceSaysWhatItNeedsInThePickerAndUnderWhere() throws Throwable
	{
		final SlayerLocations data = new SlayerLocations(GSON, new java.io.StringReader(PICKER_JSON));
		final PlaceAccess.Account account = PlaceAccess.Account.read(Collections.singletonList("PRIEST_IN_PERIL"),
				quest -> net.runelite.api.QuestState.NOT_STARTED, null);
		final List<TaskPlaces.Place> offered = TaskPlaces.offered(41, null, data, account);
		for (SidebarDetail detail : SidebarDetail.values())
		{
			// The player picked the Slayer Tower before, and it stays picked
			final Sidebar sidebar = Sidebar.spectres(detail, new TaskPlaces(41, offered, "slayer_tower"),
					Destination.resolve(41, null, 0, null, "slayer_tower", data));
			final List<TaskPlaces.Place> listed = sidebar.placeList();
			assertEquals(detail + ": every place is listed", 3, listed.size());
			final int tower = listed.indexOf(listed.stream().filter(p -> p.getKey().equals("slayer_tower")).findFirst().get());
			assertTrue(sidebar.listRow(tower).contains("Needs Priest in Peril"));
			assertEquals(SlayerCompanionPanel.tooltip("Needs Priest in Peril."), sidebar.listTip(tower));
			assertNull("an open place has no tooltip", sidebar.listTip(tower == 0 ? 1 : 0));
			assertTrue(sidebar.boxTip(), sidebar.boxTip().startsWith("Slayer Tower. Needs Priest in Peril. Pick where"));
			final List<String> texts = sidebar.texts();
			if (detail == SidebarDetail.COMPACT)
			{
				assertTrue(texts.toString(), texts.contains("- Needs Priest in Peril"));
				assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("- Needs Priest in Peril"));
				assertEquals(SlayerCompanionPanel.tooltip("Needs Priest in Peril."), sidebar.tooltip("- Needs Priest in Peril"));
				assertTrue(sidebar.tooltip("WHERE"), sidebar.tooltip("WHERE").contains("Access: Needs Priest in Peril."));
			}
			else
			{
				assertEquals(texts.toString(), "Needs Priest in Peril.", texts.get(texts.indexOf("Access") + 1));
				assertEquals(SlayerCompanionPanel.MISSING_COLOR, sidebar.color("Needs Priest in Peril."));
			}
		}

		// The recommended place is open, so nothing is said under WHERE
		final Sidebar open = Sidebar.spectres(SidebarDetail.COMPACT, new TaskPlaces(41, offered, TaskPlaces.chosen(offered, null)),
				Destination.resolve(41, null, 0, null, TaskPlaces.chosen(offered, null), data));
		final List<String> texts = open.texts();
		for (String text : texts.subList(texts.indexOf("WHERE"), texts.indexOf("FIGHT"))) assertFalse(text, text.contains("Needs"));
		assertTrue(open.placeList().get(0).isRecommended());
		assertFalse(open.placeList().get(0).isLocked());
	}

	/** The Catacombs lead, the Slayer Tower needs Priest in Peril, so finishing it changes nothing but the tower's lock. */
	private static final String SECOND_PLACE_JSON = "{\"locations\": ["
			+ "{\"key\": \"catacombs_of_kourend\", \"label\": \"Catacombs of Kourend\", \"entrance\": [1636, 3673, 0], \"entranceSource\": \"test\"},"
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\", \"entrance\": [3416, 3535, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsFinished\": [\"PRIEST_IN_PERIL\"], \"source\": \"test\"}}"
			+ "], \"taskLocations\": [{\"taskId\": 41, \"locations\": [\"catacombs_of_kourend\", \"slayer_tower\"],"
			+ " \"recommended\": \"catacombs_of_kourend\"}]}";

	@Test
	public void aPlaceThatOpensShowsItInTheListWithoutANewTask() throws Throwable
	{
		final SlayerLocations data = new SlayerLocations(GSON, new java.io.StringReader(SECOND_PLACE_JSON));
		final Destination catacombs = Destination.resolve(41, null, 0, null, "catacombs_of_kourend", data);
		final List<TaskPlaces.Place> before = TaskPlaces.offered(41, null, data, PlaceAccess.Account.read(
				Collections.singletonList("PRIEST_IN_PERIL"), quest -> net.runelite.api.QuestState.NOT_STARTED, null));
		final List<TaskPlaces.Place> after = TaskPlaces.offered(41, null, data, PlaceAccess.Account.read(
				Collections.singletonList("PRIEST_IN_PERIL"), quest -> net.runelite.api.QuestState.FINISHED, null));
		final Sidebar sidebar = Sidebar.spectres(SidebarDetail.COMPACT, new TaskPlaces(41, before, "catacombs_of_kourend"), catacombs);
		assertTrue(sidebar.listRow(1).contains("Needs Priest in Peril"));
		// The quest was finished: same places in the same order, the same pick, only the lock is gone
		sidebar.set("places", new TaskPlaces(41, after, "catacombs_of_kourend"));
		sidebar.refresh();
		assertFalse(sidebar.listRow(1), sidebar.listRow(1).contains("Needs"));
	}

	// ---- colours ----

	private static final Color TEXT_COLOR = ColorScheme.TEXT_COLOR;

	/**
	 * Every heading and the task's name in RuneLite's orange. Items are grey unless the colour means something: gear
	 * missing, to bring or ready, purchases to make now, blocks to keep, and skips and blocks to lift.
	 */
	@Test
	public void headingsAreOrangeAndOnlyMeaningfulItemsHaveColour() throws Throwable
	{
		final Sidebar sidebar = Sidebar.konarTask(SidebarDetail.COMPACT);
		sidebar.gear(SlayerGearCheck.evaluate("Abyssal demons", 112, 1, Collections.<SlayerGearCheck.Held>emptyList(),
				varbit -> varbit == VarbitID.SLAYER_HELM_UNLOCKED ? 1 : 0));
		String heading = null;
		int headings = 0;
		int coloured = 0;
		for (JTextComponent row : sidebar.textComponents())
		{
			final String text = row.getText();
			if (row.getFont().isBold())
			{
				heading = text;
				headings++;
				assertEquals(text, ColorScheme.BRAND_ORANGE, row.getForeground());
				continue;
			}
			final Color expected;
			if ("GEAR".equals(heading))
			{
				expected = text.startsWith("- Bring") ? Color.YELLOW : text.startsWith("- Missing") ? SlayerCompanionPanel.MISSING_COLOR
						: SlayerCompanionPanel.GREEN;
			}
			else if ("BUY".equals(heading)) expected = SlayerCompanionPanel.GREEN;
			else if ("SKIP".equals(heading) || text.endsWith(" (unblock)")) expected = SlayerCompanionPanel.SKIP_COLOR;
			else if (text.endsWith(" (set)")) expected = SlayerCompanionPanel.GREEN;
			else expected = TEXT_COLOR;
			assertEquals(heading + ": " + text, expected, row.getForeground());
			if (!TEXT_COLOR.equals(expected)) coloured++;
		}
		assertTrue(headings > 8);
		assertTrue(coloured > 5);
		assertEquals(ColorScheme.BRAND_ORANGE, sidebar.label("PLAN").getForeground());

		// The detailed sidebar: every label orange, the sentences grey unless they carry the same meaning
		sidebar.detail = SidebarDetail.DETAILED;
		sidebar.refresh();
		String label = null;
		for (Component c : sidebar.rows())
		{
			if (c instanceof JLabel)
			{
				label = ((JLabel) c).getText();
				assertEquals(label, ColorScheme.BRAND_ORANGE, c.getForeground());
			}
			else if (c instanceof JTextComponent)
			{
				final Color expected = "Buy now".equals(label) ? SlayerCompanionPanel.GREEN
						: label != null && label.startsWith("Skip these for") ? SlayerCompanionPanel.SKIP_COLOR
						: "Live gear check".equals(label) ? Color.YELLOW : TEXT_COLOR;
				assertEquals(label, expected, c.getForeground());
			}
		}
	}

	// ---- the fight ----

	/** Under FIGHT the prayer, then the style, the weakness and the elemental weakness, each with its sentence as the tooltip. */
	@Test
	public void theFightSectionGivesTheStyleAndTheElementalWeakness() throws Throwable
	{
		final SlayerTask demons = DATASET.lookup("Black demons");
		final Sidebar sidebar = Sidebar.task(SidebarDetail.COMPACT, "Black demons", SlayerAssigner.DURADEL);
		final List<String> texts = sidebar.texts();
		final int fight = texts.indexOf("FIGHT");
		assertEquals(texts.toString(), Arrays.asList("- Pray melee", "- Style: any (demonbane)", "- Weak: demonbane", "- Elemental: water 40%"),
				texts.subList(fight + 1, fight + 5));
		assertEquals(SlayerCompanionPanel.tooltip(demons.getKillStyle()), sidebar.tooltip("- Style: any (demonbane)"));
		assertEquals(SlayerCompanionPanel.tooltip(demons.getWeakness()), sidebar.tooltip("- Weak: demonbane"));
		assertEquals(SlayerCompanionPanel.tooltip("Water 40%"), sidebar.tooltip("- Elemental: water 40%"));
		// The heading's tooltip holds both fields, in the detailed sidebar's order
		final String heading = sidebar.tooltip("FIGHT");
		assertTrue(heading, heading.contains("Style: Any (demonbane)<br>Kill style: "));
		assertTrue(heading, heading.contains("<br>Elemental weakness: Water 40%<br>Enemy attacks: "));

		// Too long for a line: the item says it varies, and the tooltip has the full field
		final SlayerTask trolls = DATASET.lookup("Trolls");
		final Sidebar varied = Sidebar.task(SidebarDetail.COMPACT, "Trolls", SlayerAssigner.VANNAKA);
		assertEquals(SlayerCompanionPanel.tooltip(trolls.getElementalWeakness()), varied.tooltip("- Elemental: varies by type"));
		final List<String> trollTexts = varied.texts();
		assertEquals(trollTexts.indexOf("- Style: melee or ranged") + 1, trollTexts.indexOf("- Elemental: varies by type"));

		// Abyssal demons have none: no item, and the heading's tooltip says None
		final Sidebar none = Sidebar.konarTask(SidebarDetail.COMPACT);
		for (String text : none.texts()) assertFalse(text, text.startsWith("- Elemental"));
		assertTrue(none.texts().contains("- Style: melee or magic (barrage)"));
		assertTrue(none.tooltip("FIGHT"), none.tooltip("FIGHT").contains("<br>Elemental weakness: None<br>"));
	}

	/** The detailed sidebar: Style before Kill style, and Elemental weakness after Weakness, None when there is none. */
	@Test
	public void theDetailedFightLinesGiveTheStyleAndTheElementalWeakness() throws Throwable
	{
		final SlayerTask demons = DATASET.lookup("Black demons");
		List<String> texts = Sidebar.task(SidebarDetail.DETAILED, "Black demons", SlayerAssigner.DURADEL).texts();
		final int style = texts.indexOf("Style");
		assertTrue(texts.toString(), style >= 0);
		assertEquals(texts.toString(), Arrays.asList("Style", "Any (demonbane)", "Kill style", demons.getKillStyle(), "Weakness",
				demons.getWeakness(), "Elemental weakness", "Water 40%", "Enemy attacks"), texts.subList(style, style + 9));

		texts = Sidebar.konarTask(SidebarDetail.DETAILED).texts();
		assertEquals("Melee or magic (barrage)", texts.get(texts.indexOf("Style") + 1));
		assertEquals(SlayerCompanionPanel.NO_ELEMENTAL, texts.get(texts.indexOf("Elemental weakness") + 1));
		assertEquals("None", SlayerCompanionPanel.NO_ELEMENTAL);
		assertEquals(texts.indexOf("Weakness") + 2, texts.indexOf("Elemental weakness"));

		// A task the dossier does not cover yet says its weakness is not known, and no None beside it
		texts = Sidebar.task(SidebarDetail.DETAILED, "A future beast", SlayerAssigner.DURADEL).texts();
		assertTrue(texts.toString(), texts.contains("Not known yet. Do not assume an elemental weakness."));
		assertFalse(texts.toString(), texts.contains("Elemental weakness"));
		assertFalse(texts.toString(), texts.contains("Style"));
	}

	// ---- the panel's own words ----

	@Test
	public void theTaskLinesInAFewWords()
	{
		assertEquals("112 of 150 left", SlayerCompanionPanel.progressItem("112", "150"));
		assertEquals("112 left", SlayerCompanionPanel.progressItem("112", null));
		assertNull(SlayerCompanionPanel.progressItem(null, "150"));
		assertEquals("Streak 47, 521 pts", SlayerCompanionPanel.streakItem(SlayerAssigner.Streak.MAIN, "47", "521"));
		assertEquals("Wilderness streak 7, 521 pts", SlayerCompanionPanel.streakItem(SlayerAssigner.Streak.WILDERNESS, "7", "521"));
		assertEquals("Mortimer streak ?, ? pts", SlayerCompanionPanel.streakItem(SlayerAssigner.Streak.MORTIMER, null, ""));
		final SlayerTask abyssal = DATASET.lookupOrFallback("Abyssal demons");
		assertEquals("Konar: Catacombs of Kourend", SlayerCompanionPanel.assignedItem(Destination.resolve(42, abyssal, 1, null, null, LOCATIONS)));
		// Without a place list: Krystilia's rule, the dossier's place, or the name its text starts with
		assertEquals("Wilderness only", SlayerCompanionPanel.dossierPlaceItem(true, null, "Wilderness Bandit Camp."));
		assertEquals("Catacombs of Kourend", SlayerCompanionPanel.dossierPlaceItem(false, Destination.resolve(0, abyssal, 0, null, null,
				new SlayerLocations(GSON, new java.io.StringReader("{}"))), abyssal.getBestLocation()));
		assertEquals("Giant bats in Taverley Dungeon", SlayerCompanionPanel.dossierPlaceItem(false, null,
				"Giant bats in Taverley Dungeon or near Keep Le Faye."));
	}

	@Test
	public void tooltipsKeepLineBreaksAndWrapLongText()
	{
		assertNull(SlayerCompanionPanel.tooltip(" "));
		assertEquals("<html>Possible with Bigger and Badder</html>", SlayerCompanionPanel.tooltip("Possible with Bigger and Badder"));
		assertEquals("<html>Cannon: Not allowed.<br>Combat area: Multicombat</html>",
				SlayerCompanionPanel.tooltip("Cannon: Not allowed.\nCombat area: Multicombat"));
		assertEquals("<html><body width='280'>Rock hammer &amp; granite hammer &lt;9 HP. A long reason that goes on past sixty"
				+ " characters.</body></html>", SlayerCompanionPanel.tooltip("Rock hammer & granite hammer <9 HP. A long reason that goes on"
				+ " past sixty characters."));
	}

	// ---- the sidebar under test ----

	/** A plugin's state, the panel built from it, and the panel's scroll pane at the sidebar's size. */
	private static final class Sidebar
	{
		final SlayerCompanionPlugin plugin = new SlayerCompanionPlugin();
		SidebarDetail detail;
		PathMode pathMode = PathMode.NEW_TASK;
		SlayerCompanionPanel panel;
		JScrollPane scrollPane;
		String gearTask;

		/** Abyssal demons from Konar in the Catacombs of Kourend, 112 of 150 left, with the XP plan. */
		static Sidebar konarTask(SidebarDetail detail) throws Throwable
		{
			final SlayerPointsPlannerTest.Account account = SlayerPointsPlannerTest.exampleWithKonarBlocks().onTask(SlayerAssigner.KONAR, 47);
			account.varps.put(VarPlayerID.SLAYER_TARGET, 42);
			account.varps.put(VarPlayerID.SLAYER_AREA, 1);
			final SlayerTask task = DATASET.lookupOrFallback("Abyssal demons");
			final Sidebar sidebar = new Sidebar(detail, "Abyssal demons", task, SlayerAssigner.KONAR, account);
			sidebar.set("destination", Destination.resolve(42, task, 1, null, null, LOCATIONS));
			sidebar.set("taskInitialAmount", "150");
			sidebar.kill(112, 38);
			sidebar.show();
			return sidebar;
		}

		/** Aberrant spectres from Nieve, who leaves the place to the player, with these places and this destination. */
		static Sidebar spectres(SidebarDetail detail, TaskPlaces places, Destination destination) throws Throwable
		{
			final SlayerPointsPlannerTest.Account account = SlayerPointsPlannerTest.exampleWithKonarBlocks().onTask(SlayerAssigner.NIEVE, 47);
			account.varps.put(VarPlayerID.SLAYER_TARGET, 41);
			final Sidebar sidebar = new Sidebar(detail, "Aberrant spectres", DATASET.lookupOrFallback("Aberrant spectres"),
					SlayerAssigner.NIEVE, account);
			sidebar.set("places", places);
			sidebar.set("destination", destination);
			sidebar.set("taskInitialAmount", "150");
			sidebar.kill(112, 38);
			sidebar.show();
			return sidebar;
		}

		/** A task from a master, from the dossier or its general advice, 112 of 150 left, with no place picked. */
		static Sidebar task(SidebarDetail detail, String name, SlayerAssigner master) throws Throwable
		{
			final SlayerPointsPlannerTest.Account account = SlayerPointsPlannerTest.exampleWithKonarBlocks().onTask(master, 47);
			final Sidebar sidebar = new Sidebar(detail, name, DATASET.lookupOrFallback(name), master, account);
			sidebar.set("taskInitialAmount", "150");
			sidebar.kill(112, 38);
			sidebar.show();
			return sidebar;
		}

		/** Lizards from Vannaka with 26 ice coolers, 40 left. */
		static Sidebar lizards() throws Throwable
		{
			final SlayerPointsPlannerTest.Account account = SlayerPointsPlannerTest.exampleWithKonarBlocks().onTask(SlayerAssigner.VANNAKA, 47);
			final Sidebar sidebar = new Sidebar(SidebarDetail.COMPACT, "Lizards", DATASET.lookupOrFallback("Lizards"), SlayerAssigner.VANNAKA,
					account);
			sidebar.gearTask = "Lizards";
			sidebar.set("taskInitialAmount", "60");
			sidebar.kill(40, 20);
			sidebar.show();
			return sidebar;
		}

		Sidebar(SidebarDetail detail, String taskName, SlayerTask task, SlayerAssigner master, SlayerPointsPlannerTest.Account account)
				throws Exception
		{
			this.detail = detail;
			final SlayerPlanBuilder.View plan = SlayerPlanBuilder.build(account, SlayerPointsPlannerTest.DATA, DATASET,
					new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, SlayerGoal.XP, BoostInterval.EVERY_10TH, true),
					SlayerMasterAccessTest.maxed());
			set("dataset", DATASET);
			set("taskName", taskName);
			set("currentTask", task);
			set("taskStreak", "47");
			set("slayerPoints", "521");
			set("taskMaster", master);
			set("shortestPathStatus", ShortestPathBridge.Status.ACTIVE);
			set("pointsPlan", plan);
			set("unlockAdvice", plan.getTaskAdvice());
			set("unlockAdviceItems", plan.getTaskAdviceItems());
		}

		/** The kill counter went down: the kills left, the rate and the gear counts change. */
		void kill(int left, int done) throws Exception
		{
			set("taskAmount", String.valueOf(left));
			set("progressRate", SlayerCompanionPlugin.rateText(done, 3600, left, false));
			set("progressRateItem", SlayerCompanionPlugin.rateText(done, 3600, left, true));
			if (gearTask != null)
			{
				final SlayerGearCheck.Result gear = SlayerGearCheck.evaluate(gearTask, left, 0,
						Collections.singletonList(new SlayerGearCheck.Held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 26)), varbit -> 0);
				set("gearCheck", gear.getLine());
				set("gearResult", gear);
			}
		}

		void show() throws Throwable
		{
			onEdt(() ->
			{
				panel = new SlayerCompanionPanel(plugin, config());
				final JPanel wrapped = panel.getWrappedPanel();
				scrollPane = (JScrollPane) wrapped.getComponent(0);
				wrapped.setSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
				layout(wrapped);
			});
			settle();
		}

		SlayerCompanionConfig config()
		{
			return (SlayerCompanionConfig) Proxy.newProxyInstance(SlayerCompanionPanelTest.class.getClassLoader(),
					new Class<?>[]{SlayerCompanionConfig.class}, (proxy, method, args) ->
					{
						switch (method.getName())
						{
							case "sidebarDetail":
								return detail;
							case "goal":
								return SlayerGoal.XP;
							case "pathMode":
								return pathMode;
							default:
								return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
						}
					});
		}

		/** A refresh as the plugin asks for one, laid out at once as a window would be, then the queue drained. */
		void refresh() throws Throwable
		{
			onEdt(() ->
			{
				panel.refresh();
				layout(panel.getWrappedPanel());
			});
			settle();
		}

		/** Runs what the event thread still has queued, such as a caret scrolling to its text, and lays out again. */
		void settle() throws Throwable
		{
			onEdt(() -> { });
			onEdt(() -> layout(panel.getWrappedPanel()));
		}

		int scrollToMiddle() throws Throwable
		{
			final int[] middle = new int[1];
			onEdt(() ->
			{
				final JViewport viewport = scrollPane.getViewport();
				middle[0] = (viewport.getViewSize().height - viewport.getExtentSize().height) / 2;
				viewport.setViewPosition(new Point(0, middle[0]));
			});
			settle();
			assertTrue("the sidebar is taller than its pane", middle[0] > 100);
			assertEquals(middle[0], scrolled());
			return middle[0];
		}

		int scrolled() throws Throwable
		{
			final int[] y = new int[2];
			onEdt(() ->
			{
				y[0] = scrollPane.getViewport().getViewPosition().y;
				y[1] = scrollPane.getVerticalScrollBar().getValue();
			});
			assertEquals("the scroll bar shows the view's position", y[0], y[1]);
			return y[0];
		}

		/** The components of the sidebar's rows, top to bottom. */
		List<Component> rows() throws Throwable
		{
			final List<Component> rows = new ArrayList<>();
			onEdt(() -> Collections.addAll(rows, ((Container) panel.getComponent(0)).getComponents()));
			return rows;
		}

		List<String> texts() throws Throwable
		{
			final List<String> texts = new ArrayList<>();
			onEdt(() ->
			{
				for (Component c : rows())
				{
					if (c instanceof JLabel) texts.add(((JLabel) c).getText());
					if (c instanceof JTextComponent) texts.add(((JTextComponent) c).getText());
				}
			});
			return texts;
		}

		List<JTextComponent> textComponents() throws Throwable
		{
			final List<JTextComponent> texts = new ArrayList<>();
			for (Component c : rows())
			{
				if (c instanceof JTextComponent) texts.add((JTextComponent) c);
			}
			return texts;
		}

		/** The labels of every button in the sidebar, top to bottom. */
		List<String> buttons() throws Throwable
		{
			final List<String> buttons = new ArrayList<>();
			onEdt(() -> collectButtons(panel, buttons));
			return buttons;
		}

		private void collectButtons(Container container, List<String> buttons)
		{
			for (Component c : container.getComponents())
			{
				if (c instanceof JButton) buttons.add(((JButton) c).getText());
				else if (c instanceof Container) collectButtons((Container) c, buttons);
			}
		}

		/** The places the place box lists, in order. */
		List<TaskPlaces.Place> placeList() throws Throwable
		{
			final List<TaskPlaces.Place> places = new ArrayList<>();
			onEdt(() ->
			{
				final javax.swing.JComboBox<?> box = placeBox(panel);
				for (int i = 0; i < box.getItemCount(); i++) places.add((TaskPlaces.Place) box.getItemAt(i));
			});
			return places;
		}

		/** The tooltip of the closed place box. */
		String boxTip() throws Throwable
		{
			final String[] tip = new String[1];
			onEdt(() -> tip[0] = placeBox(panel).getToolTipText());
			return tip[0];
		}

		/** The text of a row of the open place list. */
		String listRow(int index) throws Throwable
		{
			return (String) listCell(index, false);
		}

		/** The tooltip of a row of the open place list. */
		String listTip(int index) throws Throwable
		{
			return (String) listCell(index, true);
		}

		@SuppressWarnings("unchecked")
		private Object listCell(int index, boolean tip) throws Throwable
		{
			final Object[] out = new Object[1];
			onEdt(() ->
			{
				final javax.swing.JComboBox<Object> box = (javax.swing.JComboBox<Object>) placeBox(panel);
				final javax.swing.JList<Object> list = new javax.swing.JList<>();
				final JComponent cell = (JComponent) box.getRenderer().getListCellRendererComponent(list, box.getItemAt(index), index, false, false);
				out[0] = tip ? cell.getToolTipText() : ((JLabel) cell).getText();
			});
			return out[0];
		}

		private javax.swing.JComboBox<?> placeBox(Container container)
		{
			for (Component c : container.getComponents())
			{
				if (c instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) c).getItemCount() > 0
						&& ((javax.swing.JComboBox<?>) c).getItemAt(0) instanceof TaskPlaces.Place)
				{
					return (javax.swing.JComboBox<?>) c;
				}
				if (c instanceof Container)
				{
					final javax.swing.JComboBox<?> box = placeBox((Container) c);
					if (box != null) return box;
				}
			}
			return null;
		}

		/** The height the row with this text takes. */
		int height(String text) throws Throwable
		{
			return row(text).getPreferredSize().height;
		}

		/** A label inside a row, such as PLAN beside the goal box. */
		JLabel label(String text) throws Throwable
		{
			final JLabel[] found = new JLabel[1];
			onEdt(() -> findLabel(panel, text, found));
			assertTrue("no label " + text, found[0] != null);
			return found[0];
		}

		private void findLabel(Container container, String text, JLabel[] found)
		{
			for (Component c : container.getComponents())
			{
				if (c instanceof JLabel && text.equals(((JLabel) c).getText()) && found[0] == null) found[0] = (JLabel) c;
				else if (c instanceof Container) findLabel((Container) c, text, found);
			}
		}

		/** A new gear check, as the plugin stores it each tick, and the refresh that shows it. */
		void gear(SlayerGearCheck.Result gear) throws Throwable
		{
			set("gearCheck", gear == null ? null : gear.getLine());
			set("gearResult", gear);
			refresh();
		}

		/** The colour of the row with this text, or with this label. */
		Color color(String text) throws Throwable
		{
			return row(text).getForeground();
		}

		String tooltip(String text) throws Throwable
		{
			return row(text).getToolTipText();
		}

		private JComponent row(String text) throws Throwable
		{
			final JComponent[] found = new JComponent[1];
			onEdt(() ->
			{
				for (Component c : rows())
				{
					final String shown = c instanceof JLabel ? ((JLabel) c).getText() : c instanceof JTextComponent ? ((JTextComponent) c).getText() : null;
					if (text.equals(shown) && found[0] == null) found[0] = (JComponent) c;
				}
			});
			assertTrue("no row " + text, found[0] != null);
			return found[0];
		}

		void set(String field, Object value) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(plugin, value);
		}
	}

	/** Without a window a layout runs only when asked, so each container is laid out top down. */
	private static void layout(Component c)
	{
		if (c instanceof Container)
		{
			((Container) c).doLayout();
			for (Component child : ((Container) c).getComponents()) layout(child);
		}
	}

	private interface Step
	{
		void run() throws Throwable;
	}

	private static void onEdt(Step step) throws Throwable
	{
		final Throwable[] failed = new Throwable[1];
		if (SwingUtilities.isEventDispatchThread())
		{
			step.run();
			return;
		}
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				step.run();
			}
			catch (Throwable t)
			{
				failed[0] = t;
			}
		});
		if (failed[0] != null) throw failed[0];
	}
}
