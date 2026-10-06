package com.slayercompanion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.VarbitID;

/**
 * Masters with their own block list. The id is the game's master id: SlayerMasterTask.COL_MASTER_ID,
 * the SLAYER_MASTER varbit and the reward shop's SLAYER_MASTER_IN_FOCUS all use it (checked against a
 * cache dump and the block list client scripts, see tools/cache-dump). Spria (9) shares Turael's block
 * list but has her own pool, so she is not listed here.
 * {@link SlayerAssigner} covers every master spot, with requirements, costs and stand-ins.
 * Block slots 1 to 6 open at 50, 100 ... 300 quest points. On the seven slot lists the last varbit is the
 * slot opened by the elite Lumbridge & Draynor diary. Mortimer has two slots only (wiki Slayer Rewards:
 * "In Mortimer's case, there are two block slots available"), taken here to open at 50 and 100 quest
 * points like everyone's first two.
 */
@Getter
@AllArgsConstructor
public enum SlayerMaster
{
	TURAEL("Turael", 1, true, new int[]{
			VarbitID.SLAYER_BLOCKED_TURAEL_1, VarbitID.SLAYER_BLOCKED_TURAEL_2, VarbitID.SLAYER_BLOCKED_TURAEL_3,
			VarbitID.SLAYER_BLOCKED_TURAEL_4, VarbitID.SLAYER_BLOCKED_TURAEL_5, VarbitID.SLAYER_BLOCKED_TURAEL_6,
			VarbitID.SLAYER_BLOCKED_TURAEL_DIARY}),
	MAZCHNA("Mazchna", 2, true, new int[]{
			VarbitID.SLAYER_BLOCKED_MAZCHNA_1, VarbitID.SLAYER_BLOCKED_MAZCHNA_2, VarbitID.SLAYER_BLOCKED_MAZCHNA_3,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_4, VarbitID.SLAYER_BLOCKED_MAZCHNA_5, VarbitID.SLAYER_BLOCKED_MAZCHNA_6,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_DIARY}),
	VANNAKA("Vannaka", 3, true, new int[]{
			VarbitID.SLAYER_BLOCKED_VANNAKA_1, VarbitID.SLAYER_BLOCKED_VANNAKA_2, VarbitID.SLAYER_BLOCKED_VANNAKA_3,
			VarbitID.SLAYER_BLOCKED_VANNAKA_4, VarbitID.SLAYER_BLOCKED_VANNAKA_5, VarbitID.SLAYER_BLOCKED_VANNAKA_6,
			VarbitID.SLAYER_BLOCKED_VANNAKA_DIARY}),
	CHAELDAR("Chaeldar", 4, true, new int[]{
			VarbitID.SLAYER_BLOCKED_CHAELDAR_1, VarbitID.SLAYER_BLOCKED_CHAELDAR_2, VarbitID.SLAYER_BLOCKED_CHAELDAR_3,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_4, VarbitID.SLAYER_BLOCKED_CHAELDAR_5, VarbitID.SLAYER_BLOCKED_CHAELDAR_6,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_DIARY}),
	KONAR("Konar", 8, true, new int[]{
			VarbitID.SLAYER_BLOCKED_KONAR_1, VarbitID.SLAYER_BLOCKED_KONAR_2, VarbitID.SLAYER_BLOCKED_KONAR_3,
			VarbitID.SLAYER_BLOCKED_KONAR_4, VarbitID.SLAYER_BLOCKED_KONAR_5, VarbitID.SLAYER_BLOCKED_KONAR_6,
			VarbitID.SLAYER_BLOCKED_KONAR_DIARY}),
	NIEVE("Nieve", 6, true, new int[]{
			VarbitID.SLAYER_BLOCKED_NIEVE_1, VarbitID.SLAYER_BLOCKED_NIEVE_2, VarbitID.SLAYER_BLOCKED_NIEVE_3,
			VarbitID.SLAYER_BLOCKED_NIEVE_4, VarbitID.SLAYER_BLOCKED_NIEVE_5, VarbitID.SLAYER_BLOCKED_NIEVE_6,
			VarbitID.SLAYER_BLOCKED_NIEVE_DIARY}),
	DURADEL("Duradel", 5, true, new int[]{
			VarbitID.SLAYER_BLOCKED_DURADEL_1, VarbitID.SLAYER_BLOCKED_DURADEL_2, VarbitID.SLAYER_BLOCKED_DURADEL_3,
			VarbitID.SLAYER_BLOCKED_DURADEL_4, VarbitID.SLAYER_BLOCKED_DURADEL_5, VarbitID.SLAYER_BLOCKED_DURADEL_6,
			VarbitID.SLAYER_BLOCKED_DURADEL_DIARY}),
	KRYSTILIA("Krystilia", 7, true, new int[]{
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_1, VarbitID.SLAYER_BLOCKED_KRYSTILIA_2, VarbitID.SLAYER_BLOCKED_KRYSTILIA_3,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_4, VarbitID.SLAYER_BLOCKED_KRYSTILIA_5, VarbitID.SLAYER_BLOCKED_KRYSTILIA_6,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_DIARY}),
	MORTIMER("Mortimer", 10, false, new int[]{
			VarbitID.SLAYER_BLOCKED_MORTIMER_1, VarbitID.SLAYER_BLOCKED_MORTIMER_2});

	/** Quest points per regular block slot. Slot n (1 to 6) needs n times this. */
	static final int QUEST_POINTS_PER_BLOCK_SLOT = 50;

	private final String displayName;
	/** The game's master id, as stored in SlayerMasterTask.COL_MASTER_ID and the SLAYER_MASTER varbit. */
	private final int id;
	/** Whether the last block varbit is the slot the elite Lumbridge & Draynor diary opens. */
	private final boolean diarySlot;
	private final int[] blockVarbits;

	/** The master for a game master id, or null for Spria, the leagues pool and unknown ids. */
	static SlayerMaster forId(int id)
	{
		for (SlayerMaster master : values())
		{
			if (master.id == id) return master;
		}
		return null;
	}

	/** The block list a master spot uses: Spria shares Turael's. Null for an unknown master. */
	static SlayerMaster forAssigner(SlayerAssigner assigner)
	{
		if (assigner == null) return null;
		return assigner == SlayerAssigner.SPRIA ? TURAEL : forId(assigner.getId());
	}

	/** The master spot whose pool this block list goes with. */
	SlayerAssigner assigner()
	{
		return SlayerAssigner.forId(id);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
