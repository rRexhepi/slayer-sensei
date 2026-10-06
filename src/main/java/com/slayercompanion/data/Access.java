package com.slayercompanion.data;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * What a player needs to get into a place and reach the task monsters there, from /slayer_areas.json. A place has one
 * for every task, and a task entry adds its own when only its monsters lie behind a further gate. Only hard gates
 * the OSRS Wiki states are here: optional shortcuts, entry fees and items to wear or carry are not.
 */
@Data
public class Access
{
	/** Quests that must be finished, as net.runelite.api.Quest constant names. */
	private List<String> questsFinished;
	/** Quests that must at least be started, for places the wiki opens during the quest. */
	private List<String> questsStarted;
	/** Real levels needed, by net.runelite.api.Skill constant name. Only levels the wiki says cannot be boosted. */
	private Map<String, Integer> skills;
	/** A requirement the plugin cannot check, shown with the place and never locking it. */
	private String note;
	/** Where every requirement and the note come from. */
	private String source;
}
