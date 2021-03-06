package main.java.environment;

import main.java.agent.Agent;
import main.java.agent.NPCAgent;
import main.java.agent.PlayerAgent;
import main.java.agent.Pulsar;
import main.java.agent.Scout;

import main.java.projectile.Projectile;

import java.awt.geom.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class Environment {
	/** Radius of the environment, in pixels. An arbitrary number. */
	private static final double RADIUS = 4000;
	/** The ideal ratio of NPCAgents to PlayerAgents */
	private static final int NPC_PLAYER_RATIO = 50;
	/** The ideal ratio of Scouts to Pulsars */
	private static final int SCOUT_PULSAR_RATIO = 25;
	/** The frame rate, in Hz */
	private static final int FRAME_RATE = 40;
	/** A random number generator **/
	private static final Random random = new Random();
	
	private int environmentLevel = 1;
	private boolean gameplayOccurring = true;
	private boolean verbose = true;

	private Set<PlayerAgent> activePlayerAgents;
	private Set<NPCAgent> activeNPCAgents;
	private Set<Projectile> activeProjectiles;

	private Set<PlayerAgent> recentlyDespawnedPlayerAgents;
	private Set<NPCAgent> recentlyDespawnedNPCAgents;
	private Set<Projectile> recentlyDespawnedProjectiles;

	private Set<PlayerAgent> redPlayers;
	private Set<PlayerAgent> bluePlayers;

	private Timer timer = new Timer("Environment Timer");

	public Environment() {
		this(true);
	}
	
	public Environment(boolean verbose) {
		this.verbose = verbose;

		activePlayerAgents = Collections.newSetFromMap(new ConcurrentHashMap<PlayerAgent, Boolean>());
		activeNPCAgents = Collections.newSetFromMap(new ConcurrentHashMap<NPCAgent, Boolean>());
		activeProjectiles = Collections.newSetFromMap(new ConcurrentHashMap<Projectile, Boolean>());
		
		recentlyDespawnedPlayerAgents = Collections.newSetFromMap(new ConcurrentHashMap<PlayerAgent, Boolean>());
		recentlyDespawnedNPCAgents = Collections.newSetFromMap(new ConcurrentHashMap<NPCAgent, Boolean>());
		recentlyDespawnedProjectiles = Collections.newSetFromMap(new ConcurrentHashMap<Projectile, Boolean>());

		redPlayers = Collections.newSetFromMap(new ConcurrentHashMap<PlayerAgent, Boolean>());
		bluePlayers = Collections.newSetFromMap(new ConcurrentHashMap<PlayerAgent, Boolean>());


		// call update at FRAME_RATE
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (gameplayOccurring) {
					update();
				}
			}
		}, 0, 1000 / FRAME_RATE);
	}

	public double getRadius(){
		return RADIUS;
	}

	public Set<PlayerAgent> getActivePlayerAgents() {
		return this.activePlayerAgents;
	}

	public Set<NPCAgent> getActiveNPCAgents() {
		return this.activeNPCAgents;
	}

	public Set<Projectile> getActiveProjectiles() {
		return this.activeProjectiles;
	}
	
	/** Gets the player agents who were despawned since the last time this method was called. */
	public synchronized Set<PlayerAgent> getRecentlyDespawnedPlayerAgents() {
		Set<PlayerAgent> agents = new HashSet<>(recentlyDespawnedPlayerAgents);
		recentlyDespawnedPlayerAgents.clear();
		return agents;
	}

	/** Gets the NPC agents that were despawned since the last time this method was called. */
	public synchronized Set<NPCAgent> getRecentlyDespawnedNPCAgents() {
		Set<NPCAgent> agents = new HashSet<>(recentlyDespawnedNPCAgents);
		recentlyDespawnedNPCAgents.clear();
		return agents;
	}

	/** Gets the projectiles that were despawned since the last time this method was called. */
	public synchronized Set<Projectile> getRecentlyDespawnedProjectiles() {
		Set<Projectile> projectiles = new HashSet<>(recentlyDespawnedProjectiles);
		recentlyDespawnedProjectiles.clear();
		return projectiles;
	}

	public Agent.Team getSmallestTeam(){
		if (redPlayers.size() < bluePlayers.size()) {
			return Agent.Team.RED;
		} else {
			return Agent.Team.BLUE;
		}
	}

	/** Takes a PlayerAgent and adds them to the roster of a team*/
	public void addPlayerToTeam(PlayerAgent player) {
		if (player.getTeam() == Agent.Team.RED) {
			redPlayers.add(player);
		} else if (player.getTeam() == Agent.Team.BLUE) {
			bluePlayers.add(player);
		}
	}

	/** Removes a PlayerAgent from the roster of its team */
	public void removePlayerFromTeam(PlayerAgent player) {
		if (player.getTeam() == Agent.Team.RED) {
			redPlayers.remove(player);
		} else if (player.getTeam() == Agent.Team.BLUE) {
			bluePlayers.remove(player);
		}
	}

	/** Despawns a NPCAgent */
	public void despawnNPCAgent(NPCAgent agent) {
		activeNPCAgents.remove(agent);
		recentlyDespawnedNPCAgents.add(agent);
		// agent = null;
		if (verbose) {
			System.out.println("[ENVIRO] Despawned NPC: " +
				agent.getClass().getSimpleName());
		}
	}

	/** Despawns a PlayerAgent */
	public void despawnPlayerAgent(PlayerAgent agent) {
		if (agent != null) {
			activePlayerAgents.remove(agent);
			recentlyDespawnedPlayerAgents.add(agent);
			removePlayerFromTeam(agent);
			decimate();
			if (verbose) {
				System.out.println("[ENVIRO] Despawned Player: \"" +
					agent.getName() + "\"");
			}
		}

	}

	/** Despawns a projectile */
	public void despawnProjectile(Projectile projectile) {
		activeProjectiles.remove(projectile);
		recentlyDespawnedProjectiles.add(projectile);
	}

	/** Spawns a playable character entity. */
	public PlayerAgent spawnPlayer(String name) {
		Point2D.Double point = randomPlayerSpawn();
		PlayerAgent player = new PlayerAgent(this, point, name,
			getSmallestTeam());
		activePlayerAgents.add(player);
		addPlayerToTeam(player);		
		updateEnvironmentLevel();
		if (verbose) {
			System.out.println("[ENVIRO] Spawned Player: \"" +
				player.getName() + "\" at (" + (int)point.getX() + ", " +
				(int)point.getY() + ")");
		}
		return player;
	}

	/** Spawns a playable character entity at a specified coordinate */
	public PlayerAgent spawnPlayer(Point2D.Double point) {
		PlayerAgent player = new PlayerAgent(this, point, "Player" +
			String.format("%04d", random.nextInt(10000)), getSmallestTeam());
		activePlayerAgents.add(player);
		addPlayerToTeam(player);
		if (verbose) {
			System.out.println("[ENVIRO] Spawned Player: \"" +
				player.getName() + "\" at (" + (int)point.getX() + ", " +
				(int)point.getY() + ")");
		}
		return player;
	}

	/** Spawns a Scout-type NPCAgent of a random level at a random location */
	public Scout spawnScout() {
		return spawnScout(randomNPCSpawn());
	}

	/** Spawns a Scout-type NPCAgent of a random level at a specified coordinate */
	public Scout spawnScout(Point2D.Double point) {
		return spawnScout(point, generateLevel(point));
	}

	/** Spawns a Scout-Type NPCAgent of a specified level at a specified coordinate */
	public Scout spawnScout(Point2D.Double point, int level) {
		Scout agent = new Scout(this, point, level);
		activeNPCAgents.add(agent);
		if (verbose) {
			System.out.println("[ENVIRO] Spawned NPC: Level " + level +
				" Scout at (" + (int)point.getX() + ", " + (int)point.getY() +
				")");
		}
		return agent;
	}

	/** Spawns a Pulsar-type NPCAgent of a random level at a random location */
	public Pulsar spawnPulsar() {
		return spawnPulsar(randomNPCSpawn());
	}

	/** Spawns a Pulsar-type NPCAgent of a random level at a specified location */
	public Pulsar spawnPulsar(Point2D.Double point) {
		return spawnPulsar(point, generateLevel(point));
	}

	/** Spawns a Pulsar-type NPCAgent of a specified level at a specified location */
	public Pulsar spawnPulsar(Point2D.Double point, int level) {
		Pulsar agent = new Pulsar(this, point, level);
		activeNPCAgents.add(agent);
		if (verbose) {
			System.out.println("[ENVIRO] Spawned NPC: Level " + level +
				" Pulsar at (" + (int)point.getX() + ", " +
				(int)point.getY() + ")");
		}
		return agent;
	}

	/** Calculates a level for new NPCAgents based on the environmentLevel */
	public int generateLevel() {
		int level = (int) Math.round(random.nextGaussian() * 2 + environmentLevel);
		/** Level must be an integer value greater than 0 */
		if (level < 1) {
			level = 1;
		}
		/** Level cannot exceed 100 regardless of environmentLevel */
		else if (level > 100) {
			level = 100;
		}
		return level;
	}

	/** Calculates a level for new NPCAgents based on the environmentLevel
	 *  and distance from the center */
	public int generateLevel(Point2D.Double point) {
		double distanceScale = 1 - checkRadius(point) / RADIUS;
		int generatedLevel = (int) Math.round(generateLevel() * distanceScale);
		return Math.max(generatedLevel, 1);
	}

	/** Changes environmentLevel to reflect the average player level */
	public void updateEnvironmentLevel() {
		double avgPlayerLevel = 0;
		for (PlayerAgent a : getActivePlayerAgents()) {
			avgPlayerLevel += a.getLevel();
		}
		avgPlayerLevel /= getActivePlayerAgents().size();
		environmentLevel = (int) Math.round(avgPlayerLevel);
	}

	/** Creates a new projectile */
	public void addProjectile(Projectile p) {
		activeProjectiles.add(p);
	}

	/** Converts polar coordinates to Cartesian coordinates */
	public static Point2D.Double polarToCartesian(double angle, double radius) {
		Point2D.Double p = new Point2D.Double();
		double x = Math.cos(angle) * radius;
		double y = Math.sin(angle) * radius;
		p.setLocation(x, y);
		return p;
	}

	/** Returns a point where the first value is the angle and the second is the radius */
	public static Point2D.Double cartesianToPolar(Point2D.Double p) {
		Point2D.Double polar = new Point2D.Double();
		polar.x = Math.atan2(p.getY(), p.getX());
		polar.y = checkRadius(p);
		return polar;
	}

	/** Returns the distance from the center of the environment */
	public static double checkRadius(Point2D.Double p){
		return Math.sqrt(Math.abs(p.getX() * p.getX()) + Math.abs(p.getY() * p.getY()));
	}

	/** returns the PlayerAgent nearest to the given Agent */
	public PlayerAgent getNearestPlayer(Agent source) {
		return getNearestPlayer(source, Double.MAX_VALUE);
	}

	/** returns the PlayerAgent nearest to the given Agent,
	 *  if there is one within the given range */
	public PlayerAgent getNearestPlayer(Agent source, double range) {
		PlayerAgent nearestPlayer = null;
		double minDistance = range;
		for (PlayerAgent player : activePlayerAgents) {
			double distance = player.getPosition().distance(source.getPosition());
			if (distance <= minDistance && !source.equals(player)) {
				minDistance = distance;
				nearestPlayer = player;
			}
		}
		return nearestPlayer;
	}

	/** Checks collisions between projectiles and agent entities and returns an array of agents hit */
	public ArrayList<Agent> checkCollision(Projectile p) {
		ArrayList<Agent> collisions = new ArrayList<Agent>();
		double projectileSize = 33 * p.getSize();

		// NPCAgents can't damage each other
		if (p.getOwner() instanceof PlayerAgent) {
			for (Agent a : getActiveNPCAgents()) {
				if (a.getTeam() != p.getOwner().getTeam() &&
					a.getPosition().distance(p.getPosition()) < projectileSize * a.getSize()) {
					collisions.add(a);
				}
			}
		}

		for (Agent a : getActivePlayerAgents()) {
			if (// target and shooter are on different teams
				(a.getTeam() != p.getOwner().getTeam()) && 
				// the shot actually hits
				(a.getPosition().distance(p.getPosition()) < projectileSize * a.getSize())) {
					collisions.add(a);
			}
		}

		return collisions;
	}

	/** Creates a polar coordinate for the location of a new PlayerAgent spawn on the perimeter of the arena and returns it as a cartesian coordinate */
	private Point2D.Double randomPlayerSpawn() {
		double angle = Math.random() * 2 * Math.PI;
		return polarToCartesian(angle, getRadius());
	}

	/** Creates a polar coordinate for the location of a new NPCAgent spawn in the interior of the arena and returns it as cartesian coordinate */
	private Point2D.Double randomNPCSpawn() {
		double angle = Math.random() * 2 * Math.PI;
		double distance = Math.random() * getRadius();
		return polarToCartesian(angle, distance);
	}

	/** Calls each entity's update method 
	* Spawns a new Scout-type NPCAgent if the NPC:player ratio is too low
	* Despawns max health NPCAgents if the NPC:player ratio is too high */
	private void update() {
		for (PlayerAgent agent : getActivePlayerAgents()) {
			agent.update();
		}
		for (NPCAgent agent : getActiveNPCAgents()) {
			agent.update();
		}
		for (Projectile p : getActiveProjectiles()) {
			p.update();
		}
		int spawnCounter = 1;
		while (getActiveNPCAgents().size() <
			(NPC_PLAYER_RATIO * getActivePlayerAgents().size())) {
			if (spawnCounter % SCOUT_PULSAR_RATIO == 0) {
				spawnPulsar();
			} else {
				spawnScout();
			}
			spawnCounter++;
		}
	}

	private void decimate() {
		int numVictims = ((int)(1.25 * NPC_PLAYER_RATIO * getActivePlayerAgents().size()) - getActiveNPCAgents().size());
		int counter = 0;
		for(NPCAgent agent : getActiveNPCAgents()){
			if(agent.getHealth() == agent.getMaxHealth() && getNearestPlayer(agent, getRadius()/3) == null) {
				despawnNPCAgent(agent);
				counter++;
			}
			if(counter == numVictims)
				break;
		}
	}

	public boolean isGameplayOccurring() {
		return gameplayOccurring;
	}

	public void setGameplayOccurring(boolean gameplayOccurring) {
		this.gameplayOccurring = gameplayOccurring;
	}
}
