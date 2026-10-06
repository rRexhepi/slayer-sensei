package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * The goal advice as short sidebar sentences, and as items for the compact sidebar. Pure, so the wording is tested
 * without Swing.
 */
final class SlayerPlanText
{
	/** The heading over {@link #fillerBlockAdvice}. */
	static final String FILLER_BLOCK_HEADING = "Blocks for filler tasks";

	private SlayerPlanText()
	{
	}

	/** "Best master for XP" or "Best master for profit". Null for the Slayer points goal, which has its routine. */
	static String masterHeading(SlayerGoal goal)
	{
		switch (goal)
		{
			case XP:
				return "Best master for XP";
			case PROFIT:
				return "Best master for profit";
			default:
				return null;
		}
	}

	/**
	 * The best master by the NPC the player will find, one reason and the runner-up, e.g. "Kuradal. About 2 in
	 * 5 tasks are good XP tasks once you block the worst. Next best: Steve." Null without a ranking.
	 */
	static String masterAdvice(SlayerPlanBuilder.View view)
	{
		final List<SlayerMasterRanker.Ranked> ranking = view.getRanking();
		if (ranking.isEmpty()) return null;
		final SlayerMasterRanker.Ranked best = ranking.get(0);
		final StringBuilder sb = new StringBuilder(best.getNpc()).append(". ").append(best.getReason());
		if (ranking.size() > 1) sb.append(" Next best: ").append(ranking.get(1).getNpc()).append('.');
		final SlayerMasterRanker.Ranked later = view.getLater();
		if (later != null) sb.append(" Later: ").append(later.getNpc()).append(", who needs ").append(later.getMissing()).append('.');
		if (!view.isFollowingGoal())
		{
			sb.append(" The plan below uses ").append(view.getMasterName()).append(" because Pin planner master is on.");
		}
		return sb.toString();
	}

	/** {@link #blockAdvice(SlayerBlockAdvisor.Advice, String, String)} for the plan, with where milestone tasks go. */
	static String blockAdvice(SlayerPlanBuilder.View view)
	{
		String note = null;
		if (view.getGoal() == SlayerGoal.POINTS && view.getGoalMaster() != null)
		{
			note = view.isFollowingGoal()
					? "Milestone tasks go to " + view.getMasterName() + ", so block the slow ones there."
					: "Milestone tasks go to " + view.getGoalMasterName() + ". Pin planner master keeps this list on "
							+ view.getMasterName() + ".";
		}
		return blockAdvice(view.getBlockAdvice(), view.getMasterName(), note);
	}

	/**
	 * The filler tasks' block advice on the Slayer points routine, e.g. "Filler tasks go to Aya, so block the longest
	 * ones there.\nBlock next: Cave Bugs. 40 points each at Aya." Null off the routine.
	 */
	static String fillerBlockAdvice(SlayerPlanBuilder.View view)
	{
		final SlayerPlanBuilder.Fillers fillers = view.getFillers();
		if (fillers == null) return null;
		final SlayerMasterAccess.Option spria = SlayerMasterAccess.find(view.getMasters(), SlayerAssigner.SPRIA);
		final String shared = spria != null && spria.isUsable() ? " " + spria.getNpc() + " shares this list." : "";
		return blockAdvice(fillers.getBlockAdvice(), fillers.getNpc(),
				"Filler tasks go to " + fillers.getNpc() + ", so block the longest ones there." + shared);
	}

	/** "Blocks for XP", "Blocks for profit" or "Blocks for Slayer points". */
	static String blockHeading(SlayerGoal goal)
	{
		return "Blocks for " + goalWord(goal);
	}

	/** {@link #blockHeading(SlayerGoal)}, or "Blocks for milestone tasks" when the routine's filler tasks have their own. */
	static String blockHeading(SlayerPlanBuilder.View view)
	{
		return view.getFillers() != null ? "Blocks for milestone tasks" : blockHeading(view.getGoal());
	}

	/** "Skip these for XP": the purchases that would lower the goal's rate. */
	static String skipHeading(SlayerGoal goal)
	{
		return "Skip these for " + goalWord(goal);
	}

	/**
	 * The points line: the points, what the reserve is for and what is left to spend, e.g. "521 points. 90 are kept
	 * to skip up to 3 bad tasks in a row at Kuradal. About 2 in 5 of Kuradal's tasks are worth skipping for XP,
	 * such as Spiritual Creatures and Waterfiends. 431 to spend."
	 */
	static String points(SlayerPointsPlanner.Plan plan, SlayerAssigner master, String npc, SlayerGoal goal)
	{
		final SlayerPointsPlanner.Reserve reserve = plan.getReserve();
		final int spendable = Math.max(0, plan.getPoints() - reserve.getPoints());
		final StringBuilder sb = new StringBuilder().append(plan.getPoints()).append(" points. ");
		if (reserve.getPoints() > 0 && reserve.isFloor())
		{
			sb.append(reserve.getPoints()).append(" are kept for skips, as Points to keep for skips says. ");
		}
		else if (reserve.getSkips() > 0)
		{
			sb.append(reserve.getPoints()).append(" are kept to skip ")
					.append(reserve.getSkips() == 1 ? "a bad task" : "up to " + reserve.getSkips() + " bad tasks in a row")
					.append(" at ").append(npc).append(". ").append(SlayerMasterRanker.share(reserve.getShare())).append(" of ")
					.append(SlayerSpend.possessive(npc)).append(" tasks are worth skipping for ").append(goalWord(goal));
			if (!reserve.getTasks().isEmpty()) sb.append(", such as ").append(SlayerSpend.join(reserve.getTasks()));
			sb.append(". ");
		}
		else if (goal == SlayerGoal.POINTS)
		{
			sb.append("None are kept for skips, since the routine does every task. ");
		}
		else if (master == SlayerAssigner.MORTIMER)
		{
			sb.append("None are kept for skips, since you choose from ").append(SlayerSpend.possessive(npc)).append(" tasks instead. ");
		}
		else if (SlayerPayout.basePoints(master, SlayerPayout.Diaries.NONE) == 0)
		{
			sb.append("None are kept for skips, since ").append(npc).append(" pays no points. ");
		}
		else
		{
			sb.append("None are kept for skips, since no task at ").append(npc).append(" is worth one. ");
		}
		return sb.append(spendable).append(" to spend.").toString();
	}

	/**
	 * Which tasks to block next with the cost, which blocks to keep and which do not fit the goal. Null without
	 * advice.
	 *
	 * @param note a first line, or null
	 */
	static String blockAdvice(SlayerBlockAdvisor.Advice advice, String npc, String note)
	{
		if (advice == null) return null;
		final List<String> lines = new ArrayList<>();
		if (note != null) lines.add(note);
		if (advice.getSlots() == 0)
		{
			lines.add("Block slots open at " + SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT + " quest points.");
			return String.join("\n", lines);
		}
		if (!advice.getAdd().isEmpty())
		{
			lines.add("Block next: " + String.join(", ", advice.getAdd()) + ". " + advice.getCost() + " points each at " + npc + ".");
		}
		else if (advice.getKeep().size() >= advice.getSlots())
		{
			lines.add("Every open slot holds a useful block.");
		}
		else
		{
			lines.add("No other task is worth a block for " + goalWord(advice.getGoal()) + ".");
		}
		if (!advice.getKeep().isEmpty())
		{
			lines.add("Already blocked and right: " + String.join(", ", advice.getKeep()) + ".");
		}
		if (!advice.getMisfits().isEmpty())
		{
			final List<String> misfits = new ArrayList<>();
			for (SlayerBlockAdvisor.Misfit m : advice.getMisfits())
			{
				misfits.add(m.getName() + " (" + m.getWhy() + ")");
			}
			lines.add("Not right for " + goalWord(advice.getGoal()) + ": " + String.join(", ", misfits)
					+ ". Unblock " + (misfits.size() == 1 ? "it" : "them") + " to free the slot.");
		}
		return String.join("\n", lines);
	}

	// ---- the compact sidebar ----

	/** The compact plan's heading over {@link #masterItems}. */
	static final String BEST_MASTER_HEADING = "BEST MASTER";

	/** "521, 401 to spend": the points and what is left above the reserve. */
	static String pointsItem(SlayerPointsPlanner.Plan plan)
	{
		final int spendable = Math.max(0, plan.getPoints() - plan.getReserve().getPoints());
		final String spend = spendable == 0 ? "none" : spendable == plan.getPoints() ? "all" : String.valueOf(spendable);
		return plan.getPoints() + ", " + spend + " to spend";
	}

	/**
	 * The best masters for XP or profit as compact items: the best, "then" the runner-up, a better one for later and
	 * the master a pinned plan uses. Empty without a ranking. {@link #masterAdvice} says why.
	 */
	static List<String> masterItems(SlayerPlanBuilder.View view)
	{
		final List<SlayerMasterRanker.Ranked> ranking = view.getRanking();
		if (ranking.isEmpty()) return Collections.emptyList();
		final List<String> items = new ArrayList<>();
		items.add(ranking.get(0).getNpc());
		if (ranking.size() > 1) items.add("then " + ranking.get(1).getNpc());
		final SlayerMasterRanker.Ranked later = view.getLater();
		if (later != null) items.add("Later: " + later.getNpc() + " (" + later.getMissing() + ")");
		if (!view.isFollowingGoal()) items.add("Plan uses " + view.getMasterName() + " (pinned)");
		return items;
	}

	/** "KONAR BLOCKLIST" */
	static String blockListHeading(String npc)
	{
		return npc.toUpperCase(Locale.ROOT) + " BLOCKLIST";
	}

	/** One item of a master's compact block list, with the reason or the cost in its tooltip. */
	@Value
	static class BlockItem
	{
		/** What an item is, which its colour shows. */
		enum Kind
		{
			/** A current block that fits the goal, marked "(set)". */
			SET,
			/** A block the advice would add. */
			ADD,
			/** A current block that does not fit the goal, marked "(unblock)". */
			UNBLOCK,
			/** "(none)", or when the first slot opens. */
			NOTE
		}

		String text;
		Kind kind;
		String tooltip;
	}

	/**
	 * A master's block list as one list of compact items: the current blocks that fit the goal marked "(set)", the blocks
	 * the advice would add next in its order, then the current blocks that do not fit marked "(unblock)". "(none)" when
	 * there is nothing to list, and while no slot is open, when the first one opens. Without advice the current blocks
	 * are listed as they are.
	 */
	static List<BlockItem> blockListItems(String npc, List<String> blocked, SlayerBlockAdvisor.Advice advice)
	{
		final List<BlockItem> items = new ArrayList<>();
		if (advice == null)
		{
			for (String task : blocked)
			{
				items.add(new BlockItem(task + " (set)", BlockItem.Kind.SET, null));
			}
		}
		else
		{
			for (String task : advice.getKeep())
			{
				items.add(new BlockItem(task + " (set)", BlockItem.Kind.SET, "Already blocked and right for " + goalWord(advice.getGoal()) + "."));
			}
			for (String task : advice.getAdd())
			{
				items.add(new BlockItem(task, BlockItem.Kind.ADD, "Block it next for " + advice.getCost() + " points at " + npc + "."));
			}
			for (SlayerBlockAdvisor.Misfit misfit : advice.getMisfits())
			{
				items.add(new BlockItem(misfit.getName() + " (unblock)", BlockItem.Kind.UNBLOCK, capitalize(misfit.getWhy())
						+ ". Unblock it to free the slot."));
			}
		}
		if (items.isEmpty()) items.add(new BlockItem("(none)", BlockItem.Kind.NOTE, null));
		if (advice != null && advice.getSlots() == 0)
		{
			items.add(new BlockItem("Slots open at " + SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT + " QP", BlockItem.Kind.NOTE,
					"Block slots open at " + SlayerMaster.QUEST_POINTS_PER_BLOCK_SLOT + " quest points."));
		}
		return items;
	}

	private static String capitalize(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
