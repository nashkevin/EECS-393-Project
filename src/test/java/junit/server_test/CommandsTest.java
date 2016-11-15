package test.java.junit.server_test;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import main.java.web.Command;
import main.java.web.WebServer;

/** Test commands that a player can perform through the chat window. */
public class CommandsTest {
	private static WebServer server;

	@BeforeClass
	public static void beforeClass() {
		server = new WebServer();
	}
	
	@Test
	/** Tests running "/help" without any arguments. */
	public void testHelp() {
		// Create a mock session and connect it to the server.
		MockConnection user = new MockConnection(server, "Test");
		
		String message = " {\"message\":\"/help\"}";
		server.onMessage(message, user.getSession());

		String expectedSubstring = "/help [command]";
		Assert.assertTrue(user.receivedMessage(expectedSubstring));
		
		server.onClose(user.getSession());
	}
	
	@Test
	/** Tests running "/help exit". */
	public void testHelpWithAliases() {
		// Create a mock session and connect it to the server.
		MockConnection user = new MockConnection(server, "Test");
		
		String message = " {\"message\":\"/help exit\"}";
		server.onMessage(message, user.getSession());

		String expectedResult = Command.EXIT.getHelpText();
		Assert.assertTrue(user.receivedMessage(expectedResult));
		
		Assert.assertTrue(user.receivedMessage("Aliases"));
		
		server.onClose(user.getSession());
	}
	
	@Test
	/** Tests running "/commands". */
	public void testCommands() {
		// Create a mock session and connect it to the server.
		MockConnection user = new MockConnection(server, "Test");
		
		String message = " {\"message\":\"/commands\"}";
		server.onMessage(message, user.getSession());

		for (Command command : Command.values()) {
			Assert.assertTrue(user.receivedMessage(command.getCommand()));
		}
		
		server.onClose(user.getSession());
	}
	
	@Test
	/** Test sending a private message to oneself. */
	public void testPmSelf() {
		// Create a mock session and connect it to the server.
		MockConnection user = new MockConnection(server, "Test");
		
		String message = " {\"message\":\"/pm Test hi\"}";
		server.onMessage(message, user.getSession());

		String expectedResponse = "You can't private message yourself";
		Assert.assertTrue(user.receivedMessage(expectedResponse));
		
		server.onClose(user.getSession());
	}
	
	@Test
	/** Test sending a private message. */
	public void testPm() {
		// Create three mock sessions and connect them to the server.
		MockConnection user1 = new MockConnection(server, "A");
		MockConnection user2 = new MockConnection(server, "B");
		MockConnection user3 = new MockConnection(server, "C");
		
		// Send a message from A to B.
		String privateMessage = "Message with multiple words";
		String message = " {\"message\":\"/pm B " + privateMessage + "\"}";
		server.onMessage(message, user1.getSession());

		// Verify that B received the message but C didn't.
		Assert.assertTrue(user2.receivedMessage(privateMessage));
		Assert.assertFalse(user3.receivedMessage(privateMessage));

		server.onClose(user1.getSession());
		server.onClose(user2.getSession());
		server.onClose(user3.getSession());
	}
	
	@Test
	/** Test trying to send a PM with no recipient or message. */
	public void testPmWithInvalidArguments() {
		// Create a mock sessions and connect it to the server.
		MockConnection user = new MockConnection(server, "Test");
		
		String message = " {\"message\":\"/pm\"}";
		server.onMessage(message, user.getSession());

		// Verify that the help info for /pm appeared.
		Assert.assertTrue(user.receivedMessage(Command.PM.getHelpText()));

		server.onClose(user.getSession());
	}
}