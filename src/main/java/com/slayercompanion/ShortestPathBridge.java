package com.slayercompanion;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

/**
 * Talks to the Shortest Path Plugin Hub plugin through RuneLite's PluginMessage events only, so there is
 * no build or runtime dependency on it. When it is missing or disabled nothing subscribes to the
 * messages and posting them does nothing.
 * <p>
 * Contract, read from the bytecode of Shortest Path 1.20.7 and again of 1.20.8 (ShortestPathPlugin#onPluginMessage):
 * namespace "shortestpath", name "path" with data key "target" holding a WorldPoint, a packed Integer
 * or a Set of either (it routes to the closest), and name "clear". Without a "start" key the handler
 * reads the local player's location, so messages must be posted on the client thread.
 */
@Singleton
class ShortestPathBridge
{
	static final String NAMESPACE = "shortestpath";
	static final String PATH = "path";
	static final String CLEAR = "clear";
	static final String TARGET = "target";
	static final String PLUGIN_CLASS = "shortestpath.ShortestPathPlugin";
	static final String PLUGIN_NAME = "Shortest Path";

	enum Status
	{
		/** Not installed. */
		MISSING,
		/** Installed but turned off. */
		DISABLED,
		ACTIVE
	}

	private final PluginManager pluginManager;
	private final EventBus eventBus;
	private PluginMessage lastPosted;

	@Inject
	ShortestPathBridge(PluginManager pluginManager, EventBus eventBus)
	{
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
	}

	Status status()
	{
		final Plugin plugin = find();
		if (plugin == null) return Status.MISSING;
		return pluginManager.isPluginActive(plugin) ? Status.ACTIVE : Status.DISABLED;
	}

	/** The installed Shortest Path plugin, or null. */
	Plugin find()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (isShortestPath(plugin)) return plugin;
		}
		return null;
	}

	/** Client thread only. */
	void postPath(Collection<WorldPoint> targets)
	{
		post(new PluginMessage(NAMESPACE, PATH, pathData(targets)));
	}

	/** Client thread only. */
	void postClear()
	{
		post(new PluginMessage(NAMESPACE, CLEAR));
	}

	private void post(PluginMessage message)
	{
		lastPosted = message;
		eventBus.post(message);
	}

	/** True for the message this bridge is posting right now. Event bus delivery is synchronous. */
	boolean isOwnMessage(PluginMessage message)
	{
		return message == lastPosted;
	}

	static boolean isShortestPath(Plugin plugin)
	{
		if (plugin == null) return false;
		if (PLUGIN_CLASS.equals(plugin.getClass().getName())) return true;
		// Plugin.getName() is RuneLite's own way to a plugin's display name, so no reflection is needed here
		return PLUGIN_NAME.equals(plugin.getName());
	}

	/** A single target goes as a WorldPoint, several as a Set so Shortest Path picks the closest. */
	static Map<String, Object> pathData(Collection<WorldPoint> targets)
	{
		if (targets == null || targets.isEmpty())
		{
			throw new IllegalArgumentException("a route needs at least one target");
		}
		if (targets.size() == 1)
		{
			return Collections.singletonMap(TARGET, targets.iterator().next());
		}
		final Set<WorldPoint> set = new LinkedHashSet<>(targets);
		return Collections.singletonMap(TARGET, set.size() == 1 ? set.iterator().next() : set);
	}

	/** A "path" or "clear" request to Shortest Path, from any plugin. */
	static boolean isRouteRequest(PluginMessage message)
	{
		return message != null && NAMESPACE.equals(message.getNamespace())
				&& (PATH.equals(message.getName()) || CLEAR.equals(message.getName()));
	}

	/**
	 * Shortest Path's own menu entries that replace or clear the target: "Set Target" (also "Target 2"
	 * and so on for extra targets), "Clear Path" and "Find closest". "Set Start" keeps the target.
	 */
	static boolean changesTarget(String option, String target)
	{
		if (option == null) return false;
		final String what = target == null ? "" : Text.removeTags(target).trim();
		switch (option)
		{
			case "Set": return what.startsWith("Target");
			case "Clear": return what.equals("Path");
			case "Find closest": return true;
			default: return false;
		}
	}
}
