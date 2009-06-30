/* *********************************************************************** *
 * project: org.matsim.*
 * LaneDefinitionsReaderWriterTest
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package org.matsim.core.network;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.network.MatsimLaneDefinitionsReader;
import org.matsim.core.network.MatsimLaneDefinitionsWriter;
import org.matsim.lanes.basic.BasicLane;
import org.matsim.lanes.basic.BasicLaneDefinitions;
import org.matsim.lanes.basic.BasicLaneDefinitionsImpl;
import org.matsim.lanes.basic.BasicLanesToLinkAssignment;
import org.matsim.signalsystems.SignalSystemsReaderWriterTest;
import org.matsim.testcases.MatsimTestCase;

/**
 * @author dgrether
 * 
 */
public class LaneDefinitionsReaderWriterTest extends MatsimTestCase {

	private static final Logger log = Logger
			.getLogger(SignalSystemsReaderWriterTest.class);

	private static final String TESTXML = "testLaneDefinitions_v1.1.xml";

	private Id id1 = new IdImpl("1");

	private Id id3 = new IdImpl("3");

	private Id id5 = new IdImpl("5");

	private Id id23 = new IdImpl("23");

	public void testParser() throws IOException {
		BasicLaneDefinitions laneDefs = new BasicLaneDefinitionsImpl();
		MatsimLaneDefinitionsReader reader = new MatsimLaneDefinitionsReader(
				laneDefs);
		reader.readFile(this.getClassInputDirectory() + TESTXML);

		checkContent(laneDefs);
	}

	public void testWriter() {
		String testoutput = this.getOutputDirectory() + "testLssOutput.xml";
		log.debug("reading file...");
		// read the test file
		BasicLaneDefinitions laneDefs = new BasicLaneDefinitionsImpl();
		MatsimLaneDefinitionsReader reader = new MatsimLaneDefinitionsReader(
				laneDefs);
		reader.readFile(this.getClassInputDirectory() + TESTXML);

		// write the test file
		log.debug("write the test file...");
		MatsimLaneDefinitionsWriter writer = new MatsimLaneDefinitionsWriter(laneDefs);
		writer.writeFile(testoutput);

		log.debug("and read it again");
		laneDefs = new BasicLaneDefinitionsImpl();
		reader = new MatsimLaneDefinitionsReader(
				laneDefs);
		reader.readFile(this.getClassInputDirectory() + TESTXML);
		checkContent(laneDefs);
	}

	private void checkContent(BasicLaneDefinitions lanedefs) {
		assertEquals(1, lanedefs.getLanesToLinkAssignments().size());
		BasicLanesToLinkAssignment l2la;
		l2la = lanedefs.getLanesToLinkAssignments().get(0);
		assertNotNull(l2la);
		assertEquals(id23, l2la.getLinkId());
		BasicLane lane = l2la.getLanes().get(0);
		assertEquals(id3, lane.getId());
		assertEquals(id1, lane.getToLinkIds().get(0));
		assertEquals(45.0, lane.getLength(), EPSILON);
		assertEquals(1, lane.getNumberOfRepresentedLanes());
		lane = l2la.getLanes().get(1);
		assertEquals(id5, lane.getId());
		assertEquals(60.0, lane.getLength(), EPSILON);
		assertEquals(2, lane.getNumberOfRepresentedLanes());
	}
}
