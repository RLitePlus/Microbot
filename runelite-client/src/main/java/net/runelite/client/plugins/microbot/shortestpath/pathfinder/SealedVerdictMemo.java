package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, per destination tile, that a sealed-target substitute search already ran to exhaustion
 * WITHOUT reaching any rim tile — i.e. the goal is sealed AND its rim is unreachable from where the
 * walker is operating.
 * <p>
 * Exists because that verdict was re-proven from scratch on every replan. Two live patterns paid for
 * it constantly: the partial-path crawl replans each pass (a walk to a goal behind an uncatalogued
 * gate re-ran the full {@code SEALED_SUBSTITUTE_NODE_BUDGET} search ~15 times in one walk), and
 * scripts polling reachability of tiles on unconnected components (agility rooftop marks) re-ran it
 * every lap for hours. The first proof stays exhaustive; while a fresh memo entry matches, repeats
 * drop to {@link Pathfinder} 's reduced budget — the best partial node is found early in the search,
 * so the truncated repeat yields nearly the same partial path at a tenth of the cost.
 * <p>
 * Safety: the memo only ever REDUCES the budget of a search whose outcome is already proven; it never
 * changes reachability decisions. A goal that becomes reachable stops probing as sealed and never
 * consults the memo. A rim that becomes reachable (a door opened) is caught by the entry's TTL and by
 * the transport-refresh key changing; the reduced budget is also still comfortably above the
 * hundreds of nodes a genuinely reachable near-side rim costs to reach.
 */
final class SealedVerdictMemo {
    /** A door opening does not change the refresh key, so staleness is time-bounded too. */
    static final long TTL_MS = 60_000L;
    /** Hard cap; beyond it the whole memo resets (verdicts are cheap to re-prove once). */
    static final int MAX_ENTRIES = 64;

    private static final Map<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();

    private SealedVerdictMemo() {
    }

    private static final class Entry {
        final int refreshKey;
        final long recordedAtMs;

        Entry(int refreshKey, long recordedAtMs) {
            this.refreshKey = refreshKey;
            this.recordedAtMs = recordedAtMs;
        }
    }

    /** True when a fresh verdict for this goal exists under the same transport-refresh key. */
    static boolean isRimUnreachable(int goalPacked, int refreshKey, long nowMs) {
        Entry e = ENTRIES.get(goalPacked);
        if (e == null) {
            return false;
        }
        if (e.refreshKey != refreshKey || nowMs - e.recordedAtMs >= TTL_MS) {
            ENTRIES.remove(goalPacked);
            return false;
        }
        return true;
    }

    static void record(int goalPacked, int refreshKey, long nowMs) {
        if (ENTRIES.size() >= MAX_ENTRIES && !ENTRIES.containsKey(goalPacked)) {
            ENTRIES.clear();
        }
        ENTRIES.put(goalPacked, new Entry(refreshKey, nowMs));
    }

    /** The substitute search reached a rim: the rim IS reachable, drop any stale verdict. */
    static void clear(int goalPacked) {
        ENTRIES.remove(goalPacked);
    }

    static void clearAll() {
        ENTRIES.clear();
    }
}
