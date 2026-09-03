package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * A sealed-target substitute search that dies on its NODE BUDGET has proven nothing about the rim —
 * it simply ran out of nodes. Recording a rim-unreachable verdict for it poisoned every subsequent
 * replan down to the reduced repeat budget, guaranteeing none could ever finish either.
 * <p>
 * Pinned against the live failure of 2026-08-15 (Varlamore→Burthorpe, ~1500 tiles): the first
 * sealed-substitute pass exhausted its 50k nodes at bestDist=112 with the rim perfectly reachable,
 * the exhaustion was memoed as "rim unreachable", and every replan after that got 5k nodes —
 * alternating between partial endpoints 124 and 1459 tiles from the goal while the player thrashed
 * in place. Only a genuinely drained frontier (SEARCH_EXHAUSTED with empty queues) is a proof and
 * may be memoed; {@link SealedVerdictMemo}'s own contract says as much.
 */
public class SealedVerdictBudgetExhaustionTest {

    private static SplitFlagMap collisionMap;
    private static HashMap<WorldPoint, Set<Transport>> transports;

    /** Lumbridge courtyard: mapped, ordinary, walkable ground. */
    private static final WorldPoint SRC = new WorldPoint(3222, 3218, 0);

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        transports = Transport.loadAllFromResources();
    }

    @Before
    public void clearMemo() {
        SealedVerdictMemo.clearAll();
    }

    private static PathfinderConfig newConfig() {
        PathfinderConfig config = new PathfinderConfig(collisionMap, transports,
                Collections.emptyList(), null, null);
        try {
            java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            f.setAccessible(true);
            f.setLong(config, 10_000);
            for (Map.Entry<WorldPoint, Set<Transport>> e : transports.entrySet()) {
                if (e.getKey() == null) continue;
                config.getTransports().put(e.getKey(), e.getValue());
                config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return config;
    }

    /** Mirrors the probe's sealed reading: no neighbour can step INTO the tile from any direction. */
    private static boolean noEntry(CollisionMap map, int x, int y, int z) {
        int[][] all = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : all) {
            if (map.canStep(x - d[0], y - d[1], z, d[0], d[1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Self-locating, same as SealedTargetFastPathTest: BFS the walkable area around SRC, then pick
     * a sealed tile one of whose neighbours is in that area — a sealed footprint beside ground the
     * player can stand on, i.e. a rim that IS reachable given enough budget.
     */
    private static WorldPoint locateSealedTileWithReachableRim(CollisionMap map) {
        Set<WorldPoint> reachable = new HashSet<>();
        ArrayDeque<WorldPoint> frontier = new ArrayDeque<>();
        reachable.add(SRC);
        frontier.add(SRC);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!frontier.isEmpty() && reachable.size() < 1_500) {
            WorldPoint c = frontier.poll();
            for (int[] d : dirs) {
                if (!map.canStep(c.getX(), c.getY(), 0, d[0], d[1])) {
                    continue;
                }
                WorldPoint n = new WorldPoint(c.getX() + d[0], c.getY() + d[1], 0);
                if (reachable.add(n)) {
                    frontier.add(n);
                }
            }
        }
        WorldPoint dst = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint open : reachable) {
            for (int[] d : dirs) {
                int x = open.getX() + d[0];
                int y = open.getY() + d[1];
                WorldPoint cand = new WorldPoint(x, y, 0);
                if (reachable.contains(cand) || !noEntry(map, x, y, 0)) {
                    continue;
                }
                int dist = Math.max(Math.abs(x - SRC.getX()), Math.abs(y - SRC.getY()));
                if (dist >= 3 && dist < bestDist) {
                    bestDist = dist;
                    dst = cand;
                }
            }
        }
        return dst;
    }

    @Test
    public void budgetExhaustionMustNotRecordARimUnreachableVerdict() {
        PathfinderConfig config = newConfig();
        CollisionMap map = config.getMap();
        map.beginSearch();
        WorldPoint dst = locateSealedTileWithReachableRim(map);
        assumeTrue("precondition: found a sealed tile whose rim the player can stand on", dst != null);

        // A one-node budget forces the substitute pass to die on the budget before it can pop a
        // rim tile — the exact shape of the live failure, minus the 1500 tiles.
        Pathfinder capped = new Pathfinder(config, SRC, dst);
        capped.setSealedSubstituteNodeBudgetForTest(1);
        capped.run();

        assertEquals("callers must still hear the original target is unreachable",
                PathTerminationReason.SEARCH_EXHAUSTED, capped.getTerminationReason());
        assertNull("the rim was never reached", capped.getReachedSealedSubstitute());
        assertFalse("an exhausted budget proves nothing — no rim-unreachable verdict may be recorded",
                SealedVerdictMemo.isRimUnreachable(WorldPointUtil.packWorldPoint(dst),
                        config.getLastTransportRefreshKeyHash(), System.currentTimeMillis()));

        // With no poisoned memo, the very next full-budget search completes the approach.
        Pathfinder full = new Pathfinder(config, SRC, dst);
        full.run();
        assertTrue("full-budget follow-up must produce the approach path", !full.getPath().isEmpty());
        WorldPoint last = full.getPath().get(full.getPath().size() - 1);
        assertTrue("approach must end beside the sealed tile, ended at " + last + " for dst=" + dst,
                last.distanceTo2D(dst) <= 2);
    }
}
