package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Task equipment in two tiers, kept apart. The mandatory tier: the monster cannot be damaged or finished without
 * the item, going without it means heavy damage that cannot be avoided, or the place cannot be entered without it.
 * The recommended tier: nothing stops the task without the item, but the wiki names an item class that gives a large
 * bonus or protection against that kind of monster, such as demonbane weapons against demons. Every entry was checked
 * on the OSRS Wiki's monster, item, attribute and location pages, named next to it.
 *
 * <p>What a place needs whatever the task, such as an axe and 875 coins for Brimhaven Dungeon, is keyed by the place's
 * key in /slayer_areas.json, so it applies alike to the area Konar assigned and to the place picked or recommended for
 * any other master. A need of a task that only one of its places has is tied to that place's key the same way.
 *
 * <p>Tasks are keyed by the names the game gives them, as {@link SlayerTaskReader} reads them, compared without case.
 * The tests check every key against those names and against RuneLite's Slayer {@code Task} enum, which holds the
 * same names. Items are matched by id rather than by name: lit and unlit lanterns
 * share a name, "Unlit bug lantern" contains "lit bug lantern", the quest copy of V's shield cannot be worn and
 * noted items keep the real name. Slayer helmets also match by name, so a recolour newer than this build counts.
 */
final class SlayerRequirements
{
	/** SLAYER_AREA ids (SlayerArea COL_AREA_ID) of the places Konar can assign that change what a task needs. */
	static final int KARUULM_AREA = 3;
	static final int BRIMHAVEN_AREA = 5;
	static final int TAVERLEY_AREA = 6;
	static final int WATERFALL_AREA = 8;
	static final int GOD_WARS_AREA = 10;
	static final int KALPHITE_LAIR_AREA = 11;
	static final int SMOKE_DUNGEON_AREA = 20;
	static final int BRINE_RAT_AREA = 35;
	static final int ZANARIS_AREA = 36;
	static final int EVIL_CHICKEN_AREA = 37;
	static final int DARKMEYER_AREA = 44;
	static final int SLEPE_AREA = 45;
	static final int MEIYERDITCH_LABS_AREA = 46;
	static final int VAMPYRIUM_AREA = 55;

	/** Keys in /slayer_areas.json of places these tables name more than once. */
	static final String KARUULM_SLAYER_DUNGEON = "karuulm_slayer_dungeon";
	static final String TAVERLEY_DUNGEON = "taverley_dungeon";
	static final String GOD_WARS_DUNGEON = "god_wars_dungeon";
	static final String DARKMEYER = "darkmeyer";
	static final String SLEPE = "slepe";
	static final String VAMPYRIUM = "vampyrium";
	static final String BRIMHAVEN_DUNGEON = "brimhaven_dungeon";
	/** What Saniboch charges each time. Wiki: Saniboch. */
	static final int BRIMHAVEN_FEE = 875;
	/** Warrior guild tokens a player must carry into either cyclops room. Wiki: Warriors' Guild. */
	static final int CYCLOPS_ROOM_TOKENS = 100;

	/**
	 * Every item id an item group lists, gathered as each group is made, so it comes before the first group. The bank
	 * memory keeps these items alone.
	 */
	private static final Set<Integer> GEAR_IDS = new HashSet<>();
	private static final Set<Integer> GEAR_IDS_VIEW = Collections.unmodifiableSet(GEAR_IDS);

	/** Every Slayer helmet in RuneLite's gameval: recolours, imbued, Soul Wars and PvP Arena imbues, Oathplate, Radiant. */
	static final int[] SLAYER_HELMET_IDS = {
		ItemID.SLAYER_HELM, ItemID.SLAYER_HELM_I, ItemID.SLAYER_HELM_BLACK, ItemID.SLAYER_HELM_I_BLACK,
		ItemID.SLAYER_HELM_GREEN, ItemID.SLAYER_HELM_I_GREEN, ItemID.SLAYER_HELM_RED, ItemID.SLAYER_HELM_I_RED,
		ItemID.SLAYER_HELM_PURPLE, ItemID.SLAYER_HELM_I_PURPLE, ItemID.SLAYER_HELM_TURQUOISE,
		ItemID.SLAYER_HELM_I_TURQUOISE, ItemID.SLAYER_HELM_HYDRA, ItemID.SLAYER_HELM_I_HYDRA,
		ItemID.SLAYER_HELM_TWISTED, ItemID.SLAYER_HELM_I_TWISTED, ItemID.SW_SLAYER_HELM_I,
		ItemID.SW_SLAYER_HELM_I_BLACK, ItemID.SW_SLAYER_HELM_I_GREEN, ItemID.SW_SLAYER_HELM_I_RED,
		ItemID.SW_SLAYER_HELM_I_PURPLE, ItemID.SW_SLAYER_HELM_I_TURQUOISE, ItemID.SW_SLAYER_HELM_I_HYDRA,
		ItemID.SW_SLAYER_HELM_I_TWISTED, ItemID.SLAYER_HELM_JAD, ItemID.SLAYER_HELM_I_JAD,
		ItemID.SW_SLAYER_HELM_I_JAD, ItemID.SLAYER_HELM_VERZIK, ItemID.SLAYER_HELM_I_VERZIK,
		ItemID.SW_SLAYER_HELM_I_VERZIK, ItemID.SLAYER_HELM_ZUK, ItemID.SLAYER_HELM_I_ZUK,
		ItemID.SW_SLAYER_HELM_I_ZUK, ItemID.PVPA_SLAYER_HELM_I, ItemID.PVPA_SLAYER_HELM_I_BLACK,
		ItemID.PVPA_SLAYER_HELM_I_GREEN, ItemID.PVPA_SLAYER_HELM_I_RED, ItemID.PVPA_SLAYER_HELM_I_PURPLE,
		ItemID.PVPA_SLAYER_HELM_I_TURQUOISE, ItemID.PVPA_SLAYER_HELM_I_HYDRA, ItemID.PVPA_SLAYER_HELM_I_TWISTED,
		ItemID.PVPA_SLAYER_HELM_I_JAD, ItemID.PVPA_SLAYER_HELM_I_VERZIK, ItemID.PVPA_SLAYER_HELM_I_ZUK,
		ItemID.SLAYER_HELM_ARAXYTE, ItemID.SLAYER_HELM_I_ARAXYTE, ItemID.SW_SLAYER_HELM_I_ARAXYTE,
		ItemID.PVPA_SLAYER_HELM_I_ARAXYTE, ItemID.SLAYER_HELM_HOODED, ItemID.SLAYER_HELM_I_HOODED,
		ItemID.SW_SLAYER_HELM_I_HOODED, ItemID.PVPA_SLAYER_HELM_I_HOODED, ItemID.LEAGUE_6_SLAYER_HELM1,
		ItemID.LEAGUE_6_SLAYER_HELM2, ItemID.LEAGUE_6_SLAYER_HELM1_I, ItemID.SW_LEAGUE_6_SLAYER_HELM1_I,
		ItemID.PVPA_LEAGUE_6_SLAYER_HELM1_I, ItemID.LEAGUE_6_SLAYER_HELM2_I, ItemID.SW_LEAGUE_6_SLAYER_HELM2_I,
		ItemID.PVPA_LEAGUE_6_SLAYER_HELM2_I,
	};

	static final ItemGroup SLAYER_HELMET = new ItemGroup("a Slayer helmet", named(SLAYER_HELMET_IDS, "Slayer helmet"), true);

	static final ItemGroup NOSE_PEG_OR_HELMET = group("a nose peg or Slayer helmet", SLAYER_HELMET,
		items("a nose peg", item(ItemID.SLAYER_NOSEPEG, "Nose peg")));
	static final ItemGroup EARMUFFS_OR_HELMET = group("earmuffs or a Slayer helmet", SLAYER_HELMET,
		items("earmuffs", item(ItemID.SLAYER_EARMUFFS, "Earmuffs")));
	static final ItemGroup FACEMASK_OR_HELMET = group("a facemask or Slayer helmet", SLAYER_HELMET,
		items("a facemask", item(ItemID.SLAYER_FACEMASK, "Facemask")));
	static final ItemGroup SPINY_OR_HELMET = group("a spiny helmet or Slayer helmet", SLAYER_HELMET,
		items("a spiny helmet", item(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet")));
	/** A helmet made after A Porcine of Interest holds the goggles. Spria only assigns sourhogs after it. */
	static final ItemGroup GOGGLES_OR_HELMET = group("reinforced goggles or a Slayer helmet", SLAYER_HELMET,
		items("reinforced goggles", item(ItemID.SLAYER_REINFORCED_GOGGLES, "Reinforced goggles")));

	/** The other V's shield id is the quest copy, which cannot be wielded. */
	static final ItemGroup MIRROR_SHIELDS = items("a mirror shield or V's shield",
		item(ItemID.SLAYER_MIRROR_SHIELD, "Mirror shield"), item(ItemID.V_SHIELD, "V's shield"));
	static final ItemGroup SLAYER_GLOVES = items("Slayer gloves", item(ItemID.DEAL_SLAYER_GLOVES, "Slayer gloves"));
	/** Charged and uncharged shields both protect. A plain anti-dragon shield does not. */
	static final ItemGroup WYVERN_SHIELDS = items("an elemental, mind, dragonfire or ancient wyvern shield, or a dragonfire ward",
		item(ItemID.ELEMENTAL_SHIELD, "Elemental shield"), item(ItemID.ELEMENTAL_MIND_SHIELD, "Mind shield"),
		item(ItemID.DRAGONFIRE_SHIELD, "Dragonfire shield"), item(ItemID.DRAGONFIRE_SHIELD_UNCHARGED, "Dragonfire shield"),
		item(ItemID.DRAGONFIRE_WARD, "Dragonfire ward"), item(ItemID.DRAGONFIRE_WARD_UNCHARGED, "Dragonfire ward"),
		item(ItemID.WYVERN_SHIELD, "Ancient wyvern shield"), item(ItemID.WYVERN_SHIELD_UNCHARGED, "Ancient wyvern shield"));
	/** Leaf-bladed weapons, broad ammunition, or a staff that casts Magic Dart. */
	static final ItemGroup TUROTH_KURASK_WEAPONS = items("a leaf-bladed weapon, broad ammo or a Magic Dart staff",
		item(ItemID.SLAYER_LEAFBLADED_SPEAR, "Leaf-bladed spear"), item(ItemID.LEAFBLADED_SWORD, "Leaf-bladed sword"),
		item(ItemID.LEAFBLADED_BATTLEAXE, "Leaf-bladed battleaxe"), item(ItemID.SLAYER_BROAD_ARROWS, "Broad arrows"),
		item(ItemID.SEEKING_SLAYER_BROAD_ARROWS, "Seeking broad arrows"), item(ItemID.SLAYER_BROAD_BOLT, "Broad bolts"),
		item(ItemID.SLAYER_BROAD_BOLT_AMETHYST, "Amethyst broad bolts"), item(ItemID.SLAYER_STAFF, "Slayer's staff"),
		item(ItemID.SLAYER_STAFF_ENCHANTED, "Slayer's staff (e)"), item(ItemID.SOTD, "Staff of the Dead"),
		item(ItemID.TOXIC_SOTD_CHARGED, "Toxic Staff of the Dead"), item(ItemID.TOXIC_SOTD, "Toxic staff (uncharged)"),
		item(ItemID.STAFF_OF_LIGHT, "Staff of Light"), item(ItemID.STAFF_OF_BALANCE, "Staff of Balance"));
	static final ItemGroup LIT_BUG_LANTERN = items("a lit bug lantern", item(ItemID.SLAYER_BUGLAN_ON, "Lit bug lantern"));
	static final ItemGroup CRYSTAL_CHIME = items("a crystal chime", item(ItemID.CRYSTAL_CHIME, "Crystal chime"));
	static final ItemGroup SLAYER_BELL = items("a Slayer bell", item(ItemID.MOLANISK_BELL, "Slayer bell"));
	static final ItemGroup WITCHWOOD_ICON = items("a witchwood icon", item(ItemID.WITCHWOOD_ICON, "Witchwood icon"));
	static final ItemGroup SPADE = items("a spade", item(ItemID.SPADE, "Spade"));
	static final ItemGroup LOCKPICK = items("a lockpick", item(ItemID.LOCKPICK, "Lockpick"));
	static final ItemGroup ROPE = items("a rope", item(ItemID.ROPE, "Rope"));
	static final ItemGroup TORTUGAN_SHIELD = items("a tortugan shield", item(ItemID.TORTUGAN_SHIELD, "Tortugan shield"));
	static final ItemGroup KARUULM_BOOTS = items("boots of stone, boots of brimstone or granite boots",
		item(ItemID.BOOTS_OF_STONE, "Boots of stone"), item(ItemID.BOOTS_OF_BRIMSTONE, "Boots of Brimstone"),
		item(ItemID.GRANITE_BOOTS, "Granite boots"));

	// ---- what places need ----

	/**
	 * Wiki: Axe, Felling axe, Vines (Brimhaven Dungeon), whose tool is any axe. The blessed axe cuts nothing but undead
	 * trees (wiki: Blessed axe). An inactive crystal axe works as a dragon axe and an uncharged infernal axe still cuts.
	 * The Gauntlet's axes, league copies and quest props are left out.
	 */
	static final ItemGroup AXES = items("an axe",
		item(ItemID.BRONZE_AXE, "Bronze axe"), item(ItemID.IRON_AXE, "Iron axe"), item(ItemID.STEEL_AXE, "Steel axe"),
		item(ItemID.BLACK_AXE, "Black axe"), item(ItemID.MITHRIL_AXE, "Mithril axe"), item(ItemID.ADAMANT_AXE, "Adamant axe"),
		item(ItemID.RUNE_AXE, "Rune axe"), item(ItemID.DRAGON_AXE, "Dragon axe"),
		item(ItemID.TRAILBLAZER_AXE_NO_INFERNAL, "Dragon axe (or)"), item(ItemID.TRAILBLAZER_RELOADED_AXE_NO_INFERNAL, "Dragon axe (or)"),
		item(ItemID.INFERNAL_AXE, "Infernal axe"), item(ItemID.INFERNAL_AXE_EMPTY, "Infernal axe (uncharged)"),
		item(ItemID.TRAILBLAZER_AXE, "Infernal axe (or)"), item(ItemID.TRAILBLAZER_AXE_EMPTY, "Infernal axe (uncharged)"),
		item(ItemID.TRAILBLAZER_RELOADED_AXE, "Infernal axe (or)"), item(ItemID.TRAILBLAZER_RELOADED_AXE_EMPTY, "Infernal axe (uncharged)"),
		item(ItemID.CRYSTAL_AXE, "Crystal axe"), item(ItemID.CRYSTAL_AXE_INACTIVE, "Crystal axe (inactive)"),
		item(ItemID._3A_AXE, "3rd Age axe"), item(ItemID.TRAIL_GILDED_AXE, "Gilded axe"),
		item(ItemID.BRONZE_AXE_2H, "Bronze felling axe"), item(ItemID.IRON_AXE_2H, "Iron felling axe"),
		item(ItemID.STEEL_AXE_2H, "Steel felling axe"), item(ItemID.BLACK_AXE_2H, "Black felling axe"),
		item(ItemID.MITHRIL_AXE_2H, "Mithril felling axe"), item(ItemID.ADAMANT_AXE_2H, "Adamant felling axe"),
		item(ItemID.RUNE_AXE_2H, "Rune felling axe"), item(ItemID.DRAGON_AXE_2H, "Dragon felling axe"),
		item(ItemID.CRYSTAL_AXE_2H, "Crystal felling axe"), item(ItemID.CRYSTAL_AXE_2H_INACTIVE, "Crystal felling axe (inactive)"),
		item(ItemID._3A_AXE_2H, "3rd Age felling axe"));
	/**
	 * Wiki: Pickaxe, Rock slide, which respawns behind each player who mines it. An inactive crystal pickaxe works as a dragon
	 * pickaxe. The Nightmare Zone's and the Gauntlet's copies, which only work there, and league copies are left out.
	 */
	static final ItemGroup PICKAXES = items("a pickaxe",
		item(ItemID.BRONZE_PICKAXE, "Bronze pickaxe"), item(ItemID.IRON_PICKAXE, "Iron pickaxe"),
		item(ItemID.STEEL_PICKAXE, "Steel pickaxe"), item(ItemID.BLACK_PICKAXE, "Black pickaxe"),
		item(ItemID.MITHRIL_PICKAXE, "Mithril pickaxe"), item(ItemID.ADAMANT_PICKAXE, "Adamant pickaxe"),
		item(ItemID.RUNE_PICKAXE, "Rune pickaxe"), item(ItemID.DRAGON_PICKAXE, "Dragon pickaxe"),
		item(ItemID.DRAGON_PICKAXE_PRETTY, "Dragon pickaxe"), item(ItemID.ZALCANO_PICKAXE, "Dragon pickaxe (or)"),
		item(ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL, "Dragon pickaxe (or)"),
		item(ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL, "Dragon pickaxe (or)"),
		item(ItemID.INFERNAL_PICKAXE, "Infernal pickaxe"), item(ItemID.INFERNAL_PICKAXE_EMPTY, "Infernal pickaxe (uncharged)"),
		item(ItemID.TRAILBLAZER_PICKAXE, "Infernal pickaxe (or)"), item(ItemID.TRAILBLAZER_PICKAXE_EMPTY, "Infernal pickaxe (uncharged)"),
		item(ItemID.TRAILBLAZER_RELOADED_PICKAXE, "Infernal pickaxe (or)"),
		item(ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY, "Infernal pickaxe (uncharged)"),
		item(ItemID.CRYSTAL_PICKAXE, "Crystal pickaxe"), item(ItemID.CRYSTAL_PICKAXE_INACTIVE, "Crystal pickaxe (inactive)"),
		item(ItemID._3A_PICKAXE, "3rd Age pickaxe"), item(ItemID.TRAIL_GILDED_PICKAXE, "Gilded pickaxe"));
	/** Counted, as Saniboch takes 875 each time (wiki: Saniboch). */
	static final ItemGroup COINS = items("coins", item(ItemID.COINS, "Coins"));
	/** Wiki: Zanaris, Fairy ring. Either staff, wielded, lets you through the shed in Lumbridge Swamp and the fairy rings. */
	static final ItemGroup DRAMEN_OR_LUNAR_STAFF = items("a dramen or lunar staff",
		item(ItemID.DRAMEN_STAFF, "Dramen staff"), item(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, "Lunar staff"));
	/** Wiki: Evil Chicken's Lair. Undead raw chicken does not work. */
	static final ItemGroup RAW_CHICKEN = items("a raw chicken", item(ItemID.RAW_CHICKEN, "Raw chicken"));
	static final ItemGroup GLARIALS_PEBBLE = items("Glarial's pebble", item(ItemID.GLARIALS_PEBBLE_WATERFALL_QUEST, "Glarial's pebble"));
	/**
	 * Wiki: Steel key ring. A key on the ring opens its door from there, and the client does not say which keys the ring
	 * holds, so the ring counts for each key it can hold.
	 */
	static final ItemGroup MAZE_KEY = items("a maze key", item(ItemID.MELZARKEY, "Maze key"),
		item(ItemID.FAVOUR_KEY_RING, "Steel key ring"));
	static final ItemGroup DUSTY_KEY = items("a dusty key", item(ItemID.DUSTY_KEY, "Dusty key"),
		item(ItemID.FAVOUR_KEY_RING, "Steel key ring"));
	/** Wiki: Shade key. Any of the 25, which are not used up on the doors. */
	static final ItemGroup SHADE_KEYS = items("a shade key",
		item(ItemID.SHADEKEY_BRONZE_BLOODRED, "Bronze key red"), item(ItemID.SHADEKEY_BRONZE_BROWN, "Bronze key brown"),
		item(ItemID.SHADEKEY_BRONZE_CRIMSON, "Bronze key crimson"), item(ItemID.SHADEKEY_BRONZE_BLACK, "Bronze key black"),
		item(ItemID.SHADEKEY_BRONZE_PURPLE, "Bronze key purple"), item(ItemID.SHADEKEY_STEEL_BLOODRED, "Steel key red"),
		item(ItemID.SHADEKEY_STEEL_BROWN, "Steel key brown"), item(ItemID.SHADEKEY_STEEL_CRIMSON, "Steel key crimson"),
		item(ItemID.SHADEKEY_STEEL_BLACK, "Steel key black"), item(ItemID.SHADEKEY_STEEL_PURPLE, "Steel key purple"),
		item(ItemID.SHADEKEY_BLACK_BLOODRED, "Black key red"), item(ItemID.SHADEKEY_BLACK_BROWN, "Black key brown"),
		item(ItemID.SHADEKEY_BLACK_CRIMSON, "Black key crimson"), item(ItemID.SHADEKEY_BLACK_BLACK, "Black key black"),
		item(ItemID.SHADEKEY_BLACK_PURPLE, "Black key purple"), item(ItemID.SHADEKEY_SILVER_BLOODRED, "Silver key red"),
		item(ItemID.SHADEKEY_SILVER_BROWN, "Silver key brown"), item(ItemID.SHADEKEY_SILVER_CRIMSON, "Silver key crimson"),
		item(ItemID.SHADEKEY_SILVER_BLACK, "Silver key black"), item(ItemID.SHADEKEY_SILVER_PURPLE, "Silver key purple"),
		item(ItemID.SHADEKEY_GOLD_BLOODRED, "Gold key red"), item(ItemID.SHADEKEY_GOLD_BROWN, "Gold key brown"),
		item(ItemID.SHADEKEY_GOLD_CRIMSON, "Gold key crimson"), item(ItemID.SHADEKEY_GOLD_BLACK, "Gold key black"),
		item(ItemID.SHADEKEY_GOLD_PURPLE, "Gold key purple"));
	/** Wiki: Smoke Dungeon. A gas mask keeps the smoke out too, though not the dust devils' attack. */
	static final ItemGroup SMOKE_PROTECTION = group("a facemask, gas mask or Slayer helmet", FACEMASK_OR_HELMET,
		items("a gas mask", item(ItemID.GASMASK, "Gas mask")));
	static final ItemGroup WARRIOR_GUILD_TOKENS = items("warrior guild tokens", item(ItemID.WARGUILD_TOKENS, "Warrior guild token"));
	/** Wiki: Warriors' Guild. Worn, it lets you into the cyclops rooms without tokens. */
	static final ItemGroup ATTACK_CAPES = items("an Attack cape", item(ItemID.SKILLCAPE_ATTACK, "Attack cape"),
		item(ItemID.SKILLCAPE_ATTACK_TRIMMED, "Attack cape(t)"));
	/** Wiki: God Wars Dungeon. A hammer, any warhammer, the elder maul or the Imcando hammer bangs the gong of Bandos' Stronghold. */
	static final ItemGroup HAMMERS = items("a hammer or warhammer",
		item(ItemID.HAMMER, "Hammer"), item(ItemID.IMCANDO_HAMMER, "Imcando hammer"),
		item(ItemID.IMCANDO_HAMMER_OFFHAND, "Imcando hammer (off-hand)"), item(ItemID.ELDER_MAUL, "Elder maul"),
		item(ItemID.ELDER_MAUL_ORNAMENT, "Elder maul (or)"), item(ItemID.BRONZE_WARHAMMER, "Bronze warhammer"),
		item(ItemID.IRON_WARHAMMER, "Iron warhammer"), item(ItemID.STEEL_WARHAMMER, "Steel warhammer"),
		item(ItemID.BLACK_WARHAMMER, "Black warhammer"), item(ItemID.WHITE_WARHAMMER, "White warhammer"),
		item(ItemID.MITHRIL_WARHAMMER, "Mithril warhammer"), item(ItemID.ADAMNT_WARHAMMER, "Adamant warhammer"),
		item(ItemID.RUNE_WARHAMMER, "Rune warhammer"), item(ItemID.DRAGON_WARHAMMER, "Dragon warhammer"),
		item(ItemID.DRAGON_WARHAMMER_ORNAMENT, "Dragon warhammer (or)"));

	/** Only lit ids. The unlit ones share the name of the lit ones for the lamps, lanterns and the mining helmet. */
	static final ItemGroup LIGHT_SOURCE = items("a lit light source",
		item(ItemID.LIT_CANDLE, "Lit candle"), item(ItemID.LIT_BLACK_CANDLE, "Lit black candle"),
		item(ItemID.TORCH_LIT, "Lit torch"), item(ItemID.CANDLE_LANTERN_LIT, "Candle lantern"),
		item(ItemID.CANDLE_LANTERN_BLACK_LIT, "Candle lantern"), item(ItemID.OIL_LAMP_LIT, "Oil lamp"),
		item(ItemID.OIL_LANTERN_LIT, "Oil lantern"), item(ItemID.BULLSEYE_LANTERN_LIT, "Bullseye lantern"),
		item(ItemID.TOG_SAPPHIRE_LANTERN_LIT, "Sapphire lantern"), item(ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST, "Emerald lantern"),
		item(ItemID.CAVE_GOBLIN_MINING_HELMET_LIT, "Mining helmet"), item(ItemID.SKILLCAPE_FIREMAKING, "Firemaking cape"),
		item(ItemID.SKILLCAPE_FIREMAKING_TRIMMED, "Firemaking cape(t)"), item(ItemID.SKILLCAPE_MAX, "Max cape"),
		item(ItemID.SKILLCAPE_MAX_WORN, "Max cape"), item(ItemID.SEERS_HEADBAND_EASY, "Kandarin headgear 1"),
		item(ItemID.SEERS_HEADBAND_MEDIUM, "Kandarin headgear 2"), item(ItemID.SEERS_HEADBAND_HARD, "Kandarin headgear 3"),
		item(ItemID.SEERS_HEADBAND_ELITE, "Kandarin headgear 4"), item(ItemID.WINT_TORCH, "Bruma torch"),
		item(ItemID.WINT_TORCH_OFFHAND, "Bruma torch (off-hand)"), item(ItemID.JAR_OF_LIGHT, "Jar of Light"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL, "Abyssal lantern (normal logs)"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL_BLUE, "Abyssal lantern (blue logs)"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL_RED, "Abyssal lantern (red logs)"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL_WHITE, "Abyssal lantern (white logs)"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE, "Abyssal lantern (purple logs)"),
		item(ItemID.ABYSSAL_LANTERN_NORMAL_GREEN, "Abyssal lantern (green logs)"),
		item(ItemID.ABYSSAL_LANTERN_OAK, "Abyssal lantern (oak logs)"),
		item(ItemID.ABYSSAL_LANTERN_WILLOW, "Abyssal lantern (willow logs)"),
		item(ItemID.ABYSSAL_LANTERN_MAPLE, "Abyssal lantern (maple logs)"),
		item(ItemID.ABYSSAL_LANTERN_YEW, "Abyssal lantern (yew logs)"),
		item(ItemID.ABYSSAL_LANTERN_BLISTERWOOD, "Abyssal lantern (blisterwood logs)"),
		item(ItemID.ABYSSAL_LANTERN_MAGIC, "Abyssal lantern (magic logs)"),
		item(ItemID.ABYSSAL_LANTERN_REDWOOD, "Abyssal lantern (redwood logs)"));

	/** Finishing items. The rock and granite hammers are kept, a thrownhammer is used up on each gargoyle. */
	static final ItemGroup ROCK_HAMMERS = items("a rock hammer or granite hammer",
		item(ItemID.SLAYER_ROCK_HAMMER, "Rock hammer"), item(ItemID.GRANITE_HAMMER, "Granite hammer"));
	static final ItemGroup ROCK_THROWNHAMMER = items("rock thrownhammers",
		item(ItemID.SLAYER_ROCK_THROWNHAMMER, "Rock thrownhammer"));
	static final ItemGroup ANY_ROCK_HAMMER = group("a rock hammer, granite hammer or rock thrownhammer",
		ROCK_HAMMERS, ROCK_THROWNHAMMER);
	static final ItemGroup BAG_OF_SALT = items("bags of salt", item(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt"));
	static final ItemGroup BRINE_SABRE = items("a brine sabre", item(ItemID.OLAF2_BRINE_SABRE, "Brine sabre"));
	static final ItemGroup ICE_COOLER = items("ice coolers", item(ItemID.SLAYER_ICY_WATER, "Ice cooler"));
	static final ItemGroup FISHING_EXPLOSIVE = items("fishing explosives", item(ItemID.FISHING_EXPLOSIVE, "Fishing explosive"));
	/** The name carries the charges left, "Fungicide spray 0" to "Fungicide spray 10". */
	static final ItemGroup FUNGICIDE_SPRAY = items("a fungicide spray",
		item(ItemID.SLAYER_SPRAY_PUMP_10, "Fungicide spray 10"), item(ItemID.SLAYER_SPRAY_PUMP_9, "Fungicide spray 9"),
		item(ItemID.SLAYER_SPRAY_PUMP_8, "Fungicide spray 8"), item(ItemID.SLAYER_SPRAY_PUMP_7, "Fungicide spray 7"),
		item(ItemID.SLAYER_SPRAY_PUMP_6, "Fungicide spray 6"), item(ItemID.SLAYER_SPRAY_PUMP_5, "Fungicide spray 5"),
		item(ItemID.SLAYER_SPRAY_PUMP_4, "Fungicide spray 4"), item(ItemID.SLAYER_SPRAY_PUMP_3, "Fungicide spray 3"),
		item(ItemID.SLAYER_SPRAY_PUMP_2, "Fungicide spray 2"), item(ItemID.SLAYER_SPRAY_PUMP_1, "Fungicide spray 1"),
		item(ItemID.SLAYER_SPRAY_PUMP_0, "Fungicide spray 0"));
	static final ItemGroup FUNGICIDE = items("fungicide", item(ItemID.SLAYER_FUNGICIDE, "Fungicide"));
	/** Each refill puts 10 charges into an emptied spray. */
	static final int FUNGICIDE_REFILL_CHARGES = 10;

	/** Tier 3 vampyres (vyrewatch, sentinels, venators) take damage from these alone. */
	static final ItemGroup VAMPYREBANE = items("an Ivandis flail or better vampyrebane weapon",
		item(ItemID.IVANDIS_FLAIL, "Ivandis flail"), item(ItemID.BLISTERWOOD_SICKLE, "Blisterwood sickle"),
		item(ItemID.BLISTERWOOD_FLAIL, "Blisterwood flail"), item(ItemID.HALLOWED_FLAIL, "Hallowed flail"),
		item(ItemID.BLISTERWOOD_STAKE, "Blisterwood stake"), item(ItemID.SUNSPEAR, "Sunspear"));
	/** Tier 2 vampyres (juvinates) also take damage from silver weapons, or from any weapon with Efaritay's aid. */
	static final ItemGroup SILVER_OR_EFARITAY = group("a silver weapon or Efaritay's aid", VAMPYREBANE, items("silver",
		item(ItemID.VAMPYRE_RING, "Efaritay's aid"), item(ItemID.ANMA_AXE, "Blessed axe"),
		item(ItemID.BURGH_ROD_COMMAND_FINAL_10, "Rod of Ivandis (10)"), item(ItemID.BURGH_ROD_COMMAND_FINAL_9, "Rod of Ivandis (9)"),
		item(ItemID.BURGH_ROD_COMMAND_FINAL_8, "Rod of Ivandis (8)"), item(ItemID.BURGH_ROD_COMMAND_FINAL_7, "Rod of Ivandis (7)"),
		item(ItemID.BURGH_ROD_COMMAND_FINAL_6, "Rod of Ivandis (6)"), item(ItemID.BURGH_ROD_COMMAND_FINAL_5, "Rod of Ivandis (5)"),
		item(ItemID.BURGH_ROD_COMMAND_FINAL_4, "Rod of Ivandis (4)"), item(ItemID.BURGH_ROD_COMMAND_FINAL_3, "Rod of Ivandis (3)"),
		item(ItemID.BURGH_ROD_COMMAND_FINAL_2, "Rod of Ivandis (2)"), item(ItemID.BURGH_ROD_COMMAND_FINAL_1, "Rod of Ivandis (1)"),
		item(ItemID.XBOWS_CROSSBOW_BOLTS_SILVER, "Silver bolts"),
		item(ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED, "Silver bolts (p)"),
		item(ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED_, "Silver bolts (p+)"),
		item(ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED__, "Silver bolts (p++)"),
		item(ItemID.SILVER_SICKLE, "Silver sickle"), item(ItemID.SILVER_SICKLE_BLESSED, "Silver sickle (b)"),
		item(ItemID.SILVER_SICKLE_EMERALD, "Emerald sickle (b)"),
		item(ItemID.SILVER_SICKLE_ENCHANTED, "Enchanted emerald sickle (b)"),
		item(ItemID.SILVER_SICKLE_RUBY, "Ruby sickle (b)"), item(ItemID.SILVER_SICKLE_RUBY_ENCHANTED, "Enchanted ruby sickle (b)"),
		item(ItemID.SILVER_SICKLE_DIAMOND, "Diamond sickle (b)"),
		item(ItemID.SILVER_SICKLE_DIAMOND_ENCHANTED, "Enchanted diamond sickle (b)"),
		item(ItemID.BLISTERWOOD_SICKLE_ENHANCED, "Blisterwood sickle (e)"), item(ItemID.SILVERLIGHT, "Silverlight"),
		item(ItemID.AGRITH_SILVERLIGHT_DYED, "Silverlight"), item(ItemID.DARKLIGHT, "Darklight"),
		item(ItemID.ARCLIGHT, "Arclight"), item(ItemID.EMBERLIGHT, "Emberlight"), item(ItemID.DAGGER_WOLFBANE, "Wolfbane")));

	// ---- the recommended tier ----

	/**
	 * Wiki: Demonbane weapons, Arclight. An Arclight out of charges turns inactive and works as Darklight does. The
	 * purging staff helps through demonbane spells. Demonbane spells need no item, so a staff of another kind is not counted.
	 */
	static final ItemGroup DEMONBANE = items("a demonbane weapon",
		item(ItemID.SILVERLIGHT, "Silverlight"), item(ItemID.AGRITH_SILVERLIGHT_DYED, "Silverlight"), item(ItemID.DARKLIGHT, "Darklight"),
		item(ItemID.ARCLIGHT, "Arclight"), item(ItemID.ARCLIGHT_INACTIVE, "Arclight (inactive)"), item(ItemID.EMBERLIGHT, "Emberlight"),
		item(ItemID.BONE_CLAWS, "Burning claws"), item(ItemID.SCORCHING_BOW, "Scorching bow"), item(ItemID.PURGING_STAFF, "Purging staff"),
		item(ItemID.HOLY_WATER, "Holy water"));
	/** Wiki: Dragonbane weapons, Dragon hunter crossbow (t) and (b). */
	static final ItemGroup DRAGONBANE = items("a dragonbane weapon",
		item(ItemID.DRAGONHUNTER_XBOW, "Dragon hunter crossbow"), item(ItemID.DRAGONHUNTER_XBOW_VORKATH, "Dragon hunter crossbow (t)"),
		item(ItemID.DRAGONHUNTER_XBOW_KBD, "Dragon hunter crossbow (b)"), item(ItemID.DRAGONHUNTER_LANCE, "Dragon hunter lance"),
		item(ItemID.DRAGONHUNTER_WAND, "Dragon hunter wand"));
	/** Wiki: Keris, Keris partisan, Kalphite (attribute). Every partisan keeps the keris's bonus, the amascut one at 15%. */
	static final ItemGroup KERIS = items("a keris or keris partisan",
		item(ItemID.CONTACT_KERIS, "Keris"), item(ItemID.CONTACT_KERIS_P, "Keris(p)"), item(ItemID.CONTACT_KERIS_P_, "Keris(p+)"),
		item(ItemID.CONTACT_KERIS_P__, "Keris(p++)"), item(ItemID.KERIS_PARTISAN, "Keris partisan"),
		item(ItemID.KERIS_PARTISAN_BREACH, "Keris partisan of breaching"), item(ItemID.KERIS_PARTISAN_CORRUPTION, "Keris partisan of corruption"),
		item(ItemID.KERIS_PARTISAN_SUN, "Keris partisan of the sun"), item(ItemID.KERIS_PARTISAN_AMASCUT, "Keris partisan of amascut"));
	/**
	 * Wiki: Dragonfire (Protection). The shields count charged or not. The elemental and mind shields do not protect from
	 * dragonfire, and the Nightmare Zone copy of the anti-dragon shield only works there.
	 */
	static final ItemGroup DRAGONFIRE_PROTECTION = items("an anti-dragon shield or antifire potion",
		item(ItemID.ANTIDRAGONBREATHSHIELD, "Anti-dragon shield"), item(ItemID.DRAGONFIRE_SHIELD, "Dragonfire shield"),
		item(ItemID.DRAGONFIRE_SHIELD_UNCHARGED, "Dragonfire shield"), item(ItemID.DRAGONFIRE_WARD, "Dragonfire ward"),
		item(ItemID.DRAGONFIRE_WARD_UNCHARGED, "Dragonfire ward"), item(ItemID.WYVERN_SHIELD, "Ancient wyvern shield"),
		item(ItemID.WYVERN_SHIELD_UNCHARGED, "Ancient wyvern shield"),
		item(ItemID._4DOSE1ANTIDRAGON, "Antifire potion(4)"), item(ItemID._3DOSE1ANTIDRAGON, "Antifire potion(3)"),
		item(ItemID._2DOSE1ANTIDRAGON, "Antifire potion(2)"), item(ItemID._1DOSE1ANTIDRAGON, "Antifire potion(1)"),
		item(ItemID.BRUTAL_2DOSE1ANTIDRAGON, "Antifire mix(2)"), item(ItemID.BRUTAL_1DOSE1ANTIDRAGON, "Antifire mix(1)"),
		item(ItemID._4DOSE2ANTIDRAGON, "Extended antifire(4)"), item(ItemID._3DOSE2ANTIDRAGON, "Extended antifire(3)"),
		item(ItemID._2DOSE2ANTIDRAGON, "Extended antifire(2)"), item(ItemID._1DOSE2ANTIDRAGON, "Extended antifire(1)"),
		item(ItemID.BRUTAL_2DOSE2ANTIDRAGON, "Extended antifire mix(2)"), item(ItemID.BRUTAL_1DOSE2ANTIDRAGON, "Extended antifire mix(1)"),
		item(ItemID._4DOSE3ANTIDRAGON, "Super antifire potion(4)"), item(ItemID._3DOSE3ANTIDRAGON, "Super antifire potion(3)"),
		item(ItemID._2DOSE3ANTIDRAGON, "Super antifire potion(2)"), item(ItemID._1DOSE3ANTIDRAGON, "Super antifire potion(1)"),
		item(ItemID.BRUTAL_2DOSE3ANTIDRAGON, "Super antifire mix(2)"), item(ItemID.BRUTAL_1DOSE3ANTIDRAGON, "Super antifire mix(1)"),
		item(ItemID._4DOSE4ANTIDRAGON, "Extended super antifire(4)"), item(ItemID._3DOSE4ANTIDRAGON, "Extended super antifire(3)"),
		item(ItemID._2DOSE4ANTIDRAGON, "Extended super antifire(2)"), item(ItemID._1DOSE4ANTIDRAGON, "Extended super antifire(1)"),
		item(ItemID.BRUTAL_2DOSE4ANTIDRAGON, "Extended super antifire mix(2)"),
		item(ItemID.BRUTAL_1DOSE4ANTIDRAGON, "Extended super antifire mix(1)"));
	/** Wiki: Insulated boots, Killerwatt. The Killerwatt page gives a max hit of 15 without them and 8 with them. */
	static final ItemGroup INSULATED_BOOTS = items("insulated boots", item(ItemID.SLAYER_BOOTS, "Insulated boots"));

	/** Every black mask, charged or not, and its Nightmare Zone, Soul Wars and Emir's Arena imbues. Wiki: Black mask, Black mask (i). */
	static final int[] BLACK_MASK_IDS = {
		ItemID.HARMLESS_BLACK_MASK, ItemID.HARMLESS_BLACK_MASK_1, ItemID.HARMLESS_BLACK_MASK_2, ItemID.HARMLESS_BLACK_MASK_3,
		ItemID.HARMLESS_BLACK_MASK_4, ItemID.HARMLESS_BLACK_MASK_5, ItemID.HARMLESS_BLACK_MASK_6, ItemID.HARMLESS_BLACK_MASK_7,
		ItemID.HARMLESS_BLACK_MASK_8, ItemID.HARMLESS_BLACK_MASK_9, ItemID.HARMLESS_BLACK_MASK_10,
		ItemID.NZONE_BLACK_MASK, ItemID.NZONE_BLACK_MASK_1, ItemID.NZONE_BLACK_MASK_2, ItemID.NZONE_BLACK_MASK_3,
		ItemID.NZONE_BLACK_MASK_4, ItemID.NZONE_BLACK_MASK_5, ItemID.NZONE_BLACK_MASK_6, ItemID.NZONE_BLACK_MASK_7,
		ItemID.NZONE_BLACK_MASK_8, ItemID.NZONE_BLACK_MASK_9, ItemID.NZONE_BLACK_MASK_10,
		ItemID.SW_BLACK_MASK, ItemID.SW_BLACK_MASK_1, ItemID.SW_BLACK_MASK_2, ItemID.SW_BLACK_MASK_3, ItemID.SW_BLACK_MASK_4,
		ItemID.SW_BLACK_MASK_5, ItemID.SW_BLACK_MASK_6, ItemID.SW_BLACK_MASK_7, ItemID.SW_BLACK_MASK_8, ItemID.SW_BLACK_MASK_9,
		ItemID.SW_BLACK_MASK_10,
		ItemID.PVPA_BLACK_MASK, ItemID.PVPA_BLACK_MASK_1, ItemID.PVPA_BLACK_MASK_2, ItemID.PVPA_BLACK_MASK_3, ItemID.PVPA_BLACK_MASK_4,
		ItemID.PVPA_BLACK_MASK_5, ItemID.PVPA_BLACK_MASK_6, ItemID.PVPA_BLACK_MASK_7, ItemID.PVPA_BLACK_MASK_8, ItemID.PVPA_BLACK_MASK_9,
		ItemID.PVPA_BLACK_MASK_10,
	};
	/** Wiki: Salve amulet, Salve amulet(i), Salve amulet (e), Salve amulet(ei), each imbue from all three places. */
	static final ItemGroup SALVE_AMULETS = items("a salve amulet",
		item(ItemID.CRYSTALSHARD_NECKLACE, "Salve amulet"), item(ItemID.LOTR_CRYSTALSHARD_NECKLACE_UPGRADE, "Salve amulet (e)"),
		item(ItemID.NZONE_SALVE_AMULET, "Salve amulet(i)"), item(ItemID.SW_SALVE_AMULET, "Salve amulet(i)"),
		item(ItemID.PVPA_SALVE_AMULET, "Salve amulet(i)"), item(ItemID.NZONE_SALVE_AMULET_E, "Salve amulet(ei)"),
		item(ItemID.SW_SALVE_AMULET_E, "Salve amulet(ei)"), item(ItemID.PVPA_SALVE_AMULET_E, "Salve amulet(ei)"));
	static final ItemGroup BLACK_MASK = new ItemGroup("a black mask", named(BLACK_MASK_IDS, "Black mask"), false);
	/**
	 * The on-task bonus of the helmet or mask (wiki: Slayer helmet, Black mask). It does not stack with a salve amulet,
	 * which does better against the undead, so the salve counts on undead tasks. Where a mandatory item takes the head
	 * slot only the helmet does both jobs, so the mask is left out there.
	 */
	static final ItemGroup HELMET_OR_MASK = group("a Slayer helmet or black mask", SLAYER_HELMET, BLACK_MASK);
	static final ItemGroup HELMET_MASK_OR_SALVE = group("a Slayer helmet, black mask or salve amulet", SLAYER_HELMET, BLACK_MASK,
		SALVE_AMULETS);
	static final ItemGroup HELMET_OR_SALVE = group("a Slayer helmet or salve amulet", SLAYER_HELMET, SALVE_AMULETS);
	/** Malevolent Masquerade. Once it is bought the player can be expected to wear the helmet, so every task asks for it. */
	static final int HELMET_UNLOCK_VARBIT = VarbitID.SLAYER_HELM_UNLOCKED;
	/** Wiki: Light sources (Gas explosions). These four are the lit light sources whose flame meets the swamp gas. */
	static final ItemGroup OPEN_FLAMES = items("an open flame",
		item(ItemID.LIT_CANDLE, "Lit candle"), item(ItemID.LIT_BLACK_CANDLE, "Lit black candle"), item(ItemID.TORCH_LIT, "Lit torch"),
		item(ItemID.OIL_LAMP_LIT, "Oil lamp"));
	/** Every other lit light source, which the same table says does not set off the gas: lanterns, capes, headgear and so on. */
	static final ItemGroup COVERED_LIGHT = without("a covered lantern", LIGHT_SOURCE, OPEN_FLAMES);
	static final String SWAMP_GAS_REASON = "An open flame sets off the swamp gas in parts of the Lumbridge Swamp Caves and goes out";

	static final String ELITE_DIARY_NOTE = "The elite Kourend & Kebos diary protects you from the hot floor";
	static final String NO_ICON_ADVICE = "No witchwood icon, so keep Protect from Melee on";
	static final String BLESSED_AXE_NOTE = "A blessed axe does not cut the vines";
	static final String BRIMHAVEN_FEE_NOTE = "Saniboch takes 875 coins each time, or 1,000,000 once for permanent access";
	static final String KALPHITE_DIARY_NOTE = "The hard Desert diary lets you into the Kalphite Lair without a rope";
	static final String ZANARIS_DIARY_NOTE = "The elite Lumbridge & Draynor diary lets you into Zanaris without a staff";
	static final String FAIRY_RING_DIARY_NOTE = "The elite Lumbridge & Draynor diary lets you use the fairy rings without a staff";
	static final String SWAMP_ROPE_ADVICE = "The entrance in Lumbridge Swamp needs a rope the first time";
	static final String GOD_WARS_ROPE_ADVICE = "The God Wars Dungeon entrance needs a rope the first time";
	static final String NO_DUSTY_KEY_ADVICE = "No dusty key, so you need 70 Agility for the pipe past the gate";
	static final String DAEYALT_REASON = "The quick way in through the mines needs 15 daeyalt ore mined to get out, unless you wear"
		+ " three pieces of Vyre noble clothing";

	static final Unlock GARGOYLE_SMASHER = new Unlock("Gargoyle Smasher", VarbitID.SLAYER_AUTOKILL_GARGOYLES, ANY_ROCK_HAMMER);
	static final Unlock SLUG_SALTER = new Unlock("Slug Salter", VarbitID.SLAYER_AUTOKILL_ROCKSLUGS, BAG_OF_SALT);
	static final Unlock REPTILE_FREEZER = new Unlock("Reptile Freezer", VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS, ICE_COOLER);
	static final Unlock SHROOM_SPRAYER = new Unlock("'Shroom Sprayer", VarbitID.SLAYER_AUTOKILL_ZYGOMITES, FUNGICIDE_SPRAY);

	private static final Map<String, Requirement> REQUIREMENTS = new LinkedHashMap<>();
	/** Needs that come with a place whatever the task, by the place's key in /slayer_areas.json. */
	private static final Map<String, List<Need>> PLACE_NEEDS = new HashMap<>();
	/** The recommended tier a place adds whatever the task, by the same key. */
	private static final Map<String, List<Recommendation>> PLACE_RECOMMENDED = new HashMap<>();
	/** Konar's SLAYER_AREA ids of the places these tables name, so an area she assigned brings their needs by its id alone. */
	private static final Map<Integer, String> AREA_PLACES = new HashMap<>();
	/** Every place key these tables name, with a need, a recommendation or a need of a task tied to it. */
	private static final Set<String> PLACE_KEYS = new LinkedHashSet<>();
	/** The recommended tier: item classes by task, in the order the line names them. The helmet is added for every task. */
	private static final Map<String, List<Recommendation>> RECOMMENDED = new LinkedHashMap<>();
	/** Tasks whose monsters have the undead attribute (wiki: Undead (attribute)). Ghouls, vampyres and skeletal wyverns do not. */
	private static final Set<String> UNDEAD = new LinkedHashSet<>();

	static
	{
		// The whole dungeon past the entrance chamber burns 4 a tick without these boots or the elite diary.
		final Need karuulmBoots = Need.have(KARUULM_BOOTS).unless(VarbitID.KOUREND_DIARY_ELITE_COMPLETE, ELITE_DIARY_NOTE);
		final Need lumbridgeLight = Need.have(LIGHT_SOURCE)
			.unless(VarbitID.MY2ARM_FIRE_LUMB, "Your Fire of Eternal Light lights the Lumbridge Swamp Caves");
		// Cave bugs and cave slimes live in the Lumbridge Swamp Caves and the Dorgesh-Kaan South Dungeon. Both are dark.
		final Need caveLight = Need.have(LIGHT_SOURCE)
			.unless(VarbitID.MY2ARM_FIRE_LUMB, "Your Fire of Eternal Light lights the Lumbridge Swamp Caves",
				"for the Dorgesh-Kaan South Dungeon");

		put("Aberrant spectres", null, Need.have(NOSE_PEG_OR_HELMET));
		put("Banshees", null, Need.have(EARMUFFS_OR_HELMET));
		put("Basilisks", null, Need.have(MIRROR_SHIELDS));
		put("Cockatrice", null, Need.have(MIRROR_SHIELDS));
		put("Dust devils", null, Need.have(FACEMASK_OR_HELMET));
		put("Smoke devils", null, Need.have(FACEMASK_OR_HELMET));
		put("Fever spiders", null, Need.have(SLAYER_GLOVES));
		put("Sourhogs", null, Need.have(GOGGLES_OR_HELMET));
		put("Skeletal wyverns", null, Need.have(WYVERN_SHIELDS));
		put("Fossil island wyverns", null, Need.have(WYVERN_SHIELDS));
		put("Turoth", null, Need.have(TUROTH_KURASK_WEAPONS));
		put("Kurask", null, Need.have(TUROTH_KURASK_WEAPONS));
		put("Harpie bug swarms", null, Need.have(LIT_BUG_LANTERN));
		put("Warped Creatures", null, Need.have(CRYSTAL_CHIME));
		put("Wall beasts", null, Need.have(SPINY_OR_HELMET), lumbridgeLight);
		put("Cave bugs", null, caveLight);
		put("Cave slimes", null, caveLight);
		put("Molanisks", null, Need.have(SLAYER_BELL), Need.have(LIGHT_SOURCE));
		// Protect from Melee stops the scream completely, so the icon is advice. The cave is dark.
		put("Cave horrors", null, Need.have(LIGHT_SOURCE)
				.unless(VarbitID.MY2ARM_FIRE_HORRORS, "Your Fire of Eternal Light lights the Mos Le'Harmless Cave"),
			Need.advice(WITCHWOOD_ICON, NO_ICON_ADVICE));
		// Needed to get in: the Brine Rat Cavern is entered by digging, the magic axe hut has locked doors.
		put("Brine rats", null, Need.have(SPADE));
		put("Magic axes", null, Need.have(LOCKPICK));

		put("Gargoyles", GARGOYLE_SMASHER, Need.perKill(ROCK_THROWNHAMMER, "rock thrownhammer", "rock thrownhammers")
			.or(ROCK_HAMMERS, true));
		put("The Grotesque Guardians", null, Need.have(ANY_ROCK_HAMMER));
		put("Rockslugs", SLUG_SALTER, Need.perKill(BAG_OF_SALT, "bag of salt", "bags of salt").or(BRINE_SABRE, false));
		// Sulphur and grimy lizards also count for the task and need no ice coolers.
		// Desert lizards alone need the coolers. Sulphur lizards at Mount Karuulm and in Neypotzli, which Konar assigns, do not.
		put("Lizards", REPTILE_FREEZER, Need.perKill(ICE_COOLER, "ice cooler", "ice coolers").onlyAt("for desert lizards",
			"kharidian_desert", "kharidian_desert_north_of_nardah", "south_east_of_shantay_pass", "west_of_ruins_of_ullek"));
		put("Mutated zygomites", SHROOM_SPRAYER, Need.charges(FUNGICIDE_SPRAY, FUNGICIDE, FUNGICIDE_REFILL_CHARGES,
			"fungicide spray charge", "fungicide spray charges"));
		// Mogres only surface for an explosive at Mudskipper Point. Since Sailing a boat's cannons can also kill them.
		put("Mogres", null, Need.perKill(FISHING_EXPLOSIVE, "fishing explosive", "fishing explosives")
			.onlyAt("for Mudskipper Point"));

		put("Drakes", null, karuulmBoots);
		put("Hydras", null, karuulmBoots);
		put("The Alchemical Hydra", null, karuulmBoots);
		// Wyrms also live in Wyrmscraig, Neypotzli and the Charred Dungeon, where the floor is safe.
		put("Wyrms", null, karuulmBoots.onlyAt("for the Karuulm Slayer Dungeon", KARUULM_SLAYER_DUNGEON));

		put("Venators", null, Need.have(VAMPYREBANE));
		// Feral vampyres elsewhere take damage from any weapon, so only these places need anything.
		put("Vampyres", null, Need.have(VAMPYREBANE).onlyIn(SLEPE, VAMPYRIUM), Need.have(SILVER_OR_EFARITAY).onlyIn(DARKMEYER));

		put("The Giant Mole", null, Need.have(SPADE), Need.have(LIGHT_SOURCE)
			.unless(VarbitID.MY2ARM_FIRE_MOLE, "Your Fire of Eternal Light lights the Mole Hole"));
		put("The Shellbane Gryphon", null, Need.have(TORTUGAN_SHIELD));

		// Wiki: Taverley Dungeon, "Access to this area requires a dusty key, or at least 70 Agility to use the obstacle pipe."
		// The hellhounds and black demons live in that second half. 70 Agility does without the key, so it is advice.
		final Need dustyKey = Need.advice(DUSTY_KEY, NO_DUSTY_KEY_ADVICE).onlyIn(TAVERLEY_DUNGEON);
		put("Black demons", null, dustyKey);
		put("Hellhounds", null, dustyKey);
		// Wiki: Slayer task/Hill giants, "Requires level 70 Strength and a hammer or dragon warhammer (and a rope the first
		// time)." Its cyclopes are in Bandos' Stronghold, whose gong is struck with a hammer (wiki: God Wars Dungeon).
		put("Hill giants", null, Need.have(HAMMERS).onlyIn(GOD_WARS_DUNGEON));

		// ---- what places need, whatever the task ----

		area(KARUULM_AREA, KARUULM_SLAYER_DUNGEON);
		area(BRIMHAVEN_AREA, BRIMHAVEN_DUNGEON);
		area(TAVERLEY_AREA, TAVERLEY_DUNGEON);
		area(WATERFALL_AREA, "waterfall_dungeon");
		area(GOD_WARS_AREA, GOD_WARS_DUNGEON);
		area(KALPHITE_LAIR_AREA, "kalphite_lair");
		area(SMOKE_DUNGEON_AREA, "smoke_dungeon");
		area(BRINE_RAT_AREA, "brine_rat_cavern");
		area(ZANARIS_AREA, "zanaris");
		area(EVIL_CHICKEN_AREA, "evil_chickens_lair");
		area(DARKMEYER_AREA, DARKMEYER);
		area(SLEPE_AREA, SLEPE);
		area(MEIYERDITCH_LABS_AREA, "meiyerditch_laboratories");
		area(VAMPYRIUM_AREA, VAMPYRIUM);

		place(KARUULM_SLAYER_DUNGEON, karuulmBoots);
		// The rope takes you over the rock and down the dead tree to the entrance. It is not used up.
		place("waterfall_dungeon", Need.have(ROPE).unless(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
			"The medium Kandarin diary lets you into the Waterfall Dungeon without a rope"));
		// Wiki: Brimhaven Dungeon, "A player will also need an axe to cut the vines blocking the path (requiring level 10
		// Woodcutting)." and "A blessed axe will not work." Saniboch: "Players must pay 875 coins to him to gain entrance, or a
		// one-off fee of 1,000,000 coins (equivalent to 1,143 entry fees) for permanent free access." The million paid sets
		// KARAM_DUNGEON_PERMANENTACCESS, and the coins are not asked for then.
		place(BRIMHAVEN_DUNGEON, Need.have(AXES).because(BLESSED_AXE_NOTE),
			Need.amount(COINS, BRIMHAVEN_FEE, "coin", "coins").because(BRIMHAVEN_FEE_NOTE).unless(VarbitID.KARAM_DUNGEON_PERMANENTACCESS));
		// Wiki: Kalphite Lair, "The main entrance requires one rope each visit unless the Hard Desert Diary is done."
		place("kalphite_lair", Need.have(ROPE).unless(VarbitID.DESERT_DIARY_HARD_COMPLETE, KALPHITE_DIARY_NOTE));
		// Wiki: Brine Rat Cavern, "The Brine Rat Cavern is in the north-eastern region of the Fremennik Province, and requires a
		// spade to access." The brine rats ask for it too, and it is named once.
		place("brine_rat_cavern", Need.have(SPADE));
		// Wiki: Spade, "A spade is also needed to enter the Barrows crypts and the Mole Hole."
		place("barrows", Need.have(SPADE));
		place("barrows_crypt", Need.have(SPADE));
		// Wiki: Zanaris, "Zanaris can also be accessed by wielding a dramen staff or lunar staff and entering the small hut in
		// the middle of Lumbridge Swamp", the other way in being a fairy ring. Fairy ring: "If the elite Lumbridge & Draynor
		// Diary is completed, no staff of either kind is needed." Shed (Lumbridge Swamp) says the same of the hut.
		final Need zanarisStaff = Need.have(DRAMEN_OR_LUNAR_STAFF).unless(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, ZANARIS_DIARY_NOTE);
		place("zanaris", zanarisStaff);
		// Wiki: Evil Chicken's Lair, "The Evil Chicken's Lair is located on Zanaris and is accessed by using a raw chicken on the
		// Chicken Shrine." Slayer task/Black dragons calls it a sacrifice, so each way in takes one.
		place("evil_chickens_lair", zanarisStaff, Need.have(RAW_CHICKEN));
		// Wiki: Enchanted Valley, "The Enchanted Valley is a hidden realm that is only accessible by fairy ring, using code BKQ."
		// Fairy ring: "Players can access the fairy ring system from any fairy ring while wielding either a dramen staff or
		// lunar staff."
		place("enchanted_valley", Need.have(DRAMEN_OR_LUNAR_STAFF).unless(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE,
			FAIRY_RING_DIARY_NOTE));
		// Wiki: Lumbridge Swamp Caves, the entrance in the swamp is "requiring a rope to enter. After one rope has been used,
		// further ropes are not required." Only the first time, so it is advice, and it goes once the varbit says the rope is
		// tied. The light the caves need stays with their tasks above.
		place("lumbridge_swamp_caves", Need.advice(ROPE, SWAMP_ROPE_ADVICE).unless(VarbitID.SWAMP_CAVES_ROPED_ENTRANCE));
		// Wiki: God Wars Dungeon, "A rope is also required the first time." Likewise advice until the rope is tied.
		place(GOD_WARS_DUNGEON, Need.advice(ROPE, GOD_WARS_ROPE_ADVICE).unless(VarbitID.GODWARS_ENTRANCE_ROPE));
		// Wiki: Glarial's Tomb, "To enter, the player needs Glarial's pebble, which is obtained during the Waterfall Quest"
		place("glarials_tomb", Need.have(GLARIALS_PEBBLE));
		// Wiki: Maze key, "The maze key is a key used to open the front door of Melzar's Maze."
		place("melzars_maze", Need.have(MAZE_KEY));
		// Wiki: Digsite Dungeon, "The player must use a rope on the west winch to enter the dungeon there, which will not be
		// consumed." The skeletons are in the south chamber of that part.
		place("digsite_dungeon", Need.have(ROPE));
		// Wiki: Pirates' Hideout, "A lockpick and 39 Thieving are required to pick the door lock and gain entry."
		place("pirates_hideout", Need.have(LOCKPICK));
		// Wiki: Smoke Dungeon, "While in the dungeon, the player must wear a facemask, Slayer helmet, or Slayer helmet (i) to
		// protect themselves from the smoke." Without one the smoke deals 20 damage every 12 seconds. The dust devils ask for
		// a facemask or helmet themselves, which this then adds nothing to.
		place("smoke_dungeon", Need.have(SMOKE_PROTECTION));
		// Wiki: Warriors' Guild, "Players need to carry at least 100 warrior guild tokens to enter this room." and "No tokens
		// are required for players that wear the level 99 Attack cape." Of the basement: "The same rules as on the top floor
		// apply here". Each minute inside takes 10 more.
		final Need tokens = Need.amount(WARRIOR_GUILD_TOKENS, CYCLOPS_ROOM_TOKENS, "warrior guild token", "warrior guild tokens")
			.or(ATTACK_CAPES, false);
		place("warriors_guild", tokens);
		place("warriors_guild_basement", tokens);
		// Wiki: Ice Queen's Lair, "The entrance is blocked by a rock slide requiring a pickaxe and level 50 Mining to remove."
		place("ice_queens_lair", Need.have(PICKAXES));
		// Wiki: Shade key, "At least 1 key of any type is required to enter the catacombs"
		place("shade_catacombs", Need.have(SHADE_KEYS));
		// Wiki: Meiyerditch Laboratories, "To exit the mine immediately, players who haven't completed The Blood Moon Rises up
		// until the final Lowerniel Drakan fight will need to equip three pieces of Vyre noble clothing when speaking with a
		// Vampyre Juvinate. Otherwise, one must mine 15 daeyalt ore to exit; bring the best pickaxe you can use". The other
		// ways in need none, so it is recommended.
		recommendAt("meiyerditch_laboratories", Recommendation.because(PICKAXES, DAEYALT_REASON));

		// The recommended tier. Wiki: Dragonfire. Chromatic, lava and frost dragons breathe it up close, brutal and metal
		// dragons and the bosses from range. Baby dragons cannot. Drakes are left out, as their breath is dodged by moving a
		// tile and the Drake page says they need no protection. Wyverns breathe ice, which these items do not stop.
		recommend(DRAGONFIRE_PROTECTION, "Black dragons", "Blue dragons", "Green dragons", "Red dragons", "Metal dragons", "Lava Dragons",
			"Frost dragons", "The King Black Dragon", "Vorkath");
		// Wiki: Draconic (attribute), whose list covers dragons, drakes, hydras, wyrms and wyverns.
		recommend(DRAGONBANE, "Black dragons", "Blue dragons", "Green dragons", "Red dragons", "Metal dragons", "Lava Dragons",
			"Frost dragons", "The King Black Dragon", "Vorkath", "Drakes", "Hydras", "The Alchemical Hydra", "Wyrms", "Skeletal wyverns",
			"Fossil island wyverns");
		// Wiki: Demon (attribute). Every monster of these tasks is a demon. Duke Sucellus takes 70% of the bonus.
		recommend(DEMONBANE, "Abyssal demons", "Black demons", "Greater demons", "Lesser demons", "Bloodveld", "Hellhounds", "Nechryael",
			"Pyrefiends", "Icefiends", "Waterfiends", "The Abyssal Sire", "Cerberus", "K'ril Tsutsaroth", "Duke Sucellus");
		// Wiki: Kalphite (attribute). Scabarites count too.
		recommend(KERIS, "Kalphites", "The Kalphite Queen", "Scabarites");
		recommend(INSULATED_BOOTS, "Killerwatts");
		// Wiki: Lumbridge Swamp Caves, Light sources (Gas explosions). An open flame still lights the cave, so the darkness
		// check takes it, but in parts of the caves it sets off the gas, which hurts and puts it out. The Fire of Eternal
		// Light stops the explosions. Asked for only while a flame is the light, so a player with no light gets the red line.
		recommend(Recommendation.onlyWith(COVERED_LIGHT, OPEN_FLAMES, VarbitID.MY2ARM_FIRE_LUMB, SWAMP_GAS_REASON),
			"Cave bugs", "Cave slimes", "Wall beasts");
		Collections.addAll(UNDEAD, normalize("Aberrant spectres"), normalize("Ankou"), normalize("Banshees"), normalize("Crawling hands"),
			normalize("Ghosts"), normalize("Revenants"), normalize("Shades"), normalize("Skeletons"), normalize("Zombies"),
			normalize("Vorkath"), normalize("Vet'ion"));
	}

	private SlayerRequirements()
	{
	}

	static Requirement forTask(String taskName)
	{
		return REQUIREMENTS.get(normalize(taskName));
	}

	/** Needs that any task assigned to this SLAYER_AREA id brings with it. */
	static List<Need> forArea(int areaId)
	{
		return forPlace(placeOfArea(areaId));
	}

	/** Needs that any task done at this place brings with it, by the place's key. Empty for a key without any or null. */
	static List<Need> forPlace(String key)
	{
		final List<Need> needs = key == null ? null : PLACE_NEEDS.get(key);
		return needs == null ? Collections.emptyList() : needs;
	}

	/** The recommended tier this place adds to any task done there. Empty when it adds none. */
	static List<Recommendation> recommendedAt(String key)
	{
		final List<Recommendation> recommendations = key == null ? null : PLACE_RECOMMENDED.get(key);
		return recommendations == null ? Collections.emptyList() : recommendations;
	}

	/** The key of the place Konar assigned by its SLAYER_AREA id, for the places these tables name. Null for any other area. */
	static String placeOfArea(int areaId)
	{
		return AREA_PLACES.get(areaId);
	}

	/** Every place key the tables name, for the tests that keep them equal to the keys of /slayer_areas.json. */
	static Set<String> placeKeys()
	{
		return Collections.unmodifiableSet(PLACE_KEYS);
	}

	/** Konar's area ids of those places, for the same tests. */
	static Map<Integer, String> areaPlaces()
	{
		return Collections.unmodifiableMap(AREA_PLACES);
	}

	/** Normalised task names with a requirement, for the tests that keep them equal to the game's names. */
	static Set<String> taskKeys()
	{
		return Collections.unmodifiableSet(REQUIREMENTS.keySet());
	}

	/** The recommended tier for a task, without the helmet. Empty when there is none. */
	static List<Recommendation> recommendedFor(String taskName)
	{
		final List<Recommendation> recommendations = RECOMMENDED.get(normalize(taskName));
		return recommendations == null ? Collections.emptyList() : Collections.unmodifiableList(recommendations);
	}

	/** Normalised task names with recommended items, for the tests that keep them equal to the game's names. */
	static Set<String> recommendedKeys()
	{
		return Collections.unmodifiableSet(RECOMMENDED.keySet());
	}

	/** Normalised names of the undead tasks, for the same test. */
	static Set<String> undeadKeys()
	{
		return Collections.unmodifiableSet(UNDEAD);
	}

	/** Every item id the item groups list, the only items the bank memory keeps. */
	static Set<Integer> gearIds()
	{
		return GEAR_IDS_VIEW;
	}

	/** Whether a mandatory item of the task is one a Slayer helmet stands in for. Each of those goes on the head. */
	static boolean helmetStandsIn(String taskName)
	{
		final Requirement requirement = forTask(taskName);
		return requirement != null && helmetStandsIn(requirement.getNeeds());
	}

	/** Whether one of these needs is met by a Slayer helmet, as a facemask's is, so it takes the head slot. */
	static boolean helmetStandsIn(List<Need> needs)
	{
		for (Need need : needs)
		{
			if (need.getItems().ids().contains(ItemID.SLAYER_HELM)) return true;
		}
		return false;
	}

	/**
	 * What gives the task's on-task bonus: a Slayer helmet or black mask, a salve amulet as well against the undead, and
	 * only the helmet where a mandatory item takes the head slot.
	 */
	static ItemGroup slayerHeadgear(String taskName)
	{
		return slayerHeadgear(taskName, helmetStandsIn(taskName));
	}

	/** As above, where {@code headTaken} says whether the needs where the task is done put such an item on the head. */
	static ItemGroup slayerHeadgear(String taskName, boolean headTaken)
	{
		final boolean undead = UNDEAD.contains(normalize(taskName));
		if (headTaken) return undead ? HELMET_OR_SALVE : SLAYER_HELMET;
		return undead ? HELMET_MASK_OR_SALVE : HELMET_OR_MASK;
	}

	static String normalize(String value)
	{
		if (value == null) return "";
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/** Names a recolour of the helmet would get, such as "Hydra slayer helmet (i)". */
	static boolean isSlayerHelmetName(String name)
	{
		if (name == null) return false;
		final String n = name.trim().toLowerCase(Locale.ROOT);
		return n.endsWith("slayer helmet") || n.endsWith("slayer helmet (i)");
	}

	private static void put(String task, Unlock unlock, Need... needs)
	{
		final String key = normalize(task);
		if (REQUIREMENTS.containsKey(key)) throw new IllegalStateException("Two requirements for " + task);
		final List<Need> list = new ArrayList<>();
		Collections.addAll(list, needs);
		REQUIREMENTS.put(key, new Requirement(Collections.unmodifiableList(list), unlock));
		for (Need need : needs)
		{
			if (need.places != null) Collections.addAll(PLACE_KEYS, need.places);
		}
	}

	private static void place(String key, Need... needs)
	{
		if (PLACE_NEEDS.containsKey(key)) throw new IllegalStateException("Two entries for " + key);
		final List<Need> list = new ArrayList<>();
		Collections.addAll(list, needs);
		PLACE_NEEDS.put(key, Collections.unmodifiableList(list));
		PLACE_KEYS.add(key);
	}

	private static void recommendAt(String key, Recommendation... recommendations)
	{
		if (PLACE_RECOMMENDED.containsKey(key)) throw new IllegalStateException("Two recommendations for " + key);
		final List<Recommendation> list = new ArrayList<>();
		Collections.addAll(list, recommendations);
		PLACE_RECOMMENDED.put(key, Collections.unmodifiableList(list));
		PLACE_KEYS.add(key);
	}

	private static void area(int areaId, String key)
	{
		if (AREA_PLACES.put(areaId, key) != null) throw new IllegalStateException("Two places for area " + areaId);
	}

	private static void recommend(ItemGroup items, String... tasks)
	{
		recommend(Recommendation.of(items), tasks);
	}

	private static void recommend(Recommendation recommendation, String... tasks)
	{
		for (String task : tasks)
		{
			final List<Recommendation> recommendations = RECOMMENDED.computeIfAbsent(normalize(task), k -> new ArrayList<>());
			for (Recommendation other : recommendations)
			{
				if (other.getItems() == recommendation.getItems())
				{
					throw new IllegalStateException("Two recommendations of " + recommendation.getItems().getLabel() + " for " + task);
				}
			}
			recommendations.add(recommendation);
		}
	}

	/** The items of {@code from} that are not in {@code minus}. */
	private static ItemGroup without(String label, ItemGroup from, ItemGroup minus)
	{
		final Map<Integer, String> map = new LinkedHashMap<>(from.items);
		map.keySet().removeAll(minus.ids());
		return new ItemGroup(label, map, false);
	}

	/** Ids that share one name, such as the recolours of the helmet. */
	private static Map<Integer, String> named(int[] ids, String name)
	{
		final Map<Integer, String> map = new LinkedHashMap<>();
		for (int id : ids)
		{
			map.put(id, name);
		}
		return map;
	}

	private static Item item(int id, String name)
	{
		return new Item(id, name);
	}

	private static ItemGroup items(String label, Item... items)
	{
		final Map<Integer, String> map = new LinkedHashMap<>();
		for (Item item : items)
		{
			if (map.put(item.id, item.name) != null) throw new IllegalStateException("Item " + item.id + " listed twice");
		}
		return new ItemGroup(label, map, false);
	}

	private static ItemGroup group(String label, ItemGroup... parts)
	{
		final Map<Integer, String> map = new LinkedHashMap<>();
		boolean helmetNames = false;
		for (ItemGroup part : parts)
		{
			map.putAll(part.items);
			helmetNames |= part.anySlayerHelmetName;
		}
		return new ItemGroup(label, map, helmetNames);
	}

	/** One item id with its in-game name. */
	static final class Item
	{
		final int id;
		final String name;

		private Item(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	/** Items that do the same job. Any one of them is enough. */
	static final class ItemGroup
	{
		private final String label;
		private final Map<Integer, String> items;
		private final boolean anySlayerHelmetName;

		private ItemGroup(String label, Map<Integer, String> items, boolean anySlayerHelmetName)
		{
			this.label = label;
			this.items = Collections.unmodifiableMap(items);
			this.anySlayerHelmetName = anySlayerHelmetName;
			GEAR_IDS.addAll(items.keySet());
		}

		/** What the line asks for when none of the items is there, such as "a nose peg or Slayer helmet". */
		String getLabel()
		{
			return label;
		}

		Set<Integer> ids()
		{
			return items.keySet();
		}

		/** The in-game name the group gives an item, which names an item the bank holds. Null when it does not list the id. */
		String name(int id)
		{
			return items.get(id);
		}

		boolean matches(int id, String name)
		{
			return items.containsKey(id) || anySlayerHelmetName && isSlayerHelmetName(name);
		}
	}

	/**
	 * One item class of the recommended tier. Most are asked for whenever none is held. A conditional one is asked for
	 * only while one of another group is held, such as a covered lantern while an open flame is the light, unless its
	 * varbit is set. A reason follows the request in the line.
	 */
	static final class Recommendation
	{
		private final ItemGroup items;
		private final ItemGroup onlyWith;
		private final int exemptVarbit;
		private final String reason;

		private Recommendation(ItemGroup items, ItemGroup onlyWith, int exemptVarbit, String reason)
		{
			this.items = items;
			this.onlyWith = onlyWith;
			this.exemptVarbit = exemptVarbit;
			this.reason = reason;
		}

		static Recommendation of(ItemGroup items)
		{
			return new Recommendation(items, null, -1, null);
		}

		/** Asked for whenever none is held, with the reason after the request. */
		static Recommendation because(ItemGroup items, String reason)
		{
			return new Recommendation(items, null, -1, reason);
		}

		static Recommendation onlyWith(ItemGroup items, ItemGroup onlyWith, int exemptVarbit, String reason)
		{
			return new Recommendation(items, onlyWith, exemptVarbit, reason);
		}

		ItemGroup getItems()
		{
			return items;
		}

		/** The group that must be held for the request, or null when it always applies. */
		ItemGroup getOnlyWith()
		{
			return onlyWith;
		}

		/** A varbit that drops the request when set, or -1. */
		int getExemptVarbit()
		{
			return exemptVarbit;
		}

		/** Said after the request, or null. */
		String getReason()
		{
			return reason;
		}
	}

	/** A Slayer reward that uses the item for you at low health. The item is still needed in the inventory. */
	static final class Unlock
	{
		private final String name;
		private final int varbit;
		private final ItemGroup uses;

		private Unlock(String name, int varbit, ItemGroup uses)
		{
			this.name = name;
			this.varbit = varbit;
			this.uses = uses;
		}

		String getName()
		{
			return name;
		}

		int getVarbit()
		{
			return varbit;
		}

		ItemGroup getUses()
		{
			return uses;
		}
	}

	/**
	 * One thing a task or a place needs. By default it applies wherever the task is done. A need of a task tied to some
	 * places, by their keys, applies when the task goes to one of them. While where the task goes is not known, it either
	 * carries the place in its text, so the line stays true, or is left out.
	 */
	static final class Need
	{
		enum Kind
		{
			/** Any one of the items. */
			HAVE,
			/** Used up once per kill, so the count is compared with the kills left. A kept item may replace it. */
			PER_KILL,
			/** A set number of the items, such as an entry fee, counted as a used-up item is with that number for the kills left. */
			AMOUNT,
			/** Charges read from the item names plus refills, one charge per kill. */
			CHARGES,
			/** Not mandatory. A note replaces it when it is missing. */
			ADVICE
		}

		private final Kind kind;
		private final ItemGroup items;
		private final int amount;
		private final ItemGroup alternative;
		private final boolean alternativeFirst;
		private final ItemGroup refills;
		private final int refillCharges;
		private final String one;
		private final String many;
		private final String advice;
		private final String reason;
		private final String where;
		private final String[] places;
		private final boolean placeOnly;
		private final int exemptVarbit;
		private final String exemptNote;
		private final String exemptWhere;

		private Need(Kind kind, ItemGroup items, int amount, ItemGroup alternative, boolean alternativeFirst, ItemGroup refills,
			int refillCharges, String one, String many, String advice, String reason, String where, String[] places,
			boolean placeOnly, int exemptVarbit, String exemptNote, String exemptWhere)
		{
			this.kind = kind;
			this.items = items;
			this.amount = amount;
			this.alternative = alternative;
			this.alternativeFirst = alternativeFirst;
			this.refills = refills;
			this.refillCharges = refillCharges;
			this.one = one;
			this.many = many;
			this.advice = advice;
			this.reason = reason;
			this.where = where;
			this.places = places;
			this.placeOnly = placeOnly;
			this.exemptVarbit = exemptVarbit;
			this.exemptNote = exemptNote;
			this.exemptWhere = exemptWhere;
		}

		static Need have(ItemGroup items)
		{
			return new Need(Kind.HAVE, items, 0, null, false, null, 0, null, null, null, null, null, null, false, -1, null, null);
		}

		static Need perKill(ItemGroup items, String one, String many)
		{
			return new Need(Kind.PER_KILL, items, 0, null, false, null, 0, one, many, null, null, null, null, false, -1, null, null);
		}

		/** A set number of the items, such as 875 coins. A kept item may replace them, as with {@link #or}. */
		static Need amount(ItemGroup items, int amount, String one, String many)
		{
			return new Need(Kind.AMOUNT, items, amount, null, false, null, 0, one, many, null, null, null, null, false, -1, null,
				null);
		}

		static Need charges(ItemGroup items, ItemGroup refills, int refillCharges, String one, String many)
		{
			return new Need(Kind.CHARGES, items, 0, null, false, refills, refillCharges, one, many, null, null, null, null, false,
				-1, null, null);
		}

		static Need advice(ItemGroup items, String advice)
		{
			return new Need(Kind.ADVICE, items, 0, null, false, null, 0, null, null, advice, null, null, null, false, -1, null, null);
		}

		/** A kept item that does the job of the used-up one, such as a rock hammer instead of thrownhammers. */
		Need or(ItemGroup alternative, boolean nameItFirst)
		{
			return new Need(kind, items, amount, alternative, nameItFirst, refills, refillCharges, one, many, advice, reason, where,
				places, placeOnly, exemptVarbit, exemptNote, exemptWhere);
		}

		/**
		 * Needed only at these places, by their keys in /slayer_areas.json. It applies when the task goes to one of them,
		 * and while where it goes is not known it applies with the place in its text. Without any place it applies only
		 * while Konar assigned no area.
		 */
		Need onlyAt(String where, String... places)
		{
			return new Need(kind, items, amount, alternative, alternativeFirst, refills, refillCharges, one, many, advice, reason,
				where, places, false, exemptVarbit, exemptNote, exemptWhere);
		}

		/** Needed only at these places, by their keys. While where the task goes is not known it is left out. */
		Need onlyIn(String... places)
		{
			return new Need(kind, items, amount, alternative, alternativeFirst, refills, refillCharges, one, many, advice, reason,
				where, places, true, exemptVarbit, exemptNote, exemptWhere);
		}

		/** Not needed once the varbit is set, such as boots after the elite Kourend & Kebos diary. */
		Need unless(int varbit, String note)
		{
			return unless(varbit, note, null);
		}

		/** Not needed once the varbit is set, and then not mentioned, such as a rope once it is tied at an entrance. */
		Need unless(int varbit)
		{
			return unless(varbit, null, null);
		}

		/** The varbit removes the need at one place, and {@code stillWhere} names where it is still needed. */
		Need unless(int varbit, String note, String stillWhere)
		{
			return new Need(kind, items, amount, alternative, alternativeFirst, refills, refillCharges, one, many, advice, reason,
				where, places, placeOnly, varbit, note, stillWhere);
		}

		/** Said after the line while the items are not carried, such as why a blessed axe does not do. */
		Need because(String reason)
		{
			return new Need(kind, items, amount, alternative, alternativeFirst, refills, refillCharges, one, many, advice, reason,
				where, places, placeOnly, exemptVarbit, exemptNote, exemptWhere);
		}

		/**
		 * Whether it applies where the task goes. {@code place} is the place's key, and {@code known} says whether anything
		 * is known of where the task goes, which an area Konar assigned and these tables do not name also is.
		 */
		boolean appliesAt(int assignedAreaId, String place, boolean known)
		{
			if (places == null) return true;
			if (places.length == 0) return assignedAreaId <= 0;
			if (!known) return !placeOnly;
			for (String key : places)
			{
				if (key.equals(place)) return true;
			}
			return false;
		}

		/** Whether it is tied to places, so its text needs no place once where the task goes is known. */
		boolean isPlaceBound()
		{
			return places != null && places.length > 0;
		}

		Kind getKind()
		{
			return kind;
		}

		ItemGroup getItems()
		{
			return items;
		}

		/** How many of the items an {@link Kind#AMOUNT} need asks for. */
		int getAmount()
		{
			return amount;
		}

		/** Said after the line while the items are not carried, or null. */
		String getReason()
		{
			return reason;
		}

		ItemGroup getAlternative()
		{
			return alternative;
		}

		boolean isAlternativeFirst()
		{
			return alternativeFirst;
		}

		ItemGroup getRefills()
		{
			return refills;
		}

		int getRefillCharges()
		{
			return refillCharges;
		}

		/** Singular or plural name of the used-up item or charge. */
		String noun(int count)
		{
			return count == 1 ? one : many;
		}

		String plural()
		{
			return many;
		}

		String getAdvice()
		{
			return advice;
		}

		/** The place in the text, such as "for desert lizards", or null when the need applies everywhere. */
		String getWhere()
		{
			return where;
		}

		int getExemptVarbit()
		{
			return exemptVarbit;
		}

		String getExemptNote()
		{
			return exemptNote;
		}

		String getExemptWhere()
		{
			return exemptWhere;
		}
	}

	static final class Requirement
	{
		private final List<Need> needs;
		private final Unlock unlock;

		private Requirement(List<Need> needs, Unlock unlock)
		{
			this.needs = needs;
			this.unlock = unlock;
		}

		List<Need> getNeeds()
		{
			return needs;
		}

		/** The reward that finishes the monster for you, or null. */
		Unlock getUnlock()
		{
			return unlock;
		}
	}
}
