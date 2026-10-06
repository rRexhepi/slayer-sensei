package com.slayercompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.ui.overlay.OverlayPosition;

@ConfigGroup("slayercompanion")
public interface SlayerCompanionConfig extends Config
{
	@ConfigItem(keyName = "showPanel", name = "Show panel", description = "Show a small panel over the game with the task, kills left, gear warnings, streak and points. It shows for a while after a new task and for three minutes after each kill on the task.", position = 0)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "position", name = "Panel position", description = "Where the panel sits on the game screen", position = 1)
	default OverlayPosition panelPosition()
	{
		return OverlayPosition.TOP_RIGHT;
	}

	@ConfigItem(keyName = "showAwayFromTaskTargets", name = "Always show panel", description = "Keep the panel on screen the whole time you have a task, not only after a new task and while you kill", position = 2)
	default boolean showAwayFromTaskTargets()
	{
		return false;
	}

	@Range(min = 0, max = 300)
	@ConfigItem(keyName = "taskIntroSeconds", name = "New-task display (seconds)", description = "Show the panel for this long after you get a new task, so the gear warnings are there before you set off", position = 3)
	default int taskIntroSeconds()
	{
		return 120;
	}

	@ConfigItem(keyName = "showPanelGear", name = "Gear lines on panel", description = "Add a red line to the panel when you lack an item the task needs, and a yellow one when it is in your bank or when you lack an item the wiki recommends for it", position = 4)
	default boolean showPanelGear()
	{
		return true;
	}

	@ConfigItem(keyName = "showRecommendedGear", name = "Recommended gear", description = "Also check for items the wiki recommends for the task, such as a demonbane weapon against demons. Off, the sidebar and the panel only ask for items the task needs.", position = 5)
	default boolean showRecommendedGear()
	{
		return true;
	}

	@ConfigItem(keyName = "showUnlockAdvisor", name = "Unlock advisor", description = "Show a line in the sidebar on the task in progress: do it, skip it, block it or buy its extension. The advice follows your Slayer goal.", position = 6)
	default boolean showUnlockAdvisor()
	{
		return true;
	}

	@ConfigItem(keyName = "showMapMarker", name = "Task map marker", description = "Pin the task location on the world map. Konar's assigned area comes first, otherwise the place you picked in the sidebar or the recommended one. Off screen it becomes an arrow at the map edge. Click it to focus the map there.", position = 7)
	default boolean showMapMarker()
	{
		return true;
	}

	@ConfigItem(keyName = "showSidebar", name = "Full dossier sidebar", description = "Add a Slayer Sensei sidebar with the complete, wrapping task guide", position = 8)
	default boolean showSidebar()
	{
		return true;
	}

	String SIDEBAR_DETAIL_KEY = "sidebarDetail";

	@ConfigItem(keyName = SIDEBAR_DETAIL_KEY, name = "Sidebar detail", description = "Compact shows short lists. Hover an item to see why. Detailed shows every line as a sentence.", position = 9)
	default SidebarDetail sidebarDetail()
	{
		return SidebarDetail.COMPACT;
	}

	@ConfigItem(keyName = "showPointsPlanner", name = "Points planner", description = "Show a slayer points spend plan in the sidebar, based on what you already bought, your block list and your levels", position = 10)
	default boolean showPointsPlanner()
	{
		return true;
	}

	@ConfigItem(keyName = "plannerMaster", name = "Planner master", description = "Master whose task pool and block list the points plan uses when Pin planner master is on, or when your goal points to no master yet", position = 11)
	default SlayerMaster plannerMaster()
	{
		return SlayerMaster.KONAR;
	}

	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "pointsReserve", name = "Points to keep for skips", description = "The plan already keeps enough to skip the bad tasks its master ranking expects you to skip. Set this to keep at least this many.", position = 12)
	default int pointsReserve()
	{
		return 0;
	}

	@ConfigItem(keyName = "pathMode", name = "Route to task", description = "Draw the route to the task with the Shortest Path plugin. New task and button draws it once each time you get a new task. Every mode except Off adds route buttons to the sidebar: Path to task, Clear path, and a path to the master your goal says to visit next.", position = 13)
	default PathMode pathMode()
	{
		return PathMode.NEW_TASK;
	}

	String GOAL_KEY = "goal";

	@ConfigItem(keyName = GOAL_KEY, name = "Slayer goal", description = "What the points plan works toward. Slayer points adds a next task routine. You can also switch it at the top of the plan in the sidebar.", position = 20)
	default SlayerGoal goal()
	{
		return SlayerGoal.POINTS;
	}

	@ConfigItem(keyName = "boostInterval", name = "Points boost", description = "For the Slayer points goal. Which milestone tasks go to the master who pays most. Every 10th task earns more points. Every 50th task means fewer hard tasks.", position = 21)
	default BoostInterval boostInterval()
	{
		return BoostInterval.EVERY_10TH;
	}

	@ConfigItem(keyName = "pinPlannerMaster", name = "Pin planner master", description = "Off: the plan uses the master your goal points to, the best master for XP or profit, or the master who pays most for milestones for Slayer points. On: it always uses the Planner master.", position = 22)
	default boolean pinPlannerMaster()
	{
		return false;
	}
}
