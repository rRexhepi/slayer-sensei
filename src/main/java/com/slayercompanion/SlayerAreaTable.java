package com.slayercompanion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/**
 * Reads area names from the game's SlayerArea table the same way the core Slayer plugin derives its
 * task location: the SLAYER_AREA varp value matches COL_AREA_ID, and COL_AREA_NAME_IN_HELPER is the
 * display name. Used only for areas the data file does not cover yet. Client thread only.
 */
@Slf4j
final class SlayerAreaTable
{
	private final Map<Integer, String> names = new HashMap<>();

	String areaName(Client client, int areaId)
	{
		if (areaId <= 0) return null;
		if (names.containsKey(areaId)) return names.get(areaId);
		String name = null;
		try
		{
			final List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
			if (rows != null && !rows.isEmpty())
			{
				final Object[] values = client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
				if (values != null && values.length > 0 && values[0] instanceof String)
				{
					name = (String) values[0];
				}
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Slayer Sensei could not read slayer area {} from the cache", areaId, ex);
		}
		names.put(areaId, name);
		return name;
	}
}
