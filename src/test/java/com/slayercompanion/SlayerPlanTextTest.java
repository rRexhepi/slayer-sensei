package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerBlockAdvisor.Advice;
import com.slayercompanion.SlayerBlockAdvisor.Misfit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SlayerPlanTextTest
{
	@Test
	public void blockAdviceSaysWhatToAddKeepAndLift()
	{
		final Advice advice = new Advice(SlayerGoal.XP, 80, 7, Collections.singletonList("Drakes"),
				Arrays.asList("Cave Kraken", "Kurask"), Collections.singletonList(new Misfit("Dagannoth", "good for XP")));
		assertEquals("Block next: Cave Kraken, Kurask. 80 points each at Konar.\n"
				+ "Already blocked and right: Drakes.\n"
				+ "Not right for XP: Dagannoth (good for XP). Unblock it to free the slot.",
				SlayerPlanText.blockAdvice(advice, "Konar", null));
		assertEquals("Blocks for XP", SlayerPlanText.blockHeading(SlayerGoal.XP));
		assertEquals("Blocks for Slayer points", SlayerPlanText.blockHeading(SlayerGoal.POINTS));
	}

	@Test
	public void blockAdviceTakesAFirstLineAndCoversEmptyCases()
	{
		final Advice advice = new Advice(SlayerGoal.POINTS, 80, 7, Collections.<String>emptyList(),
				Collections.<String>emptyList(), Collections.<Misfit>emptyList());
		assertEquals("Milestone tasks go to Konar, so block the slow ones there.\nNo other task is worth a block for Slayer points.",
				SlayerPlanText.blockAdvice(advice, "Konar", "Milestone tasks go to Konar, so block the slow ones there."));
		final Advice noSlots = new Advice(SlayerGoal.PROFIT, 100, 0, Collections.<String>emptyList(),
				Collections.<String>emptyList(), Collections.<Misfit>emptyList());
		assertEquals("Block slots open at 50 quest points.", SlayerPlanText.blockAdvice(noSlots, "Kuradal", null));
		assertNull(SlayerPlanText.blockAdvice(null, "Kuradal", null));
		// Two blocks to lift read as them
		final Advice two = new Advice(SlayerGoal.PROFIT, 100, 7, Collections.<String>emptyList(), Collections.<String>emptyList(),
				Arrays.asList(new Misfit("Kurask", "good for profit"), new Misfit("Hydras", "you cannot be given it anyway")));
		assertEquals("No other task is worth a block for profit.\nNot right for profit: Kurask (good for profit),"
				+ " Hydras (you cannot be given it anyway). Unblock them to free the slot.", SlayerPlanText.blockAdvice(two, "Kuradal", null));
	}

	private static SlayerPointsPlanner.Plan plan(int points, SlayerPointsPlanner.Reserve reserve)
	{
		return new SlayerPointsPlanner.Plan(points, reserve, Collections.<SlayerPointsPlanner.Line>emptyList(),
				Collections.<SlayerPointsPlanner.Line>emptyList(), Collections.<SlayerPointsPlanner.Line>emptyList(),
				Collections.<SlayerPointsPlanner.Line>emptyList(), 0, 0, 0);
	}

	@Test
	public void thePointsLineSaysWhatTheReserveIsFor()
	{
		final SlayerPointsPlanner.Reserve skips = new SlayerPointsPlanner.Reserve(60, 2, 30, 0.3, Arrays.asList("Kurask", "Drakes"), 0);
		assertEquals("521 points. 60 are kept to skip up to 2 bad tasks in a row at Kuradal. About 3 in 10 of Kuradal's tasks are worth"
				+ " skipping for profit, such as Kurask and Drakes. 461 to spend.",
				SlayerPlanText.points(plan(521, skips), SlayerAssigner.DURADEL, "Kuradal", SlayerGoal.PROFIT));
		final SlayerPointsPlanner.Reserve one = new SlayerPointsPlanner.Reserve(30, 1, 30, 0.08, Collections.singletonList("Kurask"), 0);
		assertEquals("20 points. 30 are kept to skip a bad task at Konar. About 1 in 10 of Konar's tasks are worth skipping for XP,"
				+ " such as Kurask. 0 to spend.", SlayerPlanText.points(plan(20, one), SlayerAssigner.KONAR, "Konar", SlayerGoal.XP));
		final SlayerPointsPlanner.Reserve floor = new SlayerPointsPlanner.Reserve(200, 2, 30, 0.3, Collections.<String>emptyList(), 200);
		assertEquals("521 points. 200 are kept for skips, as Points to keep for skips says. 321 to spend.",
				SlayerPlanText.points(plan(521, floor), SlayerAssigner.KONAR, "Konar", SlayerGoal.XP));
		assertEquals("521 points. None are kept for skips, since the routine does every task. 521 to spend.",
				SlayerPlanText.points(plan(521, SlayerPointsPlanner.Reserve.NONE), SlayerAssigner.KONAR, "Konar", SlayerGoal.POINTS));
		assertEquals("10 points. None are kept for skips, since Aya pays no points. 10 to spend.",
				SlayerPlanText.points(plan(10, SlayerPointsPlanner.Reserve.NONE), SlayerAssigner.TURAEL, "Aya", SlayerGoal.XP));
		assertEquals("10 points. None are kept for skips, since no task at Konar is worth one. 10 to spend.",
				SlayerPlanText.points(plan(10, SlayerPointsPlanner.Reserve.NONE), SlayerAssigner.KONAR, "Konar", SlayerGoal.XP));
		assertEquals("Skip these for Slayer points", SlayerPlanText.skipHeading(SlayerGoal.POINTS));
	}

	@Test
	public void masterHeadingsFollowTheGoal()
	{
		assertEquals("Best master for XP", SlayerPlanText.masterHeading(SlayerGoal.XP));
		assertEquals("Best master for profit", SlayerPlanText.masterHeading(SlayerGoal.PROFIT));
		assertNull(SlayerPlanText.masterHeading(SlayerGoal.POINTS));
	}

	// ---- the compact sidebar ----

	@Test
	public void theCompactPointsItemIsThePointsAndWhatIsLeftToSpend()
	{
		final SlayerPointsPlanner.Reserve skips = new SlayerPointsPlanner.Reserve(120, 4, 30, 0.5, Arrays.asList("Kurask", "Drakes"), 0);
		assertEquals("521, 401 to spend", SlayerPlanText.pointsItem(plan(521, skips)));
		assertEquals("521, all to spend", SlayerPlanText.pointsItem(plan(521, SlayerPointsPlanner.Reserve.NONE)));
		assertEquals("20, none to spend", SlayerPlanText.pointsItem(plan(20, skips)));
	}

	@Test
	public void eachMastersBlocksAreOneList()
	{
		assertEquals("KONAR BLOCKLIST", SlayerPlanText.blockListHeading("Konar"));
		// The blocks to keep, marked, then the ones to add in the advice's order, then the ones to lift, marked
		final Advice advice = new Advice(SlayerGoal.XP, 80, 7, Collections.singletonList("Drakes"), Arrays.asList("Cave Kraken", "Kurask"),
				Collections.singletonList(new Misfit("Dagannoth", "good for XP")));
		final List<SlayerPlanText.BlockItem> items = SlayerPlanText.blockListItems("Konar", Arrays.asList("Dagannoth", "Drakes"), advice);
		assertEquals(Arrays.asList("Drakes (set)", "Cave Kraken", "Kurask", "Dagannoth (unblock)"), texts(items));
		assertEquals(Arrays.asList(SlayerPlanText.BlockItem.Kind.SET, SlayerPlanText.BlockItem.Kind.ADD, SlayerPlanText.BlockItem.Kind.ADD,
				SlayerPlanText.BlockItem.Kind.UNBLOCK), kinds(items));
		// The reasons and the cost stay in the tooltips
		assertEquals(Arrays.asList("Already blocked and right for XP.", "Block it next for 80 points at Konar.",
				"Block it next for 80 points at Konar.", "Good for XP. Unblock it to free the slot."), tooltips(items));
	}

	@Test
	public void anEmptyBlockListSaysNoneAndWhenTheFirstSlotOpens()
	{
		final Advice nothing = new Advice(SlayerGoal.XP, 80, 7, Collections.<String>emptyList(), Collections.<String>emptyList(),
				Collections.<Misfit>emptyList());
		assertEquals(Collections.singletonList("(none)"), texts(SlayerPlanText.blockListItems("Konar", Collections.<String>emptyList(), nothing)));
		final Advice noSlots = new Advice(SlayerGoal.PROFIT, 100, 0, Collections.<String>emptyList(), Collections.<String>emptyList(),
				Collections.<Misfit>emptyList());
		assertEquals(Arrays.asList("(none)", "Slots open at 50 QP"), texts(SlayerPlanText.blockListItems("Kuradal",
				Collections.<String>emptyList(), noSlots)));
		// Without the cache tables there is no advice, so the blocks are listed as they are
		assertEquals(Arrays.asList("Drakes (set)", "Wyrms (set)"), texts(SlayerPlanText.blockListItems("Konar", Arrays.asList("Drakes", "Wyrms"),
				null)));
		assertEquals(Collections.singletonList("(none)"), texts(SlayerPlanText.blockListItems("Konar", Collections.<String>emptyList(), null)));
	}

	/** The coordinator's example on the Slayer points routine: Konar's list holds drakes and wyrms, Aya's nothing. */
	@Test
	public void theRoutinesListsKeepDrakesAndWyrmsAndAddTheSlowTasks()
	{
		final SlayerPlanBuilder.View view = SlayerPlanBuilder.build(SlayerPointsPlannerTest.exampleWithKonarBlocks(), SlayerPointsPlannerTest.DATA,
				SlayerPointsPlannerTest.DATASET, new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, SlayerGoal.POINTS, BoostInterval.EVERY_10TH,
						true), SlayerMasterAccessTest.maxed());
		assertEquals(Arrays.asList("Drakes (set)", "Wyrms (set)", "Hydras", "Cave Kraken", "Waterfiends", "Kurask", "Abyssal Demons"),
				texts(SlayerPlanText.blockListItems("Konar", view.getBlocked(), view.getBlockAdvice())));
		final SlayerPlanBuilder.Fillers aya = view.getFillers();
		assertEquals(Arrays.asList("Cave Bugs", "Cave Slimes", "Cave Crawlers", "Lizards", "Banshees", "Crawling Hands", "Icefiends"),
				texts(SlayerPlanText.blockListItems(aya.getNpc(), aya.getBlocked(), aya.getBlockAdvice())));
	}

	private static List<String> texts(List<SlayerPlanText.BlockItem> items)
	{
		final List<String> texts = new ArrayList<>();
		for (SlayerPlanText.BlockItem item : items) texts.add(item.getText());
		return texts;
	}

	private static List<SlayerPlanText.BlockItem.Kind> kinds(List<SlayerPlanText.BlockItem> items)
	{
		final List<SlayerPlanText.BlockItem.Kind> kinds = new ArrayList<>();
		for (SlayerPlanText.BlockItem item : items) kinds.add(item.getKind());
		return kinds;
	}

	private static List<String> tooltips(List<SlayerPlanText.BlockItem> items)
	{
		final List<String> tooltips = new ArrayList<>();
		for (SlayerPlanText.BlockItem item : items) tooltips.add(item.getTooltip());
		return tooltips;
	}

	@Test
	public void theCompactBestMasterNamesTheRunnerUpAndAPin()
	{
		// The example account, its plan pinned to Konar: Kuradal is best for XP, Steve next
		final SlayerPlanBuilder.View pinned = SlayerPointsPlannerTest.example(SlayerGoal.XP);
		assertEquals(Arrays.asList("Kuradal", "then Steve", "Plan uses Konar (pinned)"), SlayerPlanText.masterItems(pinned));
		// Slayer points has its routine instead
		assertTrue(SlayerPlanText.masterItems(SlayerPointsPlannerTest.example(SlayerGoal.POINTS)).isEmpty());
	}

	/** Every plan line's compact label fits on a line and uses plain punctuation, for every goal and master. */
	@Test
	public void compactPlanLabelsAreShortAndPlain()
	{
		for (SlayerGoal goal : SlayerGoal.values())
		{
			for (SlayerMaster master : SlayerMaster.values())
			{
				final SlayerPlanBuilder.View view = SlayerPlanBuilder.build(SlayerPointsPlannerTest.exampleWithKonarBlocks(), SlayerPointsPlannerTest.DATA,
						SlayerPointsPlannerTest.DATASET, new SlayerPlanBuilder.Settings(master, 0, goal, BoostInterval.EVERY_10TH),
						SlayerMasterAccessTest.maxed());
				final List<String> items = new ArrayList<>(SlayerPlanText.masterItems(view));
				items.add(SlayerPlanText.pointsItem(view.getPlan()));
				items.addAll(view.getNoteItems());
				items.addAll(texts(SlayerPlanText.blockListItems(view.getMasterName(), view.getBlocked(), view.getBlockAdvice())));
				if (view.getFillers() != null)
				{
					items.addAll(texts(SlayerPlanText.blockListItems(view.getFillers().getNpc(), view.getFillers().getBlocked(),
							view.getFillers().getBlockAdvice())));
				}
				for (List<SlayerPointsPlanner.Line> lines : Arrays.asList(view.getPlan().getBuyNow(), view.getPlan().getSaveFor(),
						view.getPlan().getLater(), view.getPlan().getAvoid()))
				{
					for (SlayerPointsPlanner.Line line : lines)
					{
						assertTrue(line.getLabel(), line.getLabel().startsWith(line.getName().replaceFirst(" at \\S+$", "")));
						items.add(line.getLabel());
					}
				}
				for (String item : items)
				{
					assertTrue(goal + " " + master + ": " + item, item.length() <= 40);
				}
			}
		}
	}
}
