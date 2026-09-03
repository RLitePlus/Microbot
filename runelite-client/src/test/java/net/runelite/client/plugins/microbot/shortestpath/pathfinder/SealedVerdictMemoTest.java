package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The sealed-verdict memo's whole contract: a recorded rim-unreachable proof is honoured only for
 * the same goal, under the same transport-refresh key, within the TTL — and a reached rim erases it.
 */
public class SealedVerdictMemoTest {
    private static final int GOAL = 12345;
    private static final int KEY = 777;
    private static final long NOW = 1_000_000L;

    @Before
    @After
    public void reset() {
        SealedVerdictMemo.clearAll();
    }

    @Test
    public void unknownGoalIsNotMemoized() {
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
    }

    @Test
    public void freshVerdictUnderSameKeyHits() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        assertTrue(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW + SealedVerdictMemo.TTL_MS - 1));
    }

    @Test
    public void verdictExpiresAtTtl() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW + SealedVerdictMemo.TTL_MS));
        // The expired entry is evicted, not just skipped: a later probe under the old key stays cold.
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
    }

    @Test
    public void refreshKeyChangeInvalidates() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY + 1, NOW));
        // A key mismatch drops the stale entry entirely.
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
    }

    @Test
    public void differentGoalDoesNotHit() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL + 1, KEY, NOW));
    }

    @Test
    public void reachedRimClearsVerdict() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        SealedVerdictMemo.clear(GOAL);
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
    }

    @Test
    public void rerecordRefreshesTimestamp() {
        SealedVerdictMemo.record(GOAL, KEY, NOW);
        SealedVerdictMemo.record(GOAL, KEY, NOW + SealedVerdictMemo.TTL_MS);
        assertTrue(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW + 2 * SealedVerdictMemo.TTL_MS - 1));
    }

    @Test
    public void capOverflowResetsInsteadOfGrowing() {
        for (int i = 0; i < SealedVerdictMemo.MAX_ENTRIES; i++) {
            SealedVerdictMemo.record(GOAL + i, KEY, NOW);
        }
        assertTrue(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
        // One more distinct goal trips the cap: the memo resets wholesale and holds only the newcomer.
        SealedVerdictMemo.record(GOAL - 1, KEY, NOW);
        assertTrue(SealedVerdictMemo.isRimUnreachable(GOAL - 1, KEY, NOW));
        assertFalse(SealedVerdictMemo.isRimUnreachable(GOAL, KEY, NOW));
    }
}
