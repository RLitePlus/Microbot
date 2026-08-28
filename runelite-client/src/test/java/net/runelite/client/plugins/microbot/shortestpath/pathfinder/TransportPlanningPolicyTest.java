package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TransportPlanningPolicyTest
{
	@Test
	public void localCoreRetainsInjectedAdmissionAndZeroRunePolicies() throws Exception
	{
		Transport admitted = transport("Allowed");
		Transport rejected = transport("Rejected");
		Transport home = transport("Home");
		TransportPlanningPolicy policy = new TransportPlanningPolicy()
		{
			@Override
			public boolean isAdmitted(Transport transport)
			{
				return transport != rejected;
			}

			@Override
			public boolean isZeroRuneSpell(Transport transport)
			{
				return transport == home;
			}
		};
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null, policy);

		Field field = PathfinderConfig.class.getDeclaredField("transportPlanningPolicy");
		field.setAccessible(true);
		TransportPlanningPolicy installed = (TransportPlanningPolicy) field.get(config);

		assertSame(policy, installed);
		assertTrue(installed.isAdmitted(admitted));
		assertFalse(installed.isAdmitted(rejected));
		assertTrue(installed.isZeroRuneSpell(home));
	}

	@Test
	public void nullTransportIsRejectedBeforeFeatureChecks() throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
		Method useTransport = PathfinderConfig.class.getDeclaredMethod("useTransport", Transport.class);
		useTransport.setAccessible(true);

		assertFalse((Boolean) useTransport.invoke(config, new Object[] {null}));
	}

	private static Transport transport(String displayInfo)
	{
		return new Transport(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3200, 3201, 0),
			displayInfo,
			TransportType.TRANSPORT,
			false,
			"Open",
			"Door",
			1);
	}
}
