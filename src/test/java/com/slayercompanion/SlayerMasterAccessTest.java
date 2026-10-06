package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerMasterAccess.Option;
import com.slayercompanion.SlayerMasterAccess.State;
import com.slayercompanion.SlayerPayout.Diaries;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class SlayerMasterAccessTest
{
	/** A maxed account after While Guthix Sleeps, Monkey Madness II, A Porcine of Interest and Fallen From Grace. */
	static State maxed()
	{
		return new State(126, 99, true, true, true, 2, true, 40, 20, true, Diaries.NONE);
	}

	static State fresh()
	{
		return new State(3, 1, false, false, false, 0, false, 0, 0, false, Diaries.NONE);
	}

	static State with(State s, int combat, int slayer)
	{
		return new State(combat, slayer, s.isPriestInPeril(), s.isLostCity(), s.isShiloVillage(), s.getGuthixSleeps(),
				s.isSteve(), s.getPorcine(), s.getFallenFromGrace(), s.isMortimerIntroduced(), s.getDiaries());
	}

	static State guthixSleeps(State s, int stage)
	{
		return new State(s.getCombatLevel(), s.getSlayerLevel(), s.isPriestInPeril(), s.isLostCity(), s.isShiloVillage(),
				stage, s.isSteve(), s.getPorcine(), s.getFallenFromGrace(), s.isMortimerIntroduced(), s.getDiaries());
	}

	private static State quests(State s, int porcine, int fallenFromGrace)
	{
		return new State(s.getCombatLevel(), s.getSlayerLevel(), s.isPriestInPeril(), s.isLostCity(), s.isShiloVillage(),
				s.getGuthixSleeps(), s.isSteve(), porcine, fallenFromGrace, s.isMortimerIntroduced(), s.getDiaries());
	}

	@Test
	public void whileGuthixSleepsSwapsThreeMasters()
	{
		// Before the masters leave to fight Lucien
		State s = guthixSleeps(maxed(), 0);
		assertEquals("Turael", SlayerMasterAccess.npcName(SlayerAssigner.TURAEL, s));
		assertEquals("Mazchna", SlayerMasterAccess.npcName(SlayerAssigner.MAZCHNA, s));
		assertEquals("Duradel", SlayerMasterAccess.npcName(SlayerAssigner.DURADEL, s));
		// While they are away Achtryn stands in for Mazchna
		s = guthixSleeps(maxed(), 1);
		assertEquals("Aya", SlayerMasterAccess.npcName(SlayerAssigner.TURAEL, s));
		assertEquals("Achtryn", SlayerMasterAccess.npcName(SlayerAssigner.MAZCHNA, s));
		assertEquals("Kuradal", SlayerMasterAccess.npcName(SlayerAssigner.DURADEL, s));
		// Turael and Duradel die in the quest, Mazchna comes back
		s = guthixSleeps(maxed(), 2);
		assertEquals("Aya", SlayerMasterAccess.npcName(SlayerAssigner.TURAEL, s));
		assertEquals("Mazchna", SlayerMasterAccess.npcName(SlayerAssigner.MAZCHNA, s));
		assertEquals("Kuradal", SlayerMasterAccess.npcName(SlayerAssigner.DURADEL, s));
	}

	@Test
	public void steveReplacesNieve()
	{
		assertEquals("Steve", SlayerMasterAccess.npcName(SlayerAssigner.NIEVE, maxed()));
		assertEquals("Nieve", SlayerMasterAccess.npcName(SlayerAssigner.NIEVE, fresh()));
		assertEquals("Konar", SlayerMasterAccess.npcName(SlayerAssigner.KONAR, maxed()));
	}

	@Test
	public void aMaxedAccountCanUseEveryMaster()
	{
		for (Option o : SlayerMasterAccess.options(maxed()))
		{
			assertTrue(o.getMaster() + " " + o.getMissing(), o.isUsable());
		}
	}

	@Test
	public void aNewAccountHasOnlyTurael()
	{
		final List<Option> options = SlayerMasterAccess.options(fresh());
		assertEquals(SlayerAssigner.values().length, options.size());
		for (Option o : options)
		{
			assertEquals(o.getMaster().name(), o.getMaster() == SlayerAssigner.TURAEL, o.isUsable());
		}
		assertEquals("combat level 20", SlayerMasterAccess.find(options, SlayerAssigner.MAZCHNA).getMissing());
		assertEquals("A Porcine of Interest", SlayerMasterAccess.find(options, SlayerAssigner.SPRIA).getMissing());
		// Krystilia has no combat requirement but refuses level 1 Slayer
		assertEquals("Slayer level 2", SlayerMasterAccess.find(options, SlayerAssigner.KRYSTILIA).getMissing());
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.KRYSTILIA, with(fresh(), 3, 2)));
	}

	@Test
	public void combatAndSlayerLevelsAndQuestsGateTheMasters()
	{
		final State s = maxed();
		assertEquals("combat level 100", SlayerMasterAccess.missing(SlayerAssigner.DURADEL, with(s, 99, 98)));
		assertEquals("Slayer level 50", SlayerMasterAccess.missing(SlayerAssigner.DURADEL, with(s, 100, 49)));
		assertEquals("combat level 85", SlayerMasterAccess.missing(SlayerAssigner.NIEVE, with(s, 84, 60)));
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.KONAR, with(s, 75, 1)));
		assertEquals("Shilo Village", SlayerMasterAccess.missing(SlayerAssigner.DURADEL,
				new State(126, 99, true, true, false, 2, true, 40, 20, true, Diaries.NONE)));
		assertEquals("Lost City", SlayerMasterAccess.missing(SlayerAssigner.CHAELDAR,
				new State(126, 99, true, false, true, 2, true, 40, 20, true, Diaries.NONE)));
		assertEquals("Priest in Peril", SlayerMasterAccess.missing(SlayerAssigner.MAZCHNA,
				new State(126, 99, false, true, true, 2, true, 40, 20, true, Diaries.NONE)));
	}

	@Test
	public void ninetyNineSlayerWaivesCombatLevels()
	{
		// The Slayer cape stands in for the combat requirement, the Slayer level still counts
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.DURADEL, with(maxed(), 40, 99)));
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.MORTIMER, with(maxed(), 40, 99)));
	}

	@Test
	public void spriaAndMortimerFollowTheirQuestStages()
	{
		assertEquals("A Porcine of Interest", SlayerMasterAccess.missing(SlayerAssigner.SPRIA, quests(maxed(), 39, 20)));
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.SPRIA, quests(maxed(), SlayerMasterAccess.PORCINE_DONE, 20)));
		assertEquals("progress in Fallen From Grace", SlayerMasterAccess.missing(SlayerAssigner.MORTIMER, quests(maxed(), 40, 11)));
		assertNull(SlayerMasterAccess.missing(SlayerAssigner.MORTIMER, quests(maxed(), 40, SlayerMasterAccess.MORTIMER_STAGE)));
		assertEquals("Slayer level 70", SlayerMasterAccess.missing(SlayerAssigner.MORTIMER, with(maxed(), 126, 69)));
		// His Assignment op stays hidden until MORTIMER_INTRODUCTION is set (tools/cache-dump, masterNpcs)
		final State s = maxed();
		assertEquals("an introduction", SlayerMasterAccess.missing(SlayerAssigner.MORTIMER, new State(s.getCombatLevel(),
				s.getSlayerLevel(), true, true, true, 2, true, 40, 20, false, Diaries.NONE)));
	}

	@Test
	public void standInsFollowTheCachesMultiNpcs()
	{
		final SlayerCacheFixture cache = SlayerCacheFixture.get();
		// Burthorpe, Canifis and Shilo Village switch together on WGS_HERO_PART_1_VIS
		for (int stage = 0; stage <= 2; stage++)
		{
			final State s = guthixSleeps(maxed(), stage);
			assertEquals("stage " + stage, new TreeSet<>(Arrays.asList(SlayerMasterAccess.npcName(SlayerAssigner.TURAEL, s),
					SlayerMasterAccess.npcName(SlayerAssigner.MAZCHNA, s), SlayerMasterAccess.npcName(SlayerAssigner.DURADEL, s))),
					cache.mastersShown(VarbitID.WGS_HERO_PART_1_VIS, stage));
		}
		assertEquals(Collections.singleton("Nieve"), cache.mastersShown(VarbitID.MM2_SLAYER_MASTER, 0));
		assertEquals(Collections.singleton("Steve"), cache.mastersShown(VarbitID.MM2_SLAYER_MASTER, 1));
		// Spria gives tasks only once the quest is complete
		assertEquals(Collections.singleton(SlayerMasterAccess.PORCINE_DONE), cache.valuesShowing(VarbitID.PORCINE, "Spria"));
		assertEquals(SlayerMasterAccess.PORCINE_DONE, cache.questEnd("QUEST_PORCINEOFINTEREST"));
		// Mortimer from stage 12 to the end of Fallen From Grace
		final SortedSet<Integer> mortimer = cache.valuesShowing(VarbitID.FFG, "Mortimer");
		assertEquals(SlayerMasterAccess.MORTIMER_STAGE, (int) mortimer.first());
		assertEquals(cache.questEnd("QUEST_FALLENFROMGRACE"), (int) mortimer.last());
		assertEquals(cache.questEnd("QUEST_FALLENFROMGRACE") - SlayerMasterAccess.MORTIMER_STAGE + 1, mortimer.size());

		// No other varbit or varp moves a task-giving master, so these four are all the access state needs
		final Set<Integer> read = new HashSet<>(Arrays.asList(VarbitID.WGS_HERO_PART_1_VIS, VarbitID.MM2_SLAYER_MASTER,
				VarbitID.PORCINE, VarbitID.FFG));
		for (SlayerCacheFixture.Spot spot : cache.masterSpots())
		{
			boolean givesTasks = false;
			for (int npc : spot.transforms) givesTasks |= cache.masterName(npc) != null;
			if (givesTasks) assertTrue("spot " + spot.npc + " varbit " + spot.varbit, spot.varp == -1 && read.contains(spot.varbit));
		}
		// Every task-giving NPC is one of the names the plugin shows
		final Set<String> named = new HashSet<>(Arrays.asList("Aya", "Achtryn", "Steve", "Kuradal", "Konar quo Maten"));
		for (SlayerAssigner a : SlayerAssigner.values()) named.add(a.getDisplayName());
		for (SlayerCacheFixture.Spot spot : cache.masterSpots())
		{
			for (int npc : spot.transforms)
			{
				if (cache.masterName(npc) != null) assertTrue(cache.masterName(npc), named.contains(cache.masterName(npc)));
			}
		}
	}

	@Test
	public void replacementsKeepTheGameMasterId()
	{
		// Aya, Achtryn, Steve and Kuradal take over a spot, so the SLAYER_MASTER id still names it
		assertEquals(SlayerAssigner.TURAEL, SlayerAssigner.forId(1));
		assertEquals(SlayerAssigner.SPRIA, SlayerAssigner.forId(9));
		assertEquals(SlayerAssigner.MORTIMER, SlayerAssigner.forId(10));
		assertNull(SlayerAssigner.forId(0));
		assertNull(SlayerAssigner.forId(11));
		for (SlayerMaster m : SlayerMaster.values())
		{
			assertEquals(m.name(), SlayerAssigner.forId(m.getId()).name());
		}
	}

	@Test
	public void blockAndCancelCostsFollowTheGame()
	{
		// Client scripts 823 (block) and 9741 (cancel), the same as the wiki's Slayer Master table
		final int[][] costs = {{1, 40}, {9, 40}, {2, 50}, {3, 60}, {4, 70}, {8, 80}, {6, 90}, {5, 100}, {7, 100}, {10, 120}};
		for (int[] c : costs)
		{
			assertEquals("master " + c[0], c[1], SlayerAssigner.forId(c[0]).getBlockCost());
		}
		for (SlayerAssigner a : SlayerAssigner.values())
		{
			assertEquals(a.name(), a == SlayerAssigner.MORTIMER ? 100 : 30, a.getCancelCost());
		}
		assertFalse(SlayerAssigner.KRYSTILIA.getStreak() == SlayerAssigner.Streak.MAIN);
	}
}
