package main.java.web;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketException;

import com.google.gson.Gson;

import main.java.agent.PlayerAgent;
import main.java.environment.Environment;


public class GameSocket extends WebSocketAdapter {
	/** The sessions of all players, mapped to each player's chosen name. */
	private static final Map<Session, String> sessionToName =
		Collections.synchronizedMap(new HashMap<>());
	/** The sessions of all players, mapped to each player agent. */
	private static final Map<Session, PlayerAgent> sessionToPlayerAgent =
		Collections.synchronizedMap(new HashMap<>());
	/** The chosen name of each named player, mapped to session. */
	private static final Map<String, Session> nameToSession =
		Collections.synchronizedMap(new HashMap<>());
	/** The chosen name in lowercase and without spaces, mapped to session. */
	private static final Map<String, Session> shortNameToSession =
		Collections.synchronizedMap(new HashMap<>());
	/** The name in lowercase and without spaces of each spoofed player, mapped to PlayerAgent. */
	private static final Map<String, PlayerAgent> shortNameToSpoofedAgent =
		Collections.synchronizedMap(new HashMap<>());
	private static Environment environment;
	
	private Session session;

	private boolean verbose = true;

	public GameSocket() {
		this(true);
	}

	public GameSocket(boolean verbose) {
		this.verbose = verbose;
		startGame();
	}

	/** The GameSocket is instantiated once for each client, but we should
	  * only instantiate one GameThread. */
	private synchronized void startGame() {
		if (environment == null) {
			// The GameThread needs a reference to any one WebServer so that it can broadcast.
			environment = new Environment(verbose);
			new GameSerializer(this, environment);
		}
	}

	/** When a new client makes a connection to the server. */
	@Override
	public void onWebSocketConnect(Session session) {
		if (verbose) {
			System.out.println("[SERVER] " + session.getRemoteAddress().toString() +
                " has opened a connection.");
		}
		session.getRemote().sendStringByFuture("Connection established.");
		synchronized(sessionToName) {
			sessionToName.put(session, null);
		}
		
		this.session = session;
	}

	/** When a client sends a message to the server. */
	@Override
	public void onWebSocketText(String message) {
		Gson g = new Gson();
		ClientInput input = g.fromJson(message, ClientInput.class);

		// Try to add new player (reject them if the name is duplicated).
		if (input.getName() != null && input.getName() != "") {
			// Check for duplicate names.
			if (shortNameToSession.containsKey(shortenName(input.getName()))) {
				unicast("{\"pregame\":true, \"duplicateName\": true}", session);
				return;  // Not in gameplay yet, so don't proceed to the rest of the method. 
			} else {
				// Name is unique, so we can add the client to the game.
				synchronized(sessionToName) {
					sessionToName.put(session, input.getName());
				}
				synchronized(nameToSession) {
					nameToSession.put(input.getName(), session);
				}
				synchronized(shortNameToSession) {
					shortNameToSession.put(shortenName(input.getName()), session);
				}
				
				PlayerAgent agent = environment.spawnPlayer(input.getName());
				synchronized(sessionToPlayerAgent) {
					sessionToPlayerAgent.put(session, agent);
				}

				// Send the character's ID to the client.
	 			unicast("{\"pregame\":true, \"id\": \"" + agent.getID() + "\"}", session);
				broadcast("<b>" + input.getName() + "</b> joined the game.");
			}
		}

		// handle client chat input
		if (input.getMessage() != null && !input.getMessage().isEmpty()) {
			if (input.getMessage().charAt(0) == '/') {
				// chat input is a command
				Command.handleInput(input.getMessage(), session, this);
			} else {
				broadcast(input.getMessage(), session);
			}
		}

		// Send client's update to the relevant agent entity.
		PlayerAgent agent = sessionToPlayerAgent.get(session);
		if (agent != null) {
			agent.addPlayerEvent(input);
		}
	}

	/** Broadcast text to all connected clients. */
	void broadcast(String message, Session sourceSession) {
		String sourceName = null;
		synchronized(sessionToName) {
			sourceName = sessionToName.get(sourceSession);
		}
		if (sourceName != null && !sourceName.isEmpty()) {
			message = "<strong>" + sourceName + "</strong>: " + message;
		}
		synchronized(sessionToName) {
			for (Session s : sessionToName.keySet()) {
				try {
					if (s.isOpen()) {
						s.getRemote().sendStringByFuture(message);
					}
				} catch (WebSocketException e) {
					System.out.println("The error " + e.getLocalizedMessage() + " occurred. This " 
							+ "can happen if the player dies before a broadcast arrives.");
				}
			}
		}
	}

	void broadcast(String message) {
		broadcast(message, null);
	}

	/** Send text to a single client */
	void unicast(String message, Session session) {
		try {
			if (session.isOpen()) {
				session.getRemote().sendStringByFuture(message);
			}
		} catch (WebSocketException e) {
			System.out.println("The error " + e.getLocalizedMessage() + " occurred. This " 
					+ "can happen if the player dies before a unicast arrives.");
		}
	}

	Session getSessionByName(String username) {
		return nameToSession.get(username);
	}

	Session getSessionByShortName(String username) {
		return shortNameToSession.get(shortenName(username));
	}

	String getNameBySession(Session session) {
		return sessionToName.get(session);
	}

	PlayerAgent getPlayerAgentBySession(Session session) {
		return sessionToPlayerAgent.get(session);
	}

	PlayerAgent getPlayerAgentByName(String username) {
		return sessionToPlayerAgent.get(getSessionByName(username));
	}
	
	PlayerAgent getPlayerAgentByShortName(String username) {
		return sessionToPlayerAgent.get(shortNameToSession.get(shortenName(username)));
	}

	PlayerAgent getSpoofedAgentByName(String username) {
		return shortNameToSpoofedAgent.get(shortenName(username));
	}

	Set<String> getNames() {
		return nameToSession.keySet();
	}

	public Environment getEnvironment() {
		return environment;
	}

	/** Spawns a fake player */
	void spoofPlayer(Point2D.Double point) {
		PlayerAgent spoofedPlayer = environment.spawnPlayer(point);
		shortNameToSpoofedAgent.put(shortenName(spoofedPlayer.getName()), spoofedPlayer);
	}

	/** When a client closes their connection. */
	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		if (verbose) {
			System.out.println("[SERVER] Session " + session.getRemoteAddress().toString() +
                " has ended.");
		}
		broadcast(getNameBySession(session) + " left the game.");
		PlayerAgent agent = sessionToPlayerAgent.remove(session);

		// Despawn player from environment
		environment.despawnPlayerAgent(agent);

		// Remove name from mapping
		String name = sessionToName.remove(session);
		if (name != null) {
			nameToSession.remove(name);
			shortNameToSession.remove(shortenName(name));
		}
	}
	
	/** Removes all whitespace and converts to lowercase. Used to check for duplicate names
	 * and so that players can refer to shortened names in chat commands. */
	private static String shortenName(String name) {
		return name.replaceAll("\\s+", "").toLowerCase();
	}
}
