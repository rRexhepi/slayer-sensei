package com.slayercompanion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.DBTableID.SlayerUnlock.Row;
import net.runelite.api.gameval.VarbitID;

/**
 * Advice layer over the reward shop. Names, costs and ownership bits come from the cache at runtime
 * ({@link SlayerGameData}). The names, costs and varbits here are fallbacks for when the cache tables
 * cannot be read, and SlayerRewardCoverageTest keeps them equal to a dump of the live cache. Every
 * SlayerUnlock row is listed here except the four rewards taken out of the shop (see that test).
 * <p>
 * The kind says how {@link SlayerSpend} values a reward for the goal. Level requirements are the Slayer level of
 * the reward's task and the skill the reward itself needs. The fact, when there is one, is something the line
 * should say whatever the goal.
 */
@Getter
@AllArgsConstructor
enum SlayerReward
{
	BIGGER_AND_BADDER(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, "Bigger and Badder", 50, Kind.GENERAL, 0, 0, 0, null,
			"It can be toggled off for free"),
	MALEVOLENT_MASQUERADE(Row.SLAYER_HELM_UNLOCKED, VarbitID.SLAYER_HELM_UNLOCKED, "Malevolent Masquerade", 400, Kind.GENERAL, 0, 55, 0, null,
			"Making the helmet takes a black mask"),
	GARGOYLE_SMASHER(Row.SLAYER_REWARDS_AUTOKILL_GARGOYLES, VarbitID.SLAYER_AUTOKILL_GARGOYLES, "Gargoyle Smasher", 120, Kind.TASK_HELPER, 75, 0, 0, "gargoyles",
			"A rock hammer must still be in your inventory"),
	STOP_THE_WYVERN(Row.SLAYER_REWARDS_UNLOCK_FOSSILWYVERNBLOCK, VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK, "Stop the Wyvern", 500, Kind.BLOCKER, 66, 0, 0, "fossil island wyverns",
			null),
	RING_BLING(Row.SLAYER_RING_UNLOCKED, VarbitID.SLAYER_RING_UNLOCKED, "Ring Bling", 150, Kind.GENERAL, 0, 75, 0, null,
			"The rings also work as a Slayer gem"),
	LIKE_A_BOSS(Row.SLAYER_REWARDS_UNLOCK_BOSSES, VarbitID.SLAYER_UNLOCK_BOSSES, "Like a Boss", 200, Kind.TASK_UNLOCK, 0, 0, 0, "boss",
			"You pick how many kills"),
	TASK_STORAGE(Row.SLAYER_REWARDS_UNLOCK_STORAGE, VarbitID.SLAYER_UNLOCK_STORAGE, "Task Storage", 500, Kind.GENERAL, 0, 0, 0, null,
			null),
	HOT_STUFF(Row.SLAYER_REWARDS_UNLOCK_TZHAAR, VarbitID.SLAYER_UNLOCK_TZHAAR, "Hot Stuff", 100, Kind.TASK_UNLOCK, 0, 0, 0, "tzhaar",
			"You can fight Jad instead, or Zuk once you have killed him"),
	DOUBLE_TROUBLE(Row.SLAYER_REWARDS_UNLOCK_GROTESQUEKILLS, VarbitID.SLAYER_UNLOCK_GROTESQUEKILLS, "Double Trouble", 500, Kind.GENERAL, 75, 0, 0, "gargoyles",
			null),
	BROADER_FLETCHING(Row.SLAYER_AMMO_UNLOCKED, VarbitID.SLAYER_AMMO_UNLOCKED, "Broader Fletching", 300, Kind.GENERAL, 0, 0, 52, null,
			null),
	SHROOM_SPRAYER(Row.SLAYER_REWARDS_AUTOKILL_ZYGOMITES, VarbitID.SLAYER_AUTOKILL_ZYGOMITES, "'Shroom Sprayer", 110, Kind.TASK_HELPER, 57, 0, 0, "mutated zygomites",
			"Fungicide spray must still be in your inventory"),
	SLUG_SALTER(Row.SLAYER_REWARDS_AUTOKILL_ROCKSLUGS, VarbitID.SLAYER_AUTOKILL_ROCKSLUGS, "Slug Salter", 10, Kind.TASK_HELPER, 20, 0, 0, "rockslugs",
			"A bag of salt must still be in your inventory"),
	REPTILE_FREEZER(Row.SLAYER_REWARDS_AUTOKILL_DESERTLIZARDS, VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS, "Reptile Freezer", 10, Kind.TASK_HELPER, 22, 0, 0, "lizards",
			"An ice cooler must still be in your inventory"),
	DULY_NOTED(Row.SLAYER_REWARDS_UNLOCK_NOTEDMITHRILBARS, VarbitID.SLAYER_UNLOCK_NOTEDMITHRILBARS, "Duly Noted", 200, Kind.GENERAL, 0, 0, 0, "metal dragons",
			null),

	SEEING_RED(Row.SLAYER_REWARDS_UNLOCK_REDDRAGONS, VarbitID.SLAYER_UNLOCK_REDDRAGONS, "Seeing Red", 50, Kind.TASK_UNLOCK, 0, 0, 0, "red dragons",
			null),
	WATCH_THE_BIRDIE(Row.SLAYER_REWARDS_UNLOCK_AVIANSIES, VarbitID.SLAYER_UNLOCK_AVIANSIES, "Watch the Birdie", 80, Kind.TASK_UNLOCK, 0, 0, 0, "aviansies",
			null),
	REPTILE_GOT_RIPPED(Row.SLAYER_REWARDS_UNLOCK_LIZARDMEN, VarbitID.SLAYER_UNLOCK_LIZARDMEN, "Reptile Got Ripped", 75, Kind.TASK_UNLOCK, 0, 0, 0, "lizardmen",
			"Shamans count and drop the dragon warhammer"),
	BASILOCKED(Row.SLAYER_REWARDS_UNLOCK_BASILISK, VarbitID.SLAYER_UNLOCK_BASILISK, "Basilocked", 80, Kind.TASK_UNLOCK, 0, 0, 0, "basilisks",
			"Basilisk knights drop the basilisk jaw"),
	ACTUAL_VAMPYRE_SLAYER(Row.SLAYER_REWARDS_UNLOCK_VAMPYRES, VarbitID.SLAYER_UNLOCK_VAMPYRES, "Actual Vampyre Slayer", 80, Kind.TASK_UNLOCK, 0, 0, 0, "vampyres",
			null),
	WARPED_REALITY(Row.SLAYER_REWARDS_UNLOCK_WARPED_CREATURES, VarbitID.SLAYER_UNLOCK_WARPED_CREATURES, "Warped Reality", 60, Kind.TASK_UNLOCK, 0, 0, 0, "warped creatures",
			null),
	GRYPHONS(Row.SLAYER_REWARDS_UNLOCK_GRYPHONS, VarbitID.SLAYER_UNLOCK_GRYPHONS, "Wings Spread", 80, Kind.TASK_UNLOCK, 0, 0, 0, "gryphons",
			null),
	AQUANITES(Row.SLAYER_REWARDS_UNLOCK_AQUANITES, VarbitID.SLAYER_UNLOCK_AQUANITES, "Lured In", 80, Kind.TASK_UNLOCK, 0, 0, 0, "aquanites",
			null),
	WILDERNESS_TASKS(Row.SLAYER_REWARDS_UNLOCK_WILDERNESS_NEWTASKS, VarbitID.SLAYER_UNLOCK_WILDY_EXTRATASKS, "I Wildy More Slayer", 0, Kind.TASK_UNLOCK, 0, 0, 0, null,
			"It is a free toggle"),
	HEAVY_FROST(Row.SLAYER_REWARDS_WEIGHTED_FROST_DRAGONS, VarbitID.SLAYER_WEIGHTED_LONGER_FROST_DRAGONS, "Chance of Heavy Frost", 100, Kind.WEIGHTING, 0, 0, 0, "frost dragons",
			null),

	EXT_ABYSSAL(Row.SLAYER_REWARDS_LONGER_ABYSSALDEMONS, VarbitID.SLAYER_LONGER_ABYSSALDEMONS, "Augment My Abbies", 100, Kind.EXTENSION, 85, 0, 0, "abyssal demons", null),
	EXT_NECHRYAEL(Row.SLAYER_REWARDS_LONGER_NECHRYAEL, VarbitID.SLAYER_LONGER_NECHRYAEL, "Nechs Please", 100, Kind.EXTENSION, 80, 0, 0, "nechryael", null),
	EXT_ARAXYTES(Row.SLAYER_REWARDS_LONGER_ARAXYTES, VarbitID.SLAYER_LONGER_ARAXYTES, "More eyes than sense", 150, Kind.EXTENSION, 92, 0, 0, "araxytes", null),
	EXT_GARGOYLES(Row.SLAYER_REWARDS_LONGER_GARGOYLES, VarbitID.SLAYER_LONGER_GARGOYLES, "Get Smashed", 100, Kind.EXTENSION, 75, 0, 0, "gargoyles", null),
	EXT_DUST_DEVILS(Row.SLAYER_REWARDS_LONGER_DUSTDEVILS, VarbitID.SLAYER_LONGER_DUSTDEVILS, "To Dust You Shall Return", 100, Kind.EXTENSION, 65, 0, 0, "dust devils", null),
	EXT_ABERRANT(Row.SLAYER_REWARDS_LONGER_ABERRANTSPECTRES, VarbitID.SLAYER_LONGER_ABERRANTSPECTRES, "Smell Ya Later", 100, Kind.EXTENSION, 60, 0, 0, "aberrant spectres", null),
	EXT_BLOODVELD(Row.SLAYER_REWARDS_LONGER_BLOODVELD, VarbitID.SLAYER_LONGER_BLOODVELD, "Bleed Me Dry", 75, Kind.EXTENSION, 50, 0, 0, "bloodveld", null),
	EXT_BLACK_DEMONS(Row.SLAYER_REWARDS_LONGER_BLACKDEMONS, VarbitID.SLAYER_LONGER_BLACKDEMONS, "It's Dark in Here", 100, Kind.EXTENSION, 0, 0, 0, "black demons", null),
	EXT_GREATER_DEMONS(Row.SLAYER_REWARDS_LONGER_GREATERDEMONS, VarbitID.SLAYER_LONGER_GREATERDEMONS, "Greater Challenge", 100, Kind.EXTENSION, 0, 0, 0, "greater demons",
			"Tormented demons, Skotizo and K'ril count too"),
	EXT_CAVE_KRAKEN(Row.SLAYER_REWARDS_LONGER_CAVEKRAKEN, VarbitID.SLAYER_LONGER_CAVEKRAKEN, "Krack On", 100, Kind.EXTENSION, 87, 0, 0, "cave kraken", null),
	EXT_DARK_BEASTS(Row.SLAYER_REWARDS_LONGER_DARKBEASTS, VarbitID.SLAYER_LONGER_DARKBEASTS, "Need More Darkness", 100, Kind.EXTENSION, 90, 0, 0, "dark beasts", null),
	EXT_BLACK_DRAGONS(Row.SLAYER_REWARDS_LONGER_BLACKDRAGONS, VarbitID.SLAYER_LONGER_BLACKDRAGONS, "Fire & Darkness", 50, Kind.EXTENSION, 0, 0, 0, "black dragons",
			"Baby and brutal black dragons and the King Black Dragon count too"),
	EXT_METAL_DRAGONS(Row.SLAYER_REWARDS_LONGER_METALDRAGONS, VarbitID.SLAYER_LONGER_METALDRAGONS, "Pedal to the Metals", 200, Kind.EXTENSION, 0, 0, 0, "metal dragons", null),
	EXT_VAMPYRES(Row.SLAYER_REWARDS_LONGER_VAMPYRES, VarbitID.SLAYER_LONGER_VAMPYRES, "More at Stake", 100, Kind.EXTENSION, 0, 0, 0, "vampyres", null),
	EXT_WYRMS(Row.SLAYER_REWARDS_LONGER_WYRMS, VarbitID.SLAYER_LONGER_WYRMS, "Can of Wyrms", 100, Kind.EXTENSION, 62, 0, 0, "wyrms", null),
	EXT_BASILISK(Row.SLAYER_REWARDS_LONGER_BASILISK, VarbitID.SLAYER_LONGER_BASILISK, "Basilonger", 100, Kind.EXTENSION, 40, 0, 0, "basilisks", null),
	EXT_FROST_DRAGONS(Row.SLAYER_REWARDS_LONGER_FROST_DRAGONS, VarbitID.SLAYER_UNLOCK_LONGER_FROST_DRAGONS, "I see Dragons", 100, Kind.EXTENSION, 0, 0, 0, "frost dragons", null),
	EXT_SPIRITUAL(Row.SLAYER_REWARDS_LONGER_SPIRITUALGWD, VarbitID.SLAYER_LONGER_SPIRITUALGWD, "Spiritual Fervour", 100, Kind.EXTENSION, 63, 0, 0, "spiritual creatures", null),
	EXT_SKELETAL_WYVERNS(Row.SLAYER_REWARDS_LONGER_SKELETALWYVERNS, VarbitID.SLAYER_LONGER_SKELETALWYVERNS, "Wyver-nother one", 100, Kind.EXTENSION, 72, 0, 0, "skeletal wyverns", null),
	EXT_CAVE_HORRORS(Row.SLAYER_REWARDS_LONGER_CAVEHORRORS, VarbitID.SLAYER_LONGER_CAVEHORRORS, "Horrorific", 100, Kind.EXTENSION, 58, 0, 0, "cave horrors", null),
	EXT_AVIANSIES(Row.SLAYER_REWARDS_LONGER_AVIANSIES, VarbitID.SLAYER_LONGER_AVIANSIES, "Birds of a Feather", 100, Kind.EXTENSION, 0, 0, 0, "aviansies", null),
	EXT_CUSTODIANS(Row.SLAYER_REWARDS_LONGER_CUSTODIANS, VarbitID.SLAYER_LONGER_CUSTODIANS, "Un-restraining Order", 100, Kind.EXTENSION, 54, 0, 0, "custodian stalkers", null),
	EXT_GRYPHONS(Row.SLAYER_REWARDS_LONGER_GRYPHONS, VarbitID.SLAYER_UNLOCK_LONGER_GRYPHON, "Gryphon and on", 50, Kind.EXTENSION, 51, 0, 0, "gryphons", null),
	EXT_AQUANITES(Row.SLAYER_REWARDS_LONGER_AQUANITES, VarbitID.SLAYER_LONGER_AQUANITES, "Let's Stay All Aquanite", 100, Kind.EXTENSION, 78, 0, 0, "aquanites", null),
	EXT_FOSSIL_WYVERNS(Row.SLAYER_REWARDS_LONGER_FOSSILWYVERNS, VarbitID.SLAYER_LONGER_FOSSILWYVERNS, "Wyver-nother Two", 100, Kind.EXTENSION, 66, 0, 0, "fossil island wyverns", null),
	EXT_ANKOU(Row.SLAYER_REWARDS_LONGER_ANKOU, VarbitID.SLAYER_LONGER_ANKOU, "Ankou Very Much", 100, Kind.EXTENSION, 0, 0, 0, "ankou", null),
	EXT_SUQAH(Row.SLAYER_REWARDS_LONGER_SUQAH, VarbitID.SLAYER_LONGER_SUQAH, "Suq-a-nother One", 100, Kind.EXTENSION, 0, 0, 0, "suqahs", null),
	EXT_SCABARITES(Row.SLAYER_REWARDS_LONGER_SCABARITES, VarbitID.SLAYER_LONGER_SCABARITES, "Get Scabaright on It", 50, Kind.EXTENSION, 0, 0, 0, "scabarites", null),
	EXT_REVENANTS(Row.SLAYER_REWARDS_LONGER_REVENANTS, VarbitID.SLAYER_LONGER_REVENANTS, "Revenenenenenants", 100, Kind.EXTENSION, 0, 0, 0, "revenants",
			"Revenants are in the Wilderness, where other players can attack you"),

	HELM_BLACK(Row.SLAYER_REWARDS_UNLOCK_HELM_BLACK, VarbitID.SLAYER_UNLOCK_HELM_BLACK, "King Black Bonnet", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_GREEN(Row.SLAYER_REWARDS_UNLOCK_HELM_GREEN, VarbitID.SLAYER_UNLOCK_HELM_GREEN, "Kalphite Khat", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_RED(Row.SLAYER_REWARDS_UNLOCK_HELM_RED, VarbitID.SLAYER_UNLOCK_HELM_RED, "Unholy Helmet", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_PURPLE(Row.SLAYER_REWARDS_UNLOCK_HELM_PURPLE, VarbitID.SLAYER_UNLOCK_HELM_PURPLE, "Dark Mantle", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_TURQUOISE(Row.SLAYER_REWARDS_UNLOCK_HELM_TURQUOISE, VarbitID.SLAYER_UNLOCK_HELM_TURQUOISE, "Undead Head", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_HYDRA(Row.SLAYER_REWARDS_UNLOCK_HELM_HYDRA, VarbitID.SLAYER_UNLOCK_HELM_HYDRA, "Use More Head", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_TWISTED(Row.SLAYER_REWARDS_UNLOCK_HELM_TWISTED, VarbitID.SLAYER_UNLOCK_HELM_TWISTED, "Twisted Vision", 200, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_ARAXYTE(Row.SLAYER_REWARDS_UNLOCK_HELM_ARAXYTE, VarbitID.SLAYER_UNLOCK_HELM_ARAXYTE, "Eye see you", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_HOODED(Row.SLAYER_REWARDS_UNLOCK_HELM_HOODED, VarbitID.SLAYER_UNLOCK_HELM_HOODED, "Absolutely Slayin'", 1000, Kind.COSMETIC, 0, 0, 0, null, null),
	HELM_OATHPLATE(Row.SLAYER_REWARDS_UNLOCK_HELM_LEAGUE_6, VarbitID.SLAYER_UNLOCK_HELM_LEAGUE_6, "Oath Breaker", 200, Kind.COSMETIC, 0, 0, 0, null, null);

	/** How {@link SlayerSpend} values the reward. */
	enum Kind
	{
		/** Longer tasks of one kind, valued with the pool model at the extended amount from the cache. */
		EXTENSION,
		/** Lets masters assign a task, valued with the task added to the pool. */
		TASK_UNLOCK,
		/** Raises a task's weight, valued with the heavier task in the pool. */
		WEIGHTING,
		/** Keeps a task out of every list without a block slot, valued with the task gone and the slot reused. */
		BLOCKER,
		/** Finishes one task's monsters for you, valued as a faster task. */
		TASK_HELPER,
		/** Anything else, valued by its documented effect per goal (see {@link SlayerSpend}). */
		GENERAL,
		COSMETIC
	}

	private final int row;
	/** Varbit at the row's ownership bit, used only when the cache tables cannot be read. */
	private final int fallbackVarbit;
	private final String fallbackName;
	private final int fallbackCost;
	private final Kind kind;
	private final int slayerLevel;
	private final int craftingLevel;
	private final int fletchingLevel;
	/** Lower-case task name, matched against the cache's task names, and used for level and block checks without the cache. */
	private final String task;
	/** Something every line about the reward should say, whatever the goal, or null. */
	private final String fact;
}
