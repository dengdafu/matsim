/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.contrib.drt.optimizer.rebalancing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;

import javax.inject.Inject;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.ZonalDemandAggregator;
import org.matsim.contrib.drt.analysis.zonal.ZonalIdleVehicleCollector;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.schedule.DrtTask;
import org.matsim.contrib.drt.schedule.DrtTask.DrtTaskType;
import org.matsim.contrib.drt.scheduler.DrtScheduler;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.util.distance.DistanceUtils;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.misc.Time;

import com.vividsolutions.jts.geom.Geometry;

/**
 * @author  jbischoff
 *
 */
/**
 *
 */
public class DemandBasedRebalancingStrategy implements RebalancingStrategy {

	private ZonalIdleVehicleCollector idleVehicles;
	private ZonalDemandAggregator demandAggregator;
	private DrtZonalSystem zonalSystem;
	

	/**
	 * 
	 */
	@Inject
	public DemandBasedRebalancingStrategy(ZonalIdleVehicleCollector idleVehicles, ZonalDemandAggregator demandAggregator, DrtZonalSystem zonalSystem) {
		this.idleVehicles = idleVehicles;
		this.demandAggregator = demandAggregator;
		this.zonalSystem = zonalSystem;
		
	}
	
	/* (non-Javadoc)
	 * @see org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy#calcRelocations(java.lang.Iterable)
	 */
	@Override
	public List<Relocation> calcRelocations(Iterable<? extends Vehicle> rebalancableVehicles, double time) {
	
		List<Relocation> relocations = new ArrayList<>();
 		Map <Id<Vehicle>,Vehicle> idleVehiclesMap = new HashMap<>();
		for (Vehicle v : rebalancableVehicles){
			if (v.getServiceEndTime()>time+3600){
				idleVehiclesMap.put(v.getId(),v);
			}
		}
		
		Map<String, Integer> requiredAdditionalVehiclesPerZone = calculateZonalVehicleRequirements(idleVehiclesMap, time);
		List<String> zones = new ArrayList<>(requiredAdditionalVehiclesPerZone.keySet());
		for (String zone : zones){
			int requiredVehicles = requiredAdditionalVehiclesPerZone.get(zone);
			if (requiredVehicles>0){
				for (int i = 0; i< requiredVehicles; i++){
					Geometry z = zonalSystem.getZone(zone);
					if (z==null) {throw new RuntimeException();	};
					Coord zoneCentroid = MGC.point2Coord(z.getCentroid());
					Vehicle v = findClosestVehicle(idleVehiclesMap,zoneCentroid, time);
					
					if (v!=null){
						idleVehiclesMap.remove(v.getId());
						relocations.add(new Relocation(v, zonalSystem.getLinkNearZoneCentroid(zone)));
					}
				}
			}
		}
//		relocations.forEach(l->Logger.getLogger(getClass()).info(l.vehicle.getId().toString()+"-->"+l.link.getId().toString()));
		
		return relocations;
	}

	/**
	 * @param idleVehicles2
	 * @return
	 */
	private Vehicle findClosestVehicle(Map<Id<Vehicle>, Vehicle> idles, Coord coord, double time) {
		double closestDistance = Double.MAX_VALUE;
		Vehicle closestVeh = null;
		for (Vehicle v : idles.values()){
			Link vl = getLastLink(v,time);
			if (vl!=null){
				double distance = DistanceUtils.calculateDistance(coord, vl.getCoord());
				if (distance<closestDistance){
					closestDistance = distance;
					closestVeh = v;
				}
			}
		}
		return closestVeh;
	}

	/**
	 * @param rebalancableVehicles
	 * @return 
	 */
	private Map<String, Integer> calculateZonalVehicleRequirements(Map <Id<Vehicle>,Vehicle> rebalancableVehicles, double time) {
		Map<String, MutableInt> expectedDemand = demandAggregator.getExpectedDemandForTimeBin(time+60);
		if (expectedDemand==null){
			return new HashMap<>();
		}
		final MutableInt totalDemand = new MutableInt(0);
		expectedDemand.values().forEach(demand->totalDemand.add(demand.intValue()));
//		Logger.getLogger(getClass()).info("Rebalancing at "+Time.writeTime(time)+" vehicles: " + rebalancableVehicles.size()+ " expected demand :"+totalDemand.toString());
		Map<String,Integer> requiredAdditionalVehiclesPerZone = new HashMap<>();
		for (Entry<String, MutableInt> entry : expectedDemand.entrySet()){
			double demand = entry.getValue().doubleValue();
			int vehPerZone = (int) Math.floor((demand / totalDemand.doubleValue()) * rebalancableVehicles.size());
			int idleVehiclesInZone = 0;
			LinkedList<Id<Vehicle>> idleVehicleIds = idleVehicles.getIdleVehiclesPerZone(entry.getKey());
			if (idleVehicleIds!=null & (!idleVehicleIds.isEmpty())){
				idleVehiclesInZone = idleVehicleIds.size();
			
			for (int i = 0; i<vehPerZone;i++){
				if (!idleVehicleIds.isEmpty()){
					Id<Vehicle> vid = idleVehicleIds.poll(); 
					if (rebalancableVehicles.remove(vid)==null) {
//					Logger.getLogger(getClass()).error("Vehicle "+vid.toString()+" not idle for rebalancing.");	
					}
				}
			}
			int zoneSurplus = (vehPerZone - idleVehiclesInZone);
			if (zoneSurplus<0){
				zoneSurplus = 0;
			}
			requiredAdditionalVehiclesPerZone.put(entry.getKey(), zoneSurplus);
			} else {
			requiredAdditionalVehiclesPerZone.put(entry.getKey(),vehPerZone );
			}
			
			
		}
		return requiredAdditionalVehiclesPerZone;
		
	}
	
	private Link getLastLink(Vehicle vehicle, double time) {
		Schedule schedule = vehicle.getSchedule();
		if (time >= vehicle.getServiceEndTime() || schedule.getStatus() != ScheduleStatus.STARTED) {
			return null;
		}

		DrtTask currentTask = (DrtTask)schedule.getCurrentTask();
		if( currentTask.getTaskIdx() == schedule.getTaskCount() - 1 // last task (because no prebooking)
				&& currentTask.getDrtTaskType() == DrtTaskType.STAY ){
			DrtStayTask st = (DrtStayTask) currentTask;
			return st.getLink();
		}
		else return null;
	}

}