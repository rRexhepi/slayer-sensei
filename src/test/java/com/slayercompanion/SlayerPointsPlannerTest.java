package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.SlayerPointsPlanner.Candidate;
import com.slayercompanion.SlayerPointsPlanner.Line;
import com.slayercompanion.SlayerPointsPlanner.Plan;
import com.slayercompanion.SlayerPointsPlanner.Reserve;
import com.slayercompanion.SlayerPointsPlanner.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class SlayerPointsPlannerTest
{
	static final SlayerCacheFixture FIXTURE = SlayerCacheFixture.get();
	static final SlayerGameData DATA = SlayerGameData.load(FIXTURE);
	static final SlayerDataset DATASET = new SlayerDataset(new Gson());

	// ---- the planner on its own ----

	private static Candidate reward(String name, int cost, double gain)
	{
		return new Candidate(Type.REWARD, null, name, cost, false, false, gain, name + " helps.", null, null);
	}

	private static Reserve keep(int points)
	{
		return new Reserve(points, 0, 30, 0, Collections.<String>emptyList(), points);
	}

	private static List<String> names(List<Line> lines)
	{
		final List<String> out = new ArrayList<>();
		for (Line l : lines) out.add(l.getName());
		return out;
	}

	@Test
	public void theMostGainPerPointComesFirst()
	{
		// 0.04 for 50 beats 0.06 for 100, which beats 0.02 for 80
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(reward("C", 80, 0.02), reward("B", 100, 0.06), reward("A", 50, 0.04)),
				1000, keep(0));
		assertEquals(Arrays.asList("A", "B", "C"), names(plan.getBuyNow()));
		assertEquals("A helps.", plan.getBuyNow().get(0).getDetail());
	}

	@Test
	public void blocksKeepTheBlockAdvicesOrder()
	{
		// Block B is worth more on top of block A, so it sorts first, but A is the one to buy first
		final Candidate a = new Candidate(Type.BLOCK, null, "Block A", 80, false, false, 0.02, "A.", null, null);
		final Candidate b = new Candidate(Type.BLOCK, null, "Block B", 80, false, false, 0.03, "B.", null, null);
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(a, b, reward("Middle", 80, 0.025)), 1000, keep(0));
		assertEquals(Arrays.asList("Block A", "Middle", "Block B"), names(plan.getBuyNow()));
	}

	@Test
	public void eachBlockListKeepsItsOwnOrder()
	{
		// Konar's B is worth more on top of A, and Aya's D on top of C. Per point: D, B, C, A.
		final Candidate a = new Candidate(Type.BLOCK, null, "Block A at Konar", 80, false, false, 0.02, "A.", null, null, SlayerMaster.KONAR);
		final Candidate b = new Candidate(Type.BLOCK, null, "Block B at Konar", 80, false, false, 0.03, "B.", null, null, SlayerMaster.KONAR);
		final Candidate c = new Candidate(Type.BLOCK, null, "Block C at Aya", 40, false, false, 0.012, "C.", null, null, SlayerMaster.TURAEL);
		final Candidate d = new Candidate(Type.BLOCK, null, "Block D at Aya", 40, false, false, 0.02, "D.", null, null, SlayerMaster.TURAEL);
		// The lists keep the places the sort gave them, and each fills its own places in its own order
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(a, b, c, d), 1000, keep(0));
		assertEquals(Arrays.asList("Block C at Aya", "Block A at Konar", "Block D at Aya", "Block B at Konar"), names(plan.getBuyNow()));
	}

	@Test
	public void spendingStopsAtTheReserve()
	{
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(reward("Cheap", 50, 0.05), reward("Helmet", 400, 0.2)), 521, keep(90));
		// 431 to spend: Cheap first, then 381 left is 19 short of the helmet
		assertEquals(Arrays.asList("Cheap"), names(plan.getBuyNow()));
		assertEquals(Arrays.asList("Helmet"), names(plan.getSaveFor()));
		assertEquals("19 more points. Helmet helps.", plan.getSaveFor().get(0).getDetail());
		assertEquals(19, plan.getSaveFor().get(0).getNeed());
		// With an estimate of the tasks it takes
		final Plan estimated = SlayerPointsPlanner.plan(Arrays.asList(reward("Helmet", 400, 0.2)), 100, keep(0), need -> need / 15);
		assertEquals("300 more points, about 20 tasks. Helmet helps.", estimated.getSaveFor().get(0).getDetail());
		assertEquals(20, estimated.getSaveFor().get(0).getTasks());
	}

	@Test
	public void eachLineHasACompactLabel()
	{
		final Candidate locked = new Candidate(Type.REWARD, null, "Gargoyle Smasher", 120, false, false, 0.01, "Finishes gargoyles.",
				"75 Slayer", null);
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(reward("Cheap", 50, 0.05), reward("Helmet", 400, 0.2), locked,
				reward("Bad", 80, -0.03)), 521, keep(90), need -> need / 15);
		assertEquals("Cheap (50)", plan.getBuyNow().get(0).getLabel());
		assertEquals("Helmet (400), 1 task", plan.getSaveFor().get(0).getLabel());
		assertEquals("Gargoyle Smasher (120), 75 Slayer", plan.getLater().get(0).getLabel());
		// What not to buy needs no price
		assertEquals("Bad", plan.getAvoid().get(0).getLabel());
	}

	@Test
	public void aCompactLabelDropsTheBlocksMasterThenTheCostThenTheWait()
	{
		assertEquals("Bigger and Badder (50)", SlayerPointsPlanner.label("Bigger and Badder", 50));
		assertEquals("Task extensions", SlayerPointsPlanner.label("Task extensions", -1));
		assertEquals("Block Hydras at Konar (80)", SlayerPointsPlanner.label("Block Hydras at Konar", 80));
		assertEquals("Block Fossil Island Wyverns (80)", SlayerPointsPlanner.label("Block Fossil Island Wyverns at Konar", 80));
		assertEquals("Block Waterfiends (100), 8 tasks", SlayerPointsPlanner.saveLabel("Block Waterfiends", 100, 400, 8));
		assertEquals("Block Abyssal Demons (80), 3 tasks", SlayerPointsPlanner.saveLabel("Block Abyssal Demons at Konar", 80, 79, 3));
		assertEquals("Malevolent Masquerade, 28 tasks", SlayerPointsPlanner.saveLabel("Malevolent Masquerade", 400, 749, 28));
		assertEquals("Ring Bling (150), 119 to go", SlayerPointsPlanner.saveLabel("Ring Bling", 150, 119, -1));
		assertEquals("Ring Bling (150)", SlayerPointsPlanner.saveLabel("Ring Bling", 150, -1, -1));
		assertEquals("Nechs Please (100), 80 Slayer", SlayerPointsPlanner.laterLabel("Nechs Please", 100, "80 Slayer"));
		// Only a block loses the master's name
		assertEquals("More at Stake (100)", SlayerPointsPlanner.label("More at Stake", 100));
	}

	@Test
	public void harmfulPurchasesAreNamedAndSmallOnesLeftOut()
	{
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(reward("Bad", 80, -0.03), reward("Worse", 80, -0.06),
				reward("Tiny", 10, 0.001), reward("Flat", 10, -0.001), reward("Good", 10, 0.01)), 1000, keep(0));
		assertEquals(Arrays.asList("Good"), names(plan.getBuyNow()));
		// The worst first
		assertEquals(Arrays.asList("Worse", "Bad"), names(plan.getAvoid()));
	}

	@Test
	public void purchasesTheWikiAdvisesAgainstAreNamedAndNeverBought()
	{
		// Worth something to the goal model, but the wiki's Slayer guide says not to buy it for the goal
		final Candidate warped = new Candidate(Type.REWARD, null, "Warped Reality", 60, false, false, 0.05, "The wiki says not to.", null, null,
				null, true);
		final List<Candidate> all = new ArrayList<>(Collections.singletonList(warped));
		for (int i = 1; i <= 6; i++) all.add(reward("Harm " + i, 10, -0.01 * i));
		final Plan plan = SlayerPointsPlanner.plan(all, 1000, keep(0), null, SlayerGoal.XP);
		assertTrue(names(plan.getBuyNow()).toString(), plan.getBuyNow().isEmpty());
		// Named first, and the goal model's own list keeps its limit after it
		assertEquals("Warped Reality", plan.getAvoid().get(0).getName());
		assertEquals(SlayerPointsPlanner.MAX_AVOID, plan.getAvoid().size());
		// One the player cannot use yet is not named
		final Candidate locked = new Candidate(Type.REWARD, null, "Seeing Red", 50, false, false, 0.05, "No.", "68 Slayer", null, null, true);
		final Plan later = SlayerPointsPlanner.plan(Collections.singletonList(locked), 1000, keep(0), null, SlayerGoal.XP);
		assertTrue(later.getAvoid().isEmpty());
		assertTrue(later.getLater().isEmpty());
	}

	@Test
	public void purchasesWaitingForALevelAreListedApart()
	{
		final Candidate locked = new Candidate(Type.REWARD, null, "Gargoyle Smasher", 120, false, false, 0.01, "Finishes gargoyles.",
				"75 Slayer", null);
		final Candidate lockedHarm = new Candidate(Type.REWARD, null, "Seeing Red", 50, false, false, -0.02, "Adds red dragons.",
				"68 Slayer", null);
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(locked, lockedHarm), 1000, keep(0));
		assertTrue(plan.getBuyNow().isEmpty());
		assertEquals("Needs 75 Slayer. Finishes gargoyles.", plan.getLater().get(0).getDetail());
		// Something that cannot be used yet cannot hurt yet
		assertTrue(plan.getAvoid().isEmpty());
	}

	@Test
	public void ownedCosmeticAndUnknownPurchasesAreCountedNotRecommended()
	{
		final Plan plan = SlayerPointsPlanner.plan(Arrays.asList(
				new Candidate(Type.REWARD, null, "Owned", 50, true, false, 0.5, null, null, null),
				new Candidate(Type.REWARD, null, "Recolour", 1000, false, true, 0, null, null, null),
				new Candidate(Type.REWARD, null, "Unknown", 100, false, false, Double.NaN, null, null, null),
				new Candidate(Type.BLOCK, null, "Block Kurask", 80, false, false, 0.02, "Stops it.", null, null),
				new Candidate(Type.ITEM, null, "Rune pouch", 750, true, false, 0.02, null, null, null),
				new Candidate(Type.SUMMARY, null, "Task extensions", -1, false, false, -1, "Longer tasks.", null, null)), 5000, keep(0));
		// Shop rewards only: blocks, items and summary lines are not rewards
		assertEquals(3, plan.getTotal());
		assertEquals(1, plan.getOwned());
		assertEquals(1, plan.getCosmeticLeft());
		assertEquals(Arrays.asList("Block Kurask"), names(plan.getBuyNow()));
		assertEquals(Arrays.asList("Task extensions"), names(plan.getAvoid()));
		assertEquals(-1, plan.getAvoid().get(0).getCost());
	}

	@Test
	public void notesFollowTheReason()
	{
		final Candidate pouch = new Candidate(Type.ITEM, null, "Rune pouch", 750, false, false, 0.02, "Frees space.", null,
				"Could not tell if you have one.");
		assertEquals("Frees space. Could not tell if you have one.", SlayerPointsPlanner.plan(Collections.singletonList(pouch), 800, keep(0))
				.getBuyNow().get(0).getDetail());
	}

	@Test
	public void shortfallText()
	{
		assertEquals("119 more points, about 12 tasks. ", SlayerPointsPlanner.shortfall(119, 12));
		assertEquals("5 more points, about 1 task. ", SlayerPointsPlanner.shortfall(5, 1));
		assertEquals("5 more points. ", SlayerPointsPlanner.shortfall(5, -1));
		assertEquals("", SlayerPointsPlanner.shortfall(-1, -1));
	}

	@Test
	public void rewardRowsAreUnique()
	{
		final java.util.Set<Integer> rows = new java.util.HashSet<>();
		for (SlayerReward r : SlayerReward.values())
		{
			assertTrue(r.name(), rows.add(r.getRow()));
		}
	}

	@Test
	public void finishingItemRewardsSayTheItemIsStillNeeded()
	{
		assertTrue(SlayerReward.GARGOYLE_SMASHER.getFact().contains("rock hammer must still be in your inventory"));
		assertTrue(SlayerReward.SLUG_SALTER.getFact().contains("bag of salt must still be in your inventory"));
		assertTrue(SlayerReward.REPTILE_FREEZER.getFact().contains("ice cooler must still be in your inventory"));
		assertTrue(SlayerReward.SHROOM_SPRAYER.getFact().contains("Fungicide spray must still be in your inventory"));
	}

	@Test
	public void levelLockedRewardsWaitForTheLevel()
	{
		assertEquals("75 Slayer", SlayerPlanBuilder.lockedBy(SlayerReward.GARGOYLE_SMASHER, 70, 99, 99));
		assertEquals("55 Crafting", SlayerPlanBuilder.lockedBy(SlayerReward.MALEVOLENT_MASQUERADE, 99, 40, 99));
		assertEquals("66 Slayer", SlayerPlanBuilder.lockedBy(SlayerReward.STOP_THE_WYVERN, 65, 99, 99));
		assertNull(SlayerPlanBuilder.lockedBy(SlayerReward.GARGOYLE_SMASHER, 75, 1, 1));
	}

	// ---- the whole pipeline, against a dump of the live cache (see tools/cache-dump) ----

	/** Varbits live inside varps through the cache's varbit layout, as in the client. */
	static final class Account implements SlayerPlanBuilder.Account
	{
		final Map<Integer, Integer> varps = new HashMap<>();
		final Map<Skill, Integer> levels = new HashMap<>();

		Account withVarbit(int varbit, int value)
		{
			final SlayerCacheFixture.Varbit v = layout(varbit);
			final int mask = mask(v) << v.lsb;
			varps.put(v.varp, (varp(v.varp) & ~mask) | (value << v.lsb & mask));
			return this;
		}

		Account withQuestPoints(int questPoints)
		{
			varps.put(VarPlayerID.QP, questPoints);
			return this;
		}

		/** Every block slot open: 300 quest points and the elite Lumbridge & Draynor diary. */
		Account withAllSlots()
		{
			return withQuestPoints(300).withVarbit(VarbitID.LUMBRIDGE_ELITE_REWARD, 1);
		}

		Account withLevel(Skill skill, int level)
		{
			levels.put(skill, level);
			return this;
		}

		/** The task in progress: kills left, the master who gave it and the streak before it. */
		Account onTask(SlayerAssigner master, int streak)
		{
			varps.put(VarPlayerID.SLAYER_COUNT, 50);
			return withVarbit(VarbitID.SLAYER_MASTER, master.getId()).withVarbit(VarbitID.SLAYER_TASKS_COMPLETED, streak);
		}

		Account ownsEverything()
		{
			for (int varp : SlayerGameData.UNLOCK_VARPS) varps.put(varp, -1);
			return this;
		}

		@Override
		public int varp(int varp)
		{
			return varps.getOrDefault(varp, 0);
		}

		@Override
		public int varbit(int varbit)
		{
			final SlayerCacheFixture.Varbit v = layout(varbit);
			return varp(v.varp) >>> v.lsb & mask(v);
		}

		/** 99 in every stat but those set. */
		@Override
		public int[] levels()
		{
			final int[] all = new int[Skill.values().length];
			Arrays.fill(all, 99);
			levels.forEach((skill, level) -> all[skill.ordinal()] = level);
			return all;
		}

		private static SlayerCacheFixture.Varbit layout(int varbit)
		{
			final SlayerCacheFixture.Varbit v = FIXTURE.varbit(varbit);
			if (v == null) throw new AssertionError("Varbit " + varbit + " is not in the fixture");
			return v;
		}

		private static int mask(SlayerCacheFixture.Varbit v)
		{
			final int bits = v.msb - v.lsb + 1;
			return bits == 32 ? -1 : (1 << bits) - 1;
		}
	}

	private static final SlayerPlanBuilder.Settings KONAR_XP =
			new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 90, SlayerGoal.XP, BoostInterval.EVERY_10TH);
	private static final SlayerPlanBuilder.Settings KONAR_POINTS =
			new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 90, SlayerGoal.POINTS, BoostInterval.EVERY_10TH);

	static SlayerPlanBuilder.Settings pinned(SlayerMaster master, SlayerGoal goal)
	{
		return new SlayerPlanBuilder.Settings(master, 0, goal, BoostInterval.EVERY_10TH);
	}

	private static SlayerPlanBuilder.View konar(Account account)
	{
		return SlayerPlanBuilder.build(account, DATA, DATASET, KONAR_XP, null);
	}

	/** The example account: 521 points, every block slot open, nothing bought, the plan pinned to Konar. */
	static SlayerPlanBuilder.View example(SlayerGoal goal)
	{
		return SlayerPlanBuilder.build(new Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withAllSlots(), DATA, DATASET,
				pinned(SlayerMaster.KONAR, goal), SlayerMasterAccessTest.maxed());
	}

	private static Account rich()
	{
		return new Account().withVarbit(VarbitID.SLAYER_POINTS, 5000);
	}

	private static Line line(Plan plan, String name)
	{
		for (List<Line> lines : Arrays.asList(plan.getBuyNow(), plan.getSaveFor(), plan.getLater(), plan.getAvoid()))
		{
			for (Line l : lines)
			{
				if (l.getName().equals(name)) return l;
			}
		}
		return null;
	}

	@Test
	public void theExampleBuyListsFollowTheGoal()
	{
		// XP: Bigger and Badder and the blocks first, then the helmet and the Fossil Island Wyvern block. The blocks come in
		// the block advice's order, and every one is a task the wiki's Slayer guide says to skip or block.
		Plan plan = example(SlayerGoal.XP).getPlan();
		assertEquals(Arrays.asList("Bigger and Badder", "Block Cave Kraken", "Block Waterfiends", "Block Metal Dragons",
				"Block Fossil Island Wyverns"), names(plan.getBuyNow()));
		assertEquals(Arrays.asList("Block Hydras", "Block Drakes", "Block Wyrms", "Malevolent Masquerade", "Stop the Wyvern"),
				names(plan.getSaveFor()));
		assertTrue(names(plan.getAvoid()).containsAll(Arrays.asList("Basilocked", "Watch the Birdie", "Like a Boss")));
		// Profit: the money tasks get longer and the money losers are blocked
		plan = example(SlayerGoal.PROFIT).getPlan();
		assertEquals(Arrays.asList("Basilocked", "Wyver-nother one", "Block Kalphites", "Block Dagannoth", "Fire & Darkness"),
				names(plan.getBuyNow()));
		assertEquals("Adds Basilisks, a good money task, to Konar's list. Basilisk knights drop the basilisk jaw.",
				line(plan, "Basilocked").getDetail());
		assertEquals(Arrays.asList("Block Black Demons", "Block Fire Giants", "Block Hellhounds", "Block Jellies", "Block Bloodveld"),
				names(plan.getSaveFor()));
		// Slayer points: the routine's whole cycle, so blocks of the slow tasks at Konar for the milestone tasks and at Aya
		// for the rest, no extension, and nothing kept for skips
		final SlayerPlanBuilder.View points = example(SlayerGoal.POINTS);
		plan = points.getPlan();
		assertEquals(Arrays.asList("Block Cave Bugs at Aya", "Block Hydras at Konar", "Block Cave Slimes at Aya", "Block Cave Crawlers at Aya",
				"Block Lizards at Aya", "Block Banshees at Aya", "Block Drakes at Konar", "Block Crawling Hands at Aya", "Block Cave Kraken at Konar",
				"Block Icefiends at Aya"), names(plan.getBuyNow()));
		assertEquals("Task extensions", plan.getAvoid().get(0).getName());
		for (Line l : plan.getBuyNow()) assertFalse(l.getName(), l.getName().startsWith("Augment"));
		assertEquals(0, plan.getReserve().getPoints());
	}

	/** The coordinator's example: 521 points, Konar's list already holding drakes (task id 112) and wyrms (111). */
	static Account exampleWithKonarBlocks()
	{
		return new Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withAllSlots()
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_1, 112).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_2, 111);
	}

	private static SlayerPlanBuilder.View routine(Account account, BoostInterval boost)
	{
		return SlayerPlanBuilder.build(account, DATA, DATASET, new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, SlayerGoal.POINTS, boost,
				true), SlayerMasterAccessTest.maxed());
	}

	@Test
	public void theRoutineValuesBlocksAtBothMastersInOnePlan()
	{
		final SlayerPlanBuilder.View view = routine(exampleWithKonarBlocks(), BoostInterval.EVERY_10TH);
		// Milestone tasks go to Konar, every other task to Aya
		assertEquals(SlayerMaster.KONAR, view.getMaster());
		final SlayerPlanBuilder.Fillers fillers = view.getFillers();
		assertEquals("Aya", fillers.getNpc());
		assertTrue(fillers.getBlocked().isEmpty());
		assertEquals(7, fillers.getEmptySlots());
		assertEquals(Arrays.asList("Drakes", "Wyrms"), view.getBlockAdvice().getKeep());
		assertEquals(Arrays.asList("Hydras", "Cave Kraken", "Waterfiends", "Kurask", "Abyssal Demons"), view.getBlockAdvice().getAdd());
		assertEquals(Arrays.asList("Cave Bugs", "Cave Slimes", "Cave Crawlers", "Lizards", "Banshees", "Crawling Hands", "Icefiends"),
				fillers.getBlockAdvice().getAdd());

		// One buy list with both, each line naming its master at that master's cost
		final Plan plan = view.getPlan();
		assertEquals(Arrays.asList("Block Hydras at Konar", "Block Cave Bugs at Aya", "Block Cave Slimes at Aya", "Block Cave Crawlers at Aya",
				"Block Lizards at Aya", "Block Banshees at Aya", "Block Crawling Hands at Aya", "Block Cave Kraken at Konar",
				"Block Icefiends at Aya", "Block Waterfiends at Konar"), names(plan.getBuyNow()));
		assertEquals(80, line(plan, "Block Hydras at Konar").getCost());
		assertEquals(40, line(plan, "Block Cave Bugs at Aya").getCost());
		assertEquals("Hydras take longer than the average task at Konar. Milestone tasks get quicker on average.",
				line(plan, "Block Hydras at Konar").getDetail());
		assertEquals("Cave Bugs take longer than the average task at Aya. Filler tasks get quicker on average.",
				line(plan, "Block Cave Bugs at Aya").getDetail());
		// Each list's blocks keep its advice's order, and Aya's are limited by her list's open slots
		final List<String> konar = new ArrayList<>();
		final List<String> aya = new ArrayList<>();
		for (List<Line> lines : Arrays.asList(plan.getBuyNow(), plan.getSaveFor()))
		{
			for (Line l : lines)
			{
				final String task = l.getName().replaceFirst("^Block ", "").replaceFirst(" at (Konar|Aya)$", "");
				if (l.getName().endsWith(" at Konar")) konar.add(task);
				if (l.getName().endsWith(" at Aya")) aya.add(task);
			}
		}
		assertEquals(view.getBlockAdvice().getAdd().subList(0, konar.size()), konar);
		assertEquals(fillers.getBlockAdvice().getAdd(), aya);

		// The sidebar labels the two lists apart
		assertEquals("Blocks for milestone tasks", SlayerPlanText.blockHeading(view));
		assertEquals("Blocks for filler tasks", SlayerPlanText.FILLER_BLOCK_HEADING);
		assertEquals("Milestone tasks go to Konar, so block the slow ones there.\n"
				+ "Block next: Hydras, Cave Kraken, Waterfiends, Kurask, Abyssal Demons. 80 points each at Konar.\n"
				+ "Already blocked and right: Drakes, Wyrms.", SlayerPlanText.blockAdvice(view));
		assertEquals("Filler tasks go to Aya, so block the longest ones there. Spria shares this list.\n"
				+ "Block next: Cave Bugs, Cave Slimes, Cave Crawlers, Lizards, Banshees, Crawling Hands, Icefiends. 40 points each at Aya.",
				SlayerPlanText.fillerBlockAdvice(view));

		// Every 50th task puts more of the time at Aya, so her blocks come first
		assertEquals("Block Cave Bugs at Aya", routine(exampleWithKonarBlocks(), BoostInterval.EVERY_50TH).getPlan().getBuyNow().get(0).getName());
	}

	@Test
	public void fillerBlocksFollowTheWikisTuraelTips()
	{
		// Every slot on Turael's list free: the quick cannonable tasks with a close teleport are never blocked while the
		// awkward ones the wiki's Turael task completion tips name are still open
		final List<String> quick = Arrays.asList("Cows", "Goblins", "Rats", "Birds");
		final List<String> add = routine(exampleWithKonarBlocks(), BoostInterval.EVERY_10TH).getFillers().getBlockAdvice().getAdd();
		for (String task : quick) assertFalse(add.toString(), add.contains(task));
		assertTrue(add.toString(), add.containsAll(Arrays.asList("Cave Bugs", "Cave Slimes")));
		// However many slots there were, the picks never reach them before cave bugs and cave slimes
		final SlayerPool aya = SlayerPool.of(SlayerAssigner.TURAEL, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		final List<String> order = new ArrayList<>();
		for (SlayerPool.Entry e : SlayerGoalModel.bestBlocks(aya, SlayerGoal.POINTS, 24)) order.add(e.getName());
		assertEquals(Arrays.asList("Cave Bugs", "Cave Slimes"), order.subList(0, 2));
		for (String task : quick) assertFalse(order.toString(), order.contains(task));
	}

	@Test
	public void fillerBlocksStayWithinTheSharedListsOpenSlots()
	{
		// 100 quest points open two slots on each list. Konar's hold drakes and wyrms, and one of Aya's holds cave bugs (task id 63).
		final Account account = new Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withQuestPoints(100)
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_1, 112).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_2, 111)
				.withVarbit(VarbitID.SLAYER_BLOCKED_TURAEL_1, 63);
		final SlayerPlanBuilder.View view = routine(account, BoostInterval.EVERY_10TH);
		assertEquals(Collections.singletonList("Cave Bugs"), view.getFillers().getBlocked());
		assertEquals(1, view.getFillers().getEmptySlots());
		assertEquals(Collections.singletonList("Cave Slimes"), view.getFillers().getBlockAdvice().getAdd());
		int aya = 0;
		for (List<Line> lines : Arrays.asList(view.getPlan().getBuyNow(), view.getPlan().getSaveFor()))
		{
			for (Line l : lines) if (l.getName().endsWith(" at Aya")) aya++;
		}
		assertEquals(1, aya);
	}

	@Test
	public void onlyTheRoutineHasAFillerList()
	{
		// The other goals, a plan without master state, and a plan pinned to a master off the main streak
		assertNull(example(SlayerGoal.XP).getFillers());
		assertNull(SlayerPlanBuilder.build(exampleWithKonarBlocks(), DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.POINTS), null)
				.getFillers());
		assertNull(SlayerPlanBuilder.build(exampleWithKonarBlocks(), DATA, DATASET, pinned(SlayerMaster.MORTIMER, SlayerGoal.POINTS),
				SlayerMasterAccessTest.maxed()).getFillers());
		// Pinned to Turael's own list there is one list only
		final SlayerPlanBuilder.View turael = SlayerPlanBuilder.build(exampleWithKonarBlocks(), DATA, DATASET,
				pinned(SlayerMaster.TURAEL, SlayerGoal.POINTS), SlayerMasterAccessTest.maxed());
		assertNull(turael.getFillers());
		assertEquals("Blocks for Slayer points", SlayerPlanText.blockHeading(turael));
		assertNull(SlayerPlanText.fillerBlockAdvice(turael));
		assertTrue(SlayerPlanBuilder.takesMilestones(SlayerAssigner.KONAR));
		assertFalse(SlayerPlanBuilder.takesMilestones(SlayerAssigner.SPRIA));
		assertFalse(SlayerPlanBuilder.takesMilestones(SlayerAssigner.KRYSTILIA));
	}

	/** A maxed account at Kuradal with nothing bought or blocked and this many points. */
	private static Plan kuradal(SlayerGoal goal, int points)
	{
		return SlayerPlanBuilder.build(new Account().withVarbit(VarbitID.SLAYER_POINTS, points).withAllSlots(), DATA, DATASET,
				pinned(SlayerMaster.DURADEL, goal), SlayerMasterAccessTest.maxed()).getPlan();
	}

	private static List<String> listed(Plan plan)
	{
		final List<String> out = new ArrayList<>(names(plan.getBuyNow()));
		out.addAll(names(plan.getSaveFor()));
		out.addAll(names(plan.getLater()));
		return out;
	}

	@Test
	public void extensionsOnlyForTasksRatedGoodAndNeverAgainstTheWiki()
	{
		// XP: abyssal demons and black demons are fair for XP, the wiki says to block them, araxytes are excellent
		final List<String> xp = listed(kuradal(SlayerGoal.XP, 5000));
		assertFalse(xp.toString(), xp.contains("Augment My Abbies"));
		assertFalse(xp.toString(), xp.contains("It's Dark in Here"));
		assertTrue(xp.toString(), xp.contains("More eyes than sense"));
		// Profit: the wiki discourages extending metal dragons, and no task rated fair or lower is extended
		final List<String> profit = listed(kuradal(SlayerGoal.PROFIT, 5000));
		assertFalse(profit.toString(), profit.contains("Pedal to the Metals"));
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		int fair = 0;
		for (SlayerPool.Entry e : pool.open())
		{
			final SlayerGameData.Extension x = DATA.extensionOf(e.getTaskRow());
			if (x == null || !e.isRated() || e.rating(SlayerGoal.PROFIT) >= SlayerSpend.GOOD) continue;
			fair++;
			final String name = DATA.unlock(x.getUnlockRow()).getName();
			assertFalse(e.getName() + ": " + profit, profit.contains(name));
		}
		assertTrue(fair > 5);
	}

	@Test
	public void theXpPlanFollowsTheSpendingOrder()
	{
		// Plenty of points: Bigger and Badder and the blocks, then the helmet and the Fossil Island Wyvern block, then
		// extensions, then the rest
		final Plan rich = kuradal(SlayerGoal.XP, 50000);
		final List<String> all = names(rich.getBuyNow());
		int tier = 1;
		for (String name : all)
		{
			final int t = tier(name);
			assertTrue(all.toString(), t >= tier);
			tier = t;
		}
		assertEquals(all.toString(), 1, tier(all.get(0)));
		assertTrue(all.toString(), all.indexOf("Bigger and Badder") < all.indexOf("Stop the Wyvern"));
		final int helmet = all.indexOf("Malevolent Masquerade");
		final int wyvern = all.indexOf("Stop the Wyvern");
		assertEquals(all.toString(), 1, Math.abs(helmet - wyvern));
		assertEquals(all.toString(), 1, tier(all.get(Math.min(helmet, wyvern) - 1)));
		assertEquals(all.toString(), 3, tier(all.get(Math.max(helmet, wyvern) + 1)));
		assertEquals(all.toString(), 4, tier(all.get(all.size() - 1)));
		// With too few points the plan buys the start of that order and saves for the rest in the same order
		for (int points : new int[]{521, 960, 1500})
		{
			final Plan plan = kuradal(SlayerGoal.XP, points);
			final List<String> both = new ArrayList<>(names(plan.getBuyNow()));
			both.addAll(names(plan.getSaveFor()));
			assertEquals(points + ": " + both, all.subList(0, both.size()), both);
			// Each save-for line counts the ones before it
			int need = -1;
			for (Line l : plan.getSaveFor())
			{
				assertTrue(l.getName(), l.getNeed() > need);
				need = l.getNeed();
			}
		}
	}

	/** The XP order's tier of a line in the plan. */
	private static int tier(String name)
	{
		if (name.startsWith("Block ") || name.equals("Bigger and Badder")) return 1;
		if (name.equals("Stop the Wyvern") || name.equals("Malevolent Masquerade")) return 2;
		for (SlayerReward r : SlayerReward.values())
		{
			if (r.getKind() == SlayerReward.Kind.EXTENSION && DATA.unlock(r.getRow()) != null && name.equals(DATA.unlock(r.getRow()).getName()))
			{
				return 3;
			}
		}
		return 4;
	}

	@Test
	public void theReserveFollowsTheSkipModel()
	{
		// About half of Konar's offers are worth skipping for XP, so the plan keeps four skips: half to the fourth power is under one in ten
		final Reserve xp = example(SlayerGoal.XP).getPlan().getReserve();
		assertEquals(4, xp.getSkips());
		assertEquals(120, xp.getPoints());
		assertEquals(Arrays.asList("Waterfiends", "Cave Kraken"), xp.getTasks());
		assertEquals("521 points. 120 are kept to skip up to 4 bad tasks in a row at Konar. About half of Konar's tasks are worth"
						+ " skipping for XP, such as Waterfiends and Cave Kraken. 401 to spend.",
				SlayerPlanText.points(example(SlayerGoal.XP).getPlan(), SlayerAssigner.KONAR, "Konar", SlayerGoal.XP));
		assertEquals(1, SlayerPlanBuilder.skipsInARow(0.05));
		assertEquals(2, SlayerPlanBuilder.skipsInARow(0.3));
		assertEquals(0, SlayerPlanBuilder.skipsInARow(0));
		assertEquals(SlayerPlanBuilder.MAX_SKIPS_IN_A_ROW, SlayerPlanBuilder.skipsInARow(0.9));
		// The configured amount is the least the plan keeps
		final Reserve floor = SlayerPlanBuilder.build(new Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withAllSlots(), DATA, DATASET,
				new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 200, SlayerGoal.XP, BoostInterval.EVERY_10TH),
				SlayerMasterAccessTest.maxed()).getPlan().getReserve();
		assertEquals(200, floor.getPoints());
		assertTrue(floor.isFloor());
		// Mortimer offers a choice instead of skips, and the points routine never skips
		assertEquals(0, SlayerPlanBuilder.build(rich().withAllSlots(), DATA, DATASET, pinned(SlayerMaster.MORTIMER, SlayerGoal.PROFIT),
				SlayerMasterAccessTest.maxed()).getPlan().getReserve().getPoints());
		assertEquals("100 points. None are kept for skips, since you choose from Mortimer's tasks instead. 100 to spend.",
				SlayerPlanText.points(new Plan(100, Reserve.NONE, Collections.<Line>emptyList(), Collections.<Line>emptyList(),
						Collections.<Line>emptyList(), Collections.<Line>emptyList(), 0, 0, 0), SlayerAssigner.MORTIMER, "Mortimer", SlayerGoal.XP));
	}

	@Test
	public void blocksAreInTheListWithTheirCost()
	{
		// Seven empty slots at Konar: every block the advice puts forward is a line at Konar's 80 points
		final SlayerPlanBuilder.View view = example(SlayerGoal.XP);
		final Line kraken = line(view.getPlan(), "Block Cave Kraken");
		assertEquals(80, kraken.getCost());
		assertEquals("Stops Konar giving Cave Kraken, a task the wiki's Slayer guide says to skip or block for XP.", kraken.getDetail());
		// A full block list puts nothing forward
		final Account full = new Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withAllSlots();
		// Cave kraken, waterfiends, metal dragons, Fossil Island wyverns, hydras, drakes and wyrms
		final int[] tasks = {92, 88, 127, 106, 113, 112, 111};
		final int[] slots = SlayerMaster.KONAR.getBlockVarbits();
		for (int i = 0; i < slots.length; i++) full.withVarbit(slots[i], tasks[i]);
		final Plan plan = SlayerPlanBuilder.build(full, DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.maxed())
				.getPlan();
		for (List<Line> lines : Arrays.asList(plan.getBuyNow(), plan.getSaveFor()))
		{
			for (Line l : lines) assertFalse(l.getName(), l.getName().startsWith("Block "));
		}
	}

	@Test
	public void buyTabItemsAreSightedNotRead()
	{
		// The rune pouch is worth little per point, so with plenty of points it is bought once seen to be missing
		final Account account = rich().withAllSlots();
		Plan plan = SlayerPlanBuilder.build(account, DATA, DATASET, pinned(SlayerMaster.DURADEL, SlayerGoal.XP),
				SlayerMasterAccessTest.maxed()).getPlan();
		Line pouch = line(plan, "Rune pouch");
		assertNotNull(pouch);
		assertEquals(750, pouch.getCost());
		assertTrue(pouch.getDetail(), pouch.getDetail().endsWith("Could not tell if you have one. Open your bank once so the plan can look."));
		assertTrue(pouch.getDetail(), pouch.getDetail().contains("A rune pouch note from the Grand Exchange or 75 Last Man Standing points"));
		// The bank was open and it was not there
		final SlayerShopItem.Sightings bankSeen = new SlayerShopItem.Sightings(Collections.<SlayerShopItem>emptySet(), true);
		pouch = line(SlayerPlanBuilder.build(account, DATA, DATASET, pinned(SlayerMaster.DURADEL, SlayerGoal.XP),
				SlayerMasterAccessTest.maxed(), bankSeen).getPlan(), "Rune pouch");
		assertTrue(pouch.getDetail(), pouch.getDetail().endsWith("Could not tell if you have one. None has been seen in your bank, inventory or worn items."));
		// Seen once: owned, never recommended again
		final SlayerShopItem.Sightings owned = bankSeen.with(Collections.singleton(SlayerShopItem.RUNE_POUCH), false);
		assertNull(line(SlayerPlanBuilder.build(account, DATA, DATASET, pinned(SlayerMaster.DURADEL, SlayerGoal.XP),
				SlayerMasterAccessTest.maxed(), owned).getPlan(), "Rune pouch"));
		// The herb sack only helps profit, and needs 58 Herblore
		assertNull(line(plan, "Herb sack"));
		final Plan profit = SlayerPlanBuilder.build(rich().withAllSlots().withLevel(Skill.HERBLORE, 50), DATA, DATASET,
				pinned(SlayerMaster.KONAR, SlayerGoal.PROFIT), SlayerMasterAccessTest.maxed()).getPlan();
		assertTrue(line(profit, "Herb sack").getDetail().startsWith("Needs 58 Herblore. Holds the herbs that"));
		// The looting bag only on Krystilia's Wilderness tasks
		assertNull(line(profit, "Looting bag"));
		assertNotNull(line(SlayerPlanBuilder.build(rich().withAllSlots(), DATA, DATASET, pinned(SlayerMaster.KRYSTILIA, SlayerGoal.PROFIT),
				SlayerMasterAccessTest.maxed()).getPlan(), "Looting bag"));
	}

	@Test
	public void ownershipIsReadFromTheUnlockBits()
	{
		final Plan plan = konar(rich().withVarbit(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1)).getPlan();
		assertEquals(1, plan.getOwned());
		assertNull(line(plan, "Bigger and Badder"));

		final Plan all = konar(rich().ownsEverything()).getPlan();
		assertEquals(all.getTotal(), all.getOwned());
		for (List<Line> lines : Arrays.asList(all.getBuyNow(), all.getSaveFor(), all.getAvoid()))
		{
			for (Line l : lines)
			{
				assertTrue(l.getName(), l.getName().startsWith("Block ") || l.getName().equals("Rune pouch")
						|| l.getName().equals("Herb sack") || l.getName().equals("Looting bag"));
			}
		}
		assertEquals(0, all.getCosmeticLeft());
	}

	@Test
	public void withoutTheCacheNothingIsJudged()
	{
		final SlayerPlanBuilder.View view = SlayerPlanBuilder.build(rich().withVarbit(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1),
				null, DATASET, KONAR_XP, null);
		assertFalse(view.isFromCache());
		assertEquals(1, view.getPlan().getOwned());
		assertEquals(SlayerReward.values().length, view.getPlan().getTotal());
		assertTrue(view.getPlan().getBuyNow().isEmpty());
		assertTrue(view.getPlan().getAvoid().isEmpty());
		assertEquals(90, view.getPlan().getReserve().getPoints());
	}

	@Test
	public void rewardsOutOfTheShopAreNeverShown()
	{
		final Plan plan = konar(rich()).getPlan();
		for (String gone : Arrays.asList("I Hope You Mith Me", "I Really Mith You", "Ada'mind Some More", "RUUUUUNE"))
		{
			assertNull(gone, line(plan, gone));
		}
		assertEquals(SlayerReward.values().length, plan.getTotal());
	}

	@Test
	public void namesAndCostsComeFromTheCache()
	{
		final Plan plan = konar(rich().withAllSlots()).getPlan();
		assertEquals(50, line(plan, "Bigger and Badder").getCost());
		assertEquals(75, line(plan, "Bleed Me Dry").getCost());
		assertEquals(400, line(plan, "Malevolent Masquerade").getCost());
		assertEquals(100, line(example(SlayerGoal.PROFIT).getPlan(), "Wyver-nother one").getCost());
	}

	@Test
	public void taskRewardsOnlyCountWhereTheMasterGivesTheTask()
	{
		final Plan plan = konar(rich()).getPlan();
		// I Wildy More Slayer only changes Krystilia, though jellies and dust devils are in Konar's pool
		assertNull(line(plan, "I Wildy More Slayer"));
		// Hot Stuff adds TzHaar for other masters only, Chance of Heavy Frost changes Nieve and Duradel only
		assertNull(line(plan, "Hot Stuff"));
		assertNull(line(plan, "Chance of Heavy Frost"));
		// Konar never assigns spiritual creatures, so their extension does nothing there
		assertNull(line(plan, "Spiritual Fervour"));
		// Vampyres need Actual Vampyre Slayer first, so their extension waits for it
		assertNull(line(plan, "More at Stake"));
		// At Kuradal the frost dragon weighting counts. Frost dragons are a good money task.
		final Plan duradel = SlayerPlanBuilder.build(rich().withAllSlots(), DATA, DATASET, pinned(SlayerMaster.DURADEL, SlayerGoal.PROFIT),
				SlayerMasterAccessTest.maxed()).getPlan();
		assertTrue(line(duradel, "Chance of Heavy Frost").getDetail().startsWith("Makes Frost Dragons, an excellent money task, come up more often"));
	}

	@Test
	public void rewardsForATaskOutOfReachWaitForItsLevel()
	{
		// 79 Slayer: nechryael need 80, so their extension waits and says why
		final Plan plan = SlayerPlanBuilder.build(new Account().withVarbit(VarbitID.SLAYER_POINTS, 5000).withAllSlots()
				.withLevel(Skill.SLAYER, 79), DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.with(
				SlayerMasterAccessTest.maxed(), 126, 79)).getPlan();
		final Line nechs = line(plan, "Nechs Please");
		assertNotNull(nechs);
		assertEquals("Needs 80 Slayer. More time on Nechryael, a task the wiki's Slayer guide says to do for XP.", nechs.getDetail());
		assertTrue(plan.getLater().contains(nechs));
	}

	@Test
	public void stopTheWyvernFreesTheSlotItsBlockTakes()
	{
		// For XP fossil wyverns are worth a block at Konar, so Stop the Wyvern is worth the next best block
		final Line wyvern = line(SlayerPlanBuilder.build(rich().withAllSlots(), DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP),
				SlayerMasterAccessTest.maxed()).getPlan(), "Stop the Wyvern");
		assertNotNull(wyvern);
		assertTrue(wyvern.getDetail(), wyvern.getDetail().startsWith("Keeps Fossil Island Wyverns out of every master's list without a block"
				+ " slot. The slot its block takes can then hold "));
	}

	@Test
	public void onlyOpenBlockSlotsCountAsEmpty()
	{
		assertTrue(SlayerPlanBuilder.blockSlotOpen(0, 7, 50, false));
		assertFalse(SlayerPlanBuilder.blockSlotOpen(1, 7, 99, false));
		assertTrue(SlayerPlanBuilder.blockSlotOpen(5, 7, 300, false));
		assertFalse(SlayerPlanBuilder.blockSlotOpen(6, 7, 300, false));
		assertTrue(SlayerPlanBuilder.blockSlotOpen(6, 7, 0, true));

		// 120 quest points open two slots, and one holds gargoyles (task id 46)
		SlayerPlanBuilder.View view = konar(new Account().withQuestPoints(120).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_1, 46));
		assertEquals(Arrays.asList("Gargoyles"), view.getBlocked());
		assertEquals(1, view.getEmptySlots());
		assertEquals("4 more slots need quest points. The next opens at 150 QP. 1 slot needs the elite Lumbridge & Draynor diary",
				view.getLockedSlots());

		// No open slot, so nothing is put forward for blocking
		view = konar(new Account());
		assertEquals(0, view.getEmptySlots());
		assertEquals(0, view.getBlockAdvice().getSlots());
		assertTrue(view.getBlockAdvice().getAdd().isEmpty());
		assertEquals("6 more slots need quest points. The next opens at 50 QP. 1 slot needs the elite Lumbridge & Draynor diary",
				view.getLockedSlots());

		view = konar(new Account().withAllSlots());
		assertEquals(7, view.getEmptySlots());
		assertNull(view.getLockedSlots());
	}

	@Test
	public void blockedShareCountsOnlyTasksYouCanGet()
	{
		// Konar's pool weighs 250, of which 40 sits behind unlocks this account lacks. The six blocks
		// (metal dragons, wyrms, drakes, hydras, cave kraken, fire giants) weigh 63.
		final Account account = new Account().withQuestPoints(300)
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_1, 127).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_2, 111)
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_3, 112).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_4, 113)
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_5, 92).withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_6, 16);
		assertEquals(30, konar(account).getBlockedWeightPercent());
		// Stop the Wyvern takes fossil wyverns (weight 5) out of the pool, until it is toggled off
		account.withVarbit(VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK, 1);
		assertEquals(31, konar(account).getBlockedWeightPercent());
		account.withVarbit(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK, 1);
		assertEquals(30, konar(account).getBlockedWeightPercent());
		// Seeing Red puts red dragons (weight 5) back in
		account.withVarbit(VarbitID.SLAYER_UNLOCK_REDDRAGONS, 1);
		assertEquals(Math.round(6300f / 215), konar(account).getBlockedWeightPercent());
	}

	@Test
	public void signatureSeesEveryPlanInput()
	{
		final SlayerMasterAccess.State maxed = SlayerMasterAccessTest.maxed();
		final int base = SlayerPlanBuilder.signature(new Account(), KONAR_POINTS, maxed);
		assertEquals(base, SlayerPlanBuilder.signature(new Account(), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_UNLOCK_HELM_LEAGUE_6, 1), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withQuestPoints(50), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.LUMBRIDGE_ELITE_REWARD, 1), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_DIARY, 46), KONAR_POINTS, maxed));
		// The goal, the boost, the streaks, the task in progress and master access
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account(), KONAR_XP, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account(),
				new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 90, SlayerGoal.POINTS, BoostInterval.EVERY_50TH), maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_TASKS_COMPLETED, 47), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, 3), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().onTask(SlayerAssigner.TURAEL, 0), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account(), KONAR_POINTS, SlayerMasterAccessTest.guthixSleeps(maxed, 1)));
		// Following the goal, any master's block list, combat level checks and Mortimer's task count
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account(), follow(SlayerGoal.POINTS), maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_BLOCKED_DURADEL_1, 46), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.SLAYER_BLOCKED_MORTIMER_2, 46), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withVarbit(VarbitID.COMBAT_LEVEL_REQS_DISABLED, 1), KONAR_POINTS, maxed));
		// A level a task needs, Slayer or not
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withLevel(Skill.MAGIC, 50), KONAR_POINTS, maxed));
		assertNotEquals(base, SlayerPlanBuilder.signature(new Account().withLevel(Skill.SAILING, 50), KONAR_POINTS, maxed));
		final Account mortimer = new Account();
		mortimer.varps.put(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, 50);
		assertNotEquals(base, SlayerPlanBuilder.signature(mortimer, KONAR_POINTS, maxed));
		// Which task is in progress, for the advice
		final Account task = new Account().onTask(SlayerAssigner.KONAR, 47);
		final int one = SlayerPlanBuilder.signature(task, KONAR_POINTS, maxed);
		task.varps.put(VarPlayerID.SLAYER_TARGET, 42);
		assertNotEquals(one, SlayerPlanBuilder.signature(task, KONAR_POINTS, maxed));
		// Kills on the task in progress do not rebuild the plan
		final Account onTask = new Account().onTask(SlayerAssigner.KONAR, 47);
		final int during = SlayerPlanBuilder.signature(onTask, KONAR_POINTS, maxed);
		onTask.varps.put(VarPlayerID.SLAYER_COUNT, 12);
		assertEquals(during, SlayerPlanBuilder.signature(onTask, KONAR_POINTS, maxed));
	}

	// ---- goals: the points routine and the save-for forecast ----

	@Test
	public void thePointsGoalAddsTheRoutine()
	{
		final Account account = rich().withVarbit(VarbitID.SLAYER_TASKS_COMPLETED, 49);
		final SlayerPlanBuilder.View points = SlayerPlanBuilder.build(account, DATA, DATASET, KONAR_POINTS, SlayerMasterAccessTest.maxed());
		assertEquals(SlayerGoal.POINTS, points.getGoal());
		assertEquals(50, points.getRoutine().getNextTask());
		assertTrue(points.getRoutine().getNext().startsWith("Task 50 is a milestone. Get it from the master who pays most: Konar 270, Kuradal 225"));
		assertEquals(SlayerAssigner.values().length, points.getMasters().size());

		// The other goals keep the master list for later advice but show no routine
		final SlayerPlanBuilder.View xp = SlayerPlanBuilder.build(account, DATA, DATASET, KONAR_XP, SlayerMasterAccessTest.maxed());
		assertNull(xp.getRoutine());
		assertEquals(SlayerAssigner.values().length, xp.getMasters().size());
		// Without master state there is nothing to rank
		assertNull(SlayerPlanBuilder.build(account, DATA, DATASET, KONAR_POINTS, null).getRoutine());
	}

	@Test
	public void saveForLinesSayRoughlyHowManyTasks()
	{
		// 100 points and nothing kept for the points routine: the first save-for line is a known number of points short
		final Account account = new Account().withVarbit(VarbitID.SLAYER_POINTS, 100).withVarbit(VarbitID.SLAYER_TASKS_COMPLETED, 51)
				.withAllSlots();
		final SlayerPlanBuilder.Settings points = new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, SlayerGoal.POINTS,
				BoostInterval.EVERY_10TH);
		final SlayerPointsRoutine.Streaks streaks = new SlayerPointsRoutine.Streaks(51, 0, 0, false);
		final List<SlayerMasterAccess.Option> options = SlayerMasterAccess.options(SlayerMasterAccessTest.maxed());
		Line line = SlayerPlanBuilder.build(account, DATA, DATASET, points, SlayerMasterAccessTest.maxed()).getPlan().getSaveFor().get(0);
		// The routine: tasks 52 to 59 at Aya pay nothing, task 60 at Konar pays 90
		assertEquals(SlayerPointsForecast.withRoutine(line.getNeed(), streaks, options, SlayerPayout.Diaries.NONE, BoostInterval.EVERY_10TH),
				line.getTasks());
		assertTrue(line.getDetail(), line.getDetail().startsWith(line.getNeed() + " more points, about " + line.getTasks() + " tasks. "));
		// Every task at Konar for the other goals: 18 each from task 52
		line = SlayerPlanBuilder.build(account, DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.maxed())
				.getPlan().getSaveFor().get(0);
		assertEquals(SlayerPointsForecast.atMaster(line.getNeed(), SlayerAssigner.KONAR, streaks, SlayerPayout.Diaries.NONE), line.getTasks());
		// Turael pays nothing, so there is no estimate
		line = SlayerPlanBuilder.build(account, DATA, DATASET, pinned(SlayerMaster.TURAEL, SlayerGoal.PROFIT), null).getPlan().getSaveFor().get(0);
		assertTrue(line.getDetail(), line.getDetail().startsWith(line.getNeed() + " more points. "));
		assertEquals(-1, line.getTasks());
	}

	@Test
	public void thePlanNamesTheNpcStandingThere()
	{
		final SlayerPlanBuilder.Settings duradel = new SlayerPlanBuilder.Settings(SlayerMaster.DURADEL, 90, SlayerGoal.XP, BoostInterval.EVERY_10TH);
		assertEquals("Kuradal", SlayerPlanBuilder.build(rich(), DATA, DATASET, duradel, SlayerMasterAccessTest.maxed()).getMasterName());
		assertEquals("Duradel", SlayerPlanBuilder.build(rich(), DATA, DATASET, duradel, SlayerMasterAccessTest.fresh()).getMasterName());
		assertEquals("Duradel", SlayerPlanBuilder.build(rich(), DATA, DATASET, duradel, null).getMasterName());
		assertEquals("Konar", konar(rich()).getMasterName());
	}

	// ---- the plan's master follows the goal ----

	private static SlayerPlanBuilder.Settings follow(SlayerGoal goal)
	{
		return new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 90, goal, BoostInterval.EVERY_10TH, true);
	}

	@Test
	public void thePlanFollowsTheGoalsMaster()
	{
		final Account account = rich().withAllSlots();
		final SlayerMasterAccess.State maxed = SlayerMasterAccessTest.maxed();
		// XP: the best master for XP, by the NPC standing there
		SlayerPlanBuilder.View view = SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.XP), maxed);
		assertEquals(SlayerMaster.DURADEL, view.getMaster());
		assertEquals("Kuradal", view.getMasterName());
		assertTrue(view.isFollowingGoal());
		assertEquals("Kuradal. About 2 in 5 tasks are good XP tasks once you block the worst. Next best: Steve.",
				SlayerPlanText.masterAdvice(view));
		// Profit: Mortimer, whose two block slots the plan then reads
		view = SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.PROFIT), maxed);
		assertEquals(SlayerMaster.MORTIMER, view.getMaster());
		assertEquals(2, view.getBlockAdvice().getSlots());
		assertEquals(120, view.getBlockAdvice().getCost());
		// Slayer points: the master who pays most for milestones, where the routine sends them
		view = SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.POINTS), maxed);
		assertEquals(SlayerMaster.KONAR, view.getMaster());
		assertTrue(view.getRanking().isEmpty());
		assertEquals(SlayerGoal.POINTS, view.getBlockAdvice().getGoal());
		assertTrue(SlayerPlanText.blockAdvice(view), SlayerPlanText.blockAdvice(view)
				.startsWith("Milestone tasks go to Konar, so block the slow ones there.\nBlock next: "));
		// Pinned elsewhere, the note says where milestones go instead
		view = SlayerPlanBuilder.build(account, DATA, DATASET,
				new SlayerPlanBuilder.Settings(SlayerMaster.DURADEL, 90, SlayerGoal.POINTS, BoostInterval.EVERY_10TH), maxed);
		assertEquals(SlayerMaster.KONAR, view.getGoalMaster());
		assertTrue(SlayerPlanText.blockAdvice(view), SlayerPlanText.blockAdvice(view)
				.startsWith("Milestone tasks go to Konar. Pin planner master keeps this list on Kuradal.\n"));
		// The plan master's pool is there for the reward advice
		assertEquals(SlayerAssigner.DURADEL, view.getPool().getMaster());
		// Pinned: the configured master, with the ranking still shown
		view = SlayerPlanBuilder.build(account, DATA, DATASET, KONAR_XP, maxed);
		assertEquals(SlayerMaster.KONAR, view.getMaster());
		assertFalse(view.isFollowingGoal());
		assertTrue(SlayerPlanText.masterAdvice(view), SlayerPlanText.masterAdvice(view)
				.endsWith(" The plan below uses Konar because Pin planner master is on."));
		// Without master state, or with no master who pays for milestones yet, the configured master stays
		assertEquals(SlayerMaster.KONAR, SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.XP), null).getMaster());
		assertEquals(SlayerMaster.KONAR, SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.POINTS),
				SlayerMasterAccessTest.fresh()).getMaster());
		// A new account can only use Turael
		assertEquals(SlayerMaster.TURAEL, SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.XP),
				SlayerMasterAccessTest.fresh()).getMaster());
		// One combat level short of Duradel: Steve now, and the sidebar says what Kuradal needs
		view = SlayerPlanBuilder.build(account, DATA, DATASET, follow(SlayerGoal.XP), SlayerMasterAccessTest.with(maxed, 99, 98));
		assertEquals(SlayerMaster.NIEVE, view.getMaster());
		assertTrue(SlayerPlanText.masterAdvice(view), SlayerPlanText.masterAdvice(view).startsWith("Steve. "));
		assertTrue(SlayerPlanText.masterAdvice(view), SlayerPlanText.masterAdvice(view).endsWith(" Later: Kuradal, who needs combat level 100."));
	}

	@Test
	public void theBlockAdviceUsesTheCurrentBlocks()
	{
		// Konar's first two slots hold dagannoth (task id 35) and cave kraken (task id 92)
		final Account account = new Account().withAllSlots()
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_1, 35)
				.withVarbit(VarbitID.SLAYER_BLOCKED_KONAR_2, 92);
		final SlayerBlockAdvisor.Advice advice = konar(account).getBlockAdvice();
		assertEquals(Collections.singletonList("Cave Kraken"), advice.getKeep());
		assertEquals("Dagannoth", advice.getMisfits().get(0).getName());
		// Seven open slots: six more blocks besides cave kraken
		assertEquals(6, advice.getAdd().size());
		// The sixth block needs dagannoth's slot, so its line says to unblock it first
		final Line last = line(SlayerPlanBuilder.build(account.withVarbit(VarbitID.SLAYER_POINTS, 5000), DATA, DATASET,
				pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.maxed()).getPlan(), "Block " + advice.getAdd().get(5));
		assertTrue(last.getDetail(), last.getDetail().endsWith("Unblock Dagannoth first to free its slot."));
	}

	@Test
	public void anUnlockThatHurtsTheGoalIsBetterTurnedOff()
	{
		final SlayerPlanBuilder.View view = SlayerPlanBuilder.build(rich().withAllSlots().withVarbit(VarbitID.SLAYER_UNLOCK_BOSSES, 1),
				DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.maxed());
		assertTrue(view.getNotes().toString(), view.getNotes().contains("Like a Boss is on, but the tasks it adds lower your XP rate here."
				+ " Boss tasks cannot be blocked. Turning it off at a master stops them, but turning it back on costs 200 points again."));
		// Basilisks are a good money task, so for profit Basilocked stays on
		final SlayerPlanBuilder.View profit = SlayerPlanBuilder.build(rich().withAllSlots().withVarbit(VarbitID.SLAYER_UNLOCK_BASILISK, 1),
				DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.PROFIT), SlayerMasterAccessTest.maxed());
		for (String note : profit.getNotes()) assertFalse(note, note.startsWith("Basilocked"));
		final SlayerPlanBuilder.View xp = SlayerPlanBuilder.build(rich().withAllSlots().withVarbit(VarbitID.SLAYER_UNLOCK_BASILISK, 1),
				DATA, DATASET, pinned(SlayerMaster.KONAR, SlayerGoal.XP), SlayerMasterAccessTest.maxed());
		assertTrue(xp.getNotes().toString(), xp.getNotes().contains("Basilocked is on, but the tasks it adds lower your XP rate here."
				+ " Turning it off at a master stops them, but turning it back on costs 80 points again."));
	}
}
