package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/**
 * Which masters this player can get a task from, and which NPC stands at each spot. {@link #options} is
 * the pure core. {@link #read} is the thin reader of client state.
 * <p>
 * Who stands where follows the game's own varbits, checked against the cache (tools/cache-dump, section
 * masterNpcs). The masters at Burthorpe, Canifis and Shilo Village are one multi-NPC each, switched by
 * WGS_HERO_PART_1_VIS, and the Tree Gnome Stronghold one by MM2_SLAYER_MASTER. The NPC Contact spell names
 * them from the same varbits (client script 2615).
 * <ul>
 * <li>While Guthix Sleeps: 0 until Turael, Mazchna and Duradel leave to fight Lucien, 1 while they are
 * away, 2 after the quest. Aya replaces Turael and Kuradal replaces Duradel from 1 on, as both die in the
 * quest. Achtryn stands in for Mazchna at 1 only.</li>
 * <li>Monkey Madness II: Steve replaces Nieve once MM2_SLAYER_MASTER is 1.</li>
 * <li>Spria gives tasks once A Porcine of Interest is complete: PORCINE 40, the quest's end state.</li>
 * <li>Mortimer stands in Wyrmscraig Cavern from Fallen From Grace stage 12 to its end state, 20. His
 * Assignment op stays hidden until MORTIMER_INTRODUCTION is set.</li>
 * </ul>
 */
@Slf4j
final class SlayerMasterAccess
{
	/** A Porcine of Interest's end state in the quest table. The master version of Spria shows only then. */
	static final int PORCINE_DONE = 40;
	/** The first Fallen From Grace stage at which Mortimer stands at his spot. */
	static final int MORTIMER_STAGE = 12;
	/** WGS_HERO_PART_1_VIS while the masters are away fighting Lucien. */
	static final int GUTHIX_SLEEPS_AWAY = 1;
	/** At 99 Slayer the Slayer cape stands in for any combat requirement. */
	static final int CAPE_LEVEL = 99;

	/** Everything master access and payouts depend on. */
	@Value
	static class State
	{
		int combatLevel;
		int slayerLevel;
		boolean priestInPeril;
		boolean lostCity;
		boolean shiloVillage;
		/** WGS_HERO_PART_1_VIS. */
		int guthixSleeps;
		/** MM2_SLAYER_MASTER is 1. */
		boolean steve;
		/** PORCINE, the quest's progress. */
		int porcine;
		/** FFG, the quest's progress. */
		int fallenFromGrace;
		/** MORTIMER_INTRODUCTION is set. Until then his Assignment, Trade and Rewards ops are hidden. */
		boolean mortimerIntroduced;
		SlayerPayout.Diaries diaries;
	}

	/** One master spot as this player finds it. */
	@Value
	static class Option
	{
		SlayerAssigner master;
		/** The NPC at the spot, e.g. Aya once she has replaced Turael. */
		String npc;
		/** The first requirement the player lacks, e.g. "combat level 100", or null when usable. */
		String missing;

		boolean isUsable()
		{
			return missing == null;
		}
	}

	/** Quest completions behind master requirements. Each read runs a client script, so callers cache it. */
	@Value
	static class Quests
	{
		static final Quests NONE = new Quests(false, false, false);

		boolean priestInPeril;
		boolean lostCity;
		boolean shiloVillage;

		/** Client thread only. A failed read counts the quests as not done until the next read. */
		static Quests read(Client client)
		{
			try
			{
				return new Quests(finished(client, Quest.PRIEST_IN_PERIL), finished(client, Quest.LOST_CITY),
						finished(client, Quest.SHILO_VILLAGE));
			}
			catch (RuntimeException ex)
			{
				log.debug("Slayer Sensei could not read quest states", ex);
				return NONE;
			}
		}

		private static boolean finished(Client client, Quest quest)
		{
			return quest.getState(client) == QuestState.FINISHED;
		}
	}

	private SlayerMasterAccess()
	{
	}

	/** Levels and varbits are read on every call. Quest completions come from the caller's cache. Client thread. */
	static State read(Client client, Quests quests)
	{
		final Player player = client.getLocalPlayer();
		return new State(player == null ? 0 : player.getCombatLevel(),
				client.getRealSkillLevel(Skill.SLAYER),
				quests.isPriestInPeril(), quests.isLostCity(), quests.isShiloVillage(),
				client.getVarbitValue(VarbitID.WGS_HERO_PART_1_VIS),
				client.getVarbitValue(VarbitID.MM2_SLAYER_MASTER) == 1,
				client.getVarbitValue(VarbitID.PORCINE),
				client.getVarbitValue(VarbitID.FFG),
				client.getVarbitValue(VarbitID.MORTIMER_INTRODUCTION) != 0,
				new SlayerPayout.Diaries(client.getVarbitValue(VarbitID.WESTERN_ELITE_REWARD) != 0,
						client.getVarbitValue(VarbitID.KOUREND_ELITE_REWARD) != 0));
	}

	/** Every master spot in {@link SlayerAssigner} order. */
	static List<Option> options(State state)
	{
		final List<Option> out = new ArrayList<>();
		for (SlayerAssigner master : SlayerAssigner.values())
		{
			out.add(new Option(master, npcName(master, state), missing(master, state)));
		}
		return Collections.unmodifiableList(out);
	}

	/** The option for one master. Never null for a list made by {@link #options}. */
	static Option find(List<Option> options, SlayerAssigner master)
	{
		for (Option o : options)
		{
			if (o.getMaster() == master) return o;
		}
		return null;
	}

	/** The NPC the player finds at the master's spot. */
	static String npcName(SlayerAssigner master, State state)
	{
		switch (master)
		{
			case TURAEL:
				return state.getGuthixSleeps() != 0 ? "Aya" : "Turael";
			case MAZCHNA:
				return state.getGuthixSleeps() == GUTHIX_SLEEPS_AWAY ? "Achtryn" : "Mazchna";
			case DURADEL:
				return state.getGuthixSleeps() != 0 ? "Kuradal" : "Duradel";
			case NIEVE:
				return state.isSteve() ? "Steve" : "Nieve";
			default:
				return master.getDisplayName();
		}
	}

	/** The first requirement the player lacks for the master, or null when the master gives them tasks. */
	static String missing(SlayerAssigner master, State state)
	{
		if (state.getCombatLevel() < master.getCombatLevel() && state.getSlayerLevel() < CAPE_LEVEL)
		{
			return "combat level " + master.getCombatLevel();
		}
		if (state.getSlayerLevel() < master.getSlayerLevel())
		{
			return "Slayer level " + master.getSlayerLevel();
		}
		switch (master.getQuestNeed())
		{
			case PRIEST_IN_PERIL:
				return state.isPriestInPeril() ? null : "Priest in Peril";
			case LOST_CITY:
				return state.isLostCity() ? null : "Lost City";
			case SHILO_VILLAGE:
				return state.isShiloVillage() ? null : "Shilo Village";
			case PORCINE_OF_INTEREST:
				return state.getPorcine() >= PORCINE_DONE ? null : "A Porcine of Interest";
			case FALLEN_FROM_GRACE_PART:
				if (state.getFallenFromGrace() < MORTIMER_STAGE) return "progress in Fallen From Grace";
				return state.isMortimerIntroduced() ? null : "an introduction";
			default:
				return null;
		}
	}
}
