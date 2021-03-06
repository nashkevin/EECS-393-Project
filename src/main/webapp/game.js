/* Table of Contents

//1. Constants, variables, etc.
//2. Event Listeners
//3. Utility Methods
//4. Network Communication
//5. Animation
*/

//1. Constants, variables, etc.

/** Variables related to server-client connection */
const INPUT_RATE = 30; // maximum number of inputs per second
/** Radius of game plus leeway (otherwise, agents could half-way cross the barrier). */
const RADIUS = 4000 + 30;
var webSocket;
var playerAgentID;  // ID referring to this player in the serialized game state
var clientInput = {};  // represents the current input of the player
var messages = document.getElementById("messages");

/** Contains game objects drawn on the screen, indexed by UUID */
var gameEntities = {};
/** Contains game objects that are fading out of existence. They no longer receive
positions from the server, but we still should keep them in the right position. */
var fadingEntities = new Set();

var canvas = document.getElementById("gameCanvas");

/** Coordinates of the player (used to calculate background tile position) */
var playerX = getGameWidth() / 2;
var playerY = getGameHeight() / 2;

/** Timestamp initialized when a ping request is given */
var pingStartTime;

/** Renderer setup */
var renderer = PIXI.autoDetectRenderer(getGameWidth(), getGameHeight(), {view: canvas});
renderer.backgroundColor = "0x272822";
renderer.autoResize = true;
const healthBackgroundColor = "0xBB5E5B";
const healthForegroundColor = "0x85B36B";
const pointsForegroundColor = "0xFFEE70";
const pointsBackgroundColor = getBorderColor(pointsForegroundColor);

/** Stage and background setup */
var stage = new PIXI.Container();
var bg = new PIXI.Texture.fromImage("images/background.png",
	scaleMode = PIXI.SCALE_MODES.LINEAR);
var bgTile = new PIXI.extras.TilingSprite(bg, 1920, 1080);
bgTile.position.set(0, 0);
bgTile.tilePosition.set(0, 0);
stage.addChild(bgTile);

/** Barrier for game arena boundary. */
var barrier = new PIXI.Graphics();
stage.addChild(barrier);

//2. Event Listeners

/** Page selects the username entry form automatically */
window.onload = function() {
	document.getElementById("username").select();
}

/** Prevent accidental right clicks from interrupting gameplay */
document.oncontextmenu = function() {
	return false;
}

function resize() {
	renderer.resize(getGameWidth(), getGameHeight());
	renderer.render(stage);
}
window.onresize = resize;


// Key down listener
window.onkeydown = function(e) {
	// Ignore key events within text input
	var currentTag = e.target.tagName.toLowerCase();
	if (currentTag == "input" || currentTag == "textarea") {
		return;
	}

	if (connectedToGame()) {
		var code = e.keyCode ? e.keyCode : e.which;
		switch (code) {
			case 32: // spacebar
				e.preventDefault();
				startFiring(e);
				break;
			case 87: case 38: // 'w' or up
				e.preventDefault();
				clientInput.up = true;
				delete clientInput.down;
				break;
			case 65: case 37: // 'a' or left
				e.preventDefault();
				clientInput.left = true;
				delete clientInput.right;
				break;
			case 83: case 40: // 's' or down
				e.preventDefault();
				clientInput.down = true;
				delete clientInput.up;
				break;
			case 68: case 39: // 'd' or right
				e.preventDefault();
				clientInput.right = true;
				delete clientInput.left;
				break;
			case 13:          // enter
				e.preventDefault();
				document.getElementById("messageInput").focus();
				stopAllMovement();
				break;
			case 191:         // forward slash
				e.preventDefault();
				document.getElementById("messageInput").focus();
				document.getElementById("messageInput").value = "/";
				stopAllMovement();
				break;
		}
	}
};

// Key up listener
window.onkeyup = function(e) {
	// Ignore key events within text input
	var currentTag = e.target.tagName.toLowerCase();
	if (currentTag == "input" || currentTag == "textarea") {
		return;
	}

	if (connectedToGame()) {
		var code = e.keyCode ? e.keyCode : e.which;
		switch (code) {
			case 32: // spacebar
				e.preventDefault();
				stopFiring(e);
				break;
			case 87: case 38:  // 'w' or up
				e.preventDefault();
				delete clientInput.up;
				break;
			case 65: case 37: // 'a' or left
				e.preventDefault();
				delete clientInput.left;
				break;
			case 83: case 40: // 's' or down
				e.preventDefault();
				delete clientInput.down;
				break;
			case 68: case 39: // 'd' or right
				e.preventDefault();
				delete clientInput.right;
				break;
		}
	}
};

/** EventListeners for mouse interaction with the canvas */
canvas.addEventListener("mousedown", startFiring);
document.addEventListener("mouseup", stopFiring);
canvas.addEventListener("mousemove", trackAngle);

// Action when the user fires a projectile by clicking with the mouse
function startFiring(e) {
	this.focus(); // Move focus to the game canvas
	if ((e.button == 0 || e.keyCode == 32) && connectedToGame()) {
		trackAngle(e);
		clientInput.isFiring = true;
	}
}

function stopFiring(e) {
	delete clientInput.isFiring;
	delete clientInput.angle;
}

function trackAngle(e) {
	if (connectedToGame()) {
		clientInput.angle = coordinateToAngle(e.clientX, e.clientY);
	}
}


//3. Utility Methods

// Adds text as a new line to the chat area.
function addMessageToChat(text) {
	messages.innerHTML += "<br/>" + text;
	messages.scrollTop = messages.scrollHeight;
}

// Gets the height available for the game canvas.
function getGameHeight() {
	return window.innerHeight - document.getElementById("chat").clientHeight;
}

// Gets the width available for the game canvas.
function getGameWidth() {
	return window.innerWidth;
}

// Stop movement of the player agent.
function stopAllMovement() {
	delete clientInput.up;
	delete clientInput.left;
	delete clientInput.down;
	delete clientInput.right;
}

/* Returns angle in radians with the following conventions
 *     N: -pi/2     *
 *     E:     0     *
 *     S:  pi/2     *
 *     W:    pi     */
function coordinateToAngle(x, y) {
	var x_origin = renderer.width / 2;
	var y_origin = renderer.height / 2;

	return Math.atan2(y - y_origin, x - x_origin);
}

/* Returns a darker shade of the input color */
function getBorderColor(hex) {
	var rgb = {
		r: parseInt(hex.substring(2, 4), 16),
		g: parseInt(hex.substring(4, 6), 16),
		b: parseInt(hex.substring(6, 8), 16)
	};

	var hsv = rgbToHsv(rgb);
	hsv.s = Math.min(hsv.s * 2.9, 1);
	hsv.v *= 0.75;
	rgb = hsvToRgb(hsv);

	rgb.r = padString(rgb.r.toString(16), "0", 2);
	rgb.g = padString(rgb.g.toString(16), "0", 2);
	rgb.b = padString(rgb.b.toString(16), "0", 2);

	return "0x" + rgb.r + rgb.g + rgb.b;
}

function rgbToHsv(rgb) {
	// Red
	var r = rgb.r / 255;
	// Green
	var g = rgb.g / 255;
	// Blue
	var b = rgb.b / 255;

	// The maximum color value
	var Cmax = Math.max(r, g, b);
	// The minimum color value
	var Cmin = Math.min(r, g, b);
	// Distance between max and min colors
	var delta = Cmax - Cmin;

	var hsv = {h: 0, s: 0, v: 0}

	// Determine hue
	if (delta == 0) {
		hsv.h = 0;
	}
	else if (Cmax == r) {
		hsv.h = 60 * (((g - b) / delta) % 6);
	}
	else if (Cmax == g) {
		hsv.h = 60 * (((b - r) / delta) + 2);
	}
	else if (Cmax == b) {
		hsv.h = 60 * (((r - g) / delta) + 4);
	}

	// Determine saturation
	hsv.s = (Cmax == 0) ? 0 : delta / Cmax;

	// Determine value
	hsv.v = Cmax;

	return hsv;
}

function hsvToRgb(hsv) {
	// Hue
	var h = hsv.h / 60;
	// Chroma
	var c = hsv.v * hsv.s;
	// Middle color value
	var x = c * (1 - Math.abs(h % 2 - 1));
	// Matching variable
	var m = hsv.v - c;

	var rgb = {r: 0, g: 0, b: 0};
	switch (Math.floor(h)) {
		case 0:
			rgb = {r: c, g: x, b: 0};
			break;
		case 1:
			rgb = {r: x, g: c, b: 0};
			break;
		case 2:
			rgb = {r: 0, g: c, b: x};
			break;
		case 3:
			rgb = {r: 0, g: x, b: c};
			break;
		case 4:
			rgb = {r: x, g: 0, b: c};
			break;
		case 5:
			rgb = {r: c, g: 0, b: x};
			break;
		default:
			rgb = {r: 0, g: 0, b: 0};
	}

	rgb.r = Math.round((rgb.r + m) * 255);
	rgb.g = Math.round((rgb.g + m) * 255);
	rgb.b = Math.round((rgb.b + m) * 255);

	return rgb;
}

/** Left pads a string with the character until it reaches the total length. */
function padString(string, padChar, length) {
	while (string.length < length) {
		string = padChar + string;
	}
	return string;
}

/** Converts a Point2D object (from geometry library) to a PIXI Point. */
function geoToPixiPoint(geoPoint) {
	return new PIXI.Point(geoPoint.x, geoPoint.y);
}

/** Comparison function for sorting 2D coordinates. */
function comparePoints(a, b) {
	return coordinateToAngle(a.x, a.y) - coordinateToAngle(b.x, b.y);
}


//4. Network Communication

// Receives a JSON object and updates the screen or does whatever else as necessary.
function parseJson(json) {
	if (json.pregame) {
		// Set the ID of the corresponding player agent on the server end
		// so this client knows which agent it is when updating the screen.
		if (json.id) {
			playerAgentID = json.id;
			startGamePlay();
		} else if (json.duplicateName) {
			alert("That name is already in use! Please choose another one.");
		}
	} else {
		updateStage(json);
	}
}

// Returns a boolean of whether or not the client is connected to the game server.
function connectedToGame() {
	return (typeof webSocket !== "undefined" && webSocket.readyState === webSocket.OPEN);
}

/** Wait until the connection is established and submit the chosen username. */
function submitUsername() {
	var state = webSocket.readyState;
	if (state === webSocket.CONNECTING) {
		setTimeout(submitUsername, 250);
	} else if (state === webSocket.OPEN) {
		// Once connection is established

		// Send username to the server
		username = document.getElementById("username").value.trim();
		webSocket.send(JSON.stringify({ 'name': username }));
	} else {
		alert("The connection to the server was closed before it could be established.");
	}
}

// Connects to the WebSocket.
function joinGame() {
	// Ensures only one connection is open at a time
	if (webSocket !== undefined && webSocket.readyState !== WebSocket.CLOSED) {
		if (playerAgentID == null) {
			// The player ID is unset, so we're retrying for a username.
			submitUsername();
		} else {
			// The player ID is already set, so we are already in gameplay.
			addMessageToChat("WebSocket is already opened.");
		}
		return;
	}
	// Create a new instance of the websocket
	var url = "ws://" + window.location.host + "/socket";
	webSocket = new WebSocket(url);

	/** Binds functions to the listeners for the websocket */
	webSocket.onopen = function(e) {
		if (e.data === undefined){
			return;
		}
		addMessageToChat(e.data);
	};

	/** Handle messages that are received from the server */
	webSocket.onmessage = function(e) {
		try {
			// First try parsing it as JSON.
			var json = JSON.parse(e.data);
			parseJson(json);
		} catch (error) { // Display non-JSON messages to the chat area
			// If input is invalid JSON, treat it as plain text.
			if (error instanceof SyntaxError) {
				// If the server is responding to a ping request
				if (e.data === "PONG") {
					addMessageToChat(Date.now() - pingStartTime + " ms");
				} else {
					addMessageToChat(e.data);
				}
			} else {
				throw error;
			}
		}
	};

	/** Handle closing the connection */
	webSocket.onclose = function(e) {
		addMessageToChat("Connection closed.");
	};

	submitUsername();
}

function startGamePlay() {
	// Change the view from welcome screen to the main game screen
	document.getElementById("pregame").classList.add("hidden");
	document.getElementById("game").classList.remove("hidden");

	resize();
	sendFrameInput();
}

/** Sends the client's input to the server. Runs each frame. */
function sendFrameInput() {
	if (connectedToGame()) {
		// Schedule the next frame.
		setTimeout(sendFrameInput, 1000 / INPUT_RATE);

		// Send any input to the server.
		json = JSON.stringify(clientInput);
		if (json !== "{}") {
			webSocket.send(json);
		}

		if (!clientInput.isFiring) {
			delete clientInput.angle;
		}
	}
}

// Sends the value of the text input to the server.
function sendChatMessage() {
	var text = document.getElementById("messageInput").value;
	document.getElementById("messageInput").value = "";
	if (text != "") {
		if (text.toLowerCase() === "/clear") {
			messages.innerHTML = "";
			messages.scrollTop = messages.scrollHeight;
		}
		pingStartTime = Date.now();
		webSocket.send(JSON.stringify({ "message": text }));
	}
	document.getElementById("gameCanvas").focus();
}

function closeSocket() {
	webSocket.close();
	window.location.reload();
}

function gameOver() {
	alert("You lost! Play again?");
	closeSocket();
}


//5. Animation

/** Updates entities on the screen, using a JSON object. */
function updateStage(json) {
	var playerAgents = json.playerAgents;
	var npcAgents = json.npcAgents;
	var projectiles = json.projectiles;

	var despawnedPlayerAgents = json.despawnedPlayerAgents;
	var despawnedNPCAgents = json.despawnedNPCAgents;
	var despawnedProjectiles = json.despawnedProjectiles;

	// Get the player agent corresponding to this client
	var thisPlayer = null;
	for (var i = 0; i < playerAgents.length; i++) {
		var agent = playerAgents[i];
		if (agent.id === playerAgentID) {
			thisPlayer = agent;
			break;
		}
	}


	// Iterate through despawned player agents
	for (var i = 0; i < despawnedPlayerAgents.length; i++) {
		var player = gameEntities[despawnedPlayerAgents[i].id];
		fadeOut(player);

		if (despawnedPlayerAgents[i].id === playerAgentID) {
			gameOver();
		}
	}

	// Iterate through despawned NPC agents
	for (var i = 0; i < despawnedNPCAgents.length; i++) {
		var npc = gameEntities[despawnedNPCAgents[i].id];
		fadeOut(npc);
	}

	// Iterate through despawned projectiles
	for (var i = 0; i < despawnedProjectiles.length; i++) {
		var projectile = gameEntities[despawnedProjectiles[i].id];
		if (projectile !== undefined) {
			fadeOutAndShrink(projectile);
		}
	}

	if (thisPlayer === null) {
		return;
	}

	// Iterate through player agents
	for (var i = 0; i < playerAgents.length; i++) {
		setScreenCoordinates(playerAgents[i], thisPlayer);
		var player = drawPlayer(playerAgents[i]);

		player.visible = isOnScreen(player);
	}

	// Iterate through NPC agents
	for (var i = 0; i < npcAgents.length; i++) {
		setScreenCoordinates(npcAgents[i], thisPlayer);
		var npcAgent = drawNpc(npcAgents[i]);

		npcAgent.visible = isOnScreen(npcAgent);
	}

	// Iterate through projectiles
	for (var i = 0; i < projectiles.length; i++) {
		setScreenCoordinates(projectiles[i], thisPlayer);
		var projectile = drawProjectile(projectiles[i]);

		projectile.visible = isOnScreen(projectile);
	}

	// Update the background tile position based on how far the player moved.
	var bgOffsetX = thisPlayer.x - playerX;
	var bgOffsetY = thisPlayer.y - playerY;
	playerX = thisPlayer.x;
	playerY = thisPlayer.y;

	bgTile._tint = calculateTint(thisPlayer.x, thisPlayer.y);

	bgTile.tilePosition.x -= bgOffsetX;
	bgTile.tilePosition.y += bgOffsetY;

	for (let entity of fadingEntities) {
		entity.x -= bgOffsetX;
		entity.y += bgOffsetY;
	}

	updateBarrier();

	renderer.render(stage);
}

function calculateTint(x, y) {
	dec = Math.round(255 * Math.sqrt(x * x + y * y) / (RADIUS - 30)) + 125;
	dec = Math.min(dec, 255);
	hex = padString(dec.toString(16), "0", 2);
	return "0x" + hex + hex + hex;
}

/** Redraw the game boundary on screen. */
function updateBarrier() {
	barrier.clear();

	// Calculate intersections between the browser screen (rectangle) and game
	// environment (circle), using screen coordinates.
	var center = new Point2D(-playerX + getGameWidth()/2, playerY + getGameHeight()/2);
	var r1 = new Point2D(0, 0); // top left of rectangle
	var r2 = new Point2D(getGameWidth(), getGameHeight()); // bottom right of rectangle
	var intersections = Intersection.intersectCircleRectangle(center, RADIUS, r1, r2);

	// If there are two points, the barrier crosses the screen and should be drawn.
	if (intersections.points.length == 2) {
		// Convert intersected points to PIXI points.
		var points = intersections.points.map(geoToPixiPoint);
		// Add corners of the screen that are outside of the game arena.
		points = points.concat(getOutsideCorners(center, RADIUS));
		// Sort points by polar angle so that the polygon is correct.
		points = points.sort(comparePoints);

		// Draw polygon of the area outside of the game arena.
		barrier.alpha = 0.5;
		barrier.lineStyle();
		barrier.beginFill(0x000000);
		barrier.drawPolygon(points);
		barrier.endFill();
	}
}

/** Determine which corners of the screen are outside of the game arena. */
function getOutsideCorners(center, radius) {
	// Coordinates relative to the screen.
	var corners = [
		new PIXI.Point(0, 0),
		new PIXI.Point(getGameWidth(), 0),
		new PIXI.Point(0, getGameHeight()),
		new PIXI.Point(getGameWidth(), getGameHeight())
	];

	return corners.filter(function(point) {
		// Coordinates relative to the center of the game arena.
		var gameX = point.x - center.x;
		var gameY = point.y - center.y;

		// Compare (a^2 + b^2) to the squared radius of the game arena.
		return (Math.pow(gameX, 2) + Math.pow(gameY, 2)) >= Math.pow(radius, 2);
	});
}

/** Calculate the coordinates of the entity in relation to the canvas screen.
thisPlayer is used as the center of the screen. */
function setScreenCoordinates(entity, thisPlayer) {
	var x_offset = entity.x - thisPlayer.x;
	var y_offset = -(entity.y - thisPlayer.y);
	entity.screen_x = getGameWidth() / 2 + x_offset;
	entity.screen_y = getGameHeight() / 2 + y_offset;
}

/** Create or update a player agent on screen. */
function drawPlayer(playerObject) {
	if (!gameEntities[playerObject.id]) {
		createPlayer(playerObject);
	}
	return updatePlayer(playerObject);
}

/** Create a new player agent on screen. */
function createPlayer(playerObject) {
	// Create the container, which contains all components of the player avatar
	var playerContainer = new PIXI.Container();

	// Create the sprite that represents the player itself
	var playerSprite = new PIXI.Sprite(getPlayerTexture(playerObject.color));
	playerSprite.anchor.set(2/3, 0.5);
	playerSprite.pivot.set(2/3, 0.5);
	playerSprite.position.set(0, 0);

	playerContainer.addChild(playerSprite);

	// Create the player's username tag
	var playerName = new PIXI.Text(playerObject.name, {
		fontSize: 12 + (16 - playerObject.name.length),
		align: "center",
		fill: "#F8F8F2",
		dropShadow: true,
		dropShadowColor: "#222222",
		dropShadowDistance: 5,
		stroke: "#131411",
		strokeThickness: 2
	});
	playerName.anchor.set(0.5, 0.5);
	playerName.position = playerSprite.position;
	playerName.position.x += 4;
	playerName.position.y += 50;

	playerContainer.addChild(playerName);

	// Create the health bar background (red)
	var healthBackground = new PIXI.Graphics();
	healthBackground.lineStyle(2, healthBackgroundColor, 1);
	healthBackground.moveTo(0, 0);
	healthBackground.lineTo(50, 0);
	healthBackground.pivot.set(0, 0);
	healthBackground.position = playerSprite.position;
	healthBackground.position.x -= 25;
	healthBackground.position.y -= 40;

	playerContainer.addChild(healthBackground);

	// Create the health bar foreground (green)
	var healthForeground = new PIXI.Graphics();
	healthForeground.lineStyle(2, healthForegroundColor, 1);
	healthForeground.moveTo(0, 0);
	healthForeground.lineTo(50, 0);
	// healthForeground.anchor.set(0, 0);
	healthForeground.pivot.set(0, 0);
	healthForeground.position = playerSprite.position;
	healthForeground.position.x -= 25;
	healthForeground.position.y -= 40;

	playerContainer.addChild(healthForeground);

	if (playerObject.id === playerAgentID) {
		// move health bar to make room for points bar
		healthForeground.position.y -= 5;
		healthBackground.position.y -= 5;

		// Create the points bar background
		var pointsBackground = new PIXI.Graphics();
		pointsBackground.lineStyle(2, pointsBackgroundColor, 1);
		pointsBackground.moveTo(0, 0);
		pointsBackground.lineTo(50, 0);
		pointsBackground.pivot.set(0, 0);
		pointsBackground.position = playerSprite.position;
		pointsBackground.position.x -= 25;
		pointsBackground.position.y -= 40;

		playerContainer.addChild(pointsBackground);

		// Create the points bar foreground
		var pointsForeground = new PIXI.Graphics();
		pointsForeground.lineStyle(2, pointsForegroundColor, 1);
		pointsForeground.moveTo(0, 0);
		pointsForeground.lineTo(50, 0);
		pointsForeground.pivot.set(0, 0);
		pointsForeground.position = playerSprite.position;
		pointsForeground.position.x -= 25;
		pointsForeground.position.y -= 40;

		playerContainer.addChild(pointsForeground);
	}

	playerContainer.position.set(0, 0);
	stage.addChild(playerContainer);

	gameEntities[playerObject.id] = playerContainer;
	return playerContainer;
}

/** Create the primitive shape that will be used as the texture for the sprite */
function getPlayerTexture(color) {
	var playerShape = new PIXI.Graphics();
	playerShape.lineStyle(4, getBorderColor(color), 1);
	playerShape.beginFill(color);
	playerShape.drawPolygon([
		0,  25,
		50, 50,
		50, 0,
		0,  25
	]);
	playerShape.endFill();

	return renderer.generateTexture(playerShape);
}

/** Update the position and rotation of the player agent. */
function updatePlayer(playerObject) {
	var playerContainer = gameEntities[playerObject.id];
	playerContainer.position.set(playerObject.screen_x, playerObject.screen_y);
	var playerSprite = playerContainer.getChildAt(0);
	playerSprite.rotation = playerObject.angle + Math.PI;
	var healthForeground = playerContainer.getChildAt(3);
	var healthPercent = playerObject.health / playerObject.maxHealth;
	healthForeground.scale.set(healthPercent, 1);
	if (playerObject.id === playerAgentID) {
		var pointsForeground = playerContainer.getChildAt(5);
		var pointsPercent = playerObject.points /
			(playerObject.points + playerObject.pointsLeft);
		pointsForeground.scale.set(pointsPercent, 1);
	}

	playerSprite.texture = getPlayerTexture(playerObject.color);

	return playerContainer;
}


/** Create or update an NPC agent on screen. */
function drawNpc(npcObject) {
	if (!gameEntities[npcObject.id]) {
		createNpc(npcObject);
	}
	return updateNpc(npcObject);
}

/** Create a new NPC agent on screen. */
function createNpc(npcObject) {
	// Create the container, which contains all components of the NPC avatar
	var npcContainer = new PIXI.Container();

	// Create the primitive shape that will be used as the texture for the sprite
	var npcShape = new PIXI.Graphics();
	npcShape.lineStyle(4 * (1 / npcObject.size), getBorderColor(npcObject.color), 1)
	npcShape.beginFill(npcObject.color);
	npcShape.drawPolygon([
		0,  25,
		50, 50,
		50, 0,
		0,  25
	]);
	npcShape.endFill();

	// Create the sprite that represents the NPC itself
	var npcSprite = new PIXI.Sprite(renderer.generateTexture(npcShape));
	npcSprite.anchor.set(2/3, 0.5);
	npcSprite.pivot.set(2/3, 0.5);
	npcSprite.position.set(0, 0);
	npcSprite.scale.set(npcObject.size);

	npcContainer.addChild(npcSprite);

	// Create the health bar background (red)
	var healthBackground = new PIXI.Graphics();
	healthBackground.lineStyle(2, healthBackgroundColor, 1);
	healthBackground.moveTo(0, 0);
	healthBackground.lineTo(50, 0);
	healthBackground.pivot.set(0, 0);
	healthBackground.position = npcSprite.position;
	healthBackground.position.x -= 25;
	healthBackground.position.y -= 40 * npcObject.size;

	npcContainer.addChild(healthBackground);

	// Create the health bar foreground (green)
	var healthForeground = new PIXI.Graphics();
	healthForeground.lineStyle(2, healthForegroundColor, 1);
	healthForeground.moveTo(0, 0);
	healthForeground.lineTo(50, 0);
	healthForeground.pivot.set(0, 0);
	healthForeground.position = npcSprite.position;
	healthForeground.position.x -= 25;
	healthForeground.position.y -= 40 * npcObject.size;

	npcContainer.addChild(healthForeground);

	npcContainer.position.set(0, 0);

	stage.addChild(npcContainer);

	gameEntities[npcObject.id] = npcContainer;
	return npcContainer;
}

/** Update the position and rotation of the NPC agent. */
function updateNpc(npcObject) {
	var npcContainer = gameEntities[npcObject.id];
	npcContainer.position.set(npcObject.screen_x, npcObject.screen_y);
	var npcSprite = npcContainer.getChildAt(0);
	npcSprite.rotation = npcObject.angle + Math.PI;
	var healthForeground = npcContainer.getChildAt(2);
	var healthPercent = npcObject.health / npcObject.maxHealth;
	healthForeground.scale.set(healthPercent, 1);

	return npcContainer;
}

/** Create or update a projectile on screen. */
function drawProjectile(projectileObject) {
	if (!gameEntities[projectileObject.id]) {
		createProjectile(projectileObject);
	}
	return updateProjectile(projectileObject);
}

/** Create a new projectile on screen. */
function createProjectile(projectileObject) {
	// Create the primitive shape that will be used as the texture for the sprite
	var projectileShape = new PIXI.Graphics();
	projectileShape.lineStyle(4, getBorderColor(projectileObject.color), 1);
	projectileShape.beginFill(projectileObject.color);
	projectileShape.drawCircle(0, 0, 7 * projectileObject.size); // x, y, r (x and y will be set later)
	projectileShape.endFill();

	// Create the sprite that represents the player itself
	var projectileSprite = new PIXI.Sprite(renderer.generateTexture(projectileShape));
	projectileSprite.anchor.set(0.5, 0.5);
	projectileSprite.pivot.set(0.5, 0.5);
	projectileSprite.position.set(0, 0);

	projectileSprite.position.set(0, 0);
	stage.addChild(projectileSprite);

	gameEntities[projectileObject.id] = projectileSprite;
	return projectileSprite;
}

/** Update the position and rotation of the player agent. */
function updateProjectile(projectileObject) {
	var projectileSprite = gameEntities[projectileObject.id];
	projectileSprite.position.set(projectileObject.screen_x, projectileObject.screen_y);

	return projectileSprite;
}

function fadeOut(entity, stepSize = 0.05) {
	if (!fadingEntities.has(entity)) {
		fadingEntities.add(entity);
	}

	entity.alpha -= stepSize;
	if (entity.alpha > 0) {
		requestAnimationFrame(function() {
			fadeOut(entity, stepSize);
		});
	} else {
		entity.visible = false;
		fadingEntities.delete(entity);
	}
}

function fadeOutAndShrink(entity, stepSize = 0.05) {
	if (!fadingEntities.has(entity)) {
		fadingEntities.add(entity);
	}

	entity.alpha -= stepSize;
	var nextScale = entity.scale.x * (1 - stepSize)
	entity.scale.set(nextScale);
	if (entity.alpha > 0) {
		requestAnimationFrame(function() {
			fadeOutAndShrink(entity, stepSize);
		});
	} else {
		entity.visible = false;
		fadingEntities.delete(entity);
	}
}

function isOnScreen(pixiObject) {
	var leftBorder = pixiObject.x - pixiObject.width / 2;
	var rightBorder = pixiObject.x + pixiObject.width / 2;
	var topBorder = pixiObject.y - pixiObject.height / 2;
	var bottomBorder = pixiObject.y + pixiObject.height / 2;

	return rightBorder > 0 && leftBorder < getGameWidth() &&
			bottomBorder > 0 && topBorder < getGameHeight();
}
