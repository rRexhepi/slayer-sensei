package com.slayercompanion;

import java.util.Objects;
import lombok.Value;

/**
 * Decides when Slayer Sensei asks Shortest Path to draw or clear a route. It is a pure state
 * machine fed once per game tick, so the rules are unit tested without a client.
 * <ul>
 * <li>After a login, hop, profile switch or plugin start, the assignment once the game's values have
 * settled is only a baseline. A task that was already in progress never draws a route on its own.</li>
 * <li>Every field of an assignment, its task name included, is read from the game on the same tick
 * ({@link SlayerTaskReader}), so a new assignment counts on the tick it appears, once.</li>
 * <li>Only a route this plugin requested is ever cleared, and ownership is dropped as soon as the
 * player or another plugin changes the Shortest Path target.</li>
 * <li>A route to the next Slayer master is ours too. Finishing the task in progress keeps it, since that is when
 * it is needed. The next assignment replaces it: in NEW_TASK mode with the route to the new task, otherwise it is
 * cleared once the new task is assigned. It is also cleared when another character logs in, or when a login finds
 * a task other than the one in progress when it was drawn.</li>
 * </ul>
 */
final class TaskRouteTracker
{
	/** Ticks after a session starts, while the game's values load, before an assignment change can count as a new task. */
	static final int SETTLE_TICKS = 5;

	enum Action
	{
		NONE,
		/** Request a route to the new assignment. This replaces any route already shown. */
		PATH,
		/** Clear the route this plugin requested earlier. */
		CLEAR
	}

	/** Identity of one task assignment. A changed field means a new assignment. */
	@Value
	static class Assignment
	{
		String profile;
		String taskName;
		int taskId;
		int bossId;
		int areaId;
		int initialAmount;
	}

	private boolean primed;
	private int sessionTicks;
	private Assignment baseline;
	/** The assignment our task route leads to, or null. */
	private Assignment owned;
	/** Our route leads to a Slayer master. */
	private boolean master;
	/** The assignment in progress when the master route was drawn, null between tasks. */
	private Assignment masterFrom;
	/** The character the master route was drawn for, null when unknown. */
	private String masterProfile;
	/** The character of this session, null when unknown. */
	private String profile;

	/** A login, hop, profile switch or plugin start, character unknown. The route this plugin drew is kept. */
	void sessionStarted()
	{
		sessionStarted(null);
	}

	/** {@link #sessionStarted()} for {@code profile}'s character, null when unknown. */
	void sessionStarted(String profile)
	{
		this.profile = profile;
		primed = false;
		sessionTicks = 0;
		baseline = null;
	}

	/**
	 * Feeds one game tick while logged in.
	 *
	 * @param current the assignment now, or null when there is no task
	 * @param autoPath whether a new assignment should get a route now: path mode is NEW_TASK, Shortest
	 *        Path is running and the destination has a verified point
	 */
	Action observe(Assignment current, boolean autoPath)
	{
		sessionTicks++;
		if (!primed)
		{
			if (sessionTicks < SETTLE_TICKS)
			{
				return Action.NONE;
			}
			primed = true;
			baseline = current;
			if (master)
			{
				// A login between tasks, or on the same task, keeps the way to the master
				final boolean sameCharacter = profile == null || masterProfile == null || profile.equals(masterProfile);
				return sameCharacter && (baseline == null || baseline.equals(masterFrom)) ? Action.NONE : clear();
			}
			return dropStaleRoute(baseline);
		}
		if (Objects.equals(current, baseline))
		{
			return Action.NONE;
		}
		baseline = current;
		if (current != null && autoPath)
		{
			master = false;
			owned = current;
			return Action.PATH;
		}
		if (master)
		{
			// Finishing the task in progress keeps the way to the master. The next task replaces it.
			return current == null ? Action.NONE : clear();
		}
		return dropStaleRoute(current);
	}

	private Action dropStaleRoute(Assignment now)
	{
		if (owned != null && !owned.equals(now))
		{
			owned = null;
			return Action.CLEAR;
		}
		return Action.NONE;
	}

	private Action clear()
	{
		owned = null;
		master = false;
		return Action.CLEAR;
	}

	/** The player pressed "Path to task" and the request was posted. */
	void routeRequested(Assignment assignment)
	{
		master = false;
		owned = assignment;
	}

	/**
	 * The player pressed the plan's path to a master and the request was posted.
	 *
	 * @param profile the character it is for, null when unknown
	 * @param current the assignment in progress, null between tasks
	 */
	void masterRouteRequested(String profile, Assignment current)
	{
		owned = null;
		master = true;
		masterFrom = current;
		masterProfile = profile;
	}

	/**
	 * The player picked another place for the current task. A route this plugin drew moves to the new
	 * place, or is cleared when Shortest Path cannot route there, because it would lead to the old place.
	 * Without a route of ours nothing is drawn.
	 *
	 * @param canRoute Shortest Path is running and the new place has a point it can reach
	 */
	Action placeChanged(boolean canRoute)
	{
		if (owned == null)
		{
			return Action.NONE;
		}
		if (canRoute)
		{
			return Action.PATH;
		}
		owned = null;
		return Action.CLEAR;
	}

	/** This plugin cleared its own route. */
	void routeCleared()
	{
		owned = null;
		master = false;
	}

	/** The player or another plugin changed the Shortest Path target, so the route is theirs now. */
	void routeTakenOver()
	{
		owned = null;
		master = false;
	}

	boolean ownsRoute()
	{
		return owned != null || master;
	}

	/** Whether our route leads to a Slayer master. */
	boolean ownsMasterRoute()
	{
		return master;
	}
}
