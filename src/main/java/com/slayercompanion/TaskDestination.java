package com.slayercompanion;

import com.slayercompanion.data.SlayerTask;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Maps the dossier's free-text best location to a place, for tasks without an assigned area. Points
 * are RuneLite's own world-map dungeon icons (net.runelite.client.plugins.worldmap.DungeonLocation),
 * re-checked against RuneLite 1.13.1. TzHaar City uses the Karamja volcano icon, the surface way in to
 * Mor Ul Rek. {@code key} names the same place in /slayer_areas.json, where a test keeps the two points
 * identical.
 */
@Getter
public final class TaskDestination
{
	private final String key;
	private final String label;
	private final WorldPoint point;

	private TaskDestination(String key, String label, int x, int y)
	{
		this.key = key;
		this.label = label;
		this.point = new WorldPoint(x, y, 0);
	}

	public static TaskDestination forTask(SlayerTask task)
	{
		if (task == null || task.getBestLocation() == null)
		{
			return null;
		}
		final String location = task.getBestLocation().toLowerCase();
		if (location.startsWith("catacombs of kourend")) return at("catacombs_of_kourend", "Catacombs of Kourend", 1636, 3673);
		if (location.startsWith("god wars dungeon")) return at("god_wars_dungeon", "God Wars Dungeon", 2917, 3747);
		if (location.startsWith("jormungand")) return at("jormungands_prison", "Jormungand's Prison", 2464, 4012);
		if (location.startsWith("taverley dungeon")) return at("taverley_dungeon", "Taverley Dungeon", 2883, 3397);
		if (location.startsWith("mos le")) return at("mos_leharmless_cave", "Mos Le'Harmless Cave", 3747, 2973);
		if (location.startsWith("kraken cove")) return at("kraken_cove", "Kraken Cove", 2277, 3611);
		if (location.startsWith("lighthouse")) return at("lighthouse", "Lighthouse basement", 2508, 3644);
		if (location.startsWith("iorwerth")) return at("iorwerth_dungeon", "Iorwerth Dungeon", 3224, 6044);
		if (location.startsWith("karuulm")) return at("karuulm_slayer_dungeon", "Karuulm Slayer Dungeon", 1308, 3807);
		if (location.startsWith("fremennik slayer")) return at("fremennik_slayer_dungeon", "Fremennik Slayer Dungeon", 2796, 3615);
		if (location.startsWith("kalphite cave")) return at("kalphite_cave", "Kalphite Cave", 3319, 3122);
		if (location.startsWith("ancient cavern")) return at("ancient_cavern", "Ancient Cavern", 2511, 3508);
		if (location.startsWith("smoke devil")) return at("smoke_devil_dungeon", "Smoke Devil Dungeon", 2411, 3061);
		if (location.startsWith("brimhaven dungeon")) return at("brimhaven_dungeon", "Brimhaven Dungeon", 2743, 3154);
		if (location.startsWith("asgarnian ice")) return at("asgarnian_ice_dungeon", "Asgarnian Ice Dungeon", 3007, 3150);
		if (location.startsWith("slayer tower")) return at("slayer_tower", "Slayer Tower", 3416, 3535);
		if (location.startsWith("stronghold slayer")) return at("stronghold_slayer_dungeon", "Stronghold Slayer Cave", 2427, 3424);
		if (location.startsWith("chasm of fire")) return at("chasm_of_fire", "Chasm of Fire", 1432, 3670);
		if (location.startsWith("mor ul rek")) return at("tzhaar_city", "TzHaar City", 2855, 3168);
		if (location.startsWith("wyvern cave")) return at("wyvern_cave", "Wyvern Cave", 3745, 3779);
		return null;
	}

	private static TaskDestination at(String key, String label, int x, int y)
	{
		return new TaskDestination(key, label, x, y);
	}
}
