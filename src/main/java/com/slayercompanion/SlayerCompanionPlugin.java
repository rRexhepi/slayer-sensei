package com.slayercompanion;

import com.google.inject.Provides;
import com.slayercompanion.data.SlayerTask;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Slayer task guide and points planner. It observes and advises, and never performs game input. It reads the task from the
 * game itself ({@link SlayerTaskReader}), so it needs no other plugin.
 */
@Slf4j
@PluginDescriptor(
		name = "Slayer Sensei",
		description = "Shows where to do your Slayer task and what gear it needs, and plans masters, blocks and reward purchases for your goal",
		tags = {"slayer", "sensei", "task", "block", "skip", "dossier", "companion", "eta", "gear"}
)
public class SlayerCompanionPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private SlayerCompanionConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SlayerCompanionOverlay overlay;
	@Inject private SlayerDataset dataset;
	@Inject private ItemManager itemManager;
	@Inject private WorldMapPointManager worldMapPointManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private SlayerCompanionPanel sidebarPanel;
	@Inject private ClientThread clientThread;
	@Inject private SlayerLocations locations;
	@Inject private ShortestPathBridge shortestPath;
	@Inject private PluginManager pluginManager;

	@Getter private String taskName;
	@Getter private String taskAmount;
	@Getter private String taskInitialAmount;
	@Getter private String taskStreak;
	@Getter private String slayerPoints;
	@Getter private SlayerTask currentTask;
	@Getter private String gearCheck;
	/** The gear check with the compact sidebar's items, replaced as one value so the sidebar reads them together. */
	@Getter private volatile SlayerGearCheck.Result gearResult;
	@Getter private String progressRate;
	/** {@link #progressRate} as the compact sidebar's item. */
	@Getter private String progressRateItem;
	@Getter private String unlockAdvice;
	/** {@link #unlockAdvice} as the compact sidebar's items. */
	@Getter private List<String> unlockAdviceItems = Collections.emptyList();
	@Getter private volatile Destination destination;
	/** The places offered for the current task, or null when the master fixes the place or there is no task. */
	@Getter private volatile TaskPlaces places;
	/** Which streak the current assignment counts toward, so the sidebar can name it. */
	@Getter private volatile SlayerAssigner.Streak streakKind = SlayerAssigner.Streak.MAIN;
	/** The master who gave the task in progress, or null when unknown or logged out. */
	@Getter private volatile SlayerAssigner taskMaster;
	@Getter private volatile ShortestPathBridge.Status shortestPathStatus = ShortestPathBridge.Status.MISSING;
	/** The last press of "Turn on Shortest Path" left it off. Cleared once it runs. */
	@Getter private volatile boolean pathTurnOnFailed;
	@Getter private SlayerPlanBuilder.View pointsPlan;

	/**
	 * Defensive upper bound for one tick of task-counter progress. This is deliberately progress,
	 * not physical kills: an expeditious bracelet can remove two task units for one kill, while a
	 * bracelet of slaughter can remove none. Larger changes are treated as assignment mutations.
	 */
	static final int MAX_PLAUSIBLE_PROGRESS_PER_TICK = 30;
	/**
	 * How long the scene panel stays after the kill counter last went down: 300 ticks, three minutes. That is longer
	 * than the time between two kills on nearly every task, slow kills and a wait for respawns included, and longer
	 * than most boss kills, so the panel does not come and go while the player fights. A bank trip takes longer, so it
	 * goes then. "Always show" keeps it all the time.
	 */
	static final int WORKING_TICKS = 300;

	private int taskIntroUntilTick;
	/** The tick the kill counter last went down on this task, or -1. */
	private int lastProgressTick = -1;
	private int taskProgress;
	private int lastRemainingAmount = -1;
	private int lastInitialAmount = -1;
	private Instant trackingStart;
	private NavigationButton navigationButton;
	private boolean navigationAdded;
	private TaskMapPoint mapPoint;
	private String lastSidebarSignature;
	/** Live SLAYER_TARGET, SLAYER_AREA and SLAYER_MASTER values the current destination was resolved from. */
	private int liveTaskId = -1;
	private int assignedAreaId = -1;
	private int liveMasterId = -1;
	private boolean destinationDirty = true;
	private final SlayerAreaTable areaTable = new SlayerAreaTable();
	private final TaskRouteTracker routeTracker = new TaskRouteTracker();
	private String activeProfileKey;
	private boolean gearCheckDirty = true;
	/** Inventory and worn items for the gear check, read again only after a container change. */
	private List<SlayerGearCheck.Held> gearItems;
	/** The client's table reads for the task, made on first use. */
	private SlayerGameData.Db tables;
	/** Reads the task each tick and keeps the last name it looked up. */
	private final SlayerTaskReader taskReader = new SlayerTaskReader();
	private SlayerGameData gameData;
	private boolean gameDataLoaded;
	private int planSignature;
	/** Quest completions behind the master requirements, cached because reading them runs a client script. */
	private SlayerMasterAccess.Quests plannerQuests;
	private int plannerQuestsTick;
	/** Quest states and real levels behind the current task's places, read for the task {@link #placeAccountTask} names. */
	private PlaceAccess.Account placeAccount = PlaceAccess.Account.UNKNOWN;
	/** The task id the place checks were read for, doubled, plus one for Krystilia's list. -1 reads them again. */
	private int placeAccountTask = -1;
	private int placeAccountTick;
	/** The Buy tab items seen on the character of {@link #shopItemsProfile}. */
	private SlayerShopItem.Sightings shopItems = SlayerShopItem.Sightings.NONE;
	private String shopItemsProfile;
	/** RS profile config group for what has been seen of the Buy tab's items, kept apart so writing it changes no setting. */
	static final String SHOP_ITEMS_GROUP = "slayercompanionshop";
	static final String SHOP_ITEMS_KEY = "seen";
	/** RS profile config group for the gear check's items last seen in the bank, kept apart so writing it changes no setting. */
	static final String BANK_GEAR_GROUP = "slayercompanionbank";
	static final String BANK_GEAR_KEY = "gear";
	/**
	 * What each character's bank held of the gear check's items, kept in its RS profile config. Lambdas rather than method
	 * references, since the config manager is injected after this is made.
	 */
	private final BankGear.Memory bankMemory = new BankGear.Memory(() -> configManager.getRSProfileKey(),
			() -> configManager.getRSProfileConfiguration(BANK_GEAR_GROUP, BANK_GEAR_KEY),
			stored -> configManager.setRSProfileConfiguration(BANK_GEAR_GROUP, BANK_GEAR_KEY, stored));
	/**
	 * Ticks between quest reads, about a minute, for the plan and for the place checks. A new plan after a login or hop,
	 * and a new task, read them at once.
	 */
	static final int QUEST_REFRESH_TICKS = 100;

	@Provides
	SlayerCompanionConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(SlayerCompanionConfig.class);
	}

	@Override
	protected void startUp()
	{
		// RuneScapeProfileChanged is not guaranteed to fire when this plugin is enabled after login.
		// Seed the owner now so the first later profile event cannot inherit this character's rate.
		final GameState state = client.getGameState();
		activeProfileKey = initialProfileKey(state,
				state == GameState.LOGGED_IN ? configManager.getRSProfileKey() : null);
		// Enabling the plugin mid-task must not draw a route on its own, so this starts a new baseline.
		routeTracker.sessionStarted(activeProfileKey);
		shortestPathStatus = shortestPath.status();
		overlayManager.add(overlay);
		navigationButton = NavigationButton.builder()
				.tooltip("Slayer Sensei")
				.icon(createIcon(24))
				.priority(6)
				.panel(sidebarPanel)
				.build();
		updateNavigation();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		removeMapPoint();
		if (navigationButton != null && navigationAdded)
		{
			final NavigationButton button = navigationButton;
			SwingUtilities.invokeLater(() -> clientToolbar.removeNavigation(button));
		}
		navigationAdded = false;
		navigationButton = null;
		activeProfileKey = null;
		if (routeTracker.ownsRoute())
		{
			// Only the route this plugin drew. Posting is harmless when Shortest Path is gone.
			routeTracker.routeCleared();
			clientThread.invokeLater(shortestPath::postClear);
		}
		routeTracker.sessionStarted();
		taskReader.clear();
		resetTaskState();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"slayercompanion".equals(event.getGroup())) return;
		final int placeTask = TaskPlaces.taskIdOfConfigKey(event.getKey());
		if (placeTask > 0)
		{
			// A remembered place only moves that task's destination. Config changes may arrive on any
			// thread, and invoke runs at once when this is already the client thread.
			clientThread.invoke(() -> placeChanged(placeTask));
			return;
		}
		if (SlayerCompanionConfig.SIDEBAR_DETAIL_KEY.equals(event.getKey()))
		{
			// Only the sidebar's layout changes, so it is rebuilt now, even while logged out, and the plan is kept.
			SwingUtilities.invokeLater(sidebarPanel::refresh);
			return;
		}
		pointsPlan = null;
		updateNavigation();
		// The config panel posts on the Swing thread. The marker belongs to the client thread.
		clientThread.invoke(this::updateMapPoint);
		lastSidebarSignature = null;
	}

	/**
	 * Task-progress rate, ETA and the cached task dossier belong to one character. Logging out, hopping or
	 * switching profiles must drop them, otherwise the next account inherits the previous one's
	 * tracking - and does so invisibly when both happen to be on a task with the same name.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (endsSession(state))
		{
			activeProfileKey = null;
			// The next login re-learns the assignment as a baseline. A route this plugin drew stays,
			// so a hop on the way to the task keeps it.
			routeTracker.sessionStarted();
			taskReader.clear();
			resetTaskState();
			removeMapPoint();
			refreshSidebar();
			return;
		}
		if (state == GameState.LOGGED_IN)
		{
			gearCheckDirty = true;
			// A failed cache read gets one retry per login instead of falling back for the whole session.
			if (gameData == null) gameDataLoaded = false;
			switchActiveProfile(configManager.getRSProfileKey());
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// This event also covers in-client profile switches which do not necessarily cross a login
		// screen. Its new-profile value is authoritative and may legitimately be null.
		switchActiveProfile(event.getNewProfile());
	}

	/** Transitions after which the previous character's tracking must not be reused. */
	static boolean endsSession(GameState state)
	{
		return state == GameState.LOGIN_SCREEN || state == GameState.HOPPING;
	}

	/**
	 * Whether a login belongs to a different character than the one currently being tracked.
	 * Without this, the progress rate and ETA carried across an account switch whenever both
	 * accounts happened to be on a task with the same name.
	 */
	static boolean shouldResetForProfile(String activeProfileKey, String newProfileKey)
	{
		return !Objects.equals(activeProfileKey, newProfileKey);
	}

	static String initialProfileKey(GameState state, String profileKey)
	{
		return state == GameState.LOGGED_IN ? profileKey : null;
	}

	private void switchActiveProfile(String newProfileKey)
	{
		if (!shouldResetForProfile(activeProfileKey, newProfileKey))
		{
			return;
		}
		activeProfileKey = newProfileKey;
		routeTracker.sessionStarted(newProfileKey);
		taskReader.clear();
		resetTaskState();
		removeMapPoint();
		refreshSidebar();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// The gear check reads every inventory and equipment item composition. Reading them once per
		// container change instead of once per game tick removes ~40 lookups from every tick.
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			gearCheckDirty = true;
		}
		// The bank is read on the next tick it is open, once however often it changed
		else if (event.getContainerId() == InventoryID.BANK)
		{
			bankMemory.bankChanged();
		}
	}

	/**
	 * Reads the task straight from the game's values and brings everything that follows from it up to date.
	 * {@link #taskTick} decides what a tick does. A tick during a loading screen, or one whose read fails, changes
	 * nothing, so everything after that check runs logged in.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		final TaskTick tick = taskTick(client.getGameState(), this::readTask);
		if (tick.isSkip())
		{
			return;
		}
		final SlayerTaskReader.Task task = tick.getTask();
		if (task == null && taskName != null)
		{
			// No kills left, so the task was finished, cancelled or blocked
			resetTaskState();
			removeMapPoint();
		}
		readLiveStats(task);
		rememberBank();
		updatePointsPlan();

		if (task == null)
		{
			observeRoute();
			// Between tasks the route to the next master can change hands, which the Clear path button shows
			refreshSidebar();
			return;
		}

		final String name = task.getName();
		if (!name.equals(taskName))
		{
			taskName = name;
			currentTask = dataset.lookupOrFallback(name);
			resetProgressTracking();
			taskIntroUntilTick = client.getTickCount() + Math.max(0, (int) Math.ceil(config.taskIntroSeconds() / 0.6));
			destinationDirty = true;
			gearCheckDirty = true;
			lastSidebarSignature = null;
		}
		readAssignedArea();
		if (destinationDirty)
		{
			updateDestination();
		}
		refreshPlaceAccount();

		observeTaskProgress(
				client.getVarpValue(VarPlayerID.SLAYER_COUNT),
				client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL));
		updateGearCheck();
		updateProgressRate();
		updateTaskAdvice();
		observeRoute();
		refreshSidebar();
	}

	/**
	 * Konar attaches an area to every task. The game keeps it in the SLAYER_AREA varp as a stable area
	 * id (the core Slayer plugin turns the same id into its taskLocation string through the SlayerArea
	 * table). Every other master leaves it at 0. A changed task id, area or master re-resolves the
	 * destination, because the master decides whether the player picks the place. Client thread, logged in.
	 */
	private void readAssignedArea()
	{
		final int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		final int areaId = Math.max(0, client.getVarpValue(VarPlayerID.SLAYER_AREA));
		final int masterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		if (taskId != liveTaskId || areaId != assignedAreaId || masterId != liveMasterId)
		{
			liveTaskId = taskId;
			assignedAreaId = areaId;
			liveMasterId = masterId;
			destinationDirty = true;
		}
	}

	/**
	 * Resolves where the current task goes, with the player's remembered place when the master leaves it
	 * open. Krystilia's tasks only count in the Wilderness, so they resolve only among her Wilderness places.
	 */
	private void updateDestination()
	{
		destinationDirty = false;
		final int areaId = Math.max(0, assignedAreaId);
		final int taskId = Math.max(0, liveTaskId);
		// The game's own name is only needed for an area the data file does not cover yet.
		final String gameAreaName = areaId > 0 && locations.byAreaId(areaId) == null
				? areaTable.areaName(client, areaId) : null;
		final boolean pickable = TaskPlaces.pickable(areaId, liveMasterId);
		final boolean wilderness = TaskPlaces.wildernessOnly(liveMasterId);
		// A new task, and the first one after a login, reads what its places need. A new pick does not.
		if (pickable && placeAccountTask != taskId * 2 + (wilderness ? 1 : 0))
		{
			readPlaceAccount(taskId, wilderness);
		}
		final List<TaskPlaces.Place> offered = !pickable ? Collections.emptyList()
				: wilderness ? TaskPlaces.offeredInWilderness(taskId, locations, placeAccount)
				: TaskPlaces.offered(taskId, currentTask, locations, placeAccount);
		final String stored = offered.size() > 1
				? configManager.getConfiguration("slayercompanion", TaskPlaces.configKey(taskId, wilderness)) : null;
		final String chosen = TaskPlaces.chosen(offered, stored);
		destination = Destination.resolve(taskId, currentTask, areaId, gameAreaName, chosen, locations, wilderness);
		places = pickable ? new TaskPlaces(taskId, offered, chosen, wilderness) : null;
		updateMapPoint();
		lastSidebarSignature = null;
	}

	/**
	 * Reads what the task's places need: the states of the quests they name and the real levels. Each quest state runs a
	 * client script, so only those quests are read, and only for a new task, after a login and then at most every
	 * {@link #QUEST_REFRESH_TICKS} ticks. Client thread, logged in.
	 */
	private void readPlaceAccount(int taskId, boolean wilderness)
	{
		placeAccount = PlaceAccess.Account.read(TaskPlaces.quests(taskId, wilderness, locations), quest -> quest.getState(client),
				client.getRealSkillLevels());
		placeAccountTask = taskId * 2 + (wilderness ? 1 : 0);
		placeAccountTick = client.getTickCount();
	}

	/**
	 * Reads the place checks again once {@link #QUEST_REFRESH_TICKS} ticks have passed, so a quest finished or a level
	 * gained shows within about a minute. When they changed, the places, the marker and a route this plugin drew follow
	 * as they do for a new pick. Client thread, logged in.
	 */
	private void refreshPlaceAccount()
	{
		final TaskPlaces now = places;
		final int tick = client.getTickCount();
		if (now == null || placeAccountTask < 0 || (tick >= placeAccountTick && tick - placeAccountTick < QUEST_REFRESH_TICKS))
		{
			return;
		}
		final PlaceAccess.Account before = placeAccount;
		readPlaceAccount(now.getTaskId(), now.isWilderness());
		if (!placeAccount.equals(before))
		{
			placeChanged(now.getTaskId());
		}
	}

	/** A place picked in the sidebar, on the Swing thread. It is stored on the client thread and the config change applies it. */
	void choosePlace(int taskId, String key)
	{
		clientThread.invokeLater(() ->
		{
			final TaskPlaces now = places;
			// A pick from a sidebar built for an earlier task, or one that changes nothing, is dropped.
			if (now == null || now.getTaskId() != taskId || !now.offers(key) || key.equals(now.getChosenKey()))
			{
				return;
			}
			configManager.setConfiguration("slayercompanion", TaskPlaces.configKey(taskId, now.isWilderness()), key);
		});
	}

	/**
	 * The remembered place of a task changed. When it is the current task, the destination, the marker and
	 * a route this plugin drew all move to the new place. Client thread.
	 */
	private void placeChanged(int taskId)
	{
		final TaskPlaces now = places;
		if (now == null || now.getTaskId() != taskId)
		{
			return;
		}
		final String before = destination == null ? null : destination.getKey();
		updateDestination();
		final Destination after = destination;
		if (after == null || Objects.equals(before, after.getKey()))
		{
			return;
		}
		final boolean canRoute = shortestPathStatus == ShortestPathBridge.Status.ACTIVE && !after.routeTargets().isEmpty();
		switch (routeTracker.placeChanged(canRoute))
		{
			case PATH:
				shortestPath.postPath(after.routeTargets());
				break;
			case CLEAR:
				shortestPath.postClear();
				break;
			default:
				break;
		}
		refreshSidebar();
	}

	/** Feeds the route tracker once per logged-in tick and carries out its decision. Client thread. */
	private void observeRoute()
	{
		final boolean autoPath = config.pathMode() == PathMode.NEW_TASK
				&& shortestPathStatus == ShortestPathBridge.Status.ACTIVE
				&& destination != null && !destination.routeTargets().isEmpty();
		switch (routeTracker.observe(currentAssignment(), autoPath))
		{
			case PATH:
				shortestPath.postPath(destination.routeTargets());
				lastSidebarSignature = null;
				break;
			case CLEAR:
				shortestPath.postClear();
				lastSidebarSignature = null;
				break;
			default:
				break;
		}
	}

	/** The live assignment, or null without a task. Client thread. */
	private TaskRouteTracker.Assignment currentAssignment()
	{
		if (taskName == null || client.getVarpValue(VarPlayerID.SLAYER_COUNT) <= 0)
		{
			return null;
		}
		return new TaskRouteTracker.Assignment(activeProfileKey, taskName,
				client.getVarpValue(VarPlayerID.SLAYER_TARGET),
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID),
				client.getVarpValue(VarPlayerID.SLAYER_AREA),
				client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL));
	}

	/** Sidebar "Path to task". Clicks arrive on the Swing thread, so the work moves to the client thread. */
	void requestRouteFromSidebar()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN || destination == null
					|| destination.routeTargets().isEmpty() || shortestPath.status() != ShortestPathBridge.Status.ACTIVE)
			{
				return;
			}
			shortestPath.postPath(destination.routeTargets());
			routeTracker.routeRequested(currentAssignment());
			lastSidebarSignature = null;
			refreshSidebar();
		});
	}

	/**
	 * The plan's "Path to" a master: the master the goal says to visit next ({@link MasterRoute}). Clicks arrive on the
	 * Swing thread, so the work moves to the client thread, where the plan of this tick decides the master.
	 */
	void requestMasterRouteFromSidebar()
	{
		clientThread.invokeLater(() ->
		{
			final MasterRoute.Target target = MasterRoute.target(pointsPlan);
			if (client.getGameState() != GameState.LOGGED_IN || target == null || !target.isRoutable()
					|| shortestPath.status() != ShortestPathBridge.Status.ACTIVE)
			{
				return;
			}
			shortestPath.postPath(target.getPoints());
			routeTracker.masterRouteRequested(activeProfileKey, currentAssignment());
			lastSidebarSignature = null;
			refreshSidebar();
		});
	}

	/** Sidebar "Clear path". Clears only a route this plugin drew. */
	void clearRouteFromSidebar()
	{
		clientThread.invokeLater(() ->
		{
			if (!routeTracker.ownsRoute())
			{
				return;
			}
			shortestPath.postClear();
			routeTracker.routeCleared();
			lastSidebarSignature = null;
			refreshSidebar();
		});
	}

	public boolean ownsRoute()
	{
		return routeTracker.ownsRoute();
	}

	/**
	 * A "path" or "clear" request from any other plugin means the route on screen is no longer ours.
	 * Other plugins may post from any thread (Shortest Path's own "transports" report comes from its
	 * pathfinder thread), so only the ownership check runs here and the change moves to the client thread.
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (ShortestPathBridge.isRouteRequest(event) && !shortestPath.isOwnMessage(event))
		{
			clientThread.invokeLater(this::routeTakenOver);
		}
	}

	/** The player set or cleared the target through Shortest Path's own menu entries. */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (ShortestPathBridge.changesTarget(event.getMenuOption(), event.getMenuTarget()))
		{
			routeTakenOver();
		}
	}

	private void routeTakenOver()
	{
		if (routeTracker.ownsRoute())
		{
			routeTracker.routeTakenOver();
			lastSidebarSignature = null;
			refreshSidebar();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (ShortestPathBridge.isShortestPath(event.getPlugin()))
		{
			// Plugins start and stop on the Swing thread. When Shortest Path stops, its route is gone.
			clientThread.invokeLater(() ->
			{
				if (!event.isLoaded()) routeTracker.routeTakenOver();
				refreshShortestPathStatus();
			});
		}
	}

	/** The sidebar's "Turn on Shortest Path". Swing thread, where RuneLite's plugin list starts plugins too. */
	void turnOnShortestPath()
	{
		pathTurnOnFailed = !PluginSwitch.turnOn(PluginSwitch.of(pluginManager), shortestPath.find());
		clientThread.invokeLater(() ->
		{
			lastSidebarSignature = null;
			refreshShortestPathStatus();
			refreshSidebar();
		});
	}

	@Subscribe
	public void onExternalPluginsChanged(ExternalPluginsChanged event)
	{
		clientThread.invokeLater(this::refreshShortestPathStatus);
	}

	private void refreshShortestPathStatus()
	{
		final ShortestPathBridge.Status status = shortestPath.status();
		if (status != shortestPathStatus)
		{
			shortestPathStatus = status;
			if (status == ShortestPathBridge.Status.ACTIVE) pathTurnOnFailed = false;
			lastSidebarSignature = null;
			refreshSidebar();
		}
	}

	/** Keeps the edge arrow aimed at the destination while the world map is panned. */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (mapPoint == null || !mapPoint.isCurrentlyEdgeSnapped())
		{
			return;
		}
		final WorldMap worldMap = client.getWorldMap();
		final Point center = worldMap == null ? null : worldMap.getWorldMapPosition();
		if (center != null)
		{
			mapPoint.aimFrom(center.getX(), center.getY());
		}
	}

	/**
	 * Measures task progress from decrements of the game's own remaining counter. Counting ActorDeath
	 * events would credit every nearby death of an on-task NPC type, other players' kills in a crowded
	 * dungeon included, which inflates the rate and shrinks the ETA. This counter only moves for the
	 * local player's task, but bracelet procs mean its units are not physical kills.
	 */
	private void observeTaskProgress(int remainingNow, int initialNow)
	{
		if (assignmentChanged(lastInitialAmount, initialNow))
		{
			// A skip/block/reassignment can keep the same task name. The original amount identifies
			// that new assignment and prevents its lower counter from entering the old rate.
			resetProgressTracking();
		}
		lastInitialAmount = initialNow;
		final int progress = progressRecorded(lastRemainingAmount, remainingNow);
		lastRemainingAmount = remainingNow;
		if (progress <= 0) return;
		taskProgress += progress;
		lastProgressTick = client.getTickCount();
		if (trackingStart == null) trackingStart = Instant.now();
		lastSidebarSignature = null;
	}

	/**
	 * Task-progress units represented by one observed drop of the remaining counter. Zero is a
	 * terminal state shared by completion, cancellation and blocking, so it is intentionally never
	 * sampled; the task state is about to be reset and treating it as combat would make a small
	 * cancel look like progress. A same-name reassignment is separately detected from its original
	 * amount. The remaining observational limitation is bracelet behaviour: this reports counter
	 * depletion (and therefore useful ETA pace), not a literal count of NPC deaths.
	 */
	static int progressRecorded(int previousRemaining, int currentRemaining)
	{
		if (previousRemaining <= 0 || currentRemaining <= 0 || currentRemaining >= previousRemaining)
		{
			return 0;
		}
		final int delta = previousRemaining - currentRemaining;
		return delta > MAX_PLAUSIBLE_PROGRESS_PER_TICK ? 0 : delta;
	}

	static boolean assignmentChanged(int previousInitial, int currentInitial)
	{
		return previousInitial >= 0 && currentInitial >= 0 && previousInitial != currentInitial;
	}

	public boolean shouldShowOverlay()
	{
		return taskName != null && overlayShows(config.showAwayFromTaskTargets(), client.getTickCount(), taskIntroUntilTick,
				lastProgressTick);
	}

	/**
	 * Whether the scene panel shows for a task: always when the player asked for that, else for a while after a new task
	 * and while the player works on it, which is within {@link #WORKING_TICKS} of the kill counter last going down.
	 *
	 * @param lastProgressTick the tick the counter last went down, or -1 when it has not on this task
	 */
	static boolean overlayShows(boolean always, int tick, int introUntilTick, int lastProgressTick)
	{
		if (always || tick <= introUntilTick) return true;
		return lastProgressTick >= 0 && tick - lastProgressTick <= WORKING_TICKS;
	}

	public String dataLabel(SlayerTask task)
	{
		return dataset.dataLabel(task);
	}

	/** What one game tick does with the task. */
	@Value
	static class TaskTick
	{
		static final TaskTick SKIP = new TaskTick(true, null);

		/** Keep the task, its rate, the new-task window, the plan, the marker and the sidebar as they are. */
		boolean skip;
		/** The task read, null when there is none. */
		SlayerTaskReader.Task task;
	}

	/**
	 * What a game tick does with the task. A tick also arrives during a loading screen, on every teleport, cave
	 * entrance and region boundary. Such a tick reads nothing and changes nothing. A read that fails changes nothing
	 * either, so what is shown stays and the next tick reads again. Logged in, the read decides, and no kills left is
	 * no task at once. Logging out, hopping and switching profiles clear the task through {@link #onGameStateChanged}
	 * and {@link #switchActiveProfile} instead.
	 */
	static TaskTick taskTick(GameState state, Supplier<SlayerTaskReader.Task> read)
	{
		if (state != GameState.LOGGED_IN)
		{
			return TaskTick.SKIP;
		}
		try
		{
			return new TaskTick(false, read.get());
		}
		catch (RuntimeException ex)
		{
			log.debug("Slayer Sensei could not read the task from the game", ex);
			return TaskTick.SKIP;
		}
	}

	/** The task from the game's own values. A table read that fails throws. Client thread, logged in. */
	private SlayerTaskReader.Task readTask()
	{
		if (tables == null) tables = SlayerGameData.db(client);
		return taskReader.read(client::getVarpValue, client::getVarbitValue, tables);
	}

	/** The kills, streak and points from the game. Client thread, logged in. */
	private void readLiveStats(SlayerTaskReader.Task task)
	{
		taskAmount = task == null ? null : Integer.toString(task.getAmount());
		taskInitialAmount = task == null || task.getInitialAmount() <= 0 ? null : Integer.toString(task.getInitialAmount());
		streakKind = streakFor(client.getVarbitValue(VarbitID.SLAYER_MASTER));
		taskMaster = SlayerAssigner.forId(client.getVarbitValue(VarbitID.SLAYER_MASTER));
		taskStreak = Integer.toString(streakValue(streakKind));
		slayerPoints = Integer.toString(client.getVarbitValue(VarbitID.SLAYER_POINTS));
	}

	/** The streak the current assignment counts toward. Krystilia and Mortimer keep their own. */
	static SlayerAssigner.Streak streakFor(int masterId)
	{
		final SlayerAssigner master = SlayerAssigner.forId(masterId);
		return master == null ? SlayerAssigner.Streak.MAIN : master.getStreak();
	}

	private int streakValue(SlayerAssigner.Streak streak)
	{
		switch (streak)
		{
			case WILDERNESS:
				return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
			case MORTIMER:
				return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
			default:
				return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		}
	}

	/**
	 * The spend plan does not depend on having a task, so it runs before the no-task early return. The task advice
	 * and the dossier's Assigned by line come from the same build, so it is built when any of them is shown. Client
	 * thread, logged in.
	 */
	private void updatePointsPlan()
	{
		if (!(config.showPointsPlanner() || config.showUnlockAdvisor() || config.showSidebar()))
		{
			return;
		}
		if (!gameDataLoaded)
		{
			gameData = SlayerGameData.load(client);
			gameDataLoaded = true;
		}
		final SlayerShopItem.Sightings items = sightShopItems();
		// The plan follows the goal's master unless the player pinned the configured one
		final SlayerPlanBuilder.Settings settings = new SlayerPlanBuilder.Settings(config.plannerMaster(),
				config.pointsReserve(), config.goal(), config.boostInterval(), !config.pinPlannerMaster());
		final SlayerMasterAccess.State access = SlayerMasterAccess.read(client, plannerQuests());
		final int signature = SlayerPlanBuilder.signature(client, settings, access, items);
		if (pointsPlan != null && signature == planSignature) return;
		planSignature = signature;
		pointsPlan = SlayerPlanBuilder.build(client, gameData, dataset, settings, access, items);
		lastSidebarSignature = null;
		refreshSidebar();
	}

	/**
	 * Which Buy tab items this character owns. The bank can only be read while it is open, so each item counts as
	 * owned once it has been seen in the inventory, worn or in the bank, and that is kept per character. The
	 * containers are polled here because RuneLite only accepts a subscriber named onItemContainerChanged, and the
	 * plugin has that one already. contains() reads the item ids without copying the container. The bank counts
	 * only while its interface is open, so a copy left from another character is never read. Client thread.
	 */
	private SlayerShopItem.Sightings sightShopItems()
	{
		final String profile = configManager.getRSProfileKey();
		if (profile == null) return SlayerShopItem.Sightings.NONE;
		if (!profile.equals(shopItemsProfile))
		{
			shopItemsProfile = profile;
			shopItems = SlayerShopItem.Sightings.decode(configManager.getRSProfileConfiguration(SHOP_ITEMS_GROUP, SHOP_ITEMS_KEY));
		}
		final ItemContainer bank = openBank();
		final List<ItemContainer> containers = new ArrayList<>();
		for (ItemContainer container : new ItemContainer[]{client.getItemContainer(InventoryID.INV),
				client.getItemContainer(InventoryID.WORN), bank})
		{
			if (container != null) containers.add(container);
		}
		final java.util.Set<SlayerShopItem> found = java.util.EnumSet.noneOf(SlayerShopItem.class);
		for (SlayerShopItem item : SlayerShopItem.values())
		{
			if (shopItems.owns(item)) continue;
			for (ItemContainer container : containers)
			{
				if (item.seenIn(container::contains)) found.add(item);
			}
		}
		if (!found.isEmpty() || (bank != null && !shopItems.isBankSeen()))
		{
			shopItems = shopItems.with(found, bank != null);
			configManager.setRSProfileConfiguration(SHOP_ITEMS_GROUP, SHOP_ITEMS_KEY, shopItems.encode());
		}
		return shopItems;
	}

	/** The bank while its interface is open, else null, so a copy left from another character is never read. Client thread. */
	private ItemContainer openBank()
	{
		final net.runelite.api.widgets.Widget bankItems = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS);
		return bankItems != null && !bankItems.isHidden() ? client.getItemContainer(InventoryID.BANK) : null;
	}

	/**
	 * Remembers which gear-check items the bank holds, on the first tick it is open after it changed. Until a change
	 * waits nothing looks at the bank, and a tick reads it at most once. Client thread, logged in.
	 */
	private void rememberBank()
	{
		if (!bankMemory.waiting()) return;
		final ItemContainer bank = openBank();
		// The gear check and the GEAR heading's tooltip follow what is remembered
		if (bank != null && bankMemory.read(bank.getItems())) lastSidebarSignature = null;
	}

	/** The "Advisor" line for the task in progress and its compact items, from the plan built this tick. */
	private void updateTaskAdvice()
	{
		final SlayerPlanBuilder.View plan = pointsPlan;
		unlockAdvice = plan == null ? null : plan.getTaskAdvice();
		unlockAdviceItems = plan == null ? Collections.<String>emptyList() : plan.getTaskAdviceItems();
	}

	/** The sidebar's "Assigned by" line: from the cache when the plan has it, else the dossier's own text. */
	String assignedBy(SlayerTask task)
	{
		final SlayerPlanBuilder.View plan = pointsPlan;
		final String fromCache = plan == null ? null : plan.getAssignedBy();
		return fromCache != null ? fromCache : task == null ? null : task.getAssignedBy();
	}

	/**
	 * Quest completions for the master requirements. They are read again whenever the plan starts over,
	 * which resetTaskState makes happen after every login, hop and profile switch (a config change does
	 * too), and every {@link #QUEST_REFRESH_TICKS} ticks. Client thread.
	 */
	private SlayerMasterAccess.Quests plannerQuests()
	{
		final int tick = client.getTickCount();
		if (plannerQuests == null || pointsPlan == null || tick < plannerQuestsTick
				|| tick - plannerQuestsTick >= QUEST_REFRESH_TICKS)
		{
			plannerQuests = SlayerMasterAccess.Quests.read(client);
			plannerQuestsTick = tick;
		}
		return plannerQuests;
	}

	/** The sidebar's goal dropdown, on the Swing thread. The config change rebuilds the plan. */
	void setGoal(SlayerGoal goal)
	{
		if (goal == null || goal == config.goal()) return;
		configManager.setConfiguration("slayercompanion", SlayerCompanionConfig.GOAL_KEY, goal);
	}

	/**
	 * Gathers the inputs for {@link SlayerGearCheck}, with the bank as it was last seen and the key of the place the task
	 * goes to, so that place's needs count whoever assigned the task. The items are read again only after a container
	 * change, but the line is rebuilt every tick, since the kills left, the destination and the varbits change on their own.
	 */
	private void updateGearCheck()
	{
		final int areaId = Math.max(0, assignedAreaId);
		final Destination where = destination;
		final String placeKey = where == null ? null : where.getKey();
		// Off, the recommended tier is left out everywhere: the sidebar, the detailed line and the panel
		final boolean recommend = config.showRecommendedGear();
		if (!SlayerGearCheck.hasNeeds(taskName, areaId, placeKey, client::getVarbitValue, recommend))
		{
			gearCheck = null;
			gearResult = null;
			return;
		}
		if (gearCheckDirty || gearItems == null)
		{
			gearItems = heldItems();
			gearCheckDirty = false;
		}
		final SlayerGearCheck.Result result = SlayerGearCheck.evaluate(taskName, parseInt(taskAmount), areaId, placeKey, gearItems,
				bankMemory.get(), client::getVarbitValue, recommend);
		gearCheck = result == null ? null : result.getLine();
		gearResult = result;
	}

	/** Worn items first, so the line names what is in use. */
	private List<SlayerGearCheck.Held> heldItems()
	{
		final List<SlayerGearCheck.Held> held = new ArrayList<>();
		addHeldItems(held, client.getItemContainer(InventoryID.WORN));
		addHeldItems(held, client.getItemContainer(InventoryID.INV));
		return held;
	}

	private void addHeldItems(List<SlayerGearCheck.Held> held, ItemContainer container)
	{
		if (container == null) return;
		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				held.add(new SlayerGearCheck.Held(item.getId(),
						itemManager.getItemComposition(item.getId()).getName(), item.getQuantity()));
			}
		}
	}

	private void updateProgressRate()
	{
		final boolean tracking = taskProgress > 0 && trackingStart != null;
		final long elapsed = tracking ? Math.max(1, Duration.between(trackingStart, Instant.now()).getSeconds()) : 0;
		final int progress = tracking ? taskProgress : 0;
		final int remaining = parseInt(taskAmount);
		progressRate = rateText(progress, elapsed, remaining, false);
		progressRateItem = rateText(progress, elapsed, remaining, true);
	}

	/**
	 * The Task rate line, or with {@code compact} its item for the compact sidebar, e.g. "310 an hour. About 12m left."
	 * and "310 an hour, 12m left".
	 *
	 * @param progress task-progress units counted so far, 0 before the count first goes down
	 * @param elapsedSeconds since the first unit was counted
	 * @param remaining kills left
	 */
	static String rateText(int progress, long elapsedSeconds, int remaining, boolean compact)
	{
		if (progress <= 0)
		{
			return compact ? "After the first kill" : "Shows once the task count first goes down.";
		}
		if (elapsedSeconds < 60 || progress < 3)
		{
			return compact ? "Learning, " + progress + " done" : "Learning the rate. " + progress + " done so far.";
		}
		final double progressPerHour = progress * 3600.0 / elapsedSeconds;
		final long etaSeconds = progressPerHour > 0 ? Math.round(remaining * 3600.0 / progressPerHour) : 0;
		return Math.round(progressPerHour) + (compact ? " an hour, " + formatEta(etaSeconds) + " left"
				: " an hour. About " + formatEta(etaSeconds) + " left.");
	}

	/**
	 * One marker at the destination's entrance: Konar's assigned area when there is one, otherwise the place
	 * the player picked or the recommended one. Off screen it snaps to the map edge as an arrow, and clicking
	 * it focuses the map.
	 */
	private void updateMapPoint()
	{
		removeMapPoint();
		if (!config.showMapMarker() || destination == null || !destination.hasEntrance() || taskName == null) return;
		mapPoint = new TaskMapPoint(destination.getEntrance(), mapTooltip(taskName, destination));
		worldMapPointManager.add(mapPoint);
	}

	static String mapTooltip(String taskName, Destination destination)
	{
		final StringBuilder tooltip = new StringBuilder("Slayer task: ").append(taskName).append("<br>");
		if (destination.getOrigin() == Destination.Origin.ASSIGNED)
		{
			tooltip.append("Konar assigned: ").append(destination.getLabel());
			if (destination.getEntranceLabel() != null)
			{
				tooltip.append("<br>Marker: ").append(destination.getEntranceLabel());
			}
		}
		else
		{
			tooltip.append(destination.markerLabel());
		}
		return tooltip.toString();
	}

	private void removeMapPoint()
	{
		if (mapPoint != null)
		{
			worldMapPointManager.remove(mapPoint);
			mapPoint = null;
		}
	}

	private void updateNavigation()
	{
		if (navigationButton == null) return;
		final boolean shouldAdd = config.showSidebar();
		if (shouldAdd == navigationAdded) return;
		navigationAdded = shouldAdd;
		final NavigationButton button = navigationButton;
		SwingUtilities.invokeLater(() ->
		{
			if (shouldAdd) clientToolbar.addNavigation(button);
			else clientToolbar.removeNavigation(button);
		});
	}

	private void refreshSidebar()
	{
		if (!config.showSidebar()) return;
		final String signature = String.join("|", value(taskName), value(taskAmount), value(taskStreak),
				streakKind.name(), String.valueOf(taskMaster), value(slayerPoints), value(gearCheck), value(progressRate), value(unlockAdvice),
				currentTask == null ? "" : value(currentTask.getTask()), routeSignature(), Boolean.toString(pathTurnOnFailed));
		if (signature.equals(lastSidebarSignature)) return;
		lastSidebarSignature = signature;
		SwingUtilities.invokeLater(sidebarPanel::refresh);
	}

	private void resetTaskState()
	{
		taskName = null;
		taskAmount = null;
		taskInitialAmount = null;
		taskStreak = null;
		slayerPoints = null;
		streakKind = SlayerAssigner.Streak.MAIN;
		taskMaster = null;
		currentTask = null;
		gearCheck = null;
		gearResult = null;
		progressRate = null;
		progressRateItem = null;
		unlockAdvice = null;
		unlockAdviceItems = Collections.emptyList();
		// The area belongs to the assignment, so it resets with it and is read again next tick.
		destination = null;
		places = null;
		liveTaskId = -1;
		assignedAreaId = -1;
		liveMasterId = -1;
		destinationDirty = true;
		placeAccount = PlaceAccess.Account.UNKNOWN;
		placeAccountTask = -1;
		resetProgressTracking();
		taskIntroUntilTick = 0;
		lastSidebarSignature = null;
		pointsPlan = null;
	}

	/** Everything the sidebar shows about the destination, the place picker and the route buttons. */
	private String routeSignature()
	{
		final Destination d = destination;
		final String where = d == null ? "" : String.join(",", d.getOrigin().name(), value(d.getKey()),
				Integer.toString(d.getAreaId()), d.getLabel(), String.valueOf(d.getEntrance()),
				Integer.toString(d.routeTargets().size()));
		final TaskPlaces p = places;
		final String picker = p == null ? "" : p.getTaskId() + ":" + value(p.getChosenKey()) + ":" + p.getPlaces().size()
				+ ":" + p.isWilderness();
		return String.join(",", where, picker, config.pathMode().name(), shortestPathStatus.name(),
				Boolean.toString(routeTracker.ownsRoute()));
	}

	private void resetProgressTracking()
	{
		taskProgress = 0;
		lastRemainingAmount = -1;
		lastInitialAmount = -1;
		lastProgressTick = -1;
		trackingStart = null;
	}

	private static BufferedImage createIcon(int size)
	{
		final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(45, 45, 45, 240));
		g.fillOval(0, 0, size - 1, size - 1);
		// RuneLite's own accent, as the sidebar's headings, the panel's title and the map pin use
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.drawOval(1, 1, size - 3, size - 3);
		g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, size - 7)));
		g.drawString("S", Math.max(3, size / 4), size - Math.max(3, size / 5));
		g.dispose();
		return image;
	}

	private static String formatEta(long seconds)
	{
		final long hours = seconds / 3600;
		final long minutes = (seconds % 3600) / 60;
		return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
	}

	private static int parseInt(String value)
	{
		try { return value == null ? 0 : Integer.parseInt(value); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private static String value(String value)
	{
		return value == null ? "" : value;
	}
}
