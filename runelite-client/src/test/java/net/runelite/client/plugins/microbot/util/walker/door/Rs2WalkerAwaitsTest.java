package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerAwaitsTest {
    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    @Test
    public void shouldAcceptIdleDoorAwait_acceptsStationaryPastMinimum() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, false));
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1201L, true));
    }

    /**
     * The old implementation ended in {@code return edgeResolved}, which made this branch dead code:
     * the traversal wait returns the moment the edge resolves, so it only ever reached here with
     * edgeResolved == false. A stalled interaction burned the full 2200ms budget instead of releasing.
     */
    @Test
    public void shouldAcceptIdleDoorAwait_acceptsUnresolvedEdgeWhenStalled() {
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1500L, false));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsMovingOrAnimating() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(true, false, 5000L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, true, 5000L, true));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsBeforeMinimumElapsed() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 800L, true));
    }

    @Test
    public void hasReachedDoorFarSide_rejectsNearSideEndpoint() {
        assertFalse(Rs2WalkerAwaits.hasReachedDoorFarSide(
                wp(3246, 9892), wp(3246, 9892), wp(3247, 9892)));
    }

    @Test
    public void hasReachedDoorFarSide_rejectsEquidistantSideTile() {
        assertFalse(Rs2WalkerAwaits.hasReachedDoorFarSide(
                wp(3246, 9891), wp(3246, 9892), wp(3247, 9892)));
    }

    @Test
    public void hasReachedDoorFarSide_acceptsDestinationEndpoint() {
        assertTrue(Rs2WalkerAwaits.hasReachedDoorFarSide(
                wp(3247, 9892), wp(3246, 9892), wp(3247, 9892)));
    }

    @Test
    public void hasReachedDoorFarSide_acceptsTileBeyondDestination() {
        assertTrue(Rs2WalkerAwaits.hasReachedDoorFarSide(
                wp(3248, 9892), wp(3246, 9892), wp(3247, 9892)));
    }

    @Test
    public void hasReachedDoorFarSide_rejectsOtherPlane() {
        assertFalse(Rs2WalkerAwaits.hasReachedDoorFarSide(
                new WorldPoint(3247, 9892, 1), wp(3246, 9892), wp(3247, 9892)));
    }

    @Test
    public void resolvedDoorReadyForFollowup_requiresFullStableTick() {
        assertFalse(Rs2WalkerAwaits.resolvedDoorReadyForFollowup(
                true, false, false, 1_000L, 1_599L, 600L));
        assertTrue(Rs2WalkerAwaits.resolvedDoorReadyForFollowup(
                true, false, false, 1_000L, 1_600L, 600L));
    }

    @Test
    public void resolvedDoorReadyForFollowup_rejectsMovementAnimationAndUnresolvedEdge() {
        assertFalse(Rs2WalkerAwaits.resolvedDoorReadyForFollowup(
                false, false, false, 1_000L, 2_000L, 600L));
        assertFalse(Rs2WalkerAwaits.resolvedDoorReadyForFollowup(
                true, true, false, 1_000L, 2_000L, 600L));
        assertFalse(Rs2WalkerAwaits.resolvedDoorReadyForFollowup(
                true, false, true, 1_000L, 2_000L, 600L));
    }
}
