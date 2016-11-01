package main.java.web;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.Gson;

import main.java.agent.PlayerAgent;
import main.java.environment.Environment;

@ServerEndpoint("/socket") 
public class WebServer {
	private static final int RADIUS = 200;
	
	/** The sessions of all players, mapped to each player's chosen name. */
	private static final Map<Session, String> sessions = Collections.synchronizedMap(new HashMap<Session, String>());
	private Environment environment = new Environment(RADIUS);
	
	/** When a new client makes a connection to the server. */
	@OnOpen
	public void onOpen(Session session) {
		System.out.println(session.getId() + " has opened a connection.");
		try {
			session.getBasicRemote().sendText("Connection established.");
			sessions.put(session, null);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	 
	/** When a client sends a message to the server. */
	@OnMessage
	public void onMessage(String message, Session session) {
		System.out.println("Message from " + session.getId() + ": " + message);
		
		Gson g = new Gson();
		ClientInput input = g.fromJson(message, ClientInput.class);
		
		// Add new player.
		if (input.getName() != null && input.getName() != "") {
			synchronized(sessions) {
				sessions.put(session, input.getName());
				environment.spawnPlayer(input.getName(), session.getId());
			}
		}
		
		// Broadcast chat message.
		if (input.getMessage() != null && !input.getMessage().isEmpty()) {
			broadcast(input.getMessage(), session);
		}
		
		// Broadcast movement (for testing purposes).
		if (input.isMoving()) {
			String direction = "";
			if (input.isUp()) {
				direction += "up ";
			}
			if (input.isDown()) {
				direction += "down ";
			}
			if (input.isLeft()) {
				direction += "left ";
			}
			if (input.isRight()) {
				direction += "right";
			}
			
			broadcast("moved " + direction, session);
		}
		
		// Broadcast click info (for testing purposes).
		if (input.isClicked()) {
			broadcast("clicked on " + input.getPoint() + " at " +
					String.format("%.2f", input.getClickAngle()) +
					" radians.", session);
		}
		
		// Send client's update to the relevant agent entity.
		PlayerAgent agent = environment.getActivePlayerAgents().get(session.getId());
		agent.addPlayerEvent(input);
	}
	
	private void broadcast(String message) {
		broadcast(message, null);
	}

	/** Broadcast text to all connected clients. */
	private void broadcast(String message, Session sourceSession) {
		String sourceName = sessions.get(sourceSession);
		if (sourceName != null && !sourceName.isEmpty()) {
			message = "<strong>" + sourceName + ":</strong> " + message;
		}
		for (Session s: sessions.keySet()) {
			if (s.isOpen()) {
				try {
					s.getBasicRemote().sendText(message);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
	}
 
	/** When a client closes their connection. */
	@OnClose
	public void onClose(Session session) {
		System.out.println("Session " + session.getId() + " has ended.");
		sessions.remove(session);
	}
}