package net.runelite.client.plugins.microbot.testing.webwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class F2PWebWalkerRouteTest {

    @Test
    public void varrockSewerRegressionHasAStableSurfaceOrigin() {
        F2PWebWalkerRoute route = F2PWebWalkerRoute.selected("F2P-17").get(0);

        assertEquals(new WorldPoint(3236, 3458, 0), route.start);
        assertEquals(new WorldPoint(3237, 9858, 0), route.destination);
        assertEquals(5, route.repetitions);
        assertFalse("the route must not become a no-op after an earlier run ends underground",
                route.currentLocationStart);
    }

    @Test
    public void canoeSelectionGateRouteIsExplicitAndRequiresFiveSelections() {
        F2PWebWalkerRoute route = F2PWebWalkerRoute.selected("F2P-18").get(0);

        assertEquals(new WorldPoint(3243, 3237, 0), route.start);
        assertEquals(new WorldPoint(3199, 3344, 0), route.destination);
        assertEquals(3, route.repetitions);
        assertEquals(Rs2TransportExecutor.CANOE, route.expectedShadowExecutor);
        assertEquals(5, route.minimumExpectedShadowExecutions);
        assertEquals(10, route.forcedBankRouteComparisons);
        assertEquals(10, route.minimumExpectedBankRouteFromBankComparisons);
        assertEquals(10, route.minimumExpectedBankRouteFromBankItemGatedComparisons);
        assertFalse("resource-gated evidence route must not run in the default fresh-account suite",
                F2PWebWalkerRoute.selected("all").contains(route));
    }

    @Test
    public void shipSelectionGateRouteIsExplicitAndRequiresThreeTerminalSelections() {
        F2PWebWalkerRoute route = F2PWebWalkerRoute.selected("F2P-19").get(0);

        assertEquals(new WorldPoint(3029, 3217, 0), route.start);
        assertEquals(new WorldPoint(2956, 3146, 0), route.destination);
        assertEquals(2, route.repetitions);
        assertEquals(Rs2TransportExecutor.TERMINAL_TRAVEL, route.expectedShadowExecutor);
        assertEquals(3, route.minimumExpectedShadowExecutions);
        assertFalse("fare-gated evidence route must not run in the default fresh-account suite",
                F2PWebWalkerRoute.selected("all").contains(route));
    }

    @Test
    public void activeReplanSelectionGateRouteContributesTwelveObservedReplans() {
        F2PWebWalkerRoute route = F2PWebWalkerRoute.selected("F2P-20").get(0);

        assertEquals(new WorldPoint(3029, 3217, 0), route.start);
        assertEquals(new WorldPoint(2946, 3368, 0), route.destination);
        assertEquals(12, route.forcedActiveReplans);
        assertEquals(12, route.minimumExpectedActiveReplanComparisons);
        assertTrue("Musa Point setup must retain the proven reverse ship", route.forceShips);
        assertFalse("evidence-injection route must not run in the default fresh-account suite",
                F2PWebWalkerRoute.selected("all").contains(route));
    }

    @Test
    public void recoveryReplanSelectionGateRouteRequiresRecoveredArrivals() {
        F2PWebWalkerRoute route = F2PWebWalkerRoute.selected("F2P-21").get(0);

        assertEquals(new WorldPoint(3029, 3217, 0), route.start);
        assertEquals(new WorldPoint(2957, 3214, 0), route.destination);
        assertEquals(3, route.repetitions);
        assertEquals(2, route.forcedRecoveryReplans);
        assertEquals(2, route.forcedSetupRecoveryReplans);
        assertEquals(10, route.minimumExpectedRecoveryReplanComparisons);
        assertEquals(5, route.minimumExpectedRecoveryArrivals);
        assertTrue(route.requireF2PWorld);
        assertTrue(route.forceNoAgilityShortcuts);
        assertTrue(route.forceNoTeleports);
        assertFalse("recovery evidence route must not run in the default fresh-account suite",
                F2PWebWalkerRoute.selected("all").contains(route));
    }
}
