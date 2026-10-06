package com.slayercompanion.data;

import java.util.List;
import lombok.Data;

/**
 * Every place one task can be done, in display order, from /slayer_areas.json. Masters other than
 * Konar leave the choice to the player, so this list is what the sidebar's place picker offers.
 */
@Data
public class TaskLocations
{
	/** Value of the SLAYER_TARGET varp (SlayerTask COL_ID). */
	private int taskId;
	/** Readable task name, for auditing only. */
	private String task;
	/** Location keys in display order, the recommended one first. */
	private List<String> locations;
	/**
	 * Key of the recommended location. When absent, the dossier's place leads if the list holds it, else
	 * the first listed place (see Destination.recommendedKey).
	 */
	private String recommended;
	private String source;
}
