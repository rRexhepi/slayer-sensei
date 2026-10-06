package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import lombok.Value;

/**
 * Turns a master's pool into one number per goal, so masters and block lists can be compared. Pure.
 * <p>
 * The XP and profit ratings are per hour, so a pool is judged by where the time goes. Each task counts by
 * its chance of coming up times how long it takes, and the minutes between tasks (getting the task, travel,
 * banking) earn nothing. A short poor task costs little, which is why the wiki puts the common long tasks on
 * block lists before the rare quick ones ("between the choice of spiritual creatures (7) or zygomites (2)
 * from Duradel, blocking the former would be more beneficial", wiki Slayer training). How long a task takes
 * comes from its speed rating, scaled by how many the master assigns against the amount the rating was
 * judged at. Turael's and Spria's small filler tasks have their own estimate instead
 * ({@link com.slayercompanion.data.TaskRating.Filler}), as their time is in the trip, not the kills. For Slayer
 * points the rate is tasks finished per hour, which is what decides points per hour at one master. On the points
 * routine it is points per hour over a cycle of filler tasks and one milestone task ({@link #cycleRate}), so the
 * filler master's list counts as much as the time spent there.
 * <p>
 * The minutes per speed rating are judgments that match the scale in {@link com.slayercompanion.data.TaskRating}.
 * The rates only ever compare masters or tasks with each other. They are never shown.
 */
final class SlayerGoalModel
{
	/** Minutes per assignment for speed ratings 1 to 5, everything included. */
	private static final double[] TASK_MINUTES = {0, 100, 65, 40, 22, 12};
	/** Of those, the minutes per assignment that earn nothing: getting the task, travel and banking. */
	static final double OVERHEAD_MINUTES = 6;
	/** A skip costs one trip to a master. */
	static final double SKIP_MINUTES = 3;
	/** The lowest rating: a task this poor is worth a skip however short it is. */
	private static final int VERY_POOR = 1;
	private static final double EPSILON = 1e-9;

	/** One task the player may be given, with its chance of coming up. */
	@Value
	static class Offer
	{
		SlayerPool.Entry entry;
		double chance;
	}

	/** What the skip model skips: the worst offers first, while a skip still raises the rate. */
	@Value
	static class Skips
	{
		/** Share of all offers skipped. */
		double share;
		/** The goal's rate with these skips. */
		double rate;
		/** The tasks skipped, worst first. The last may be skipped only part of the time. */
		List<SlayerPool.Entry> tasks;
	}

	private SlayerGoalModel()
	{
	}

	/**
	 * Minutes spent killing on one assignment of the task at its master. A task with its own estimate at the master,
	 * as Turael's and Spria's filler tasks have, takes that, less the minutes between tasks.
	 */
	static double killMinutes(SlayerPool.Entry e)
	{
		if (e.getMinutes() > 0) return Math.max(0, e.getMinutes() - OVERHEAD_MINUTES);
		final double minutes = TASK_MINUTES[e.getRating().getSpeed()] - OVERHEAD_MINUTES;
		final double amount = e.averageAmount();
		if (amount <= 0 || e.getTypicalAmount() <= 0) return minutes;
		return minutes * amount / e.getTypicalAmount();
	}

	/** The rated tasks the master can give now, each with its chance among them. */
	static List<Offer> offers(SlayerPool pool)
	{
		int weight = 0;
		for (SlayerPool.Entry e : pool.getEntries())
		{
			if (e.isOpen() && e.isRated()) weight += e.getWeight();
		}
		final List<Offer> out = new ArrayList<>();
		if (weight == 0) return out;
		for (SlayerPool.Entry e : pool.getEntries())
		{
			if (e.isOpen() && e.isRated()) out.add(new Offer(e, e.getWeight() / (double) weight));
		}
		return out;
	}

	/** The goal's rate when every task the master gives is done. Higher is better. */
	static double rate(SlayerPool pool, SlayerGoal goal)
	{
		return rate(offers(pool), goal, 0, 0);
	}

	/**
	 * @param bonus added to every task's rating for XP and profit: a master-level effect, see {@link SlayerMasterRanker}
	 * @param skipped share of all offers skipped, at {@link #SKIP_MINUTES} each. The chances must already leave them out.
	 */
	static double rate(List<Offer> offers, SlayerGoal goal, double bonus, double skipped)
	{
		double gain = 0;
		double minutes = skipped * SKIP_MINUTES;
		double done = 0;
		for (Offer o : offers)
		{
			final double kill = killMinutes(o.getEntry());
			gain += o.getChance() * kill * (o.getEntry().rating(goal) + bonus);
			minutes += o.getChance() * (kill + OVERHEAD_MINUTES);
			done += o.getChance();
		}
		if (minutes <= 0) return 0;
		return goal == SlayerGoal.POINTS ? 60 * done / minutes : gain / minutes;
	}

	/**
	 * A what-if rate for rewards that change how a task goes rather than which tasks come up. {@code time} scales a
	 * task's killing minutes, so a faster kill earns the same in less time. {@code yield} scales what it earns for XP
	 * or profit. {@code overhead} is the task's minutes between tasks, {@link #OVERHEAD_MINUTES} unless a reward
	 * shortens the trip.
	 */
	static double rate(List<Offer> offers, SlayerGoal goal, ToDoubleFunction<SlayerPool.Entry> time,
			ToDoubleFunction<SlayerPool.Entry> yield, ToDoubleFunction<SlayerPool.Entry> overhead)
	{
		double gain = 0;
		double minutes = 0;
		double done = 0;
		for (Offer o : offers)
		{
			final SlayerPool.Entry e = o.getEntry();
			final double kill = killMinutes(e);
			gain += o.getChance() * kill * e.rating(goal) * yield.applyAsDouble(e);
			minutes += o.getChance() * (kill * time.applyAsDouble(e) + overhead.applyAsDouble(e));
			done += o.getChance();
		}
		if (minutes <= 0) return 0;
		return goal == SlayerGoal.POINTS ? 60 * done / minutes : gain / minutes;
	}

	/** Minutes per assignment over the offers on average, killing and the minutes between tasks. 0 without offers. */
	static double minutesPerTask(List<Offer> offers)
	{
		return minutesPerTask(offers, e -> 1, e -> OVERHEAD_MINUTES);
	}

	/** {@link #minutesPerTask(List)} when a reward changes how tasks go: {@code time} and {@code overhead} as in {@link #rate}. */
	static double minutesPerTask(List<Offer> offers, ToDoubleFunction<SlayerPool.Entry> time, ToDoubleFunction<SlayerPool.Entry> overhead)
	{
		double minutes = 0;
		double done = 0;
		for (Offer o : offers)
		{
			final SlayerPool.Entry e = o.getEntry();
			minutes += o.getChance() * (killMinutes(e) * time.applyAsDouble(e) + overhead.applyAsDouble(e));
			done += o.getChance();
		}
		return done > 0 ? minutes / done : 0;
	}

	/**
	 * Slayer points an hour on the points routine. One cycle is {@code fillers} tasks at the filler master, 9 for
	 * every 10th task or 49 for every 50th, and one milestone task, which pays {@code points}. Each task takes its
	 * own master's average minutes. Only the time depends on the lists, so blocks at either master compare on it.
	 */
	static double cycleRate(double fillerMinutes, double milestoneMinutes, int fillers, double points)
	{
		final double minutes = fillers * fillerMinutes + milestoneMinutes;
		return minutes <= 0 ? 0 : 60 * points / minutes;
	}

	/** {@link #cycleRate(double, double, int, double)} over the filler master's and the milestone master's lists. */
	static double cycleRate(SlayerPool fillerList, SlayerPool milestoneList, int fillers, double points)
	{
		return cycleRate(minutesPerTask(offers(fillerList)), minutesPerTask(offers(milestoneList)), fillers, points);
	}

	/**
	 * The tasks worth blocking for the goal, best first, at most {@code slots}. Each pick is the block that
	 * raises the rate most, and picking stops once no block raises it. The pool's current blocks are lifted
	 * first, so this is the list to aim for, not the change from today's.
	 */
	static List<SlayerPool.Entry> bestBlocks(SlayerPool pool, SlayerGoal goal, int slots)
	{
		return bestBlocks(pool, goal, Collections.<Integer>emptyList(), slots);
	}

	/** {@link #bestBlocks(SlayerPool, SlayerGoal, int)} on top of {@code fixed} blocks, which stay. */
	static List<SlayerPool.Entry> bestBlocks(SlayerPool pool, SlayerGoal goal, Collection<Integer> fixed, int slots)
	{
		final List<Integer> blocked = new ArrayList<>(fixed);
		final List<SlayerPool.Entry> out = new ArrayList<>();
		SlayerPool current = pool.withBlocks(blocked);
		for (int n = 0; n < slots; n++)
		{
			SlayerPool.Entry best = null;
			double bestRate = rate(current, goal) + EPSILON;
			for (SlayerPool.Entry e : current.open())
			{
				// "The only way to block a boss is by toggling the ability off" (wiki Boss Slayer). A task the wiki says to
				// do for the goal is never blocked.
				if (!e.isRated() || e.isBoss() || e.wikiSaysDo(goal)) continue;
				blocked.add(e.getTaskRow());
				final double r = rate(pool.withBlocks(blocked), goal);
				blocked.remove(blocked.size() - 1);
				// Equal gains go to the heavier task, then by name, so the advice never flips between rebuilds
				if (r > bestRate + EPSILON || (best != null && Math.abs(r - bestRate) <= EPSILON && before(e, best)))
				{
					best = e;
					bestRate = r;
				}
			}
			if (best == null) break;
			blocked.add(best.getTaskRow());
			out.add(best);
			current = pool.withBlocks(blocked);
		}
		return out;
	}

	private static boolean before(SlayerPool.Entry a, SlayerPool.Entry b)
	{
		if (a.getWeight() != b.getWeight()) return a.getWeight() > b.getWeight();
		return a.getName().compareTo(b.getName()) < 0;
	}

	/**
	 * The rate when the worst offers are skipped, up to {@code budget} as a share of all offers, and only while
	 * a skip raises the rate. Worst means lowest rated for the goal, then longest. A skip costs 30 points, about a
	 * task's worth, so a task shorter than half the average one is only skipped when it is very poor: it is over
	 * almost as soon as a skip would be. The wiki does the same with black dragons, which Duradel gives in tens:
	 * "a very fast task when unextended" (Slayer training, task summary).
	 */
	static double rateWithSkips(List<Offer> offers, SlayerGoal goal, double bonus, double budget)
	{
		return skips(offers, goal, bonus, budget).getRate();
	}

	/** Which offers {@link #rateWithSkips} skips, how often and the rate it reaches. */
	static Skips skips(List<Offer> offers, SlayerGoal goal, double bonus, double budget)
	{
		final List<Offer> worstFirst = new ArrayList<>(offers);
		worstFirst.sort(Comparator.comparingInt((Offer o) -> o.getEntry().rating(goal))
				.thenComparing(Comparator.comparingDouble((Offer o) -> killMinutes(o.getEntry())).reversed()));
		final List<Offer> kept = new ArrayList<>(worstFirst);
		final List<SlayerPool.Entry> tasks = new ArrayList<>();
		final double shortTask = minutesPerTask(offers) / 2;
		double skipped = 0;
		double best = rate(kept, goal, bonus, 0);
		for (int i = 0; i < kept.size() && skipped < budget - EPSILON; i++)
		{
			final Offer o = kept.get(i);
			// A task the wiki says to do for the goal is never skipped
			if (o.getEntry().wikiSaysDo(goal)) continue;
			if (o.getEntry().rating(goal) > VERY_POOR && killMinutes(o.getEntry()) + OVERHEAD_MINUTES < shortTask) continue;
			final double take = Math.min(o.getChance(), budget - skipped);
			final List<Offer> trial = new ArrayList<>(kept);
			trial.set(i, new Offer(o.getEntry(), o.getChance() - take));
			final double r = rate(trial, goal, bonus, skipped + take);
			if (r <= best + EPSILON) break;
			kept.set(i, trial.get(i));
			skipped += take;
			best = r;
			tasks.add(o.getEntry());
		}
		return new Skips(skipped, best, Collections.unmodifiableList(tasks));
	}

	/**
	 * The rate when every assignment offers {@code choices} different tasks and the player takes the best one
	 * for the goal, as Mortimer offers them. Exact over every ordered draw without replacement.
	 */
	static double rateBestOf(List<Offer> offers, SlayerGoal goal, double bonus, int choices)
	{
		return rate(bestOf(offers, goal, choices), goal, bonus, 0);
	}

	/** The chance of doing each task when every assignment offers {@code choices} tasks and the player takes the best. */
	static List<Offer> bestOf(List<Offer> offers, SlayerGoal goal, int choices)
	{
		final List<Offer> chosen = new ArrayList<>();
		if (offers.isEmpty()) return chosen;
		final double[] picked = new double[offers.size()];
		draw(offers, goal, Math.max(1, Math.min(choices, offers.size())), new boolean[offers.size()], 1, 1, -1, picked);
		for (int i = 0; i < picked.length; i++)
		{
			if (picked[i] > 0) chosen.add(new Offer(offers.get(i).getEntry(), picked[i]));
		}
		return chosen;
	}

	/** Chance a task from these offers is rated at least {@code min} for the goal. */
	static double share(List<Offer> offers, SlayerGoal goal, int min)
	{
		double good = 0;
		double all = 0;
		for (Offer o : offers)
		{
			all += o.getChance();
			if (o.getEntry().rating(goal) >= min) good += o.getChance();
		}
		return all > 0 ? good / all : 0;
	}

	private static void draw(List<Offer> offers, SlayerGoal goal, int left, boolean[] used, double probability,
			double remaining, int best, double[] picked)
	{
		if (left == 0)
		{
			picked[best] += probability;
			return;
		}
		for (int i = 0; i < offers.size(); i++)
		{
			if (used[i] || remaining <= EPSILON) continue;
			final double chance = offers.get(i).getChance();
			used[i] = true;
			draw(offers, goal, left - 1, used, probability * chance / remaining, remaining - chance,
					best < 0 || better(offers.get(i), offers.get(best), goal) ? i : best, picked);
			used[i] = false;
		}
	}

	/** Which of two offered tasks the player takes for the goal: the higher rated, then the quicker. */
	static boolean better(Offer a, Offer b, SlayerGoal goal)
	{
		final int ra = a.getEntry().rating(goal);
		final int rb = b.getEntry().rating(goal);
		if (ra != rb) return ra > rb;
		return killMinutes(a.getEntry()) < killMinutes(b.getEntry());
	}
}
