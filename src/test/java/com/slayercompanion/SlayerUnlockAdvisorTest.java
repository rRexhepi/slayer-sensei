package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.SlayerUnlockAdvisor.Extension;
import com.slayercompanion.SlayerUnlockAdvisor.Situation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** The Advisor line: do it, extend it, skip it or block it, for the goal and with the real costs. */
public class SlayerUnlockAdvisorTest
{
	/** A poor XP task (xp 1) and a good one (xp 4), both quick. */
	private static final SlayerPool.Entry POOR = SlayerPoolTest.entry(1, 6, SlayerPoolTest.rating(1, 1, 4), SlayerPool.Status.OPEN);
	private static final SlayerPool.Entry GOOD = SlayerPoolTest.entry(2, 6, SlayerPoolTest.rating(4, 4, 3), SlayerPool.Status.OPEN);
	/** Named tasks for the Slayer points lines, which name the task. */
	private static final SlayerPool.Entry CAVE_BUGS = new SlayerPool.Entry(3, "Cave Bugs", 8, 10, 30, 20, SlayerPool.Status.OPEN,
			SlayerPoolTest.rating(1, 1, 5));
	private static final SlayerPool.Entry KURASK = new SlayerPool.Entry(4, "Kurask", 6, 100, 100, 100, SlayerPool.Status.OPEN,
			SlayerPoolTest.rating(1, 1, 2));

	private static Situation situation(SlayerGoal goal, SlayerPool.Entry entry, SlayerAssigner master, int points,
			boolean skip, boolean block, boolean freeSlot, Extension extension, int taskNumber, int payout)
	{
		return new Situation(goal, entry, master, master.getDisplayName(), points, skip, block, freeSlot, null, extension,
				taskNumber, payout, false, 50, null);
	}

	@Test
	public void blocksCostWhatTheTasksMasterChargesAndNeedAFreeSlot()
	{
		// The block advice at Konar would block it, a slot is free and 80 points are there
		assertEquals("Block it for 80 points. A very poor XP task at Konar. A block slot is free and you have 100 points.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, 100, true, true, true, null, 12, 18)));
		// Duradel charges 100, so 90 points only pay for a skip until then
		assertEquals("Skip it for 30 points. A very poor XP task at Duradel. You have 90 points. It is worth a block once you have"
						+ " 100 points and a free slot.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, POOR, SlayerAssigner.DURADEL, 90, true, true, true, null, 12, 15)));
		// No free slot: the skip instead
		assertTrue(SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, 500, true, true, false, null, 12, 18))
				.startsWith("Skip it for 30 points."));
		// A block that does not fit the goal can make room
		final Situation swap = new Situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, "Konar", 500, true, true, false, "Dagannoth",
				null, 12, 18, false, 50, null);
		assertEquals("Block it for 80 points once you unblock Dagannoth, which does not fit your goal. A very poor XP task at Konar."
				+ " You have 500 points.", SlayerUnlockAdvisor.advise(swap));
		// Mortimer charges 120 a block
		assertTrue(SlayerUnlockAdvisor.advise(situation(SlayerGoal.PROFIT, POOR, SlayerAssigner.MORTIMER, 500, false, true, true, null, 0, 0))
				.startsWith("Block it for 120 points."));
	}

	@Test
	public void skipsCostThirtyAndNeedThePoints()
	{
		assertEquals("Skip it for 30 points. A very poor money task at Konar. You have 30 points.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.PROFIT, POOR, SlayerAssigner.KONAR, 30, true, false, true, null, 12, 18)));
		assertEquals("Do it. Skipping costs 30 points and you have 12. A very poor money task at Konar.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.PROFIT, POOR, SlayerAssigner.KONAR, 12, true, false, true, null, 12, 18)));
	}

	@Test
	public void goodTasksAreDoneAndTheirExtensionIsNamed()
	{
		final Extension longer = new Extension("Augment My Abbies", 100, false, 0.02);
		assertEquals("Do it. A good XP task at Duradel. Buy Augment My Abbies for 100 points to make the next ones longer.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL, 500, false, false, true, longer, 12, 15)));
		// Owned, or no help to the goal: nothing to buy
		assertEquals("Do it. A good XP task at Duradel.", SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL,
				500, false, false, true, new Extension("Augment My Abbies", 100, true, 0.02), 12, 15)));
		assertEquals("Do it. A good XP task at Duradel.", SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL,
				500, false, false, true, new Extension("Augment My Abbies", 100, false, -0.01), 12, 15)));
		// Bigger and Badder when the task has a superior
		final Situation superior = new Situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL, "Kuradal", 500, false, false, true, null,
				null, 12, 15, true, 50, null);
		assertEquals("Do it. A good XP task at Kuradal. Bigger and Badder, 50 points, adds its superior.", SlayerUnlockAdvisor.advise(superior));
	}

	@Test
	public void slayerPointsProtectTheStreak()
	{
		// Turael and Aya: never change the task, it resets the streak
		final String turael = SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 900, true, false, true,
				null, 7, 0));
		assertEquals("Do it. Task 7 of your streak pays no points but counts toward the next milestone. Changing it with Turael would"
				+ " reset your streak.", turael);
		// Spria cannot change tasks, so there is no warning
		assertEquals("Do it. Task 7 of your streak pays no points but counts toward the next milestone.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.SPRIA, 900, true, false, true, null, 7, 0)));
		// A milestone task is never skipped or blocked, however poor
		assertEquals("Do it. Task 50 is a milestone. Konar pays 270 for it.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, KURASK, SlayerAssigner.KONAR, 900, true, true, true, null, 50, 270)));
		// An ordinary task at a paying master may get a block that keeps milestone tasks quick
		assertEquals("Block it for 80 points. Kurask take longer than the average task at Konar, and a block keeps your milestone tasks"
						+ " quick. A block slot is free and you have 900 points.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, KURASK, SlayerAssigner.KONAR, 900, true, true, true, null, 13, 18)));
		// Never a skip for points: 30 is more than an ordinary task pays
		assertEquals("Do it. Konar pays 18 for task 13.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, POOR, SlayerAssigner.KONAR, 900, true, false, true, null, 13, 18)));
		assertEquals("Do it. Mortimer's tasks do not count toward your streak.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, POOR, SlayerAssigner.MORTIMER, 900, true, true, true, null, 0, 0)));
	}

	@Test
	public void aSlowFillerTaskMayGetABlock()
	{
		// The block advice at Turael's spot would block it, a slot is free and 40 points are there. A block is no task change.
		assertEquals("Block it for 40 points. Cave Bugs take longer than the average task at Turael, and a block keeps your filler tasks"
						+ " quick. A block slot is free and you have 40 points. A block skips it without penalty, but changing it with Turael"
						+ " would reset your streak.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 40, false, true, true, null, 7, 0)));
		// Spria shares the list and cannot change tasks
		assertEquals("Block it for 40 points. Cave Bugs take longer than the average task at Spria, and a block keeps your filler tasks"
						+ " quick. A block slot is free and you have 500 points.",
				SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.SPRIA, 500, false, true, true, null, 7, 0)));
		// Too few points, no free slot, or a milestone number: the streak rules stand
		final String done = "Do it. Task 7 of your streak pays no points but counts toward the next milestone. Changing it with Turael would"
				+ " reset your streak.";
		assertEquals(done, SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 39, false, true, true,
				null, 7, 0)));
		assertEquals(done, SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 900, false, true, false,
				null, 7, 0)));
		assertTrue(SlayerUnlockAdvisor.advise(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 900, false, true, true, null, 10,
				0)).startsWith("Do it. Task 10 "));
	}

	@Test
	public void unknownTasksGetNoLine()
	{
		assertNull(SlayerUnlockAdvisor.advise(null));
		assertNull(SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, null, SlayerAssigner.KONAR, 100, false, false, true, null, 1, 0)));
		final SlayerPool.Entry unrated = SlayerPoolTest.entry(3, 5, null, SlayerPool.Status.OPEN);
		assertNull(SlayerUnlockAdvisor.advise(situation(SlayerGoal.XP, unrated, SlayerAssigner.KONAR, 100, false, false, true, null, 1, 0)));
	}

	@Test
	public void theCompactItemsSayTheSameInAFewWords()
	{
		assertEquals(Collections.singletonList("Block (80)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, 100, true, true, true, null, 12, 18)));
		assertEquals(Arrays.asList("Skip (30)", "Later: block (100)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, POOR, SlayerAssigner.DURADEL, 90, true, true, true, null, 12, 15)));
		final Situation swap = new Situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, "Konar", 500, true, true, false, "Dagannoth",
				null, 12, 18, false, 50, null);
		assertEquals(Arrays.asList("Unblock Dagannoth", "Then block (80)"), SlayerUnlockAdvisor.compact(swap));
		assertEquals(Collections.singletonList("Skip (30)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.PROFIT, POOR, SlayerAssigner.KONAR, 30, true, false, true, null, 12, 18)));
		assertEquals(Arrays.asList("Do it", "Skip costs 30, you have 12"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.PROFIT, POOR, SlayerAssigner.KONAR, 12, true, false, true, null, 12, 18)));
		assertEquals(Arrays.asList("Do it", "Later: block (80)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, POOR, SlayerAssigner.KONAR, 50, false, true, true, null, 12, 18)));
		// The one purchase that makes the task better
		final Extension longer = new Extension("Augment My Abbies", 100, false, 0.02);
		assertEquals(Arrays.asList("Do it", "Buy Augment My Abbies (100)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL, 500, false, false, true, longer, 12, 15)));
		assertEquals(Collections.singletonList("Do it"), SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL,
				500, false, false, true, new Extension("Augment My Abbies", 100, true, 0.02), 12, 15)));
		final Situation superior = new Situation(SlayerGoal.XP, GOOD, SlayerAssigner.DURADEL, "Kuradal", 500, false, false, true, null,
				null, 12, 15, true, 50, null);
		assertEquals(Arrays.asList("Do it", "Buy Bigger and Badder (50)"), SlayerUnlockAdvisor.compact(superior));
		// Slayer points: the task's number and what it pays
		assertEquals(Collections.singletonList("Do it, #7 pays 0"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 900, true, false, true, null, 7, 0)));
		assertEquals(Collections.singletonList("Do it, #50 pays 270"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, KURASK, SlayerAssigner.KONAR, 900, true, true, true, null, 50, 270)));
		assertEquals(Collections.singletonList("Block (80)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, KURASK, SlayerAssigner.KONAR, 900, true, true, true, null, 13, 18)));
		assertEquals(Collections.singletonList("Block (40)"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, CAVE_BUGS, SlayerAssigner.TURAEL, 40, false, true, true, null, 7, 0)));
		assertEquals(Collections.singletonList("Do it, #13 pays 18"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, POOR, SlayerAssigner.KONAR, 900, true, false, true, null, 13, 18)));
		assertEquals(Collections.singletonList("Do it, not on your streak"),
				SlayerUnlockAdvisor.compact(situation(SlayerGoal.POINTS, POOR, SlayerAssigner.MORTIMER, 900, true, true, true, null, 0, 0)));
		// XP or profit from Aya
		assertEquals(Collections.singletonList("Do it"), SlayerUnlockAdvisor.compact(new Situation(SlayerGoal.XP, CAVE_BUGS,
				SlayerAssigner.TURAEL, "Aya", 500, true, true, true, null, null, 4, 0, false, 50, "Kuradal")));
		// No line, no items
		assertTrue(SlayerUnlockAdvisor.compact(null).isEmpty());
		assertTrue(SlayerUnlockAdvisor.compact(situation(SlayerGoal.XP, null, SlayerAssigner.KONAR, 100, false, false, true, null, 1, 0)).isEmpty());
	}

	@Test
	public void theWikisAdviceInCompactItems()
	{
		final SlayerPool.Entry abyssal = new SlayerPool.Entry(42, "Abyssal Demons", 12, 130, 200, 200, SlayerPool.Status.OPEN,
				SlayerPoolTest.rating(5, 3, 3), 0, com.slayercompanion.data.TaskRating.XpAdvice.SKIP);
		assertEquals(Collections.singletonList("Skip (30)"), SlayerUnlockAdvisor.compact(new Situation(SlayerGoal.XP, abyssal,
				SlayerAssigner.DURADEL, "Kuradal", 500, true, false, true, null, null, 12, 15, false, 50, null)));
		assertEquals(Arrays.asList("Do it", "Pick another next time"), SlayerUnlockAdvisor.compact(new Situation(SlayerGoal.XP, abyssal,
				SlayerAssigner.MORTIMER, "Mortimer", 500, false, false, true, null, null, 0, 0, false, 50, null)));
	}

	/** The compact items are short and plain for every goal, master and branch the punctuation test covers. */
	@Test
	public void compactItemsAreShortAndPlain()
	{
		for (SlayerGoal goal : SlayerGoal.values())
		{
			for (SlayerAssigner master : SlayerAssigner.values())
			{
				for (boolean flag : new boolean[]{true, false})
				{
					final List<String> items = SlayerUnlockAdvisor.compact(situation(goal, flag ? POOR : GOOD, master, flag ? 500 : 10, flag, flag,
							flag, new Extension("Augment My Abbies", 100, false, 0.05), 10, 50));
					assertFalse(items.isEmpty());
					for (String item : items)
					{
						assertTrue(item, item.length() <= 34);
						assertFalse(item, item.contains("."));
					}
				}
			}
		}
	}

	/** User-facing text: short plain sentences without em dashes, en dashes or semicolons. */
	@Test
	public void linesEndWithAFullStop()
	{
		for (SlayerGoal goal : SlayerGoal.values())
		{
			for (SlayerAssigner master : SlayerAssigner.values())
			{
				for (boolean flag : new boolean[]{true, false})
				{
					final String line = SlayerUnlockAdvisor.advise(situation(goal, flag ? POOR : GOOD, master, flag ? 500 : 10, flag, flag,
							flag, new Extension("Ext", 100, false, 0.05), 10, 50));
					assertTrue(line, line.endsWith("."));
				}
			}
		}
	}

	// ---- built from the account, against the live cache dump ----

	private static final SlayerGameData DATA = SlayerMasterRankerTest.DATA;
	private static final SlayerDataset DATASET = new SlayerDataset(new Gson());

	private static SlayerPointsPlannerTest.Account onTask(SlayerAssigner master, int taskId, int streak)
	{
		final SlayerPointsPlannerTest.Account account = new SlayerPointsPlannerTest.Account().withVarbit(VarbitID.SLAYER_POINTS, 521)
				.withQuestPoints(300).withVarbit(VarbitID.LUMBRIDGE_ELITE_REWARD, 1).onTask(master, streak);
		account.varps.put(VarPlayerID.SLAYER_TARGET, taskId);
		return account;
	}

	private static Situation built(SlayerPointsPlannerTest.Account account, SlayerGoal goal)
	{
		return SlayerPlanBuilder.build(account, DATA, DATASET, new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, goal,
				BoostInterval.EVERY_10TH), SlayerMasterAccessTest.maxed()).getTask();
	}

	@Test
	public void theExtensionComesFromTheCache()
	{
		// Abyssal demons (task id 42) at Kuradal: Augment My Abbies, read from SlayerTask COL_EXTENSION_MIN_MAX
		final Situation s = built(onTask(SlayerAssigner.DURADEL, 42, 11), SlayerGoal.XP);
		assertNotNull(s.getExtension());
		assertEquals("Augment My Abbies", s.getExtension().getName());
		assertEquals(100, s.getExtension().getCost());
		assertFalse(s.getExtension().isOwned());
		assertEquals("Kuradal", s.getNpc());
		assertEquals(12, s.getTaskNumber());
		// Owned once its bit is set
		final Situation owned = built(onTask(SlayerAssigner.DURADEL, 42, 11).withVarbit(VarbitID.SLAYER_LONGER_ABYSSALDEMONS, 1), SlayerGoal.XP);
		assertTrue(owned.getExtension().isOwned());
		// Gryphons are lengthened by a number of kills added to the master's own
		assertEquals("Gryphon and on", built(onTask(SlayerAssigner.DURADEL, 128, 11), SlayerGoal.XP).getExtension().getName());
		// Kurask has no extension
		assertNull(built(onTask(SlayerAssigner.DURADEL, 45, 11), SlayerGoal.XP).getExtension());
	}

	@Test
	public void theLineFollowsTheGoalAtTheTasksMaster()
	{
		// Cave kraken (task id 92) at Kuradal, every slot open and empty: block it for XP
		final String xp = SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, 92, 11), SlayerGoal.XP));
		assertTrue(xp, xp.startsWith("Block it for 100 points. The wiki's Slayer guide says to skip or block it for XP."));
		// Smoke devils (task id 95) are an excellent XP task the wiki says to do
		final String smoke = SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, 95, 11), SlayerGoal.XP));
		assertTrue(smoke, smoke.startsWith("Do it. The wiki's Slayer guide says to do it for XP."));
		// Fire giants (task id 16): the wiki's advice on them is conditional, so the rating speaks
		assertEquals("Do it. A fair XP task at Kuradal.", SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, 16, 11), SlayerGoal.XP)));
		// For Slayer points a milestone task at Konar is done, whatever it is
		assertEquals("Do it. Task 50 is a milestone. Konar pays 270 for it.",
				SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.KONAR, 45, 49), SlayerGoal.POINTS)));
		// And a task from Aya is never changed
		final String aya = SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.TURAEL, 45, 3), SlayerGoal.POINTS));
		assertTrue(aya, aya.endsWith("Changing it with Aya would reset your streak."));
	}

	@Test
	public void aSlowFillerTaskFromAyaGetsABlockWhenTheRoutinePays()
	{
		// Cave bugs (task id 63) from Aya on task 4, every slot open and empty: the routine's model blocks them at Aya
		final Situation bugs = built(onTask(SlayerAssigner.TURAEL, 63, 3), SlayerGoal.POINTS);
		assertTrue(bugs.isWorthBlocking());
		assertEquals("Block it for 40 points. Cave Bugs take longer than the average task at Aya, and a block keeps your filler tasks quick."
				+ " A block slot is free and you have 521 points. A block skips it without penalty, but changing it with Aya would reset"
				+ " your streak.", SlayerUnlockAdvisor.advise(bugs));
		// Bats (task id 8) are among Aya's shorter tasks, so they are done
		final Situation bats = built(onTask(SlayerAssigner.TURAEL, 8, 3), SlayerGoal.POINTS);
		assertFalse(bats.isWorthBlocking());
		assertEquals("Do it. Task 4 of your streak pays no points but counts toward the next milestone. Changing it with Aya would reset"
				+ " your streak.", SlayerUnlockAdvisor.advise(bats));
		// Every slot of the shared list full: nothing to block it with
		final SlayerPointsPlannerTest.Account full = onTask(SlayerAssigner.TURAEL, 63, 3);
		final int[] tasks = {62, 6, 75, 76, 2, 3, 13};
		final int[] slots = SlayerMaster.TURAEL.getBlockVarbits();
		for (int i = 0; i < slots.length; i++) full.withVarbit(slots[i], tasks[i]);
		assertTrue(SlayerUnlockAdvisor.advise(built(full, SlayerGoal.POINTS)).startsWith("Do it. Task 4 "));
	}

	@Test
	public void quickFillerTasksAreDone()
	{
		// A cows task (task id 6) from Aya under Slayer points: a quick cannonable task with a close teleport
		assertEquals("Do it. Task 4 of your streak pays no points but counts toward the next milestone. Changing it with Aya would reset"
				+ " your streak.", SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.TURAEL, 6, 3), SlayerGoal.POINTS)));
	}

	@Test
	public void extensionAdviceOnlyForTasksRatedGood()
	{
		// Kuradal for XP, no blocks: abyssal demons (42) and black demons (30) are fair and the wiki says to block them
		for (int id : new int[]{42, 30})
		{
			final String line = SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, id, 11), SlayerGoal.XP));
			assertFalse(line, line.contains("Buy "));
		}
		// Araxytes (124) are excellent, and the wiki's own example of a task to extend
		assertEquals("Do it. The wiki's Slayer guide says to do it for XP. Buy More eyes than sense for 150 points to make the next ones longer.",
				SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, 124, 11), SlayerGoal.XP)));
		// Profit: never Pedal to the Metals on metal dragons (127), and nothing for a task rated fair or lower
		assertFalse(SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.DURADEL, 127, 11), SlayerGoal.PROFIT)).contains("Pedal to the Metals"));
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, DATA, SlayerMasterRankerTest.maxed(), java.util.Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		int fair = 0;
		for (SlayerPool.Entry e : pool.open())
		{
			if (DATA.extensionOf(e.getTaskRow()) == null || !e.isRated() || e.rating(SlayerGoal.PROFIT) >= SlayerSpend.GOOD) continue;
			fair++;
			final Situation s = built(onTask(SlayerAssigner.DURADEL, idOf(e.getTaskRow()), 11), SlayerGoal.PROFIT);
			assertTrue(e.getName(), s.getExtension() == null || s.getExtension().getGain() <= 0);
			final String line = SlayerUnlockAdvisor.advise(s);
			assertFalse(e.getName() + ": " + line, line.contains("Buy "));
		}
		assertTrue(fair > 5);
	}

	private static int idOf(int row)
	{
		for (int id = 0; id < 400; id++)
		{
			if (DATA.taskRowForId(id) == row) return id;
		}
		throw new AssertionError("No task id for row " + row);
	}

	@Test
	public void theWikisXpAdviceDecidesAndIsCited()
	{
		final SlayerPool.Entry abyssal = new SlayerPool.Entry(42, "Abyssal Demons", 12, 130, 200, 200, SlayerPool.Status.OPEN,
				SlayerPoolTest.rating(5, 3, 3), 0, com.slayercompanion.data.TaskRating.XpAdvice.SKIP);
		final SlayerPool.Entry trolls = new SlayerPool.Entry(18, "Trolls", 6, 130, 200, 200, SlayerPool.Status.OPEN,
				SlayerPoolTest.rating(3, 2, 3), 0, com.slayercompanion.data.TaskRating.XpAdvice.DO);
		// A task the wiki says to skip is blocked or skipped, with the guide cited rather than a rating word
		assertEquals("Block it for 100 points. The wiki's Slayer guide says to skip or block it for XP. A block slot is free and you have"
				+ " 500 points.", SlayerUnlockAdvisor.advise(new Situation(SlayerGoal.XP, abyssal, SlayerAssigner.DURADEL, "Kuradal", 500, true,
				true, true, null, null, 12, 15, false, 50, null)));
		assertEquals("Skip it for 30 points. The wiki's Slayer guide says to skip or block it for XP. You have 500 points.",
				SlayerUnlockAdvisor.advise(new Situation(SlayerGoal.XP, abyssal, SlayerAssigner.DURADEL, "Kuradal", 500, true, false, true, null,
						null, 12, 15, false, 50, null)));
		// A task it says to do is done, even if a block or skip were on offer
		assertEquals("Do it. The wiki's Slayer guide says to do it for XP.", SlayerUnlockAdvisor.advise(new Situation(SlayerGoal.XP, trolls,
				SlayerAssigner.DURADEL, "Kuradal", 500, true, true, true, null, null, 12, 15, false, 50, null)));
		// Mortimer takes no skips but offers a choice
		assertEquals("Do it. The wiki's Slayer guide says to skip or block it for XP. Mortimer offers a choice, so pick another next time.",
				SlayerUnlockAdvisor.advise(new Situation(SlayerGoal.XP, abyssal, SlayerAssigner.MORTIMER, "Mortimer", 500, false, false, true,
						null, null, 0, 0, false, 50, null)));
		// For profit the rating speaks
		assertEquals("Do it. A fair money task at Kuradal.", SlayerUnlockAdvisor.advise(new Situation(SlayerGoal.PROFIT, abyssal,
				SlayerAssigner.DURADEL, "Kuradal", 500, false, false, true, null, null, 12, 15, false, 50, null)));
	}

	@Test
	public void fillerMastersTasksAreDoneForXpAndProfit()
	{
		// Every task Turael, Aya or Spria gives is poor for XP and profit, so a block or skip there buys nothing
		final Situation aya = new Situation(SlayerGoal.XP, CAVE_BUGS, SlayerAssigner.TURAEL, "Aya", 500, true, true, true, null, null, 4, 0,
				false, 50, "Kuradal");
		assertEquals("Do it. Aya's tasks are quick fillers for a Slayer points streak. Kuradal is the best master for XP.",
				SlayerUnlockAdvisor.advise(aya));
		final Situation spria = new Situation(SlayerGoal.PROFIT, CAVE_BUGS, SlayerAssigner.SPRIA, "Spria", 500, true, true, true, null, null, 4,
				0, false, 50, null);
		assertEquals("Do it. Spria's tasks are quick fillers for a Slayer points streak.", SlayerUnlockAdvisor.advise(spria));
		// Built from the account: banshees (task id 38) from Aya, with the goal's best master by name
		assertEquals("Do it. Aya's tasks are quick fillers for a Slayer points streak. Kuradal is the best master for XP.",
				SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.TURAEL, 38, 3), SlayerGoal.XP)));
		assertEquals("Do it. Aya's tasks are quick fillers for a Slayer points streak. Mortimer is the best master for profit.",
				SlayerUnlockAdvisor.advise(built(onTask(SlayerAssigner.TURAEL, 38, 3), SlayerGoal.PROFIT)));
	}

	@Test
	public void aFullBlockListLeavesTheSkip()
	{
		// Seven blocks at Kuradal that fit XP: the slot is not free
		final SlayerPointsPlannerTest.Account account = onTask(SlayerAssigner.DURADEL, 45, 11);
		final List<Integer> blocks = Arrays.asList(92, 89, 106, 111, 112, 72, 46);
		final int[] slots = SlayerMaster.DURADEL.getBlockVarbits();
		for (int i = 0; i < slots.length; i++) account.withVarbit(slots[i], blocks.get(i));
		final Situation s = built(account, SlayerGoal.XP);
		assertFalse(s.isFreeSlot());
	}
}
