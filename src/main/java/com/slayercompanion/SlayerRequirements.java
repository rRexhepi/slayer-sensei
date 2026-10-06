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
	static final int WATERFALL_AREA = 8;
	static final int DARKMEYER_AREA = 44;
	static final int SLEPE_AREA = 45;
	static final int VAMPYRIUM_AREA = 55;

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

	static final Unlock GARGOYLE_SMASHER = new Unlock("Gargoyle Smasher", VarbitID.SLAYER_AUTOKILL_GARGOYLES, ANY_ROCK_HAMMER);
	static final Unlock SLUG_SALTER = new Unlock("Slug Salter", VarbitID.SLAYER_AUTOKILL_ROCKSLUGS, BAG_OF_SALT);
	static final Unlock REPTILE_FREEZER = new Unlock("Reptile Freezer", VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS, ICE_COOLER);
	static final Unlock SHROOM_SPRAYER = new Unlock("'Shroom Sprayer", VarbitID.SLAYER_AUTOKILL_ZYGOMITES, FUNGICIDE_SPRAY);

	private static final Map<String, Requirement> REQUIREMENTS = new LinkedHashMap<>();
	/** Needs that come with a place Konar assigned, whatever the task. */
	private static final Map<Integer, List<Need>> AREA_NEEDS = new HashMap<>();
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
		put("Lizards", REPTILE_FREEZER, Need.perKill(ICE_COOLER, "ice cooler", "ice coolers").onlyAt("for desert lizards"));
		put("Mutated zygomites", SHROOM_SPRAYER, Need.charges(FUNGICIDE_SPRAY, FUNGICIDE, FUNGICIDE_REFILL_CHARGES,
			"fungicide spray charge", "fungicide spray charges"));
		// Mogres only surface for an explosive at Mudskipper Point. Since Sailing a boat's cannons can also kill them.
		put("Mogres", null, Need.perKill(FISHING_EXPLOSIVE, "fishing explosive", "fishing explosives")
			.onlyAt("for Mudskipper Point"));

		put("Drakes", null, karuulmBoots);
		put("Hydras", null, karuulmBoots);
		put("The Alchemical Hydra", null, karuulmBoots);
		// Wyrms also live in Wyrmscraig, Neypotzli and the Charred Dungeon, where the floor is safe.
		put("Wyrms", null, karuulmBoots.onlyAt("for the Karuulm Slayer Dungeon", KARUULM_AREA));
		AREA_NEEDS.put(KARUULM_AREA, Collections.singletonList(karuulmBoots));
		// The rope takes you over the rock and down the dead tree to the entrance. It is not used up.
		AREA_NEEDS.put(WATERFALL_AREA, Collections.singletonList(Need.have(ROPE).unless(
			VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, "The medium Kandarin diary lets you into the Waterfall Dungeon without a rope")));

		put("Venators", null, Need.have(VAMPYREBANE));
		// Feral vampyres elsewhere take damage from any weapon, so only Konar's places need anything.
		put("Vampyres", null, Need.have(VAMPYREBANE).onlyIfAssigned(SLEPE_AREA, VAMPYRIUM_AREA),
			Need.have(SILVER_OR_EFARITAY).onlyIfAssigned(DARKMEYER_AREA));

		put("The Giant Mole", null, Need.have(SPADE), Need.have(LIGHT_SOURCE)
			.unless(VarbitID.MY2ARM_FIRE_MOLE, "Your Fire of Eternal Light lights the Mole Hole"));
		put("The Shellbane Gryphon", null, Need.have(TORTUGAN_SHIELD));

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
		final List<Need> needs = AREA_NEEDS.get(areaId);
		return needs == null ? Collections.emptyList() : needs;
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
		if (requirement == null) return false;
		for (Need need : requirement.getNeeds())
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
		final boolean undead = UNDEAD.contains(normalize(taskName));
		if (helmetStandsIn(taskName)) return undead ? HELMET_OR_SALVE : SLAYER_HELMET;
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
	 * varbit is set, and its reason follows the request in the line.
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
	 * One thing a task needs. By default it applies wherever the task is done. A need tied to a place applies
	 * when Konar assigned that place, and otherwise carries the place in its text so the line stays true.
	 */
	static final class Need
	{
		enum Kind
		{
			/** Any one of the items. */
			HAVE,
			/** Used up once per kill, so the count is compared with the kills left. A kept item may replace it. */
			PER_KILL,
			/** Charges read from the item names plus refills, one charge per kill. */
			CHARGES,
			/** Not mandatory. A note replaces it when it is missing. */
			ADVICE
		}

		private final Kind kind;
		private final ItemGroup items;
		private final ItemGroup alternative;
		private final boolean alternativeFirst;
		private final ItemGroup refills;
		private final int refillCharges;
		private final String one;
		private final String many;
		private final String advice;
		private final String where;
		private final int[] areas;
		private final boolean assignedOnly;
		private final int exemptVarbit;
		private final String exemptNote;
		private final String exemptWhere;

		private Need(Kind kind, ItemGroup items, ItemGroup alternative, boolean alternativeFirst, ItemGroup refills,
			int refillCharges, String one, String many, String advice, String where, int[] areas, boolean assignedOnly,
			int exemptVarbit, String exemptNote, String exemptWhere)
		{
			this.kind = kind;
			this.items = items;
			this.alternative = alternative;
			this.alternativeFirst = alternativeFirst;
			this.refills = refills;
			this.refillCharges = refillCharges;
			this.one = one;
			this.many = many;
			this.advice = advice;
			this.where = where;
			this.areas = areas;
			this.assignedOnly = assignedOnly;
			this.exemptVarbit = exemptVarbit;
			this.exemptNote = exemptNote;
			this.exemptWhere = exemptWhere;
		}

		static Need have(ItemGroup items)
		{
			return new Need(Kind.HAVE, items, null, false, null, 0, null, null, null, null, null, false, -1, null, null);
		}

		static Need perKill(ItemGroup items, String one, String many)
		{
			return new Need(Kind.PER_KILL, items, null, false, null, 0, one, many, null, null, null, false, -1, null, null);
		}

		static Need charges(ItemGroup items, ItemGroup refills, int refillCharges, String one, String many)
		{
			return new Need(Kind.CHARGES, items, null, false, refills, refillCharges, one, many, null, null, null, false,
				-1, null, null);
		}

		static Need advice(ItemGroup items, String advice)
		{
			return new Need(Kind.ADVICE, items, null, false, null, 0, null, null, advice, null, null, false, -1, null, null);
		}

		/** A kept item that does the job of the used-up one, such as a rock hammer instead of thrownhammers. */
		Need or(ItemGroup alternative, boolean nameItFirst)
		{
			return new Need(kind, items, alternative, nameItFirst, refills, refillCharges, one, many, advice, where, areas,
				assignedOnly, exemptVarbit, exemptNote, exemptWhere);
		}

		/** Needed only at one place. It applies when Konar assigned one of the areas, or names the place otherwise. */
		Need onlyAt(String where, int... areas)
		{
			return new Need(kind, items, alternative, alternativeFirst, refills, refillCharges, one, many, advice, where,
				areas, false, exemptVarbit, exemptNote, exemptWhere);
		}

		/** Needed only when Konar assigned one of these areas. Without an assigned area it is left out. */
		Need onlyIfAssigned(int... areas)
		{
			return new Need(kind, items, alternative, alternativeFirst, refills, refillCharges, one, many, advice, where,
				areas, true, exemptVarbit, exemptNote, exemptWhere);
		}

		/** Not needed once the varbit is set, such as boots after the elite Kourend & Kebos diary. */
		Need unless(int varbit, String note)
		{
			return unless(varbit, note, null);
		}

		/** The varbit removes the need at one place, and {@code stillWhere} names where it is still needed. */
		Need unless(int varbit, String note, String stillWhere)
		{
			return new Need(kind, items, alternative, alternativeFirst, refills, refillCharges, one, many, advice, where,
				areas, assignedOnly, varbit, note, stillWhere);
		}

		boolean appliesIn(int assignedAreaId)
		{
			if (assignedAreaId <= 0) return !assignedOnly;
			if (areas == null) return true;
			for (int area : areas)
			{
				if (area == assignedAreaId) return true;
			}
			return false;
		}

		Kind getKind()
		{
			return kind;
		}

		ItemGroup getItems()
		{
			return items;
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
