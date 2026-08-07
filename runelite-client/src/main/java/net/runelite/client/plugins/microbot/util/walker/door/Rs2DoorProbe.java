package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;

/**
 * Door-probe logic that operates against a {@link DoorProbeContext} (the scan-scoped caches) and
 * explicit state (the session door blacklist, the recently-opened-door map), rather than
 * {@code Rs2Walker}'s static fields. The walker facade owns the state and passes it in.
 */
public final class Rs2DoorProbe {

    private Rs2DoorProbe() {
    }

    /**
     * Resolves an object's composition, memoised in the scan's composition cache when present so a
     * single raw-scene scan doesn't re-resolve the same object repeatedly. Falls back to a live
     * lookup when the context has no cache (outside a scan).
     */
    public static ObjectComposition resolveDoorComposition(DoorProbeContext ctx, TileObject object) {
        if (object == null) {
            return null;
        }
        Map<TileObject, Optional<ObjectComposition>> cache = ctx == null ? null : ctx.compositionCache();
        if (cache == null) {
            return Rs2GameObject.convertToObjectComposition(object);
        }
        return cache.computeIfAbsent(object,
                        ignored -> Optional.ofNullable(Rs2GameObject.convertToObjectComposition(object)))
                .orElse(null);
    }

    /**
     * True when this scene object is the interactable listed on a transport catalog row (same
	 * coordinates and object ids as TSV loaded into the shortest-path catalog), and is not
     * itself door-like. Used to avoid treating a catalog transport as a plain door.
     */
    public static boolean isCatalogTransportObject(TileObject object) {
        if (object == null) {
            return false;
        }
        WorldPoint loc = object.getWorldLocation();
        if (loc == null) {
            return false;
        }
        int id = object.getId();
        if (id <= 0) {
            return false;
        }
		for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                WorldPoint catalogOrigin = new WorldPoint(loc.getX() + dx, loc.getY() + dy, loc.getPlane());
				for (Rs2TransportEdge t : Rs2PathApi.getCatalogTransportEdges(catalogOrigin)) {
                    if (t != null && t.getObjectId() == id && !isDoorLikeCatalogTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when the scene object belongs to a catalog transport rather than the generic door
     * cascade. In addition to the catalogued open object id, this recognises the closed precursor
     * of a nearby climb-down transport (for example Manhole/Open -> Manhole/Climb-down).
     *
     * <p>The closed precursor cannot be identified by id alone because the catalog deliberately
     * stores the usable/open object. Treating its {@code Open} action as a normal walk-through door
     * makes the door handler release the route before the transport handler can perform the second
     * interaction.</p>
     */
    public static boolean isTransportOwnedSceneObject(DoorProbeContext ctx, TileObject object) {
        if (object == null) {
            return false;
        }
        Map<TileObject, Boolean> cache = ctx == null ? null : ctx.objectEligibilityCache();
        if (cache == null) {
            return computeTransportOwnedSceneObject(ctx, object);
        }
        return cache.computeIfAbsent(object, o -> computeTransportOwnedSceneObject(ctx, o));
    }

    private static boolean computeTransportOwnedSceneObject(DoorProbeContext ctx, TileObject object) {
        if (isCatalogTransportObject(object) && !Rs2DoorDetection.isDoorLikeSceneObject(object)) {
            return true;
        }
        TransportSceneSnapshot snapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint location = object.getWorldLocation();
            ObjectComposition composition = resolveDoorComposition(ctx, object);
            if (location == null || composition == null) {
                return null;
            }
            return new TransportSceneSnapshot(
                    location, composition.getName(), composition.getActions());
        }).orElse(null);
        if (snapshot == null) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                WorldPoint catalogOrigin = new WorldPoint(
                        snapshot.location.getX() + dx,
                        snapshot.location.getY() + dy,
                        snapshot.location.getPlane());
                for (Rs2TransportEdge transport : Rs2PathApi.getCatalogTransportEdges(catalogOrigin)) {
                    if (isClosedPortalVariantForTransport(
                            transport, snapshot.name, snapshot.actions)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final class TransportSceneSnapshot {
        private final WorldPoint location;
        private final String name;
        private final String[] actions;

        private TransportSceneSnapshot(WorldPoint location, String name, String[] actions) {
            this.location = location;
            this.name = name;
            this.actions = actions;
        }
    }

    static boolean isClosedPortalVariantForTransport(Rs2TransportEdge transport,
                                                       String objectName,
                                                       String[] objectActions) {
        if (transport == null
                || transport.getType() != Rs2TransportType.TRANSPORT
                || transport.getExecutor() != Rs2TransportExecutor.OBJECT
                || !isClimbDownAction(transport.getAction())
                || objectName == null
                || objectActions == null) {
            return false;
        }
        String normalizedName = objectName.toLowerCase();
        boolean portalName = normalizedName.contains("trapdoor")
                || normalizedName.contains("manhole")
                || normalizedName.contains("grate")
                || normalizedName.contains("hatch");
        if (!portalName) {
            return false;
        }
        for (String action : objectActions) {
            if (action != null && "Open".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClimbDownAction(String action) {
        return action != null
                && ("Climb-down".equalsIgnoreCase(action)
                || "Climb down".equalsIgnoreCase(action));
    }

	public static boolean isDoorLikeCatalogTransport(Rs2TransportEdge transport) {
		if (transport == null || transport.getType() != Rs2TransportType.TRANSPORT) {
            return false;
        }
		return Rs2DoorClassifier.isDoorLikeGameObjectName(transport.getTarget())
                || Rs2DoorClassifier.isDoorLikeGameObjectName(transport.getDisplayInfo())
                || isDoorLikeTransportAction(transport.getAction());
    }

    private static boolean isDoorLikeTransportAction(String action) {
        if (action == null) {
            return false;
        }
        return Rs2DoorClassifier.doorActionPriorityIndex(action) != Integer.MAX_VALUE;
    }

    /** Whether {@code object} (at {@code objectLocation}) is a walk-through door lying on the segment. */
    public static boolean isDoorCandidateOnSegment(DoorProbeContext ctx, Set<WorldPoint> blacklist,
                                                   TileObject object, WorldPoint objectLocation,
                                                   WorldPoint playerLoc, WorldPoint fromWp, WorldPoint toWp,
                                                   List<String> doorActions, int searchDistance) {
        if (object == null || objectLocation == null) {
            return false;
        }
        WorldPoint loc = objectLocation;
        // Cheap, player-relative checks first — these depend on where the player is standing, so they
        // stay live rather than memoised.
        if (loc.getPlane() != playerLoc.getPlane()
                || loc.distanceTo2D(playerLoc) > searchDistance
                || blacklist.contains(loc)
                || (!(object instanceof WallObject) && !(object instanceof GameObject))) {
            return false;
        }
        if (isTransportOwnedSceneObject(ctx, object)) {
            return false;
        }
        // Snapshot location, not object.getWorldLocation(): this runs per candidate per segment.
        if (!Rs2DoorGeometry.isDoorOnSegment(object, loc, fromWp, toWp)) {
            return false;
        }
        ObjectComposition comp = resolveDoorComposition(ctx, object);
        return Rs2DoorClassifier.isDoorComposition(comp, doorActions);
    }

    /**
     * "A catalog transport that is not itself door-like" — the expensive, segment-independent half of
     * the candidate test, memoised for the scan via {@link DoorProbeContext#objectEligibilityCache()}.
     * <p>
     * The answer depends only on the object (id, location, composition), yet the probe re-evaluated it
     * for every route segment against the entire snapshot, paying a {@code getWorldLocation()}, nine
     * transport-map lookups and an uncached composition resolve each time. With no cache available the
     * behaviour is unchanged, just uncached.
     */
    /** Nearest walk-through door lying on the {@code fromWp -> toWp} segment, using scan snapshots when present. */
    public static TileObject findDoorNearSegment(DoorProbeContext ctx, Set<WorldPoint> blacklist,
                                                 Map<WorldPoint, Long> recentlyOpened, long stationaryDoorSuppressMs,
                                                 WorldPoint fromWp, WorldPoint toWp, List<String> doorActions) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return null;
        }
        if (Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(recentlyOpened, stationaryDoorSuppressMs, fromWp, toWp)) {
            return null;
        }

        final int searchDistance = 10;
        List<WallObject> wallSnapshot = ctx.wallSnapshot();
        List<GameObject> gameObjectSnapshot = ctx.gameObjectSnapshot();
        if (wallSnapshot != null || gameObjectSnapshot != null) {
            Map<TileObject, WorldPoint> locations = ctx.locationSnapshot();
            if (locations == null) {
                return null;
            }
            Map<String, Optional<TileObject>> segmentCache = ctx.segmentCache();
            String segmentKey = fromWp.getX() + "," + fromWp.getY() + "," + fromWp.getPlane()
                    + ">" + toWp.getX() + "," + toWp.getY() + "," + toWp.getPlane();
            if (segmentCache != null && segmentCache.containsKey(segmentKey)) {
                return segmentCache.get(segmentKey).orElse(null);
            }
            List<TileObject> candidates = new ArrayList<>(
                    (wallSnapshot != null ? wallSnapshot.size() : 0)
                            + (gameObjectSnapshot != null ? gameObjectSnapshot.size() : 0));
            if (wallSnapshot != null) {
                candidates.addAll(wallSnapshot);
            }
            if (gameObjectSnapshot != null) {
                candidates.addAll(gameObjectSnapshot);
            }
            TileObject match = candidates.stream()
                    .filter(o -> isDoorCandidateOnSegment(ctx, blacklist, o, locations.get(o),
                            playerLoc, fromWp, toWp, doorActions, searchDistance))
                    .min(Comparator.comparingInt(o -> locations.get(o).distanceTo2D(playerLoc)))
                    .orElse(null);
            if (segmentCache != null) {
                segmentCache.put(segmentKey, Optional.ofNullable(match));
            }
            return match;
        }
        return Rs2GameObject.getAll(o -> isDoorCandidateOnSegment(ctx, blacklist, o, o.getWorldLocation(),
                        playerLoc, fromWp, toWp, doorActions, searchDistance), playerLoc, searchDistance).stream()
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo2D(playerLoc)))
                .orElse(null);
    }
}
