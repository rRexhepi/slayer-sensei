package com.slayercompanion;

import lombok.Value;

/**
 * What the sidebar says about Shortest Path, the optional plugin that draws the routes, chosen from its state and the
 * path mode. Pure, so which notice shows, its words and its button are tested without RuneLite.
 */
final class PluginNotices
{
	/** One notice in both layouts. */
	@Value
	static class Notice
	{
		/** The compact item. */
		String item;
		/** The notice in full: the compact item's tooltip and the detailed sidebar's line. */
		String text;
		/** The button's label, or null without a button. */
		String button;
		/** The last press of the button did not turn the plugin on, so the item reads as an error. */
		boolean failed;
	}

	static final String PATH_BUTTON = "Turn on Shortest Path";
	static final String FAILED_ITEM = "Could not turn it on";
	private static final String PATH_OPTIONAL = "Shortest Path is optional. It draws the route to your task and to your next master.";
	private static final String TRY_THE_LIST = " Turn it on in RuneLite's plugin list instead.";

	private PluginNotices()
	{
	}

	/**
	 * The line where the route buttons go when Shortest Path cannot draw a route, or null when it can, or when the
	 * routes are off, as nothing is said about Shortest Path then.
	 *
	 * @param failed whether the last press of the button left the plugin off
	 */
	static Notice shortestPath(ShortestPathBridge.Status status, PathMode mode, boolean failed)
	{
		if (!mode.showsButtons() || status == ShortestPathBridge.Status.ACTIVE)
		{
			return null;
		}
		if (status == ShortestPathBridge.Status.MISSING)
		{
			return new Notice("Optional: Shortest Path, Plugin Hub", PATH_OPTIONAL + " You can install it from the Plugin Hub.", null, false);
		}
		if (failed)
		{
			return new Notice(FAILED_ITEM, PATH_OPTIONAL + " The button could not turn it on." + TRY_THE_LIST, PATH_BUTTON, true);
		}
		return new Notice("Optional, draws the route", PATH_OPTIONAL + " It is installed but off.", PATH_BUTTON, false);
	}
}
