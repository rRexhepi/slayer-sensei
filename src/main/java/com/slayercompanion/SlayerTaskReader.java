package com.slayercompanion;

import java.util.List;
import java.util.function.IntUnaryOperator;
import lombok.Value;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * The task in progress, read from the game the same way RuneLite's own Slayer plugin reads it (SlayerPlugin.updateTask
 * in client 1.13.1), so the name comes out exactly as that plugin would store it. The dossier, the gear tables and the
 * places are keyed by those names. Slayer Sensei needs no other plugin for its task. That plugin reads the tables
 * only when one of its varps changes. This reader is asked every tick, so it keeps the last name it found and reads
 * the tables again only when SLAYER_TARGET or the boss id changes. Pure apart from the reads it is given, which belong
 * on the client thread.
 */
final class SlayerTaskReader
{
	/** The SLAYER_TARGET value of a boss task. Which boss it is, SLAYER_TARGET_BOSSID says. */
	static final int BOSS_TASK = 98;
	/** SLAYER_MODIFIER_ID of "Slayer Quantity Change" (SlayerModifiers table), which moves the amount assigned. */
	static final int QUANTITY_MODIFIER = 2;

	/** The task as the game holds it. */
	@Value
	static class Task
	{
		/** The name RuneLite's Slayer plugin stores, such as "Abyssal Demons" or "The Alchemical Hydra". */
		String name;
		/** Kills left (SLAYER_COUNT). */
		int amount;
		/** Kills assigned (SLAYER_COUNT_ORIGINAL), with a quantity modifier counted in, as that plugin counts it. */
		int initialAmount;
	}

	/** The SLAYER_TARGET and boss id of {@link #lastName}. */
	private int lastTarget;
	private int lastBossId;
	/** The name the last lookup found, or null when none is kept. */
	private String lastName;

	/**
	 * The task, or null without one. Null too while the tables have no row for the target, so nothing shows until the
	 * game's values can be read. A table read that throws goes to the caller and keeps nothing.
	 */
	Task read(IntUnaryOperator varps, IntUnaryOperator varbits, SlayerGameData.Db db)
	{
		final int amount = varps.applyAsInt(VarPlayerID.SLAYER_COUNT);
		if (amount <= 0) return null;
		final String name = name(db, varps.applyAsInt(VarPlayerID.SLAYER_TARGET), varbits.applyAsInt(VarbitID.SLAYER_TARGET_BOSSID));
		return name == null ? null : new Task(name, amount, initialAmount(varps, varbits));
	}

	/**
	 * {@link #taskName}, kept for the last SLAYER_TARGET and boss id it found a name for, so the tables are read only when
	 * one of them changes. A lookup that finds no name, or throws, is never kept, so the next call looks again.
	 */
	String name(SlayerGameData.Db db, int target, int bossId)
	{
		if (lastName != null && target == lastTarget && bossId == lastBossId) return lastName;
		final String name = taskName(db, target, bossId);
		if (name == null || name.isEmpty()) return null;
		lastTarget = target;
		lastBossId = bossId;
		lastName = name;
		return name;
	}

	/** Forgets the name kept. The session ended. */
	void clear()
	{
		lastName = null;
	}

	/**
	 * The stored name for a SLAYER_TARGET value. A boss task leads from SLAYER_TARGET_BOSSID through the boss list
	 * (SlayerTaskSublist) to its SlayerTask row, any other task goes straight to the SlayerTask row with that id. The
	 * name is the row's COL_NAME_UPPERCASE, unchanged. Null when the tables have no such row.
	 */
	static String taskName(SlayerGameData.Db db, int target, int bossId)
	{
		final int row;
		if (target == BOSS_TASK)
		{
			final List<Integer> bosses = db.rowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0, bossId);
			if (bosses.isEmpty()) return null;
			final Object[] task = db.field(bosses.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
			if (task.length == 0 || !(task[0] instanceof Integer)) return null;
			row = (Integer) task[0];
		}
		else
		{
			final List<Integer> tasks = db.rowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, target);
			if (tasks.isEmpty()) return null;
			row = tasks.get(0);
		}
		final Object[] name = db.field(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		return name.length == 0 || !(name[0] instanceof String) ? null : (String) name[0];
	}

	/** SLAYER_COUNT_ORIGINAL, moved up or down by a quantity modifier the way RuneLite's Slayer plugin moves it. */
	static int initialAmount(IntUnaryOperator varps, IntUnaryOperator varbits)
	{
		int initial = varps.applyAsInt(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		if (varbits.applyAsInt(VarbitID.SLAYER_MODIFIER_ID) == QUANTITY_MODIFIER)
		{
			final int change = varbits.applyAsInt(VarbitID.SLAYER_MODIFIER_VALUE);
			initial += varbits.applyAsInt(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -change : change;
		}
		return initial;
	}
}
