/* *********************************************************************** *
 * project: org.matsim.*
 * CalcPaidToll.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.jjoubert.roadpricing.senozon.scoring;

import java.util.Map;
import java.util.TreeMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.algorithms.Vehicle2DriverEventHandler;
import org.matsim.core.network.Network;
import org.matsim.roadpricing.RoadPricingScheme;
import org.matsim.roadpricing.RoadPricingSchemeImpl.Cost;
import org.matsim.vehicles.Vehicle;

import playground.jjoubert.roadpricing.senozon.SanralTollFactor;

/**
 * Calculates the toll agents pay during a simulation by analyzing events. To
 * fully function, add an instance of this class as EventHandler to your Events
 * object.
 *
 * @author mrieser
 */
public class SanralCalcPaidToll implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {
	
	private Vehicle2DriverEventHandler delegate = new Vehicle2DriverEventHandler();

	static class AgentInfo {
		public double toll = 0.0;
		public boolean insideCordonArea = true;
	}

	final RoadPricingScheme scheme;
	final TreeMap<Id<Vehicle>, AgentInfo> agents = new TreeMap<Id<Vehicle>, AgentInfo>();
	private final Network network;

	private TollBehaviourI handler = null;

	public SanralCalcPaidToll(final Network network, final RoadPricingScheme scheme) {
		super();
		this.network = network;
		this.scheme = scheme;
		if (RoadPricingScheme.TOLL_TYPE_DISTANCE.equals(scheme.getType())) {
			this.handler = new DistanceTollBehaviour();
		} else if (RoadPricingScheme.TOLL_TYPE_AREA.equals(scheme.getType())) {
			this.handler = new AreaTollBehaviour();
		} else if (RoadPricingScheme.TOLL_TYPE_CORDON.equals(scheme.getType())) {
			this.handler = new CordonTollBehaviour();
		} else {
			throw new IllegalArgumentException("RoadPricingScheme of type \"" + scheme.getType() + "\" is not supported.");
		}
	}

	@Override
	public void handleEvent(final LinkEnterEvent event) {
		Link link = this.network.getLinks().get(event.getLinkId());
		this.handler.handleEvent(event, link);
	}

	@Override
	public void handleEvent(final VehicleEntersTrafficEvent event) {
		Link link = this.network.getLinks().get(event.getLinkId());
		if (handler instanceof DistanceTollBehaviour) {
			/* we do not handle wait2link-events for these tolls, because the agent
			 * should not pay twice for this link, and he (likely) paid already when
			 * arriving at this link.  */
			return;
		} else {
			// Just like a LinkEnterEvent
			this.handler.handleEvent(new LinkEnterEvent(event.getTime(), event.getVehicleId(), event.getLinkId()), link);
		}
	}

	/**
	 * Sends {@link PersonMoneyEvent}s for all agents that must pay a toll.
	 * This method should usually be called at the end before of an iteration.
	 *
	 * <strong>Important note: </strong>Do not call this method twice without
	 * calling {@link #reset(int)} in between. Otherwise the toll-disutility
	 * may be added twice to the agents' score!
	 *
	 * @param time the current time the generated events are associated with
	 * @param events the {@link EventsManager} collection, the generated events are sent to for processing
	 */
	public void sendUtilityEvents(final double time, final EventsManager events) {
		for (Map.Entry<Id<Vehicle>, AgentInfo> entries : this.agents.entrySet()) {
			events.processEvent(new PersonMoneyEvent(time, delegate.getDriverOfVehicle(entries.getKey()), -entries.getValue().toll));
		}
	}

	@Override
	public void reset(final int iteration) {
		this.agents.clear();
	}

	/**
	 * Returns the toll the specified agent has paid in the course of the
	 * simulation so far.
	 *
	 * @param agentId
	 * @return The toll paid by the specified agent, 0.0 if no toll was paid.
	 */
	public double getAgentToll(final Id<Vehicle> agentId) {
		AgentInfo info = this.agents.get(agentId);
		if (info == null) {
			return 0.0;
		}
		return info.toll;
	}

	/**
	 * @return The toll paid by all the agents.
	 */
	public double getAllAgentsToll() {
		double tolls = 0;
		for (AgentInfo ai : this.agents.values()) {
			tolls += (ai == null) ? 0.0 : ai.toll;
		}
		return tolls;
	}

	/**
	 * @return The Number of all the Drawees.
	 */
	public int getDraweesNr() {
		int dwCnt = 0;
		for (AgentInfo ai : this.agents.values()) {
			if ((ai != null) && (ai.toll > 0.0)) {
					dwCnt++;
			}
		}
		return dwCnt;
	}

	/**
	 * A simple interface to implement different toll schemes.
	 */
	private interface TollBehaviourI {
		public void handleEvent(LinkEnterEvent event, Link link);
	}

	/**
	 * Handles the calculation of the distance toll. If an agent enters a link at
	 * a time the toll has to be paid, the toll amount is added to the agent. The
	 * agent does not have to pay the toll for a link if it starts on the link,
	 * as it may have paid already when arriving on the link.
	 */
	class DistanceTollBehaviour implements TollBehaviourI {
		@Override
		public void handleEvent(final LinkEnterEvent event, final Link link) {
			Cost baseCost = SanralCalcPaidToll.this.scheme.getLinkCostInfo(link.getId(),
					event.getTime(), delegate.getDriverOfVehicle(event.getVehicleId()), event.getVehicleId() );
			if (baseCost != null) {
				double newToll = link.getLength() * baseCost.amount * SanralTollFactor.getTollFactor(event.getVehicleId(), link.getId(), event.getTime());
				AgentInfo info = SanralCalcPaidToll.this.agents.get(event.getVehicleId());
				if (info == null) {
					info = new AgentInfo();
					SanralCalcPaidToll.this.agents.put(event.getVehicleId(), info);
				}
				info.toll += newToll;
			}
		}
	}

	/** Handles the calculation of the area toll. Whenever the agent is seen on
	 * one of the tolled link, the constant toll amount has to be paid once. */
	class AreaTollBehaviour implements TollBehaviourI {
		@Override
		public void handleEvent(final LinkEnterEvent event, final Link link) {
			Cost baseCost = SanralCalcPaidToll.this.scheme.getLinkCostInfo(
					link.getId(), 
					event.getTime(), 
					delegate.getDriverOfVehicle(event.getVehicleId()), 
					event.getVehicleId());
			if (baseCost != null) {
				AgentInfo info = SanralCalcPaidToll.this.agents.get(event.getVehicleId());
				if (info == null) {
					info = new AgentInfo();
					SanralCalcPaidToll.this.agents.put(event.getVehicleId(), info);
					info.toll = baseCost.amount * SanralTollFactor.getTollFactor(event.getVehicleId(), link.getId(), event.getTime());
				}
			}
		}
	}

	/**
	 * Handles the calculation of the cordon toll. An agent has only to pay if he
	 * crosses the cordon from the outside to the inside.
	 */
	class CordonTollBehaviour implements TollBehaviourI {
		@Override
		public void handleEvent(final LinkEnterEvent event, final Link link) {
			Cost baseCost = SanralCalcPaidToll.this.scheme.getLinkCostInfo(
					link.getId(), 
					event.getTime(), 
					delegate.getDriverOfVehicle(event.getVehicleId()), 
					event.getVehicleId() );
			if (baseCost != null) {
				// this is a link inside the toll area.
				AgentInfo info = SanralCalcPaidToll.this.agents.get(event.getVehicleId());
				if (info == null) {
					// no information about this agent, so it did not yet pay the toll
					info = new AgentInfo();
					SanralCalcPaidToll.this.agents.put(event.getVehicleId(), info);
					info.toll = 0.0; // we start in the area, do not toll
				} else if (!info.insideCordonArea) {
					// agent was outside before, now inside the toll area --> agent has to pay
					info.insideCordonArea = true;
					info.toll += baseCost.amount * SanralTollFactor.getTollFactor(event.getVehicleId(), link.getId(), event.getTime());
				}
			} else {
				// this is a link outside the toll area.
				AgentInfo info = SanralCalcPaidToll.this.agents.get(event.getVehicleId());
				if (info == null) {
					info = new AgentInfo();
					SanralCalcPaidToll.this.agents.put(event.getVehicleId(), info);
				}
				info.insideCordonArea = false;
			}
		}
	}

}
