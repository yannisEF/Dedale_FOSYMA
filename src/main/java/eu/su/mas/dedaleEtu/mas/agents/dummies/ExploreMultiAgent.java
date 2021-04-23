package eu.su.mas.dedaleEtu.mas.agents.dummies;

import java.util.ArrayList;
import java.util.List;

import CustomBehaviour.DecisionBehaviour;
import CustomBehaviour.ListenBehaviour;
import CustomBehaviour.PingPositionBehaviour;
import CustomBehaviour.ShareMapBehaviour;
import dataStructures.serializableGraph.SerializableSimpleGraph;

import java.util.HashSet;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.startMyBehaviours;
import eu.su.mas.dedaleEtu.mas.behaviours.ExploMultiBehaviour;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentKnowledge;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.domain.AMSService;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;

/**
 * <pre>
 * ExploreSolo agent. 
 * It explore the map using a DFS algorithm.
 * It stops when all nodes have been visited.
 *  </pre>
 *  
 * @author hc
 *
 */

public class ExploreMultiAgent extends AbstractDedaleAgent {

	private static final long serialVersionUID = -6431752665590433727L;
	
	// Knowledge about the other agents
	private List<AgentKnowledge> agentsKnowledge = new ArrayList<AgentKnowledge>();
	
	// Name of the application's other agents
	private List<String> agentNames;
	
	// Maps of all the agents
	private MapRepresentation myMap;
	private List<MapRepresentation> agentsMaps = new ArrayList<MapRepresentation>();

	// Current destination of the agent
	private String intentions;
	
	// Agents detected in the vicinity with their last known position
	private ArrayList<String> agentsAround = new ArrayList<String>();
	
	// An agent's listen and ping behaviours, stored here to be able to stop and resume them at will
	private PingPositionBehaviour pingPositionBehaviour;
	private ListenBehaviour listenBehaviour;
	
	// Agents with whom the agent already has communicated during a meeting
	private boolean onGoingMeeting = false;
	private ArrayList<String> alreadyCommunicated;
	
	// Information about current loading state
	private boolean loaded = false;
	
	// Ignored cell for hunting
	public ArrayList<String> labord = new ArrayList<String>();
	
	/**
	 * This method is automatically called when "agent".start() is executed.
	 * Consider that Agent is launched for the first time. 
	 * 			1) set the agent attributes 
	 *	 		2) add the behaviours
	 *          
	 */
	protected void setup(){

		super.setup();
	
	    /**
	     * Get the other agents' names
	     */
		this.agentNames = getAgentsList();
		List<Behaviour> lb=new ArrayList<Behaviour>();
		
		/************************************************
		 * 
		 * ADD the initial behaviours of the Agent here
		 * 
		 ************************************************/
		
		// Instantiating all the maps, serializing them
		this.myMap = new MapRepresentation("me");
		
		for (String agentName: this.agentNames) {
			this.agentsKnowledge.add(new AgentKnowledge(agentName));
		}
		this.serializeAllMaps();
		
		// Ping behaviour : let other agents know where I am
		this.pingPositionBehaviour = new PingPositionBehaviour(this);
		lb.add(this.pingPositionBehaviour);
		// Listen behaviour : know where the other agents are
		this.listenBehaviour = new ListenBehaviour(this);
		lb.add(this.listenBehaviour);
		// Decision behaviour : decides what happens next
		lb.add(new DecisionBehaviour(this, this.myMap));
		
		/***
		 * MANDATORY TO ALLOW YOUR AGENT TO BE DEPLOYED CORRECTLY
		 */
		
		
		addBehaviour(new startMyBehaviours(this,lb));
		
		System.out.println("the  agent "+this.getLocalName()+ " is started");

	}
	
	public void sayConsole(String content) {
		// Prints a new line in the console
		System.out.println(this.getLocalName() + ": " + content);
	}
	
	public void serializeAllMaps() {
		// Prepare all maps for migration
		this.loaded = false;
		
		this.myMap.prepareMigration();
		
		for(AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			otherKnowledge.map.prepareMigration();
		}
	}
	public void loadAllMaps() {
		// Restore all maps from migrated state
		this.myMap.loadSavedData();
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			otherKnowledge.map.loadSavedData();
			otherKnowledge.setLastPosition(this.getCurrentPosition());
		}
		this.loaded = true;
	}
	
	public List <String> getAgentsList(){
		// Get the list of all the agents through the yellow pages
		AMSAgentDescription[] agentsDescriptionCatalog = null;
		List <String> agentsNames= new ArrayList<String>();
		try {
			SearchConstraints c = new SearchConstraints();
			c.setMaxResults(new Long(-1));
			agentsDescriptionCatalog = AMSService.search(this, new
					AMSAgentDescription(), c);
		}
		catch (Exception e) {
			System.out.println("Problem searching AMS: " + e );
			e.printStackTrace();
		}
		for (int i=0; i<agentsDescriptionCatalog.length; i++){
			AID agentID = agentsDescriptionCatalog[i].getName();
			agentsNames.add(agentID.getLocalName());
		}
		return agentsNames;
	}
	
	public String getIntention() {
		return this.intentions;
	}
	public void setIntention(String newIntention) {
		this.intentions = newIntention;
	}
	
	public AgentKnowledge getMyKnowledge(String otherName) {
		// Return the knowledge about the given agent
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			if (otherKnowledge.getName().equals(otherName)) {
				return otherKnowledge;
			}
		}
		return null;
	}
	public void updateOthersIgnorance() {
		// Increments the difference in known edges and known nodes after a step from myPosition to newNode
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			otherKnowledge.map.updateIgnorance(this.myMap);
		}
	}
	
	public List<String> getAgentsAround() {
		return this.agentsAround;
	}
	public void addAgentsAround(String newAgent, String newPosition, long timeStamp) {
		AgentKnowledge otherKnowledge = this.getMyKnowledge(newAgent);
		// Checking if the information is relevant
		if (otherKnowledge.getMostRecentTime() < timeStamp) {
			// Add an agent to the vicinity and updates its last position
			if (!this.agentsAround.contains(newAgent)) {
				this.agentsAround.add(newAgent);
				otherKnowledge.addNbEncounters(1);
			}
			
			otherKnowledge.setMostRecentTime(timeStamp);
			otherKnowledge.setLastPosition(newPosition);
			this.sayConsole(newAgent + " last known position is now " + newPosition);
		}
		
		// Agent might be in an unknown location, still needs to add update to the "me" map
		//
	}
	public void removeAgentsAround(String otherAgent) {
		this.agentsAround.remove(otherAgent);
	}
	
	public void clearSurroundings(int maxDistance) {
		// If an agent is too far, it is removed from the surroundings
		String myPosition = this.getCurrentPosition();
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			int distance = 0;
			try {
				distance = this.myMap.getShortestPath(myPosition, otherKnowledge.getLastPosition()).size();
			} catch (NullPointerException e) {
				distance = -1;
			}
			
			if (distance > maxDistance || distance == 0) {
				this.removeAgentsAround(otherKnowledge.map.getOwner());
			}
		}
	}
	
	public void addAgentName(String newAgent) {
		this.agentNames.add(newAgent);
	}
	public void removeAgentName(String otherAgent) {
		this.agentNames.remove(otherAgent);
	}
	
	public List<String> getAgentsNames(){
		return this.agentNames;
	}
	public PingPositionBehaviour getPingBehaviour() {
		return this.pingPositionBehaviour;
	}
	public ListenBehaviour getListenBehaviour() {
		return this.listenBehaviour;
	}
	
	public void updateMap(String otherAgent, SerializableSimpleGraph<String, MapAttribute> otherSg) {
		this.sayConsole("I'm updating my map with that of " + otherAgent);
		// Update the "me" map
		this.myMap.fuseMap(otherSg);
		
		// Update the other agent's last known map
		AgentKnowledge otherKnowledge = this.getMyKnowledge(otherAgent);
		otherKnowledge.map.fuseMap(this.myMap.getSg());
		this.updateOthersIgnorance();
	}
	
	public void addDiffNodes(int increment) {
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			otherKnowledge.map.addDiffNodes(increment);
		}
	}
	public void addDiffEdges(int increment) {
		for (AgentKnowledge otherKnowledge: this.agentsKnowledge) {
			otherKnowledge.map.addDiffEdges(increment);
		}
	}
	
	public List<String> getAlreadyCommunicated(){
		return this.alreadyCommunicated;
	}
	
	public void addAlreadyCommunicated(String otherAgent) {
		if (!this.alreadyCommunicated.contains(otherAgent)) {
			this.alreadyCommunicated.add(otherAgent);
		}
	}
	
	public void removeAlreadyCommunicated(String otherAgent) {
		this.alreadyCommunicated.remove(otherAgent);
	}
	
	public void resetAlreadyCommunicated() {
		this.alreadyCommunicated = new ArrayList<String>();
	}
	
	public boolean getOnGoingMeeting() {
		return this.onGoingMeeting;
	}
	public void setOnGoingMeeting(boolean newTruth) {
		this.onGoingMeeting = newTruth;
	}
	
	public void endMeeting() {
		this.setOnGoingMeeting(false);
		this.resetAlreadyCommunicated();
	}
	
	public boolean isLoaded() {
		return this.loaded;
	}
}
