/* *********************************************************************** *
 * project: org.matsim.*
 * TransitDijkstra.java
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

package playground.sergioo.singapore2012.transitRouterVariable.old;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.Network;
import org.matsim.core.router.util.DijkstraNodeData;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.PseudoRemovePriorityQueue;
import org.matsim.vehicles.Vehicle;


/**
 * A variant of Dijkstra's algorithm for route finding that supports multiple
 * nodes as start and end. Each start/end node can contain a specific cost
 * component that describes the cost to reach that node to find the least cost
 * path to some place not part of the network.
 *
 * @author mrieser
 */
public class MultiNodeDijkstra {

	private static final Logger log = Logger.getLogger(MultiNodeDijkstra.class);

	/**
	 * Provides an unique id (loop number) for each routing request, so we don't
	 * have to reset all nodes at the beginning of each re-routing but can use the
	 * loop number instead.
	 */
	private int iterationID = Integer.MIN_VALUE + 1;
	
	/**
	 * The network on which we find routes.
	 */
	protected Network network;
	
	/**
	 * The cost calculator. Provides the cost for each link and time step.
	 */
	final TravelDisutility costFunction;

	/**
	 * The travel time calculator. Provides the travel time for each link and time step.
	 */
	final TravelTime timeFunction;
	
	public MultiNodeDijkstra(final Network network, final TravelDisutility costFunction, final TravelTime timeFunction) {
		this.network = network;
		this.costFunction = costFunction;
		this.timeFunction = timeFunction;
	}

	/**
	 * Augments the iterationID and checks whether the visited information in
	 * the nodes in the nodes have to be reset.
	 */
	private synchronized int augmentIterationId(Map<Id<Node>, DijkstraNodeData> nodeData) {
		if (this.iterationID == Integer.MAX_VALUE) {
			this.iterationID = Integer.MIN_VALUE + 1;
			resetNetworkVisited(nodeData);
		} else {
			this.iterationID++;
		}
		return this.iterationID;
	}
	
	/**
	 * Resets all nodes in the network as if they have not been visited yet.
	 */
	private void resetNetworkVisited(Map<Id<Node>, DijkstraNodeData> nodeData) {
		for (Node node : this.network.getNodes().values()) {
			DijkstraNodeData data = getData(node, nodeData);
			data.resetVisited();
		}
	}

	
	public Path calcLeastCostPath(final Map<Node, InitialNode> fromNodes, final Map<Node, InitialNode> toNodes, final Person person) {
		return calcLeastCostPath(fromNodes, toNodes, person, null);
	}
	
	public Path calcLeastCostPath(final Map<Node, InitialNode> fromNodes, final Map<Node, InitialNode> toNodes, final Person person, final Vehicle vehicle) {
		Map<Id<Node>, DijkstraNodeData> nodeData = new HashMap<Id<Node>, DijkstraNodeData>((int)(network.getNodes().size() * 1.1), 0.95f);
		Set<Node> endNodes = new HashSet<Node>(toNodes.keySet());

		int itID = augmentIterationId(nodeData);
		PseudoRemovePriorityQueue<Node> pendingNodes = new PseudoRemovePriorityQueue<Node>(500);
		for (Map.Entry<Node, InitialNode> entry : fromNodes.entrySet()) {
			DijkstraNodeData data = getData(entry.getKey(), nodeData);
			visitNode(entry.getKey(), data, pendingNodes, entry.getValue().initialTime, entry.getValue().initialCost, null, itID);
		}

		// find out which one is the cheapest end node
		double minCost = Double.POSITIVE_INFINITY;
		Node minCostNode = null;

		// do the real work
		while (endNodes.size() > 0) {
			Node outNode = pendingNodes.poll();

			if (outNode == null) {
				// seems we have no more nodes left, but not yet reached all endNodes...
				endNodes.clear();
			} else {
				DijkstraNodeData data = getData(outNode, nodeData);
				boolean isEndNode = endNodes.remove(outNode);
				if (isEndNode) {
					InitialNode initData = toNodes.get(outNode);
					double cost = data.getCost() + initData.initialCost;
					if (cost < minCost) {
						minCost = cost;
						minCostNode = outNode;
					}
				}
				if (data.getCost() > minCost) {
					endNodes.clear(); // we can't get any better now
				} else {
					relaxNode(outNode, null, pendingNodes, person, vehicle, itID, nodeData);
				}
			}
		}

		if (minCostNode == null) {
			log.warn("No route was found");
			return null;
		}
		Node toNode = minCostNode;

		// now construct the path
		List<Node> nodes = new LinkedList<Node>();
		List<Link> links = new LinkedList<Link>();

		nodes.add(0, toNode);
		Link tmpLink = getData(toNode, nodeData).getPrevLink();
		while (tmpLink != null) {
			links.add(0, tmpLink);
			nodes.add(0, tmpLink.getFromNode());
			tmpLink = getData(tmpLink.getFromNode(), nodeData).getPrevLink();
		}
		DijkstraNodeData startNodeData = getData(nodes.get(0), nodeData);
		DijkstraNodeData toNodeData = getData(toNode, nodeData);
		Path path = new Path(nodes, links, toNodeData.getTime() - startNodeData.getTime(), toNodeData.getCost() - startNodeData.getCost());

		return path;
	}
	
	/**
	 * Inserts the given Node n into the pendingNodes queue and updates its time
	 * and cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The node from which we came visiting n.
	 */
	protected void visitNode(final Node n, final DijkstraNodeData data,
			final PseudoRemovePriorityQueue<Node> pendingNodes, final double time, final double cost,
			final Link outLink, int itID) {
		data.visit(outLink, cost, time, itID);
		pendingNodes.add(n, getPriority(data));
	}

	/**
	 * Expands the given Node in the routing algorithm; may be overridden in
	 * sub-classes.
	 *
	 * @param outNode
	 *            The Node to be expanded.
	 * @param toNode
	 *            The target Node of the route.
	 * @param pendingNodes
	 *            The set of pending nodes so far.
	 */
	protected void relaxNode(final Node outNode, final Node toNode, final PseudoRemovePriorityQueue<Node> pendingNodes, final Person person, final Vehicle vehicle, int itID, Map<Id<Node>, DijkstraNodeData> nodeData) {

		DijkstraNodeData outData = getData(outNode, nodeData);
		double currTime = outData.getTime();
		double currCost = outData.getCost();
		for (Link l : outNode.getOutLinks().values()) {
			relaxNodeLogic(l, pendingNodes, currTime, currCost, toNode, null, person, vehicle, itID, nodeData);
		}				
	}
	
	/**
	 * Logic that was previously located in the relaxNode(...) method. 
	 * By doing so, the FastDijkstra can overwrite relaxNode without copying the logic. 
	 */
	/*package*/ void relaxNodeLogic(final Link l, final PseudoRemovePriorityQueue<Node> pendingNodes,
			final double currTime, final double currCost, final Node toNode,
			final PreProcessDijkstra.DeadEndData ddOutData, final Person person, final Vehicle vehicle, int itID, Map<Id<Node>, DijkstraNodeData> nodeData) {
				addToPendingNodes(l, l.getToNode(), pendingNodes, currTime, currCost, toNode, person, vehicle, itID, nodeData);
	}
	
	/**
	 * Adds some parameters to the given Node then adds it to the set of pending
	 * nodes.
	 *
	 * @param l
	 *            The link from which we came to this Node.
	 * @param n
	 *            The Node to add to the pending nodes.
	 * @param pendingNodes
	 *            The set of pending nodes.
	 * @param currTime
	 *            The time at which we started to traverse l.
	 * @param currCost
	 *            The cost at the time we started to traverse l.
	 * @param toNode
	 *            The target Node of the route.
	 * @return true if the node was added to the pending nodes, false otherwise
	 * 		(e.g. when the same node already has an earlier visiting time).
	 */
	protected boolean addToPendingNodes(final Link l, final Node n,
			final PseudoRemovePriorityQueue<Node> pendingNodes, final double currTime,
			final double currCost, final Node toNode, final Person person, final Vehicle vehicle, int itID, Map<Id<Node>, DijkstraNodeData> nodeData) {

		double travelTime = this.timeFunction.getLinkTravelTime(l, currTime, person, vehicle);
		double travelCost = this.costFunction.getLinkTravelDisutility(l, currTime, person, vehicle);
		DijkstraNodeData data = getData(n, nodeData);
		double nCost = data.getCost();
		if (!data.isVisited(itID)) {
			visitNode(n, data, pendingNodes, currTime + travelTime, currCost + travelCost, l, itID);
			return true;
		}
		double totalCost = currCost + travelCost;
		if (totalCost < nCost) {
			revisitNode(n, data, pendingNodes, currTime + travelTime, totalCost, l, itID);
			return true;
		}

		return false;
	}

	/**
	 * Changes the position of the given Node n in the pendingNodes queue and
	 * updates its time and cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The link from which we came visiting n.
	 */
	void revisitNode(final Node n, final DijkstraNodeData data,
			final PseudoRemovePriorityQueue<Node> pendingNodes, final double time, final double cost,
			final Link outLink, int itID) {
		pendingNodes.remove(n);

		data.visit(outLink, cost, time, itID);
		pendingNodes.add(n, getPriority(data));
	}
	
	/**
	 * The value used to sort the pending nodes during routing.
	 * This implementation compares the total effective travel cost
	 * to sort the nodes in the pending nodes queue during routing.
	 */
	private double getPriority(final DijkstraNodeData data) {
		return data.getCost();
	}
	
	public static class InitialNode {
		public final double initialCost;
		public final double initialTime;
		public InitialNode(final double initialCost, final double initialTime) {
			this.initialCost = initialCost;
			this.initialTime = initialTime;
		}
	}
	
	/**
	 * Returns the data for the given node. Creates a new NodeData if none exists
	 * yet.
	 *
	 * @param n
	 *            The Node for which to return the data.
	 * @return The data for the given Node
	 */
	protected DijkstraNodeData getData(final Node n, Map<Id<Node>, DijkstraNodeData> nodeData) {
		DijkstraNodeData r = nodeData.get(n.getId());
		if (null == r) {
			r = new DijkstraNodeData();
			nodeData.put(n.getId(), r);
		}
		return r;
	}

}
