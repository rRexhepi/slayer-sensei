package com.slayercompanion.data;

import lombok.Data;

/**
 * One task's notes, read from /slayer_tasks.json and /slayer_tasks_supplement.json, which were written from the
 * OSRS Wiki. All fields but the rating are display strings, so an entry is fixed by editing the JSON.
 * The sidebar's verdict is worked out from the rating for the selected goal, so the note keeps only facts
 * beyond XP, profit and points opinions, and must never contradict the rating.
 */
@Data
public class SlayerTask
{
	private String task;
	/** Shown only when the game cache cannot be read. The sidebar builds the line from the cache otherwise. */
	private String assignedBy;
	/** Facts the ratings do not carry, such as which bosses count or which quest opens the area. Often null. */
	private String note;
	private String bestLocation;
	private String multiOrSingle;
	private String killStyle;
	private String weakness;
	private String monsterAttackStyle;
	private String protectionPrayer;
	private String requiredItems;
	private String cannon;
	private boolean hasSuperior;
	private String notableDrops;
	/** XP, profit and speed ratings, set for every task a master can assign. Null for the rest. */
	private TaskRating rating;
}
