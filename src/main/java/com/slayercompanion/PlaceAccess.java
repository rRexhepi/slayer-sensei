package com.slayercompanion;

import com.slayercompanion.data.Access;
import com.slayercompanion.data.SlayerLocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Whether a place is open for an account. A place names the quests that must be finished or started and the real
 * levels it needs (/slayer_areas.json, from the OSRS Wiki). The checks are pure, so tests run them without a client. A
 * quest or skill the client does not know, a quest that was not read and a level that is not loaded lock nothing, so
 * data newer than the client never shuts a player out.
 */
@Slf4j
final class PlaceAccess
{
	/** The quest states and real levels the checks compare with. */
	@Value
	static class Account
	{
		/** Nothing read: every place is open. */
		static final Account UNKNOWN = new Account(Collections.emptyMap(), new int[0]);

		/** States of the quests read, by Quest constant name. */
		Map<String, QuestState> quests;
		/** Real levels by Skill ordinal, in the order of Client.getRealSkillLevels. */
		int[] levels;

		/**
		 * Reads the named quests with {@code state} and keeps a copy of the levels. A name the client does not know is
		 * skipped, and a quest whose state cannot be read stays unknown until the next read.
		 */
		static Account read(Collection<String> questNames, Function<Quest, QuestState> state, int[] levels)
		{
			final Map<String, QuestState> quests = new LinkedHashMap<>();
			for (String name : questNames)
			{
				final Quest quest = quest(name);
				if (quest == null) continue;
				try
				{
					final QuestState read = state.apply(quest);
					if (read != null) quests.put(name, read);
				}
				catch (RuntimeException ex)
				{
					log.debug("Slayer Sensei could not read the state of {}", name, ex);
				}
			}
			return new Account(Collections.unmodifiableMap(quests), levels == null ? new int[0] : levels.clone());
		}
	}

	/** Open, or locked with what the account lacks. A note can come with either. */
	@Value
	static class Verdict
	{
		static final Verdict OPEN = new Verdict(Collections.emptyList(), null);

		/** What the account lacks, such as "Priest in Peril", "Sins of the Father started" or "60 Sailing". */
		List<String> missing;
		/** A requirement the plugin cannot check, shown with the place and never locking it, or null. */
		String note;

		boolean isLocked()
		{
			return !missing.isEmpty();
		}

		/** "Needs Priest in Peril and 60 Sailing", or null when the place is open. */
		String needs()
		{
			if (missing.isEmpty()) return null;
			final StringBuilder sb = new StringBuilder("Needs ");
			for (int i = 0; i < missing.size(); i++)
			{
				if (i > 0) sb.append(i == missing.size() - 1 ? " and " : ", ");
				sb.append(missing.get(i));
			}
			return sb.toString();
		}

		/** The sentence for a tooltip or the sidebar: what the place needs, then the note. Null when there is neither. */
		String sentence()
		{
			final String needs = needs();
			if (needs == null) return note;
			return note == null ? needs + "." : needs + ". " + note;
		}
	}

	private PlaceAccess()
	{
	}

	/** What a place needs for one task: the place's own access, then the task's own access there. */
	static List<Access> of(SlayerLocation location, int taskId)
	{
		final List<Access> out = new ArrayList<>(2);
		if (location == null) return out;
		if (location.getAccess() != null) out.add(location.getAccess());
		final SlayerLocation.TaskEntry task = Destination.taskEntry(location, taskId);
		if (task != null && task.getAccess() != null) out.add(task.getAccess());
		return out;
	}

	/** Every quest the places name for the task, so a reader can read only those. */
	static Set<String> quests(Collection<SlayerLocation> places, int taskId)
	{
		final Set<String> quests = new LinkedHashSet<>();
		for (SlayerLocation location : places)
		{
			for (Access access : of(location, taskId))
			{
				if (access.getQuestsFinished() != null) quests.addAll(access.getQuestsFinished());
				if (access.getQuestsStarted() != null) quests.addAll(access.getQuestsStarted());
			}
		}
		return quests;
	}

	/**
	 * Whether the account can enter a place with these requirements. A quest that must be finished locks unless it is
	 * finished, a quest that must be started locks only while it is not started, and a level locks while the real
	 * level is below it.
	 */
	static Verdict check(List<Access> requirements, Account account)
	{
		final Set<String> finished = new LinkedHashSet<>();
		final Set<String> started = new LinkedHashSet<>();
		final Map<String, Integer> levels = new LinkedHashMap<>();
		final List<String> notes = new ArrayList<>();
		for (Access access : requirements)
		{
			if (access == null) continue;
			if (access.getQuestsFinished() != null) finished.addAll(access.getQuestsFinished());
			if (access.getQuestsStarted() != null) started.addAll(access.getQuestsStarted());
			if (access.getSkills() != null)
			{
				for (Map.Entry<String, Integer> skill : access.getSkills().entrySet())
				{
					levels.merge(skill.getKey(), skill.getValue() == null ? 0 : skill.getValue(), Math::max);
				}
			}
			if (access.getNote() != null && !access.getNote().trim().isEmpty()) notes.add(access.getNote().trim());
		}
		final List<String> missing = new ArrayList<>();
		for (String name : finished)
		{
			final Quest quest = quest(name);
			final QuestState state = account.getQuests().get(name);
			if (quest != null && state != null && state != QuestState.FINISHED) missing.add(quest.getName());
		}
		for (String name : started)
		{
			final Quest quest = quest(name);
			final QuestState state = account.getQuests().get(name);
			// A quest that must be finished already says it
			if (quest != null && state == QuestState.NOT_STARTED && !finished.contains(name)) missing.add(quest.getName() + " started");
		}
		for (Map.Entry<String, Integer> level : levels.entrySet())
		{
			final Skill skill = skill(level.getKey());
			final int have = skill == null || skill.ordinal() >= account.getLevels().length ? 0 : account.getLevels()[skill.ordinal()];
			// A level of 0 is not loaded yet
			if (have > 0 && have < level.getValue()) missing.add(level.getValue() + " " + skill.getName());
		}
		if (missing.isEmpty() && notes.isEmpty()) return Verdict.OPEN;
		return new Verdict(Collections.unmodifiableList(missing), notes.isEmpty() ? null : String.join(" ", notes));
	}

	/** The quest of a constant name, or null when this client does not know it. */
	static Quest quest(String name)
	{
		if (name == null) return null;
		try
		{
			return Quest.valueOf(name);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	/** The skill of a constant name, or null when this client does not know it. */
	static Skill skill(String name)
	{
		if (name == null) return null;
		try
		{
			return Skill.valueOf(name);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}
}
