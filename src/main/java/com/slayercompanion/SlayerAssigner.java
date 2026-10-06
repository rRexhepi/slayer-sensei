package com.slayercompanion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Every master spot that hands out tasks, keyed by the game's master id (SlayerMasterTask.COL_MASTER_ID
 * and the SLAYER_MASTER varbit). A replacement NPC takes over the spot and keeps its id, task list and
 * block list: Aya for Turael, Achtryn for Mazchna, Steve for Nieve, Kuradal for Duradel. See
 * {@link SlayerMasterAccess} for who stands where and {@link SlayerPayout} for the points.
 * {@link SlayerMaster} stays the list of block lists the planner can show.
 * <p>
 * Levels and quests are from the OSRS Wiki "Slayer Master" table and each master's page. Block and cancel
 * costs are the game's own (client scripts 823 and 9741, which match the same wiki table).
 */
@Getter
@AllArgsConstructor
enum SlayerAssigner
{
	TURAEL(1, "Turael", "Burthorpe", 0, 0, QuestNeed.NONE, Streak.MAIN, 40, 30),
	SPRIA(9, "Spria", "Draynor Village", 0, 0, QuestNeed.PORCINE_OF_INTEREST, Streak.MAIN, 40, 30),
	MAZCHNA(2, "Mazchna", "Canifis", 20, 0, QuestNeed.PRIEST_IN_PERIL, Streak.MAIN, 50, 30),
	VANNAKA(3, "Vannaka", "Edgeville Dungeon", 40, 0, QuestNeed.NONE, Streak.MAIN, 60, 30),
	CHAELDAR(4, "Chaeldar", "Zanaris", 70, 0, QuestNeed.LOST_CITY, Streak.MAIN, 70, 30),
	KONAR(8, "Konar", "Mount Karuulm", 75, 0, QuestNeed.NONE, Streak.MAIN, 80, 30),
	NIEVE(6, "Nieve", "the Tree Gnome Stronghold", 85, 0, QuestNeed.NONE, Streak.MAIN, 90, 30),
	DURADEL(5, "Duradel", "Shilo Village", 100, 50, QuestNeed.SHILO_VILLAGE, Streak.MAIN, 100, 30),
	/** Refuses players with level 1 Slayer, so her Slayer requirement is 2. */
	KRYSTILIA(7, "Krystilia", "Edgeville", 0, 2, QuestNeed.NONE, Streak.WILDERNESS, 100, 30),
	MORTIMER(10, "Mortimer", "Wyrmscraig Cavern", 100, 70, QuestNeed.FALLEN_FROM_GRACE_PART, Streak.MORTIMER, 120, 100);

	/** The quest a master needs before giving tasks. */
	enum QuestNeed
	{
		NONE,
		/** Canifis lies behind it. */
		PRIEST_IN_PERIL,
		/** Zanaris lies behind it. */
		LOST_CITY,
		/** Shilo Village lies behind it. */
		SHILO_VILLAGE,
		/** Spria takes assignments once A Porcine of Interest is done. */
		PORCINE_OF_INTEREST,
		/** Mortimer appears part way through Fallen From Grace. */
		FALLEN_FROM_GRACE_PART
	}

	/** Which task streak a master's tasks count toward. Each streak pays its own milestones. */
	enum Streak
	{
		/** SLAYER_TASKS_COMPLETED, shared by every master below. */
		MAIN,
		/** SLAYER_WILDERNESS_TASKS_COMPLETED, Krystilia only. */
		WILDERNESS,
		/** SLAYER_MORTIMER_TASKS_COMPLETED. His tasks pay no streak points at all. */
		MORTIMER
	}

	private final int id;
	/** The name of the NPC who first held the spot. */
	private final String displayName;
	private final String location;
	/** Combat level needed. 99 Slayer waives it, as the Slayer cape does in game. */
	private final int combatLevel;
	private final int slayerLevel;
	private final QuestNeed questNeed;
	private final Streak streak;
	/** Points to block a task at this master. */
	private final int blockCost;
	/** Points to cancel a task from this master. */
	private final int cancelCost;

	/** What client script 823 charges to block at a master it does not list. */
	static final int DEFAULT_BLOCK_COST = 100;

	/** The master for a game master id, or null for the leagues pool, no task and unknown ids. */
	static SlayerAssigner forId(int id)
	{
		for (SlayerAssigner a : values())
		{
			if (a.id == id) return a;
		}
		return null;
	}

	/** Points to block a task at the master with this game id. */
	static int blockCost(int masterId)
	{
		final SlayerAssigner master = forId(masterId);
		return master == null ? DEFAULT_BLOCK_COST : master.blockCost;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
