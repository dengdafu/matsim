/* *********************************************************************** *
 * project: org.matsim.*
 * Vehicle.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2009 by the members listed in the COPYING,  *
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

package org.matsim.core.mobsim.queuesim;

import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.network.Link;

public class QueueVehicleImpl implements QueueVehicle {

	private double earliestLinkExitTime = 0;

	private DriverAgent driver = null;

	private final Id id;

	public QueueVehicleImpl(final Id id) {
		this.id = id;
	}

	public double getEarliestLinkExitTime() {
		return this.earliestLinkExitTime;
	}

	public void setEarliestLinkExitTime(final double time) {
		this.earliestLinkExitTime = time;
	}

	public Link getCurrentLink() {
		return this.driver.getCurrentLink();
	}

	public DriverAgent getDriver() {
		return this.driver;
	}

	public void setDriver(final DriverAgent driver) {
		this.driver = driver;
	}

	public Id getId() {
		return this.id;
	}

	@Override
	public String toString() {
		return "Vehicle Id " + getId() + ", driven by (personId) " + this.driver.getPerson().getId()
				+ ", on link " + this.driver.getCurrentLink().getId();
	}

}
