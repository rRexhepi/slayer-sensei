package com.slayercompanion;

import com.slayercompanion.SlayerPointsPlanner.Candidate;
import com.slayercompanion.SlayerPointsPlanner.Plan;
import com.slayercompanion.SlayerPointsPlanner.Reserve;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads purchases, block lists, levels, streaks and the task in progress from the client and feeds the spend plan,
 * the points routine and the task advice.
 */
final class SlayerPlanBuilder
{
	/** Skips in a row the reserve is ever sized for. */
	static final int MAX_SKIPS_IN_A_ROW = 5;

	/** Everything the plan reads from the client, behind an interface so a plan can be built from test data. */
	interface Account
	{
		int varp(int varp);

		int varbit(int varbit);

		/**
		 * Real levels by stat id, read as the game's whole array. Its order is that of {@link Skill} and of the cache's
		 * stat ids, and slots past the last stat stay 0.
		 */
		int[] levels();

		static Account of(Client client)
		{
			return new Account()
			{
				@Override
				public int varp(int varp)
				{
					return client.getVarpValue(varp);
				}

				@Override
				public int varbit(int varbit)
				{
					return client.getVarbitValue(varbit);
				}

				@Override
				public int[] levels()
				{
					final int[] levels = client.getRealSkillLevels();
					return levels == null ? new int[0] : levels.clone();
				}
			};
		}
	}

	/** The player's planner choices from config. */
	@Value
	@AllArgsConstructor
	static class Settings
	{
		/** Whose task pool and block list the plan reads when it does not follow the goal. */
		SlayerMaster master;
		/** The fewest points the plan keeps for skips, from config. It keeps more when the skip model needs more. */
		int reserve;
		SlayerGoal goal;
		BoostInterval boost;
		/**
		 * The plan uses the goal's master: the best master for XP or profit, the master who pays most for
		 * milestones for Slayer points. False pins it to {@link #master}.
		 */
		boolean followGoal;

		/** A plan pinned to {@code master}. */
		Settings(SlayerMaster master, int reserve, SlayerGoal goal, BoostInterval boost)
		{
			this(master, reserve, goal, boost, false);
		}
	}

	@Value
	static class View
	{
		Plan plan;
		/** Whose pool and block list the plan reads: the goal's master, or the pinned one. */
		SlayerMaster master;
		List<String> blocked;
		/** Open block slots with nothing in them. Slots still locked behind quest points or the diary do not count. */
		int emptySlots;
		/** How the locked block slots open, or null when every slot is open. */
		String lockedSlots;
		int blockedWeightPercent;
		/**
		 * The plan master's pool as the player gets it, with the current blocks: every task's weight, whether
		 * it can be assigned and its ratings. Null without the cache tables.
		 */
		SlayerPool pool;
		/** Which tasks to block at the plan's master for the goal, or null without the cache tables. */
		SlayerBlockAdvisor.Advice blockAdvice;
		List<String> notes;
		/** The notes in a few words for the compact sidebar, one per note in the same order. */
		List<String> noteItems;
		boolean fromCache;
		SlayerGoal goal;
		/** Every master spot with the NPC there and whether the player can use it. Empty without master state. */
		List<SlayerMasterAccess.Option> masters;
		/** The Slayer points routine, or null for the other goals. */
		SlayerPointsRoutine.Advice routine;
		/** The masters the player can use, best first for XP or profit. Empty for Slayer points. */
		List<SlayerMasterRanker.Ranked> ranking;
		/** A master the player cannot use yet who would beat all of them, or null. */
		SlayerMasterRanker.Ranked later;
		/**
		 * The master the goal points to: the best for XP or profit, the best milestone payer for Slayer points.
		 * Null when nothing is known yet. The plan uses it unless Pin planner master is on.
		 */
		SlayerMaster goalMaster;
		/** The task in progress at its master, or null without a task or without the cache tables. */
		SlayerUnlockAdvisor.Situation task;
		/** The "Assigned by" line for the task in progress from the cache, or null to keep the dossier's text. */
		String assignedBy;
		/**
		 * On the Slayer points routine, the filler tasks' block list at Turael's spot, valued in the same plan. Null for
		 * the other goals, and when the plan's master is not where the routine's milestone tasks go.
		 */
		Fillers fillers;

		/** The NPC at the plan master's spot, e.g. Kuradal once she has replaced Duradel. */
		String getMasterName()
		{
			return npc(master);
		}

		/** The NPC at the goal master's spot, or null without one. */
		String getGoalMasterName()
		{
			return goalMaster == null ? null : npc(goalMaster);
		}

		/** Whether the plan reads the goal's master, rather than a pinned one that differs. */
		boolean isFollowingGoal()
		{
			return goalMaster != null && goalMaster == master;
		}

		/** The "Advisor" line for the task in progress, or null. */
		String getTaskAdvice()
		{
			return SlayerUnlockAdvisor.advise(task);
		}

		/** The same advice as the compact sidebar's items. Empty without a line. */
		List<String> getTaskAdviceItems()
		{
			return SlayerUnlockAdvisor.compact(task);
		}

		private String npc(SlayerMaster m)
		{
			final SlayerMasterAccess.Option option = SlayerMasterAccess.find(masters, m.assigner());
			return option == null ? m.toString() : option.getNpc();
		}
	}

	/** The routine's filler list at Turael's spot, which Aya and Spria share, as the sidebar shows it. */
	@Value
	static class Fillers
	{
		/** The NPC at Turael's spot, e.g. Aya. */
		String npc;
		List<String> blocked;
		/** Open block slots with nothing in them. */
		int emptySlots;
		/** How the locked block slots open, or null when every slot is open. */
		String lockedSlots;
		int blockedWeightPercent;
		/** Which filler tasks to block for Slayer points. */
		SlayerBlockAdvisor.Advice blockAdvice;
	}

	/** A master's block list as the player has it. */
	private static final class BlockList
	{
		final Set<Integer> rows = new HashSet<>();
		final List<Integer> inOrder = new ArrayList<>();
		final List<String> names = new ArrayList<>();
		int emptySlots;
	}

	private SlayerPlanBuilder()
	{
	}

	/** Cheap per-tick fingerprint of every input, so the plan is only rebuilt when something changed. */
	static int signature(Client client, Settings settings, SlayerMasterAccess.State access, SlayerShopItem.Sightings items)
	{
		return 31 * signature(Account.of(client), settings, access) + items.hashCode();
	}

	static int signature(Account account, Settings settings, SlayerMasterAccess.State access)
	{
		final SlayerMaster master = settings.getMaster();
		int h = 31 * master.ordinal() + settings.getReserve();
		h = 31 * h + settings.getGoal().ordinal();
		h = 31 * h + settings.getBoost().ordinal();
		h = 31 * h + (settings.isFollowGoal() ? 1 : 0);
		h = 31 * h + (access == null ? 0 : access.hashCode());
		h = 31 * h + account.varbit(VarbitID.SLAYER_TASKS_COMPLETED);
		h = 31 * h + account.varbit(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
		h = 31 * h + account.varp(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
		h = 31 * h + account.varbit(VarbitID.SLAYER_MASTER);
		// Only whether a task is in progress and which task, not the kill count, so kills do not rebuild the plan
		h = 31 * h + (account.varp(VarPlayerID.SLAYER_COUNT) > 0 ? 1 : 0);
		h = 31 * h + account.varp(VarPlayerID.SLAYER_TARGET);
		h = 31 * h + account.varbit(VarbitID.SLAYER_POINTS);
		// Task requirements reach past Slayer, e.g. Magic for cave kraken and Sailing for frost dragons
		h = 31 * h + Arrays.hashCode(account.levels());
		h = 31 * h + account.varbit(VarbitID.COMBAT_LEVEL_REQS_DISABLED);
		h = 31 * h + account.varbit(VarbitID.SLAYER_TOGGLEOFF_SUPERIORMOBS);
		h = 31 * h + account.varbit(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK);
		// Every reward's ownership bit, cached or fallback, lives in these three varps
		for (int varp : SlayerGameData.UNLOCK_VARPS)
		{
			h = 31 * h + account.varp(varp);
		}
		h = 31 * h + account.varp(VarPlayerID.QP);
		h = 31 * h + account.varbit(VarbitID.LUMBRIDGE_ELITE_REWARD);
		// The goal can move the plan to any master, so every block list counts
		for (SlayerMaster m : SlayerMaster.values())
		{
			for (int varbit : m.getBlockVarbits())
			{
				h = 31 * h + account.varbit(varbit);
			}
		}
		return h;
	}

	static View build(Client client, SlayerGameData data, SlayerDataset dataset, Settings settings, SlayerMasterAccess.State access,
			SlayerShopItem.Sightings items)
	{
		return build(Account.of(client), data, dataset, settings, access, items);
	}

	/** A plan that has seen none of the Buy tab's items. */
	static View build(Account account, SlayerGameData data, SlayerDataset dataset, Settings settings, SlayerMasterAccess.State access)
	{
		return build(account, data, dataset, settings, access, SlayerShopItem.Sightings.NONE);
	}

	/**
	 * {@code access} is what {@link SlayerMasterAccess#read} returns. Without it there is no routine, no
	 * master list or ranking, and the forecast assumes every task is done at the plan's master.
	 *
	 * @param items which Buy tab items have been seen on this character
	 */
	static View build(Account account, SlayerGameData data, SlayerDataset dataset, Settings settings, SlayerMasterAccess.State access,
			SlayerShopItem.Sightings items)
	{
		final SlayerGoal goal = settings.getGoal();
		final int points = account.varbit(VarbitID.SLAYER_POINTS);
		final int questPoints = account.varp(VarPlayerID.QP);
		final boolean diaryDone = account.varbit(VarbitID.LUMBRIDGE_ELITE_REWARD) != 0;

		final Set<Integer> owned = ownedRewards(account, data);
		final List<SlayerMasterAccess.Option> masters = access == null
				? Collections.<SlayerMasterAccess.Option>emptyList() : SlayerMasterAccess.options(access);
		final SlayerPayout.Diaries diaries = access == null ? SlayerPayout.Diaries.NONE : access.getDiaries();
		final SlayerPool.Player player = player(account, data, owned, access);
		final Function<String, TaskRating> ratings = ratings(dataset);
		final Function<String, SlayerTask> dossier = dossier(dataset);

		// Masters are ranked by what their pool could be, so each gets the best blocks for its open slots
		final Function<SlayerAssigner, SlayerPool> rankPools = a -> SlayerPool.of(a, data, player, Collections.<Integer>emptySet(), ratings);
		final ToIntFunction<SlayerAssigner> rankSlots = a -> openSlots(SlayerMaster.forAssigner(a), questPoints, diaryDone);
		final int mortimerTasks = account.varp(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
		final List<SlayerMasterRanker.Ranked> ranking = data == null || access == null
				? Collections.<SlayerMasterRanker.Ranked>emptyList()
				: SlayerMasterRanker.rank(goal, masters, rankPools, rankSlots, diaries, mortimerTasks);
		final SlayerMasterRanker.Ranked later = data == null || access == null ? null
				: SlayerMasterRanker.later(goal, masters, rankPools, rankSlots, diaries, mortimerTasks, ranking);
		final SlayerMaster goalMaster = goalMaster(goal, ranking, masters, diaries, settings.getBoost());
		final SlayerMaster master = settings.isFollowGoal() && goalMaster != null ? goalMaster : settings.getMaster();
		final SlayerAssigner planMaster = master.assigner();
		final BlockList blocks = blockList(account, data, master, questPoints, diaryDone);

		int blockedWeightPercent = -1;
		SlayerPool planPool = null;
		SlayerBlockAdvisor.Advice blockAdvice = null;
		if (data != null)
		{
			planPool = SlayerPool.of(planMaster, data, player, blocks.rows, ratings);
			blockedWeightPercent = blockedWeightPercent(planPool);
			blockAdvice = SlayerBlockAdvisor.advise(master, planPool, goal, openSlots(master, questPoints, diaryDone), blocks.inOrder);
		}

		final SlayerPointsRoutine.Streaks streaks = streaks(account);
		final boolean routine = goal == SlayerGoal.POINTS && access != null;
		// On the routine every task but the milestone goes to Turael's spot, so that block list is valued in the same plan
		SlayerSpend.Routine cycle = null;
		Fillers fillers = null;
		if (routine && planPool != null && takesMilestones(planMaster))
		{
			final SlayerMaster list = SlayerMaster.TURAEL;
			final BlockList fillerBlocks = blockList(account, data, list, questPoints, diaryDone);
			final SlayerPool fillerPool = SlayerPool.of(SlayerAssigner.TURAEL, data, player, fillerBlocks.rows, ratings);
			final SlayerBlockAdvisor.Advice fillerAdvice = SlayerBlockAdvisor.advise(list, fillerPool, goal,
					openSlots(list, questPoints, diaryDone), fillerBlocks.inOrder);
			final String fillerNpc = npc(masters, SlayerAssigner.TURAEL);
			final double milestonePoints = SlayerPayout.basePoints(planMaster, diaries)
					* SlayerPayout.averageMultiplier(settings.getBoost().getEvery());
			cycle = new SlayerSpend.Routine(settings.getBoost().getEvery() - 1, Math.max(1, milestonePoints), fillerNpc, fillerPool,
					fillerBlocks.emptySlots, fillerAdvice);
			fillers = new Fillers(fillerNpc, fillerBlocks.names, fillerBlocks.emptySlots, lockedSlots(list, questPoints, diaryDone),
					blockedWeightPercent(fillerPool), fillerAdvice);
		}

		final SlayerSpend.Context context = new SlayerSpend.Context(goal, planMaster, npc(masters, planMaster), planPool, data, player,
				Collections.unmodifiableSet(blocks.rows), blocks.emptySlots, blockAdvice, owned, dossier, items, cycle,
				skipBudget(goal, planMaster, diaries, mortimerTasks));
		final List<Candidate> candidates = SlayerSpend.candidates(context, r -> rewardName(data, r), r -> rewardCost(data, r),
				i -> itemCost(data, i));
		final Reserve reserve = reserve(goal, planMaster, planPool, diaries, mortimerTasks, settings.getReserve());
		// Save-for lines say roughly how many tasks the missing points take: with the routine for the
		// points goal, otherwise every task at the plan's master
		final IntUnaryOperator tasksFor = routine
				? need -> SlayerPointsForecast.withRoutine(need, streaks, masters, diaries, settings.getBoost())
				: need -> SlayerPointsForecast.atMaster(need, planMaster, streaks, diaries);
		Plan plan = SlayerPointsPlanner.plan(candidates, points, reserve, tasksFor, goal);
		// Stop the Wyvern blocks fossil wyverns at every master without a slot. When the plan buys it now, the block advice treats
		// them as gone, so the slot they would take goes to the next task. The plan is made again with that advice, and the task
		// in progress is judged with them gone too.
		SlayerPool.Player taskPlayer = player;
		final String wyvernBlock = rewardName(data, SlayerReward.STOP_THE_WYVERN);
		if (blockAdvice != null && buys(plan, wyvernBlock))
		{
			final Set<Integer> bought = new HashSet<>(owned);
			bought.add(SlayerReward.STOP_THE_WYVERN.getRow());
			final SlayerPool.Player gone = player(account, data, bought, access);
			final SlayerBlockAdvisor.Advice advice = SlayerBlockAdvisor.advise(master, SlayerPool.of(planMaster, data, gone, blocks.rows,
					ratings), goal, openSlots(master, questPoints, diaryDone), blocks.inOrder);
			final String slot = slotGoesTo(blockAdvice, advice, gone.getRemovedTasks());
			final List<Candidate> again = new ArrayList<>();
			for (Candidate c : SlayerSpend.candidates(context.withBlocks(advice), r -> rewardName(data, r), r -> rewardCost(data, r),
					i -> itemCost(data, i)))
			{
				again.add(c.getReward() == SlayerReward.STOP_THE_WYVERN && slot != null ? c.withNote(slot) : c);
			}
			final Plan withBlock = SlayerPointsPlanner.plan(again, points, reserve, tasksFor, goal);
			if (buys(withBlock, wyvernBlock))
			{
				plan = withBlock;
				blockAdvice = advice;
				taskPlayer = gone;
			}
		}

		final SlayerUnlockAdvisor.Situation task = situation(account, data, goal, taskPlayer, ratings, dossier, owned, masters, diaries,
				questPoints, diaryDone, mortimerTasks, points, cycle, planPool, ranking.isEmpty() ? null : ranking.get(0).getNpc());
		final int taskRow = data == null || !streaks.isTaskInProgress() ? -1 : data.taskRowForId(account.varp(VarPlayerID.SLAYER_TARGET));
		final List<String> notes = new ArrayList<>();
		final List<String> noteItems = new ArrayList<>();
		notes(account, data, owned, planPool, goal, notes, noteItems);

		return new View(plan, master, blocks.names, blocks.emptySlots, lockedSlots(master, questPoints, diaryDone),
				blockedWeightPercent,
				planPool,
				blockAdvice,
				notes,
				noteItems,
				data != null,
				goal,
				masters,
				routine ? SlayerPointsRoutine.advise(streaks, masters, diaries, settings.getBoost()) : null,
				ranking,
				later,
				goalMaster,
				task,
				SlayerTaskText.assignedBy(data, taskRow, a -> npc(masters, a)),
				fillers);
	}

	/** Whether the plan buys this purchase now. */
	private static boolean buys(Plan plan, String name)
	{
		for (SlayerPointsPlanner.Line line : plan.getBuyNow())
		{
			if (line.getName().equals(name)) return true;
		}
		return false;
	}

	/**
	 * "The slot goes to Drakes instead." when the advice {@code before} holds a slot for one of these tasks, and the
	 * advice {@code after}, made without them, gives a slot to a task {@code before} does not. Null otherwise.
	 */
	private static String slotGoesTo(SlayerBlockAdvisor.Advice before, SlayerBlockAdvisor.Advice after, Set<Integer> rows)
	{
		boolean held = false;
		for (int row : rows) held |= before.getKeepRows().contains(row) || before.getAddRows().contains(row);
		String task = null;
		for (int i = 0; held && i < after.getAddRows().size(); i++)
		{
			final int row = after.getAddRows().get(i);
			if (!before.getKeepRows().contains(row) && !before.getAddRows().contains(row)) task = after.getAdd().get(i);
		}
		return task == null ? null : SlayerSpend.slotGoesTo(task);
	}

	/**
	 * Whether a plan master takes the routine's milestone tasks while Turael's spot takes the rest: a master on the
	 * main streak with a list of its own. Turael, Aya and Spria share Turael's list.
	 */
	static boolean takesMilestones(SlayerAssigner master)
	{
		return master.getStreak() == SlayerAssigner.Streak.MAIN && SlayerMaster.forAssigner(master) != SlayerMaster.TURAEL;
	}

	/** Share of the weight of the tasks the player can get that blocks remove, in percent, or -1 when there is none. */
	private static int blockedWeightPercent(SlayerPool pool)
	{
		final int assignable = pool.assignableWeight();
		return assignable > 0 ? Math.round(pool.blockedWeight() * 100f / assignable) : -1;
	}

	/**
	 * Points kept for skips at the plan's master. The master ranking assumes the worst offers are skipped with
	 * the master's own points ({@link SlayerMasterRanker#skipBudget}). The skip model says which and how often, and
	 * the plan keeps enough for the skips that come in a row nine times in ten, never less than {@code floor}.
	 * The Slayer points routine never skips (see {@link SlayerUnlockAdvisor}), and Mortimer offers a choice instead.
	 */
	static Reserve reserve(SlayerGoal goal, SlayerAssigner master, SlayerPool pool, SlayerPayout.Diaries diaries, int mortimerTasks,
			int floor)
	{
		final int cancel = master.getCancelCost();
		final double budget = skipBudget(goal, master, diaries, mortimerTasks);
		if (pool == null || budget <= 0)
		{
			return new Reserve(floor, 0, cancel, 0, Collections.<String>emptyList(), floor);
		}
		final SlayerGoalModel.Skips skips = SlayerGoalModel.skips(SlayerGoalModel.offers(pool), goal,
				SlayerMasterRanker.bonus(master, goal), budget);
		final int run = skipsInARow(skips.getShare());
		final List<String> worst = new ArrayList<>();
		for (SlayerPool.Entry e : skips.getTasks())
		{
			if (worst.size() < 2) worst.add(e.getName());
		}
		return new Reserve(Math.max(floor, run * cancel), run, cancel, skips.getShare(), Collections.unmodifiableList(worst), floor);
	}

	/**
	 * The most of a master's offers the skip model skips for the goal, as a share: the master ranking's budget, or 0
	 * when the goal skips nothing. The Slayer points routine never skips, and Mortimer offers a choice instead.
	 */
	static double skipBudget(SlayerGoal goal, SlayerAssigner master, SlayerPayout.Diaries diaries, int mortimerTasks)
	{
		if (goal == SlayerGoal.POINTS || SlayerMasterRanker.choices(master, mortimerTasks) > 1) return 0;
		return SlayerMasterRanker.skipBudget(master, diaries);
	}

	/** Skips in a row that cover nine runs in ten: the fewest k with {@code share} to the power k at most one in ten. */
	static int skipsInARow(double share)
	{
		if (share <= 0) return 0;
		int k = 1;
		double chance = share;
		while (chance > 0.1 && k < MAX_SKIPS_IN_A_ROW)
		{
			chance *= share;
			k++;
		}
		return k;
	}

	/**
	 * The task in progress at the master who gave it, or null without a task or without the cache tables.
	 *
	 * @param cycle the routine's filler side, null off the routine
	 * @param milestone the plan master's list, which takes the routine's milestone tasks
	 * @param bestMaster the NPC at the best master for XP or profit, or null
	 */
	static SlayerUnlockAdvisor.Situation situation(Account account, SlayerGameData data, SlayerGoal goal, SlayerPool.Player player,
			Function<String, TaskRating> ratings, Function<String, SlayerTask> dossier, Set<Integer> owned,
			List<SlayerMasterAccess.Option> masters, SlayerPayout.Diaries diaries, int questPoints, boolean diaryDone, int mortimerTasks,
			int points, SlayerSpend.Routine cycle, SlayerPool milestone, String bestMaster)
	{
		final SlayerPointsRoutine.Streaks streaks = streaks(account);
		final SlayerAssigner master = streaks.currentMaster();
		if (master == null) return null;
		final int number = master.getStreak() == SlayerAssigner.Streak.MAIN ? streaks.getMain() + 1
				: master.getStreak() == SlayerAssigner.Streak.WILDERNESS ? streaks.getWilderness() + 1 : 0;
		final String npc = npc(masters, master);
		final int payout = SlayerPointsRoutine.currentPayout(streaks, diaries);
		if (data == null)
		{
			return new SlayerUnlockAdvisor.Situation(goal, null, master, npc, points, false, false, false, null, null, number, payout, false, 0,
					bestMaster);
		}
		final int row = data.taskRowForId(account.varp(VarPlayerID.SLAYER_TARGET));
		final SlayerMaster list = SlayerMaster.forAssigner(master);
		final BlockList blocks = list == null ? new BlockList() : blockList(account, data, list, questPoints, diaryDone);
		final SlayerPool pool = SlayerPool.of(master, data, player, blocks.rows, ratings);
		final SlayerPool.Entry entry = row < 0 ? null : pool.find(row);

		final SlayerBlockAdvisor.Advice advice = list == null ? null
				: SlayerBlockAdvisor.advise(list, pool, goal, openSlots(list, questPoints, diaryDone), blocks.inOrder);
		boolean worthBlocking = advice != null && advice.getAddRows().contains(row);
		if (worthBlocking && goal == SlayerGoal.POINTS && list == SlayerMaster.TURAEL)
		{
			// A filler task's block pays only when it raises the routine's rate enough to notice
			worthBlocking = SlayerSpend.fillerBlockGain(cycle, milestone, advice.getKeepRows(), row) > SlayerPointsPlanner.NOTICEABLE;
		}
		final String unblock = advice == null || advice.getMisfits().isEmpty() ? null : advice.getMisfits().get(0).getName();
		boolean worthSkipping = false;
		final double skipBudget = skipBudget(goal, master, diaries, mortimerTasks);
		if (entry != null && skipBudget > 0)
		{
			final SlayerGoalModel.Skips skips = SlayerGoalModel.skips(SlayerGoalModel.offers(pool), goal,
					SlayerMasterRanker.bonus(master, goal), skipBudget);
			for (SlayerPool.Entry e : skips.getTasks())
			{
				worthSkipping |= e.getTaskRow() == row;
			}
			// The wiki's Slayer guide says to skip it, or not to have it at all, for XP
			final TaskRating.XpAdvice wiki = entry.wikiAdvice(goal);
			worthSkipping |= wiki == TaskRating.XpAdvice.SKIP || wiki == TaskRating.XpAdvice.DONT_UNLOCK;
		}

		SlayerUnlockAdvisor.Extension extension = null;
		final SlayerGameData.Extension x = row < 0 ? null : data.extensionOf(row);
		final SlayerGameData.Unlock unlock = x == null ? null : data.unlock(x.getUnlockRow());
		if (unlock != null && unlock.isListed())
		{
			extension = new SlayerUnlockAdvisor.Extension(unlock.getName(), unlock.getCost(), owned.contains(unlock.getRow()),
					SlayerSpend.extensionGain(pool, data, row, goal, skipBudget));
		}
		final SlayerTask dossierTask = entry == null ? null : dossier.apply(entry.getName());
		final SlayerGameData.Unlock superiors = data.unlock(SlayerReward.BIGGER_AND_BADDER.getRow());
		final boolean superiorMissing = dossierTask != null && dossierTask.isHasSuperior()
				&& !owned.contains(SlayerReward.BIGGER_AND_BADDER.getRow());
		final int superiorCost = superiors == null || superiors.getCost() < 0 ? SlayerReward.BIGGER_AND_BADDER.getFallbackCost() : superiors.getCost();
		return new SlayerUnlockAdvisor.Situation(goal, entry, master, npc, points, worthSkipping, worthBlocking, blocks.emptySlots > 0,
				unblock, extension, number, payout, superiorMissing, superiorCost, bestMaster);
	}

	/**
	 * The master a goal points to: the best ranked master with a block list for XP and profit, the master who
	 * pays most for milestone tasks for Slayer points (the one the routine sends milestones to). Null when
	 * nothing is known, so the plan keeps the configured master.
	 */
	static SlayerMaster goalMaster(SlayerGoal goal, List<SlayerMasterRanker.Ranked> ranking, List<SlayerMasterAccess.Option> masters,
			SlayerPayout.Diaries diaries, BoostInterval boost)
	{
		if (goal == SlayerGoal.POINTS)
		{
			// Every milestone multiplies all masters' points alike, so any milestone gives the same order
			final List<SlayerPointsRoutine.Ranked> payers = SlayerPointsRoutine.rank(boost.getEvery(), masters, diaries);
			return payers.isEmpty() ? null : SlayerMaster.forAssigner(payers.get(0).getMaster());
		}
		for (SlayerMasterRanker.Ranked r : ranking)
		{
			final SlayerMaster m = SlayerMaster.forAssigner(r.getMaster());
			if (m != null) return m;
		}
		return null;
	}

	/** The streak counters and whether a task is in progress. */
	static SlayerPointsRoutine.Streaks streaks(Account account)
	{
		return new SlayerPointsRoutine.Streaks(account.varbit(VarbitID.SLAYER_TASKS_COMPLETED),
				account.varbit(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED), account.varbit(VarbitID.SLAYER_MASTER),
				account.varp(VarPlayerID.SLAYER_COUNT) > 0);
	}

	/** What the master pools depend on. Without master state the combat level is not known, so it is not checked. */
	static SlayerPool.Player player(Account account, SlayerGameData data, Set<Integer> owned, SlayerMasterAccess.State access)
	{
		final int[] levels = account.levels();
		// Tasks a bought blocker keeps out of every pool. Stop the Wyvern works until it is toggled off.
		final Set<Integer> removed = new HashSet<>();
		final SlayerGameData.Unlock wyvernBlock = data == null ? null : data.unlock(SlayerReward.STOP_THE_WYVERN.getRow());
		if (wyvernBlock != null && owned.contains(wyvernBlock.getRow())
				&& account.varbit(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK) == 0)
		{
			removed.addAll(wyvernBlock.getRelatedTaskRows());
		}
		return new SlayerPool.Player(levels, access == null ? Integer.MAX_VALUE : access.getCombatLevel(),
				account.varbit(VarbitID.COMBAT_LEVEL_REQS_DISABLED) != 0,
				Collections.unmodifiableSet(owned), Collections.unmodifiableSet(removed));
	}

	/** A task's ratings by its cache name, through the dossier's lookup. */
	static Function<String, TaskRating> ratings(SlayerDataset dataset)
	{
		return name ->
		{
			final SlayerTask task = dataset == null ? null : dataset.lookup(name);
			return task == null ? null : task.getRating();
		};
	}

	/** A task's dossier entry by its cache name, or null. */
	static Function<String, SlayerTask> dossier(SlayerDataset dataset)
	{
		return name -> dataset == null ? null : dataset.lookup(name);
	}

	/** The NPC at a master's spot, or the master's own name without master state. */
	private static String npc(List<SlayerMasterAccess.Option> masters, SlayerAssigner master)
	{
		final SlayerMasterAccess.Option option = SlayerMasterAccess.find(masters, master);
		return option == null ? master.getDisplayName() : option.getNpc();
	}

	/** A master's block list: rows, names and the open slots with nothing in them. */
	private static BlockList blockList(Account account, SlayerGameData data, SlayerMaster master, int questPoints, boolean diaryDone)
	{
		final BlockList list = new BlockList();
		final int[] slots = master.getBlockVarbits();
		for (int i = 0; i < slots.length; i++)
		{
			final int taskId = account.varbit(slots[i]);
			if (taskId <= 0)
			{
				if (blockSlotOpen(master, i, questPoints, diaryDone)) list.emptySlots++;
				continue;
			}
			final int row = data == null ? -1 : data.taskRowForId(taskId);
			final String name = row < 0 ? null : data.taskName(row);
			if (row >= 0 && list.rows.add(row)) list.inOrder.add(row);
			list.names.add(name == null ? "Task #" + taskId : name);
		}
		return list;
	}

	/** The cache's name, the fallback without one, or null for a reward taken out of the shop. */
	private static String rewardName(SlayerGameData data, SlayerReward r)
	{
		final SlayerGameData.Unlock unlock = data == null ? null : data.unlock(r.getRow());
		// A reward taken out of the shop keeps its cache row but can no longer be bought
		if (unlock != null && !unlock.isListed()) return null;
		return unlock != null && unlock.getName() != null ? unlock.getName() : r.getFallbackName();
	}

	/** The cache's cost, or null to use the fallback. */
	private static Integer rewardCost(SlayerGameData data, SlayerReward r)
	{
		final SlayerGameData.Unlock unlock = data == null ? null : data.unlock(r.getRow());
		return unlock != null && unlock.getCost() >= 0 ? unlock.getCost() : null;
	}

	/** The Buy tab's cost, null to use the fallback, or -1 when the tab no longer sells the item. */
	private static Integer itemCost(SlayerGameData data, SlayerShopItem item)
	{
		if (data == null || data.shopItems().isEmpty()) return null;
		final Integer cost = data.shopItems().get(item.getItemId());
		return cost == null ? -1 : cost;
	}

	/**
	 * Rows of every owned reward. With the cache this reads each row's bit, which also covers rewards
	 * this plugin does not know yet. Without it, the advice layer's varbits.
	 */
	private static Set<Integer> ownedRewards(Account account, SlayerGameData data)
	{
		final Set<Integer> owned = new HashSet<>();
		if (data != null)
		{
			for (SlayerGameData.Unlock u : data.unlocks())
			{
				if (SlayerGameData.ownsBit(account::varp, u.getBit())) owned.add(u.getRow());
			}
		}
		for (SlayerReward r : SlayerReward.values())
		{
			final SlayerGameData.Unlock u = data == null ? null : data.unlock(r.getRow());
			if ((u == null || u.getBit() < 0) && account.varbit(r.getFallbackVarbit()) != 0) owned.add(r.getRow());
		}
		return owned;
	}

	static String lockedBy(SlayerReward r, int slayer, int crafting, int fletching)
	{
		if (slayer < r.getSlayerLevel()) return r.getSlayerLevel() + " Slayer";
		if (crafting < r.getCraftingLevel()) return r.getCraftingLevel() + " Crafting";
		if (fletching < r.getFletchingLevel()) return r.getFletchingLevel() + " Fletching";
		return null;
	}

	/** {@link #lockedBy(SlayerReward, int, int, int)} with the player's levels. */
	static String lockedBy(SlayerReward r, SlayerPool.Player player)
	{
		return lockedBy(r, player.level(Skill.SLAYER.ordinal()), player.level(Skill.CRAFTING.ordinal()),
				player.level(Skill.FLETCHING.ordinal()));
	}

	/**
	 * Whether block slot {@code slot} (0-based) of a seven slot list can be used. Every slot but the last
	 * needs 50 more quest points than the one before, the last needs the elite Lumbridge & Draynor diary.
	 * Matches the game's block list script (9738).
	 */
	static boolean blockSlotOpen(int slot, int slots, int questPoints, boolean diaryDone)
	{
		if (slot == slots - 1) return diaryDone;
		return questPoints >= (slot + 1) * SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT;
	}

	/** {@link #blockSlotOpen(int, int, int, boolean)} for a master's list. Mortimer's two slots both take quest points. */
	static boolean blockSlotOpen(SlayerMaster master, int slot, int questPoints, boolean diaryDone)
	{
		if (master.isDiarySlot()) return blockSlotOpen(slot, master.getBlockVarbits().length, questPoints, diaryDone);
		return questPoints >= (slot + 1) * SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT;
	}

	/** Block slots the player can use at a master, used or not. 0 for an unknown master. */
	static int openSlots(SlayerMaster master, int questPoints, boolean diaryDone)
	{
		if (master == null) return 0;
		int open = 0;
		for (int i = 0; i < master.getBlockVarbits().length; i++)
		{
			if (blockSlotOpen(master, i, questPoints, diaryDone)) open++;
		}
		return open;
	}

	/** How the locked block slots of a master's list open, or null when every slot is open. */
	static String lockedSlots(SlayerMaster master, int questPoints, boolean diaryDone)
	{
		final int slots = master.getBlockVarbits().length;
		return lockedSlots(master.isDiarySlot() ? slots - 1 : slots, master.isDiarySlot(), questPoints, diaryDone);
	}

	private static String lockedSlots(int questSlots, boolean diarySlot, int questPoints, boolean diaryDone)
	{
		int locked = 0;
		int next = 0;
		for (int i = 0; i < questSlots; i++)
		{
			if (questPoints >= (i + 1) * SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT) continue;
			if (locked++ == 0) next = (i + 1) * SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT;
		}
		final List<String> parts = new ArrayList<>();
		if (locked > 0)
		{
			parts.add((locked == 1 ? "1 more slot needs" : locked + " more slots need") + " quest points. The next opens at " + next + " QP");
		}
		if (diarySlot && !diaryDone)
		{
			parts.add("1 slot needs the elite Lumbridge & Draynor diary");
		}
		return parts.isEmpty() ? null : String.join(". ", parts);
	}

	/** The plan's notes, each with a few words for the compact sidebar in {@code items}. */
	private static void notes(Account account, SlayerGameData data, Set<Integer> owned, SlayerPool pool, SlayerGoal goal, List<String> notes,
			List<String> items)
	{
		if (owned.contains(SlayerReward.BIGGER_AND_BADDER.getRow()) && account.varbit(VarbitID.SLAYER_TOGGLEOFF_SUPERIORMOBS) != 0
				&& goal != SlayerGoal.POINTS)
		{
			notes.add("Bigger and Badder is bought but toggled off. Turn it back on at a slayer master.");
			items.add("Bigger and Badder is off");
		}
		if (owned.contains(SlayerReward.STOP_THE_WYVERN.getRow()) && account.varbit(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK) != 0)
		{
			notes.add("Stop the Wyvern is bought but toggled off, so fossil wyverns can still be assigned.");
			items.add("Stop the Wyvern is off");
		}
		// A task unlock that hurts the goal is better turned off than blocked: "For toggled tasks like lizardmen, the
		// player should simply cancel the toggle" (wiki Slayer training). Boss tasks cannot be blocked at all.
		if (pool == null || data == null) return;
		for (SlayerReward r : SlayerReward.values())
		{
			if (r.getKind() != SlayerReward.Kind.TASK_UNLOCK || !owned.contains(r.getRow())) continue;
			final Set<Integer> rows = new HashSet<>();
			for (SlayerGameData.PoolEntry e : data.pool(pool.getMaster().getId()))
			{
				if (e.getUnlockRow() == r.getRow()) rows.add(e.getTaskRow());
			}
			final SlayerPool without = pool.map(e -> rows.contains(e.getTaskRow()) && e.isOpen() ? e.withStatus(SlayerPool.Status.NEEDS_UNLOCK) : e);
			if (rows.isEmpty() || SlayerSpend.change(pool, without, goal) <= SlayerPointsPlanner.NOTICEABLE) continue;
			final SlayerGameData.Unlock unlock = data.unlock(r.getRow());
			final String name = unlock == null || unlock.getName() == null ? r.getFallbackName() : unlock.getName();
			final int cost = unlock == null || unlock.getCost() < 0 ? r.getFallbackCost() : unlock.getCost();
			notes.add(name + " is on, but the tasks it adds lower your " + goalWord(goal) + " rate here."
					+ (r == SlayerReward.LIKE_A_BOSS ? " Boss tasks cannot be blocked." : "")
					+ " Turning it off at a master stops them" + (cost > 0 ? ", but turning it back on costs " + cost + " points again." : "."));
			items.add("Turn off " + name);
		}
	}

	private static String goalWord(SlayerGoal goal)
	{
		switch (goal)
		{
			case XP:
				return "XP";
			case PROFIT:
				return "profit";
			default:
				return "Slayer points";
		}
	}
}
