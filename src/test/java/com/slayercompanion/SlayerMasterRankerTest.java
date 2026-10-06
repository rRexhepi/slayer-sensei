package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.SlayerMasterAccess.State;
import com.slayercompanion.SlayerMasterRanker.Ranked;
import com.slayercompanion.SlayerPayout.Diaries;
import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.Skill;
import org.junit.Test;

/** The master ranking for XP and profit against the live cache dump and the dossier's ratings. */
public class SlayerMasterRankerTest
{
	static final SlayerGameData DATA = SlayerGameData.load(SlayerCacheFixture.get());
	static final Function<String, TaskRating> RATINGS = SlayerPlanBuilder.ratings(new SlayerDataset(new Gson()));

	static int[] levels(int level)
	{
		final int[] levels = new int[Skill.values().length];
		Arrays.fill(levels, level);
		return levels;
	}

	/** 99 in every skill and combat level 126. */
	static SlayerPool.Player maxed(Set<Integer> owned)
	{
		return new SlayerPool.Player(levels(99), 126, false, owned, Collections.<Integer>emptySet());
	}

	static SlayerPool.Player maxed()
	{
		return maxed(Collections.<Integer>emptySet());
	}

	static Set<Integer> everyUnlock()
	{
		final Set<Integer> owned = new HashSet<>();
		for (SlayerGameData.Unlock u : DATA.unlocks()) owned.add(u.getRow());
		return owned;
	}

	/** Seven block slots everywhere, Mortimer's two. */
	static List<Ranked> rank(SlayerGoal goal, SlayerPool.Player player, State access, int mortimerTasks)
	{
		return SlayerMasterRanker.rank(goal, SlayerMasterAccess.options(access),
				a -> SlayerPool.of(a, DATA, player, Collections.<Integer>emptySet(), RATINGS),
				a -> SlayerPlanBuilder.openSlots(SlayerMaster.forAssigner(a), 300, true), access.getDiaries(), mortimerTasks);
	}

	private static double score(List<Ranked> ranking, String npc)
	{
		for (Ranked r : ranking)
		{
			if (r.getNpc().equals(npc)) return r.getScore();
		}
		throw new AssertionError(npc);
	}

	static List<String> npcs(List<Ranked> ranking)
	{
		final List<String> out = new ArrayList<>();
		for (Ranked r : ranking) out.add(r.getNpc());
		return out;
	}

	@Test
	public void kuradalIsBestForXpAsTheSlayerTrainingGuideSays()
	{
		// "Duradel is the best Slayer master to use for the fastest experience, but some players may find that
		// Nieve's (or Steve's) task weighting better suit their preferences" (wiki Slayer training)
		final List<Ranked> xp = rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.maxed(), 0);
		assertEquals(SlayerAssigner.DURADEL, xp.get(0).getMaster());
		assertEquals(Arrays.asList("Kuradal", "Steve", "Mortimer", "Konar"), npcs(xp).subList(0, 4));
		assertEquals("About 2 in 5 tasks are good XP tasks once you block the worst.", xp.get(0).getReason());
		// Once 50 of Mortimer's tasks are done he offers three and gains, still behind Kuradal. The ranking assumes the best
		// blocks and the skip budget, the case where the wiki says Duradel wins: players may do better at Mortimer "if they
		// don't have a lot of slayer points for skipping or a block list fully built out" (wiki Mortimer strategies).
		for (int done : new int[]{50, 100})
		{
			final List<Ranked> later = rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.maxed(), done);
			assertEquals("Kuradal", later.get(0).getNpc());
			assertTrue(score(later, "Mortimer") > score(xp, "Mortimer"));
		}
		// Every task-adding unlock dilutes the pools, Kuradal stays first
		assertEquals("Kuradal", rank(SlayerGoal.XP, maxed(everyUnlock()), SlayerMasterAccessTest.maxed(), 0).get(0).getNpc());
		// Every master a maxed account can use is ranked
		assertEquals(SlayerAssigner.values().length, xp.size());
	}

	@Test
	public void profitFavoursMortimersChoiceThenKonarsKeys()
	{
		final List<Ranked> profit = rank(SlayerGoal.PROFIT, maxed(), SlayerMasterAccessTest.maxed(), 0);
		assertEquals(Arrays.asList("Mortimer", "Konar", "Kuradal"), npcs(profit).subList(0, 3));
		assertEquals("You choose from 2 tasks each time. About 3 in 10 of the tasks you pick are good money tasks once you"
				+ " block the worst. Mortimer's tasks pay no Slayer points.", profit.get(0).getReason());
		assertTrue(profit.get(1).getReason(), profit.get(1).getReason().startsWith("Brimstone keys add to every task. "));
		// Before Fallen From Grace reaches Mortimer, Konar leads
		final State noMortimer = new State(126, 99, true, true, true, 2, true, 40, 0, false, Diaries.NONE);
		assertEquals(Arrays.asList("Konar", "Kuradal"), npcs(rank(SlayerGoal.PROFIT, maxed(), noMortimer, 0)).subList(0, 2));
	}

	@Test
	public void masterEffectsFollowTheWiki()
	{
		assertEquals(SlayerMasterRanker.BRIMSTONE_KEYS_GP, SlayerMasterRanker.bonus(SlayerAssigner.KONAR, SlayerGoal.PROFIT), 0);
		assertEquals(SlayerMasterRanker.KONAR_LOCATION_XP, SlayerMasterRanker.bonus(SlayerAssigner.KONAR, SlayerGoal.XP), 0);
		assertEquals(SlayerMasterRanker.LARRANS_KEYS_GP, SlayerMasterRanker.bonus(SlayerAssigner.KRYSTILIA, SlayerGoal.PROFIT), 0);
		assertEquals(0, SlayerMasterRanker.bonus(SlayerAssigner.KRYSTILIA, SlayerGoal.XP), 0);
		assertEquals(0, SlayerMasterRanker.bonus(SlayerAssigner.DURADEL, SlayerGoal.PROFIT), 0);
		// Mortimer offers two tasks, three once 50 of his are done. Everyone else gives one.
		assertEquals(2, SlayerMasterRanker.choices(SlayerAssigner.MORTIMER, 49));
		assertEquals(3, SlayerMasterRanker.choices(SlayerAssigner.MORTIMER, 50));
		assertEquals(1, SlayerMasterRanker.choices(SlayerAssigner.DURADEL, 500));
	}

	@Test
	public void pointsPayForSkips()
	{
		// 1,000 tasks pay 900 ordinary, 80 tenth, 8 fiftieth, 8 hundredth, 3 two hundred fiftieth and 1 thousandth
		assertEquals(1.775, SlayerMasterRanker.AVERAGE_MULTIPLIER, 1e-9);
		// Duradel: 15 a task, 26.6 with milestones, pays to skip 47% of offers at 30 points each
		assertEquals(26.625 / 56.625, SlayerMasterRanker.skipBudget(SlayerAssigner.DURADEL, Diaries.NONE), 1e-9);
		assertEquals(0, SlayerMasterRanker.skipBudget(SlayerAssigner.TURAEL, Diaries.NONE), 0);
		assertEquals(0, SlayerMasterRanker.skipBudget(SlayerAssigner.MORTIMER, Diaries.NONE), 0);
		// The elite Kourend & Kebos diary raises Konar to 20 a task
		assertTrue(SlayerMasterRanker.skipBudget(SlayerAssigner.KONAR, new Diaries(false, true))
				> SlayerMasterRanker.skipBudget(SlayerAssigner.KONAR, Diaries.NONE));
	}

	@Test
	public void onlyUsableMastersAreRanked()
	{
		// A new account can use Turael alone
		final List<Ranked> fresh = rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.fresh(), 0);
		assertEquals(Collections.singletonList(SlayerAssigner.TURAEL), masters(fresh));
		// After While Guthix Sleeps the NPC there is Aya
		assertEquals("Aya", rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.guthixSleeps(SlayerMasterAccessTest.fresh(), 2), 0)
				.get(0).getNpc());
	}

	@Test
	public void aBetterMasterOutOfReachIsNamedWithWhatHeNeeds()
	{
		// Combat level 99 and 98 Slayer: Duradel needs 100 combat, and the Slayer cape would waive it at 99
		final State combat99 = SlayerMasterAccessTest.with(SlayerMasterAccessTest.maxed(), 99, 98);
		final SlayerPool.Player player = new SlayerPool.Player(levels(98), 99, false, Collections.<Integer>emptySet(),
				Collections.<Integer>emptySet());
		final List<Ranked> xp = rank(SlayerGoal.XP, player, combat99, 0);
		assertEquals("Steve", xp.get(0).getNpc());
		final Ranked later = SlayerMasterRanker.later(SlayerGoal.XP, SlayerMasterAccess.options(combat99),
				a -> SlayerPool.of(a, DATA, player, Collections.<Integer>emptySet(), RATINGS),
				a -> SlayerPlanBuilder.openSlots(SlayerMaster.forAssigner(a), 300, true), Diaries.NONE, 0, xp);
		assertEquals("Kuradal", later.getNpc());
		assertEquals("combat level 100", later.getMissing());
		// A maxed account can use every master, so there is nothing to name
		final List<Ranked> maxed = rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.maxed(), 0);
		assertNull(SlayerMasterRanker.later(SlayerGoal.XP, SlayerMasterAccess.options(SlayerMasterAccessTest.maxed()),
				a -> SlayerPool.of(a, DATA, maxed(), Collections.<Integer>emptySet(), RATINGS), a -> 7, Diaries.NONE, 0, maxed));
		assertNull(maxed.get(0).getMissing());
	}

	@Test
	public void theSlayerPointsGoalHasItsRoutineInstead()
	{
		assertTrue(rank(SlayerGoal.POINTS, maxed(), SlayerMasterAccessTest.maxed(), 0).isEmpty());
	}

	@Test
	public void sharesReadAsWordsNotNumbers()
	{
		assertEquals("About 2 in 5", SlayerMasterRanker.share(0.41));
		assertEquals("About 3 in 10", SlayerMasterRanker.share(0.33));
		assertEquals("About 1 in 10", SlayerMasterRanker.share(0.06));
		assertEquals("Hardly any", SlayerMasterRanker.share(0.04));
		assertEquals("Nearly all", SlayerMasterRanker.share(0.97));
		for (SlayerGoal goal : Arrays.asList(SlayerGoal.XP, SlayerGoal.PROFIT))
		{
			for (Ranked r : rank(goal, maxed(everyUnlock()), SlayerMasterAccessTest.maxed(), 50))
			{
				assertTrue(r.getReason(), r.getReason().endsWith("."));
			}
		}
	}

	@Test
	public void theRankingAssumesTheBestBlocksForTheGoal()
	{
		final Ranked kuradal = rank(SlayerGoal.XP, maxed(), SlayerMasterAccessTest.maxed(), 0).get(0);
		assertEquals(7, kuradal.getBlocks().size());
		// The wiki's task summary says to block each of these for XP at Duradel
		assertTrue(kuradal.getBlocks().toString(), kuradal.getBlocks().containsAll(Arrays.asList("Cave Kraken", "Abyssal Demons",
				"Hellhounds", "Metal Dragons")));
		// No open slot, no blocks
		final List<Ranked> noSlots = SlayerMasterRanker.rank(SlayerGoal.XP, SlayerMasterAccess.options(SlayerMasterAccessTest.maxed()),
				a -> SlayerPool.of(a, DATA, maxed(), Collections.<Integer>emptySet(), RATINGS), a -> 0, Diaries.NONE, 0);
		assertTrue(noSlots.get(0).getBlocks().isEmpty());
		assertTrue(noSlots.get(0).getReason(), noSlots.get(0).getReason().endsWith("XP tasks."));
	}

	private static List<SlayerAssigner> masters(List<Ranked> ranking)
	{
		final List<SlayerAssigner> out = new ArrayList<>();
		for (Ranked r : ranking) out.add(r.getMaster());
		return out;
	}
}
