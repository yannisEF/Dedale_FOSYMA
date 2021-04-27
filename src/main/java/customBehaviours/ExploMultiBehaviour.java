package customBehaviours;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dataStructures.serializableGraph.SerializableNode;
import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.agents.dummies.ExploreMultiAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentKnowledge;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/**
 * This behaviour allows an agent to explore the environment and learn the associated topological map.
 * The algorithm is a pseudo - DFS computationally consuming because its not optimised at all.</br>
 * 
 * When all the nodes around him are visited, the agent randomly select an open node and go there to restart its dfs.</br> 
 * This (non optimal) behaviour is done until all nodes are explored. </br> 
 * 
 * Warning, this behaviour does not save the content of visited nodes, only the topology.</br> 
 * Warning, this behaviour is a solo exploration and does not take into account the presence of other agents (or well) and indefinitely tries to reach its target node
 * @author hc
 *
 */
public class ExploMultiBehaviour extends OneShotBehaviour {

	private static final long serialVersionUID = 8567689731496787661L;

	/**
	 * Current knowledge of the agent regarding the environment
	 */
	private MapRepresentation myMap;

	/**
	 * Nodes known but not yet visited
	 */
	private ArrayList<String> openNodes;

	/**
	 * Visited nodes
	 */
	private HashSet<String> closedNodes;
	
	private String nextNode;
	private HashMap<String, Integer> decisionToInt;
	
	private BrainBehaviour brain;

	public ExploMultiBehaviour(BrainBehaviour brain) {
		super(brain.getAgent());
		
		this.brain = brain;
		this.decisionToInt = brain.getDecisionToInt();
	}

	@Override
	public void action() {
		if(this.brain.getMap().getMigration()==true) {
			((ExploreMultiAgent)this.myAgent).loadAllMaps();
		}
		
		// Retrieve the current position
		String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();
		
		if (myPosition!=null){
			// New path
			List<String> nextPath = new ArrayList<String>();
			
			String nodeId = ((ExploreMultiAgent)this.myAgent).discover();
			this.openNodes = brain.getOpenNodes();
			this.closedNodes = brain.getClosedNodes();
			this.myMap = brain.getMap();

			//3) while openNodes is not empty, continues.
			if (this.openNodes.isEmpty()){
				this.brain.finishExploration();
			}else{
				//4) select next move.
				// If stuck, go to a node at random
				if (this.brain.isStuck() == true) {
					List<Couple<String,List<Couple<Observation,Integer>>>> lobs=((AbstractDedaleAgent)this.myAgent).observe();
					Collections.shuffle(lobs);
					
					this.nextNode = lobs.get(0).getLeft();
					nextPath.add(myPosition);
					nextPath.add(this.nextNode);
					
					((ExploreMultiAgent)this.myAgent).sayConsole("I am stuck! Moving out of the way to " + this.nextNode);
				}
				
				//4.1 If there exist one open node directly reachable, go for it,
				//	 otherwise choose the closest node from the openNode list and go for it
				
				if (this.nextNode==null){
					// If there exists an open node directly reachable, go for it
					if (nodeId != "") {
						nextNode=nodeId;
						nextPath.add(myPosition);
						nextPath.add(nextNode);
					} else {
						//no directly accessible openNode
						//chose one, compute the path and take the first step.
						ArrayList<List<String>> paths = new ArrayList<List<String>>();
						for (int i = 0; i < this.openNodes.size(); i++) {
							try {
								paths.add(this.myMap.getShortestPath(myPosition, this.openNodes.get(i)));
							} catch (java.lang.IndexOutOfBoundsException | java.lang.NullPointerException e) {
								// No path to node
							}
						}
						Collections.sort(paths, Comparator.comparing(a -> a.size()));
						
						// Giving the exploration priority lexicographically
						int index = 0;
						for (String otherAgent: ((ExploreMultiAgent)this.myAgent).getAgentsAround()) {
							if (this.myAgent.getName().compareTo(otherAgent) == -1)	index ++;
						}
						
						if (index >= paths.size() - 1)	index = paths.size() - 1;
						
						nextPath = paths.get(index);
						this.nextNode = nextPath.get(0);
					}

				}
				
				//((ExploreMultiAgent)this.myAgent).sayConsole("I want to go to " + this.nextNode + " I am following this path : " + nextPath);
				this.brain.setLastPath(nextPath);
				((ExploreMultiAgent)this.myAgent).moveToIntention(this.nextNode, nextPath);
			}
		}		
	}

	@Override
	public int onEnd() {
		this.brain.deregisterState("Exploration");
		this.brain.registerState(new ExploMultiBehaviour(this.brain), "Exploration");
		
		this.brain.registerTransition("Decision", "Exploration", (int) this.decisionToInt.get("Exploration"));
		this.brain.registerTransition("Exploration", "Decision", (int) this.decisionToInt.get("Decision"));
		
		return this.decisionToInt.get("Decision");
	}
}
