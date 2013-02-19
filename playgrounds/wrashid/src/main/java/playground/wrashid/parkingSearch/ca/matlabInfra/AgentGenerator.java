package playground.wrashid.parkingSearch.ca.matlabInfra;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.parking.lib.GeneralLib;
import org.matsim.core.api.experimental.events.ActivityStartEvent;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkImpl;


public class AgentGenerator {

	public static void main(String[] args) {
		String eventsFile = Config.getEventsFile();
		EventsManager events = (EventsManager) EventsUtils.createEventsManager();
		MyEventHandler eventHandler = new MyEventHandler(Config.getNetwork());
		events.addHandler(eventHandler);
		EventsReaderXMLv1 reader = new EventsReaderXMLv1(events);
		reader.parse(eventsFile);
		eventHandler.fillUnknownActDurations();
		
		new File(Config.getOutputFolder()).mkdir();
		writeOutAgents(eventHandler.processedAgents, Config.getOutputFolder() + "population.xml");
	}

	private static void writeOutAgents(LinkedList<Agent> processedAgents, String fileName) {
		ArrayList<String> list = new ArrayList<String>();
		list.add("<agents>");
		for (Agent agent : processedAgents) {
			list.add(agent.getXMLString(Config.getNetwork()));
		}
		list.add("</agents>");
		GeneralLib.writeList(list, fileName);
	}
}

class MyEventHandler implements LinkEnterEventHandler, AgentDepartureEventHandler, AgentArrivalEventHandler,
		ActivityStartEventHandler {

	private final NetworkImpl network;
	private HashMap<Id, Agent> agentsInStudyArea;
	public LinkedList<Agent> processedAgents;
	public HashMap<Id,Double> agentLastArrivalTime;

	MyEventHandler(NetworkImpl network) {
		this.network = network;
		agentsInStudyArea = new HashMap<Id, Agent>();
		processedAgents = new LinkedList<Agent>();
		agentLastArrivalTime=new HashMap<Id, Double>();
	}

	public void fillUnknownActDurations(){
		for (Agent agent: processedAgents){
			if (agent.actDur==Double.NEGATIVE_INFINITY){
				agent.actDur=GeneralLib.getIntervalDuration(agent.tripStartTime, agentLastArrivalTime.get(agent.id));
			}
		}
	}
	
	@Override
	public void reset(int iteration) {
		// TODO Auto-generated method stub
	}

	@Override
	public void handleEvent(AgentArrivalEvent event) {
		Id personId = event.getPersonId();
		if (event.getLegMode().equalsIgnoreCase("car") && Config.isInsideStudyArea(event.getLinkId())) {
			if (!agentsInStudyArea.containsKey(personId)) {
				agentsInStudyArea.put(personId, new Agent(personId, event.getTime()));
				//DebugLib.stopSystemAndReportInconsistency("there is some conceptual problem in the prog...");
			}
			
			Agent agent = agentsInStudyArea.get(personId);
			agent.routeTo = agent.tmpRoute.getNodeString(network);
			agent.actStartTime = event.getTime();
		}
		agentLastArrivalTime.put(personId, event.getTime());
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		Id personId = event.getPersonId();
		if (event.getLegMode().equalsIgnoreCase("car") && Config.isInsideStudyArea(event.getLinkId())) {
			if (!agentsInStudyArea.containsKey(personId)) {

				// this indicates an agent, which starts his trip with a car in
				// the study area
				// either he started the day there or he was beamed there with
				// some other mode...

				agentsInStudyArea.put(personId, new Agent(personId, event.getTime()));
				Agent agent = agentsInStudyArea.get(personId);
				
				if (agentLastArrivalTime.containsKey(personId)){
					agent.actDur = GeneralLib.getIntervalDuration(agentLastArrivalTime.get(personId),event.getTime());
				} else {
					agent.actDur = Double.NEGATIVE_INFINITY;
				}
			} else {
				// agent continues trip
				
				Agent agent = agentsInStudyArea.get(personId);

				agent.actDur = GeneralLib.getIntervalDuration(agent.actStartTime, event.getTime());

				agent.tmpRoute = new Route();
			}
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		Id personId = event.getPersonId();
		if (Config.isInsideStudyArea(event.getLinkId())) {
			if (!agentsInStudyArea.containsKey(personId)) {
				agentsInStudyArea.put(personId, new Agent(personId, event.getTime()));
			}
			Agent agent = agentsInStudyArea.get(personId);

			agent.tmpRoute.addLink(network.getLinks().get(event.getLinkId()));

		} else {
			if (agentsInStudyArea.containsKey(personId)) {
				Agent agent = agentsInStudyArea.get(personId);

				agent.routeAway = agent.tmpRoute.getNodeString(network);

				processedAgents.add(agent);
				agentsInStudyArea.remove(personId);
			}
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		Id personId = event.getPersonId();
		if (agentsInStudyArea.containsKey(personId)) {
			Agent agent = agentsInStudyArea.get(personId);

			if (agent.actType == null) {
				agent.actType = event.getActType();
			}
		}
	}

}
