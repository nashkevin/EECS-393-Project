var webSocket;
var messages = document.getElementById("messages");

var canvas = document.getElementById("gameCanvas");

canvas.addEventListener("click", clickPosition);

var renderer = PIXI.autoDetectRenderer(getGameWidth(), getGameHeight(), {view: canvas});
renderer.backgroundColor = 0x272822;
renderer.autoResize = true;

var stage = new PIXI.Container();
renderer.render(stage);

//console.log(renderer instanceof PIXI.WebGLRenderer); // Successfully using WebGL?

function joinGame() {
	// Ensures only one connection is open at a time
	if(webSocket !== undefined && webSocket.readyState !== WebSocket.CLOSED) {
		writeResponse("WebSocket is already opened.");
		return;
	}
	// Create a new instance of the websocket
	var url = "ws://" + window.location.host + "/socket";
	webSocket = new WebSocket(url);
	 
	// Binds functions to the listeners for the websocket.
	webSocket.onopen = function(event) {
		if(event.data === undefined) 
			return;

		writeResponse(event.data);
	};

	webSocket.onmessage = function(event) {
		writeResponse(event.data);
	};

	webSocket.onclose = function(event) {
		writeResponse("Connection closed");
	};
	
	function completeConnection() {
		var state = webSocket.readyState;
		if (state === webSocket.CONNECTING) {
			setTimeout(completeConnection, 250);
		} else if (state === webSocket.OPEN) {
			// Once connection is established.
			var username = document.getElementById("username").value;
			webSocket.send(JSON.stringify({ 'name': username }));
			
			document.getElementById("pregame").classList.add("hidden");
			document.getElementById("game").classList.remove("hidden");
			onResize();
		} else {
			alert("The connection to the server was closed before it could be established.");
		}
	}
	
	completeConnection();
}

// Sends the value of the text input to the server
function send() {
	var text = document.getElementById("messageinput").value;
	document.getElementById("messageinput").value = "";
	if (text != "") {
		webSocket.send(JSON.stringify({ 'message': text }));
	}
}

function closeSocket() {
	webSocket.close();
}

function writeResponse(text) {
	messages.innerHTML += "<br/>" + text;
	messages.scrollTop = messages.scrollHeight;
}

function preventDefault(e) {
	if (e.preventDefault) e.preventDefault();
}

function getGameHeight() {
	return window.innerHeight - document.getElementById("chat").clientHeight;
}

function getGameWidth() {
	return window.innerWidth;
}

function onResize() {
	renderer.resize(getGameWidth(), getGameHeight());
	render();
}

function render() {
	renderer.render(stage);
	requestAnimationFrame(render);
}

function inGameState() {
	return (typeof webSocket !== "undefined" && webSocket.readyState === webSocket.OPEN);
}
// Key listener
window.onkeydown = function (e) {
	// Ignore key events within text input
	var currentTag = e.target.tagName.toLowerCase();
	if (currentTag == "input" || currentTag == "textarea") {
		return;
	}
	
	if (inGameState()) {
		var code = e.keyCode ? e.keyCode : e.which;
		switch (code) {
			case 87: case 38:  // 'w' or up
				e.preventDefault();
				webSocket.send(JSON.stringify({ 'direction': 'up' }));
				break;
			case 65: case 37: // 'a' or left 
				e.preventDefault();
				webSocket.send(JSON.stringify({ 'direction': 'left' }));
				break;
			case 83: case 40: // 's' or down
				e.preventDefault();
				webSocket.send(JSON.stringify({ 'direction': 'down' }));
				break;
			case 68: case 39: // 'd' or right
				e.preventDefault();
				webSocket.send(JSON.stringify({ 'direction': 'right' }));
				break;
		}
	}
};

//Mouse click listener function
function clickPosition(e) {
	this.focus(); // Move focus to the game canvas
	if (inGameState()) {
		var clickX = e.clientX;
		var clickY = e.clientY;
        var clickAngle = convertClickToAngle(clickX, clickY);

		webSocket.send(JSON.stringify({'clickX': clickX, 'clickY': clickY,
                                       'clickAngle': clickAngle}));
	}
}

/* Returns angle in radians with the following conventions
 *     N: -pi/2		*
 *     E: 	  0		*
 *     S:  pi/2		*
 *     W:    pi     */
function convertClickToAngle(clickX, clickY) {
    var originX = renderer.width / 2;
    var originY = renderer.height / 2;
    
    return Math.atan2(clickY - originY, clickX - originX);
}

window.onresize = onResize;

window.onload = function() {
	document.getElementById("username").select();
}