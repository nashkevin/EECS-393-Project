package main.java.web;

import main.java.agent.Agent;
import main.java.agent.PlayerAgent;

import java.awt.geom.Point2D;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.websocket.api.Session;



/** Manages commands that a player can perform by typing in the chat window. */
public enum Command {
	// Pro tip: the order in which the commands are defined
	// is the order they will appear when using "/commands".
	HELP ("help",
			new String[] {"?"},
			"Syntax: /help [command]"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length == 1) {
					// Display generic help info.
					server.unicast("/commands for a list of commands" +
							"<br>/help [command] for description and syntax", session);
				} else if (args.length == 2) {
					// Display help for the specified command.
					Command command = commands.get(args[1]);
					if (command == null) {
						server.unicast("No documentation found for that command.", session);
					} else {
						server.unicast(command.getHelpText(), session);
					}
				} else {
					throw new IllegalArgumentException();
				}
			}
		},
	COMMANDS ("commands",
			null,
			"Lists all available commands.<br>Syntax: /commands"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length > 1) {
					throw new IllegalArgumentException();
				}

				StringBuilder sb = new StringBuilder();
				boolean firstInList = true;

				for (Command command : Command.values()) {
					// Put a comma before every command except the first.
					if (firstInList) {
						firstInList = false;
					} else {
						sb.append(", ");
					}

					// Prepend each command with a slash.
					sb.append("/").append(command.getCommand());

				}
				server.unicast(sb.toString(), session);
			}
		},
	BRING ("bring",
			new String[] {"fetch", "summon"},
			("Brings a player to you or to another player or to coordinates." +
				"<br>Syntax: /bring username [username|x y]")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) throws PlayerNotFoundException {
				PlayerAgent caller = server.getPlayerAgentBySession(session);

				if (args.length == 2) {
					// Bring target to you
					PlayerAgent targetPlayer = getPlayerAgent(server, args[1]);
					targetPlayer.setPosition(caller.getPosition());
				}
				else if (args.length == 3) {
					// Bring first target to second target
					PlayerAgent playerFrom = getPlayerAgent(server, args[1]);
					PlayerAgent playerTo = getPlayerAgent(server, args[2]);
					playerFrom.setPosition(playerTo.getPosition());
				}
				else if (args.length == 4) {
					// Bring target to specified location
					PlayerAgent targetPlayer = getPlayerAgent(server, args[1]);
					
					try {
						int x = Integer.parseInt(args[2]);
						int y = Integer.parseInt(args[3]);
						targetPlayer.setPosition(x, y);
					}
					catch (NumberFormatException ex) {
						server.unicast("Invalid coordinates", session);
					}

				}
			}
		},
	CLEAR ("clear",
			null,
			"Clears your chat window.<br>Syntax: /clear"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length > 1) {
					throw new IllegalArgumentException();
				}
			}
		},
	EXIT ("exit",
			new String[] {"quit"},
			"Disconnects you from the server.<br>Syntax: /exit"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length > 1) {
					throw new IllegalArgumentException();
				}
				session.close();
			}
		},
	GIVEXP ("givexp",
			new String[] {"xp"},
			"Gives a player experience points.<br>Syntax: /givexp [username] [amount]"
		) {
			@Override
			protected void perform(String[] args, Session sourceSession, GameSocket server) throws PlayerNotFoundException {
				PlayerAgent target;
				int pointValue;
				// /givexp -> gives self enough experience points for next level
				if (args.length == 1) {
					target = server.getPlayerAgentBySession(sourceSession);
					target.awardPoints(target.getPointsUntilLevelUp());
				}
				else if (args.length == 2) {
					// /givexp 10 -> gives self 10 experience points
					try {
						pointValue = Integer.parseInt(args[1]);
						target = server.getPlayerAgentBySession(sourceSession);
						target.awardPoints(pointValue);
					}
					// /givexp Player -> gives Player enough experience points for next level
					catch (NumberFormatException ex) {
						target = getPlayerAgent(server, args[1]);
						target.awardPoints(target.getPointsUntilLevelUp());
					}
				}
				// /givexp Player 10 -> gives Player 10 experience points
				else if (args.length == 3) {
					target = getPlayerAgent(server, args[1]);
					try {
						pointValue = Integer.parseInt(args[2]);
						target = getPlayerAgent(server, args[1]);
						target.awardPoints(pointValue);
					}
					catch (NumberFormatException ex) {
						throw new IllegalArgumentException();
					}
				}
				else {
					throw new IllegalArgumentException();
				}
			}
		},
	GOTO ("goto",
			new String[] {"tp"},
			"Sends you to a player or to coordinates.<br>Syntax: /goto [player|x y]"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) throws PlayerNotFoundException {
				PlayerAgent caller = server.getPlayerAgentBySession(session);

				if (args.length == 3) {
					try {
						int x = Integer.parseInt(args[1]);
						int y = Integer.parseInt(args[2]);
						caller.setPosition(x, y);
					}
					catch (NumberFormatException ex) {
						server.unicast("Invalid coordinates", session);
					}
				}
				else if (args.length == 2) {
					PlayerAgent targetPlayer = getPlayerAgent(server, args[1]);
					caller.setPosition(targetPlayer.getPosition());
				}
				else {
					throw new IllegalArgumentException();
				}
			}
		},
	HEAL ("heal",
			null,
			"Heals a player to full health or by a given amount.<br>Syntax: /heal [username] [amount]"
		) {
			@Override
			protected void perform(String[] args, Session sourceSession, GameSocket server) throws PlayerNotFoundException {
				PlayerAgent target;
				int healValue;
				// /heal -> heals self to max health
				if (args.length == 1) {
					target = server.getPlayerAgentBySession(sourceSession);
					target.applyHealing(target.getMaxHealth());
				}
				else if (args.length == 2) {
					// /heal 10 -> heals self by 10
					try {
						healValue = Integer.parseInt(args[1]);
						target = server.getPlayerAgentBySession(sourceSession);
						target.applyHealing(healValue);
					}
					// /heal Player -> heals Player to max health
					catch (NumberFormatException ex) {
						target = getPlayerAgent(server, args[1]);
						target.applyHealing(target.getMaxHealth());
					}
				}
				// /heal Player 10 -> heals Player by 10
				else if (args.length == 3) {
					target = getPlayerAgent(server, args[1]);
					try {
						healValue = Integer.parseInt(args[2]);
						target.applyHealing(healValue);
					}
					catch (NumberFormatException ex) {
						throw new IllegalArgumentException();
					}
				}
				else {
					throw new IllegalArgumentException();
				}
			}
		},
	KICK ("kick",
			null,
			"Disconnects another user from the server.<br>Syntax: /kick username"
		) {
			@Override
			protected void perform(String[] args, Session sourceSession, GameSocket server) throws PlayerNotFoundException {
				if (args.length != 2) {
					throw new IllegalArgumentException();
				}

				String targetName = args[1];
				String sourceName = server.getNameBySession(sourceSession);
				Session targetSession = getSession(server, targetName);
				if (sourceSession.equals(targetSession)) {
					server.unicast("You can't kick yourself. Use /exit instead.", sourceSession);
				} else {
					System.out.println("[SERVER] " + targetName +
                        " was kicked from the game by " + sourceName + ".");
					server.unicast("You were kicked by " + sourceName + ".", targetSession);
					targetSession.close();
				}
			}
		},
	KILL ("kill",
			null,
			"Kills a player.<br>Syntax: /kill username"
		) {
			@Override
			protected void perform(String[] args, Session sourceSession, GameSocket server) {
				if (args.length != 2) {
					throw new IllegalArgumentException();
				}

				String targetName = args[1];
				PlayerAgent target = server.getPlayerAgentByShortName(targetName);
				if (target == null) {
					target = server.getSpoofedAgentByName(targetName);
					if (target == null) {
						server.unicast("Player '" + targetName + "' not found.", sourceSession);
					} else {
						target.applyDamage(target.getMaxHealth());
					}
				} else {
					target.applyDamage(target.getMaxHealth());
				}
			}
		},
	PLAYERS ("players",
			new String[] {"who"},
			"Lists the usernames of everyone playing.<br>Syntax: /players"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length > 1) {
					throw new IllegalArgumentException();
				}
				Set<String> players = server.getNames();
				StringBuilder sb = new StringBuilder();
				sb.append(players.size());
				if (players.size() == 1) {
					sb.append(" player: ");
				} else {
					sb.append(" players: ");
				}
				sb.append(String.join(", ", server.getNames()));
				server.unicast(sb.toString(), session);
			}
		},
	PING ("ping",
			null,
			"Requests a timed response from the server.<br>Syntax: /ping"
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length > 1) {
					throw new IllegalArgumentException();
				}
				server.unicast("PONG", session);
			}
		},
	PM ("pm",
			new String[] {"tell"},
			"Sends a private message to a player.<br>Syntax: /pm username message"
		) {
			@Override
			protected void perform(String[] args, Session sourceSession, GameSocket server) throws PlayerNotFoundException {
				if (args.length <= 2) {
					throw new IllegalArgumentException();
				}

				String sourceName = server.getNameBySession(sourceSession);
				String destinationName = args[1];
				Session destinationSession = getSession(server, destinationName);

				if (sourceSession.equals(destinationSession)) {
					server.unicast("You can't private message yourself.", sourceSession);
				} else {
					StringBuilder msg = new StringBuilder();
					StringBuilder echo = new StringBuilder();
					msg.append("<i>From " + sourceName + ":");
					echo.append("<i>To " + destinationName + ":");
					for (int i = 2; i < args.length; i++) {
						msg.append(" ").append(args[i]);
						echo.append(" ").append(args[i]);
					}
					msg.append("</i>");
					echo.append("</i>");
					server.unicast(msg.toString(), destinationSession);
					server.unicast(echo.toString(), sourceSession);
				}
				
			}
		},
	SAY ("say",
			new String[] {"announce", "broadcast"},
			("Sends a message to all connected players. " +
				"Useful for server announcements.<br>Syntax: /say message")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length < 2) {
					throw new IllegalArgumentException();
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append("<b><i>");
					for (int i = 1; i < args.length; i++)
						sb.append(args[i] + " ");
					sb.append("</i></b>");
					server.broadcast(sb.toString());
				}
			}
		},
	SPAWN ("spawn",
			new String[] {"create"},
			("Spawns an Agent at your location or given coordinates." +
				"<br>Syntax: /spawn (Player|Pulsar|Scout) [x y]")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				PlayerAgent caller = server.getPlayerAgentBySession(session);
				int x = (int) caller.getPosition().getX();
				int y = (int) caller.getPosition().getY();
				if (args.length != 2 && args.length != 4) {
					throw new IllegalArgumentException();
				} else if (args.length == 4) {
					try {
						x = Integer.parseInt(args[2]);
						y = Integer.parseInt(args[3]);
					}
					catch (NumberFormatException ex) {
						server.unicast("Invalid coordinates", session);
					}
				}
				if (args[1].equalsIgnoreCase("player")) {
					server.spoofPlayer(new Point2D.Double(x, y));
				} else if (args[1].equalsIgnoreCase("pulsar")) {
					server.getEnvironment().spawnPulsar(new Point2D.Double(x, y));
				} else if (args[1].equalsIgnoreCase("scout")) {
					server.getEnvironment().spawnScout(new Point2D.Double(x, y));
				}
			}
		},
	STATS ("stats",
			new String[] {"statistics", "status"},
			("Displays your level and attributes or those of another player. " +
				"<br>Syntax: /stats [username]")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) throws PlayerNotFoundException {
				PlayerAgent player;
				StringBuilder sb = new StringBuilder();
				if (args.length == 1) {
					player = server.getPlayerAgentBySession(session);
				}
				else if (args.length == 2) {
					player = getPlayerAgent(server, args[1]);
				}
				else {
					throw new IllegalArgumentException();
				}

				String tab = "&nbsp&nbsp&nbsp&nbsp";
				sb.append("<strong>" + player.getName() + "</strong>");
				sb.append(", Lv. ");
				sb.append(player.getLevel());
				sb.append(" (");
				sb.append(player.getPoints());
				sb.append("/");
				sb.append(PlayerAgent.levelToPoints(player.getLevel() + 1));
				sb.append(" exp)");
				sb.append("<br>" + tab);
				sb.append("Health: ");
				sb.append(player.getHealth());
				sb.append("/");
				sb.append(player.getMaxHealth());
				sb.append("<br>" + tab);
				sb.append("Damage: ");
				sb.append(player.getGun().getDamage());
				sb.append("<br>" + tab);
				sb.append("Bullet Speed: ");
				sb.append(player.getGun().getSpeed());
				sb.append("<br>" + tab);
				sb.append("Firing Speed: ");
				sb.append(String.format("%.2f", 1000.0 / player.getGun().getFiringDelay()));
				sb.append(" shots/sec<br>" + tab);
				sb.append("Haste: ");
				sb.append(String.format("%.2f", player.getHaste() * 100) + "%");

				server.unicast(sb.toString(), session);
			}
		},
	TEAM ("team",
			null,
			("Displays your current team or that of another player, " +
				"or assigns you or that player to a given team. " +
				"<br>Syntax: /team [username] [red|blue]")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) {
				if (args.length == 1) {
					Agent.Team team = server.getPlayerAgentBySession(session).getTeam();
					server.unicast("You are on the " + team.toString() + " team.", session);
				}
				else if (args.length == 2) {
					try {
						// Parse the first argument as a team (to change teams).
						Agent.Team team = Agent.Team.valueOf(args[1].toUpperCase());
						PlayerAgent player = server.getPlayerAgentBySession(session);
						server.getEnvironment().removePlayerFromTeam(player);
						player.setTeam(team);
						server.getEnvironment().addPlayerToTeam(player);
						server.unicast("You were placed on the " + team.toString() + " team.", session);
					} catch (IllegalArgumentException ex) {
						// Parse the first argument as a player (to check the player's team).
						PlayerAgent target = server.getPlayerAgentByShortName(args[1].toLowerCase());
						if (target != null) {
							server.unicast(target.getName() + " is on the " +
								target.getTeam().toString() + " team.", session);
						} else {
							server.unicast("Found no teams or players named '" + args[1] + "'.", session);
						}
					}
				}
				else if (args.length == 3) {
					PlayerAgent target = server.getPlayerAgentByShortName(args[1].toLowerCase());
					if (target != null) {
						try {
							Agent.Team team = Agent.Team.valueOf(args[2].toUpperCase());
							server.getEnvironment().removePlayerFromTeam(target);
							target.setTeam(team);
							server.getEnvironment().addPlayerToTeam(target);
							server.unicast(target.getName() + " was placed on the " +
								team.toString() + " team.", session);
						} catch (IllegalArgumentException ex) {
							server.unicast("There is no '" + args[2] + "' team.", session);
						}
					} else {
						server.unicast("Player '" + args[1] + "' not found.", session);
					}
				}
				else {
					throw new IllegalArgumentException();
				}
			}
		},
	WHERE ("where",
			new String[] {"locate"},
			("Displays your coordinates or the coordinates of another player. " +
				"<br>Syntax: /where [username]")
		) {
			@Override
			protected void perform(String[] args, Session session, GameSocket server) throws PlayerNotFoundException {
				Point2D.Double position = new Point2D.Double();
				if (args.length == 1) {
					position = server.getPlayerAgentBySession(session).getPosition();
					server.unicast("(" +  String.format("%.2f", position.getX()) +
						", " +  String.format("%.2f", position.getY()) +
						")", session);
				}
				else if (args.length == 2) {
					PlayerAgent agent = getPlayerAgent(server, args[1]);
					position = agent.getPosition();
					server.unicast("(" +  String.format("%.2f", position.getX()) +
						", " +  String.format("%.2f", position.getY()) +
						")", session);
				}
				else {
					throw new IllegalArgumentException();
				}
			}
		};


	private String command;
	private String[] aliases;
	private String helpText;

	/**
	 * @param command the primary way to call the command.
	 * @param aliases alternative ways to call the command (can be null).
	 * @param helpText a description of how to use the command. Aliases are automatically appended.
	 */
	private Command(String command, String[] aliases, String helpText) {
		this.command = command;
		this.aliases = aliases;
		this.helpText = helpText;
	}

	/** Returns alternate ways to call the command. Can be null. */
	public String[] getAliases() {
		return aliases;
	}

	/** Returns the primary way to call the command. */
	public String getCommand() {
		return command;
	}

	/** Returns the description of the command, including any aliases. */
	public String getHelpText() {
		if (aliases != null) {
			// If there are aliases, append all command names to the description.
			StringBuilder sb = new StringBuilder();
			sb.append(helpText);
			sb.append("<br>Aliases: ");
			sb.append("/").append(String.join(", /", aliases));
			return sb.toString();
		} else {
			return helpText;
		}
	}

	/** Performs the command. Each command must implement this function. */
	protected abstract void perform(String[] args, Session session, GameSocket server) throws PlayerNotFoundException;



	/** Map of names + aliases to commands. */
	private static Map<String, Command> commands = new HashMap<>();

	static {
		// Populate the commands map.
		for (Command command : Command.values()) {
			commands.put(command.getCommand(), command);
			if (command.getAliases() != null) {
				for (String alias : command.getAliases()) {
					commands.put(alias, command);
				}
			}
		}
	}

	public static void handleInput(String input, Session session, GameSocket server) {
		// split commands into arguments
		String[] args = input.substring(1).trim().split("\\s++");

		if (!args[0].equalsIgnoreCase("clear")) {
			// echo the command to the client's chatbox
			String selfText = "<strong>" + server.getNameBySession(session)
				+ "</strong>: " + input;
			server.unicast(selfText, session);
		}

		parseCommand(args, session, server);
	}

	private static void parseCommand(String[] args, Session session, GameSocket server) {
		Command command = commands.get(args[0].toLowerCase());
		if (command != null) {
			try {
				command.perform(args, session, server);
			} catch (PlayerNotFoundException e) {
				server.unicast(e.getMessage(), session);
			} catch (IllegalArgumentException e) {
				// Incorrect parameters for command
				server.unicast(command.getHelpText(), session);
			}
		} else {
			// Command not found
			server.unicast("Unrecognized command.", session);
		}
	}
	
	/** Gets the PlayerAgent corresponding to the name, or throws PlayerNotFoundException. */
	private static PlayerAgent getPlayerAgent(GameSocket server, String name) throws PlayerNotFoundException {
		PlayerAgent agent = server.getPlayerAgentByShortName(name);
		
		if (agent == null) {
			throw new PlayerNotFoundException("Player '" + name + "' was not found.");
		} else {
			return agent;
		}
	}
	
	/** Gets the Session corresponding to the name, or throws PlayerNotFoundException. */
	private static Session getSession(GameSocket server, String name) throws PlayerNotFoundException {
		Session session = server.getSessionByShortName(name);
		
		if (session == null) {
			throw new PlayerNotFoundException("Player '" + name + "' was not found.");
		} else {
			return session;
		}
	}
}
