import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * Checks which candidate tiles Shortest Path could route to at all. It floods Shortest Path's own
 * collision map from every teleport destination and Lumbridge, following its transport tables, and
 * ignores every requirement (quests, items, levels). A tile it cannot reach is in a place Shortest Path
 * has no way into, so a route to it would end somewhere unrelated. Read-only use of the plugin jar.
 *
 * Input: lines of "x<TAB>y<TAB>plane". Output: the same lines with a fourth column, 1 when reachable.
 */
public class ReachabilityCheck
{
	private static final String[] TABLES = {
		"agility_shortcuts", "boats", "canoes", "charter_ships", "fairy_rings", "gnome_gliders", "hot_air_balloons",
		"magic_carpets", "magic_mushtrees", "minecarts", "quetzal_whistle", "quetzals", "seasonal_transports", "ships",
		"spirit_trees", "teleportation_boxes", "teleportation_items", "teleportation_levers", "teleportation_minigames",
		"teleportation_portals", "teleportation_portals_poh", "teleportation_spells", "teleportation_spells_home",
		"transports", "wilderness_obelisks",
	};

	private static int pack(int x, int y, int z)
	{
		return (z * 16384 + y) * 4096 + x;
	}

	private static int[] point(String text)
	{
		final String[] p = text.trim().split("\\s+");
		if (p.length != 3) return null;
		try
		{
			return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	public static void main(String[] args) throws Exception
	{
		final CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		final Map<Integer, List<Integer>> edges = new HashMap<>();
		final List<Integer> seeds = new ArrayList<>();
		seeds.add(pack(3222, 3218, 0));
		for (String table : TABLES)
		{
			try (InputStream in = ReachabilityCheck.class.getResourceAsStream("/transports/" + table + ".tsv"))
			{
				if (in == null) continue;
				final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				int origin = -1;
				int destination = -1;
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.startsWith("#"))
					{
						final String[] header = line.substring(1).split("\t");
						for (int i = 0; i < header.length; i++)
						{
							if (header[i].trim().equals("Origin")) origin = i;
							if (header[i].trim().equals("Destination")) destination = i;
						}
						continue;
					}
					final String[] cols = line.split("\t", -1);
					final int[] to = destination >= 0 && destination < cols.length ? point(cols[destination]) : null;
					if (to == null) continue;
					final int[] from = origin >= 0 && origin < cols.length ? point(cols[origin]) : null;
					if (from == null) seeds.add(pack(to[0], to[1], to[2]));
					else edges.computeIfAbsent(pack(from[0], from[1], from[2]), k -> new ArrayList<>()).add(pack(to[0], to[1], to[2]));
				}
			}
		}

		final BitSet seen = new BitSet();
		final ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int seed : seeds)
		{
			if (!seen.get(seed))
			{
				seen.set(seed);
				queue.add(seed);
			}
		}
		while (!queue.isEmpty())
		{
			final int packed = queue.poll();
			final int x = packed % 4096;
			final int y = (packed / 4096) % 16384;
			final int z = packed / 4096 / 16384;
			if (map.isBlocked(x, y, z))
			{
				// Same rule as CollisionMap.getTileNeighbors: from a blocked tile, such as a transport
				// destination on an object, any unblocked neighbour can be entered.
				for (int dx = -1; dx <= 1; dx++)
				{
					for (int dy = -1; dy <= 1; dy++)
					{
						if ((dx != 0 || dy != 0) && !map.isBlocked(x + dx, y + dy, z)) visit(seen, queue, pack(x + dx, y + dy, z));
					}
				}
			}
			else
			{
				final boolean n = map.n(x, y, z);
				final boolean s = map.s(x, y, z);
				final boolean e = map.e(x, y, z);
				final boolean w = map.w(x, y, z);
				if (n) visit(seen, queue, pack(x, y + 1, z));
				if (s) visit(seen, queue, pack(x, y - 1, z));
				if (e) visit(seen, queue, pack(x + 1, y, z));
				if (w) visit(seen, queue, pack(x - 1, y, z));
				// Diagonals, mirroring CollisionMap.ne/nw/se/sw.
				if (n && e && map.e(x, y + 1, z) && map.n(x + 1, y, z)) visit(seen, queue, pack(x + 1, y + 1, z));
				if (n && w && map.w(x, y + 1, z) && map.n(x - 1, y, z)) visit(seen, queue, pack(x - 1, y + 1, z));
				if (s && e && map.e(x, y - 1, z) && map.s(x + 1, y, z)) visit(seen, queue, pack(x + 1, y - 1, z));
				if (s && w && map.w(x, y - 1, z) && map.s(x - 1, y, z)) visit(seen, queue, pack(x - 1, y - 1, z));
			}
			final List<Integer> next = edges.get(packed);
			if (next != null)
			{
				for (int to : next) visit(seen, queue, to);
			}
		}

		int reachable = 0;
		int checked = 0;
		try (BufferedReader in = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8);
			PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				final int[] p = point(line.replace('\t', ' '));
				if (p == null) continue;
				final boolean ok = seen.get(pack(p[0], p[1], p[2]));
				out.print(p[0] + "\t" + p[1] + "\t" + p[2] + "\t" + (ok ? 1 : 0) + "\n");
				checked++;
				if (ok) reachable++;
			}
		}
		System.out.println(reachable + " of " + checked + " points are reachable in Shortest Path's map ("
				+ seen.cardinality() + " tiles flooded)");
	}

	private static void visit(BitSet seen, ArrayDeque<Integer> queue, int packed)
	{
		if (packed < 0 || seen.get(packed)) return;
		seen.set(packed);
		queue.add(packed);
	}
}
