package webserver;

import org.json.JSONObject;

import commands.wins.wins;
import datautil.DBUtil;
import datawrapper.Player;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class LinkWebServer {

	private static volatile HttpServer server;
	private static String apiSecret;
	private static int port;
	private static final Object lock = new Object();

	public static void start() {
		synchronized (lock) {
			if (server != null) {
				System.out.println("[LinkAPI] REST API server already running");
				return;
			}

			// Get configuration from environment variables
			apiSecret = System.getenv("LOSTCRMANAGER_API_SECRET");
			String portEnv = System.getenv("LOSTCRMANAGER_PORT");

			// Parse port with validation
			try {
				port = (portEnv != null && !portEnv.isEmpty()) ? Integer.parseInt(portEnv) : 7070;
				if (port < 1 || port > 65535) {
					System.err.println("[LinkAPI] Invalid port number: " + port + ". Using default port 7070.");
					port = 7070;
				}
			} catch (NumberFormatException e) {
				System.err.println("[LinkAPI] Invalid port format: " + portEnv + ". Using default port 7070.");
				port = 7070;
			}

			System.out.println("[LinkAPI] Initializing REST API server...");
			System.out.println("[LinkAPI] Target port: " + port);

			if (apiSecret == null || apiSecret.isEmpty()) {
				System.out.println("[LinkAPI] Warning: LOSTCRMANAGER_API_SECRET not set. API authentication disabled.");
			} else {
				System.out.println("[LinkAPI] API authentication: enabled");
			}

			try {
				// Create HttpServer instance
				System.out.println("[LinkAPI] Creating HttpServer instance...");
				server = HttpServer.create(new InetSocketAddress(port), 0);

				// Use default executor (creates a thread pool)
				server.setExecutor(null);

				System.out.println("[LinkAPI] Registering endpoints...");

				// Health check endpoint
				server.createContext("/api/health", new HealthCheckHandler());

				// Link endpoint
				server.createContext("/api/link", new LinkRequestHandler());

				// Search linked players endpoint (for autocomplete)
				server.createContext("/api/search-linked", new SearchLinkedHandler());

				// Remove player endpoint
				server.createContext("/api/remove", new RemovePlayerHandler());

				System.out.println("[LinkAPI] Starting server on port " + port + "...");

				// Start the server
				server.start();

				// If we get here, the server started successfully
				System.out.println("========================================");
				System.out.println("[LinkAPI] ✓ REST API server RUNNING");
				System.out.println("[LinkAPI] ✓ Listening on port: " + port);
				System.out.println("[LinkAPI] ✓ Health check: http://localhost:" + port + "/api/health");
				System.out.println("[LinkAPI] ✓ Link endpoint: http://localhost:" + port + "/api/link");
				System.out.println("========================================");

			} catch (Exception e) {
				System.err.println("========================================");
				System.err.println("[LinkAPI] ✗ FAILED TO START SERVER!");
				System.err.println("[LinkAPI] Error: " + e.getClass().getName());
				System.err.println("[LinkAPI] Message: " + e.getMessage());
				System.err.println("========================================");
				e.printStackTrace();
				server = null;

				// Provide helpful diagnostic info
				System.err.println("\n[LinkAPI] Troubleshooting tips:");
				System.err.println("  1. Check if port " + port + " is already in use: lsof -i :" + port);
				System.err.println("  2. Try a different port: export LOSTCRMANAGER_PORT=8080");
				System.err.println("  3. Check firewall settings");
				System.err.println("  4. Ensure you have permission to bind to the port");
			}
		}
	}

	public static void stop() {
		synchronized (lock) {
			if (server != null) {
				System.out.println("[LinkAPI] Stopping REST API server...");
				// Stop with 2 second delay to allow ongoing exchanges to complete
				server.stop(2);
				server = null;
				System.out.println("[LinkAPI] REST API server stopped");
			}
		}
	}

	/**
	 * Validate Bearer token from Authorization header
	 */
	private static boolean validateAuth(HttpExchange exchange) {
		if (apiSecret == null || apiSecret.isEmpty()) {
			return true; // No auth required if secret not set
		}

		List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
		if (authHeaders == null || authHeaders.isEmpty()) {
			return false;
		}

		String authHeader = authHeaders.get(0);
		if (!authHeader.startsWith("Bearer ")) {
			return false;
		}

		String token = authHeader.substring(7);
		return apiSecret.equals(token);
	}

	/**
	 * Send JSON response
	 */
	private static void sendJsonResponse(HttpExchange exchange, int statusCode, JSONObject response)
			throws IOException {
		byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, responseBytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(responseBytes);
		}
	}

	/**
	 * Read request body as string
	 */
	private static String readRequestBody(HttpExchange exchange) throws IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
			return br.lines().collect(Collectors.joining("\n"));
		}
	}

	/**
	 * Handler for GET /api/health
	 */
	private static class HealthCheckHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			System.out.println("[LinkAPI] Health check request from " + exchange.getRemoteAddress());

			try {
				// Check method
				if (!"GET".equals(exchange.getRequestMethod())) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Method not allowed");
					sendJsonResponse(exchange, 405, error);
					return;
				}

				JSONObject response = new JSONObject();
				response.put("status", "ok");
				response.put("service", "lostcrmanager");
				response.put("port", port);

				sendJsonResponse(exchange, 200, response);

			} catch (Exception e) {
				System.err.println("[LinkAPI] Error handling health check: " + e.getMessage());
				e.printStackTrace();

				JSONObject error = new JSONObject();
				error.put("success", false);
				error.put("error", "Internal server error");
				sendJsonResponse(exchange, 500, error);
			}
		}
	}

	/**
	 * Handler for POST /api/link
	 */
	private static class LinkRequestHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			System.out.println("[LinkAPI] Link request received from " + exchange.getRemoteAddress());

			try {
				// Check method
				if (!"POST".equals(exchange.getRequestMethod())) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Method not allowed");
					sendJsonResponse(exchange, 405, error);
					return;
				}

				// Check authentication
				if (!validateAuth(exchange)) {
					System.out.println("[LinkAPI] Authentication failed for POST /api/link");
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Missing or invalid Authorization header");
					sendJsonResponse(exchange, 401, error);
					return;
				}

				// Parse request body
				String body = readRequestBody(exchange);
				if (body == null || body.trim().isEmpty()) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Request body is required");
					sendJsonResponse(exchange, 400, error);
					return;
				}

				JSONObject requestData;
				try {
					requestData = new JSONObject(body);
				} catch (Exception e) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Invalid JSON in request body");
					sendJsonResponse(exchange, 400, error);
					return;
				}

				// Extract parameters
				String tag = requestData.optString("tag", null);
				String userId = requestData.optString("userId", null);
				String source = requestData.optString("source", "unknown");

				// Validate required parameters
				if (tag == null || tag.isEmpty() || userId == null || userId.isEmpty()) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Missing required parameters: tag and userId");
					if (tag != null) {
						error.put("tag", tag);
					}
					sendJsonResponse(exchange, 400, error);
					return;
				}

				// Normalize tag (add # if missing)
				if (!tag.startsWith("#")) {
					tag = "#" + tag;
				}
				tag = tag.replaceAll("O", "0").toUpperCase(); // Replace O with 0 and uppercase

				final String finalTag = tag;
				final String finalUserId = userId;
				final String finalSource = source;

				System.out.println(
						"[LinkAPI] Request from " + finalSource + ": tag=" + finalTag + ", userId=" + finalUserId);

				// Execute link logic
				try {
					Player p = new Player(finalTag);

					// Check if player account exists via CR API
					if (!p.AccExists()) {
						System.out.println("[LinkAPI] Failed to link " + finalTag + ": Player not found or API error");
						JSONObject error = new JSONObject();
						error.put("success", false);
						error.put("error", "Player not found or API error");
						error.put("tag", finalTag);
						sendJsonResponse(exchange, 400, error);
						return;
					}

					// Check if player is already linked
					if (p.IsLinked()) {
						String linkedUserId = p.getUser().getUserID();
						System.out.println(
								"[LinkAPI] Failed to link " + finalTag + ": Already linked to user " + linkedUserId);
						JSONObject error = new JSONObject();
						error.put("success", false);
						error.put("error", "Player already linked to another user");
						error.put("tag", finalTag);
						error.put("linkedUserId", linkedUserId);
						sendJsonResponse(exchange, 400, error);
						return;
					}

					// Get player name from API
					String playerName = null;
					try {
						playerName = p.getNameAPI();
					} catch (Exception e) {
						System.err.println("[LinkAPI] Error getting player name: " + e.getMessage());
						e.printStackTrace();
					}

					// Insert into database
					DBUtil.executeUpdate("INSERT INTO players (cr_tag, discord_id, name) VALUES (?, ?, ?)", finalTag,
							finalUserId, playerName);

					// Save initial wins data asynchronously
					Thread saveWinsThread = new Thread(() -> {
						wins.savePlayerWins(finalTag);
					});
					saveWinsThread.setDaemon(true);
					saveWinsThread.start();

					// Build success response
					System.out.println("[LinkAPI] Successfully linked " + finalTag + " to user " + finalUserId
							+ " (source: " + finalSource + ")");
					JSONObject response = new JSONObject();
					response.put("success", true);
					response.put("tag", finalTag);
					response.put("userId", finalUserId);
					response.put("playerName", playerName);
					response.put("playerInfo", playerName + " (" + finalTag + ")");
					response.put("source", finalSource);
					response.put("message", "Player successfully linked");

					if (p.getClanDB() != null) {
						System.out.println("[LinkAPI] Player " + p.getInfoStringAPI()
								+ " already in a Clan. Cannot be added to Waitlist (again)" + " (source: " + finalSource
								+ ")");
						response.put("waitlistsuccess", false);
					} else {
						// Add to waitlist
						DBUtil.executeUpdate(
								"INSERT INTO clan_members (player_tag, clan_tag, clan_role) VALUES (?, ?, ?)", finalTag,
								"warteliste", "member");
						System.out.println("[LinkAPI] Player " + p.getInfoStringAPI()
								+ " added to Waitlist after linking" + " (source: " + finalSource + ")");
						response.put("waitlistsuccess", true);
					}

					sendJsonResponse(exchange, 200, response);

				} catch (Exception e) {
					System.err.println("[LinkAPI] Failed to link " + finalTag + ": " + e.getMessage());
					e.printStackTrace();
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Internal server error");
					error.put("tag", finalTag);
					sendJsonResponse(exchange, 500, error);
				}

			} catch (Exception e) {
				System.err.println("[LinkAPI] Error handling link request: " + e.getMessage());
				e.printStackTrace();
				JSONObject error = new JSONObject();
				error.put("success", false);
				error.put("error", "Internal server error");
				sendJsonResponse(exchange, 500, error);
			}
		}
	}

	/**
	 * Handler for GET /api/search-linked?q=&lt;query&gt;
	 * Returns linked players matching the query for autocomplete.
	 */
	private static class SearchLinkedHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			System.out.println("[LinkAPI] Search-linked request from " + exchange.getRemoteAddress());

			try {
				if (!"GET".equals(exchange.getRequestMethod())) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Method not allowed");
					sendJsonResponse(exchange, 405, error);
					return;
				}

				if (!validateAuth(exchange)) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Missing or invalid Authorization header");
					sendJsonResponse(exchange, 401, error);
					return;
				}

				// Parse query parameter
				String rawQuery = exchange.getRequestURI().getRawQuery();
				String q = "";
				if (rawQuery != null) {
					for (String param : rawQuery.split("&")) {
						String[] kv = param.split("=", 2);
						if (kv.length == 2 && "q".equals(kv[0])) {
							q = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
							break;
						}
					}
				}

				final String query = q.toLowerCase();

				String sql = "SELECT players.cr_tag AS tag, players.name AS player_name, clans.name AS clan_name "
						+ "FROM players "
						+ "LEFT JOIN clan_members ON clan_members.player_tag = players.cr_tag "
						+ "LEFT JOIN clans ON clans.tag = clan_members.clan_tag";

				org.json.JSONArray results = new org.json.JSONArray();

				try (java.sql.PreparedStatement pstmt = datautil.Connection.getConnection().prepareStatement(sql)) {
					try (java.sql.ResultSet rs = pstmt.executeQuery()) {
						while (rs.next() && results.length() < 25) {
							String tag = rs.getString("tag");
							String playerName = rs.getString("player_name");
							String clanName = rs.getString("clan_name");

							String display = (playerName != null ? playerName : tag) + " (" + tag + ")";
							if (clanName != null && !clanName.isEmpty()) {
								display += " - " + clanName;
							}

							if (display.toLowerCase().contains(query)
									|| tag.toLowerCase().startsWith(query)) {
								JSONObject entry = new JSONObject();
								entry.put("tag", tag);
								entry.put("name", playerName != null ? playerName : "");
								entry.put("clan", clanName != null ? clanName : "");
								entry.put("display", display);
								results.put(entry);
							}
						}
					}
				} catch (java.sql.SQLException e) {
					System.err.println("[LinkAPI] DB error in SearchLinkedHandler: " + e.getMessage());
					e.printStackTrace();
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Database error");
					sendJsonResponse(exchange, 500, error);
					return;
				}

				JSONObject response = new JSONObject();
				response.put("success", true);
				response.put("players", results);
				sendJsonResponse(exchange, 200, response);

			} catch (Exception e) {
				System.err.println("[LinkAPI] Error in SearchLinkedHandler: " + e.getMessage());
				e.printStackTrace();
				JSONObject error = new JSONObject();
				error.put("success", false);
				error.put("error", "Internal server error");
				sendJsonResponse(exchange, 500, error);
			}
		}
	}

	/**
	 * Handler for POST /api/remove
	 * Removes a player's clan membership and link.
	 */
	private static class RemovePlayerHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			System.out.println("[LinkAPI] Remove request from " + exchange.getRemoteAddress());

			try {
				if (!"POST".equals(exchange.getRequestMethod())) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Method not allowed");
					sendJsonResponse(exchange, 405, error);
					return;
				}

				if (!validateAuth(exchange)) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Missing or invalid Authorization header");
					sendJsonResponse(exchange, 401, error);
					return;
				}

				String body = readRequestBody(exchange);
				if (body == null || body.trim().isEmpty()) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Request body is required");
					sendJsonResponse(exchange, 400, error);
					return;
				}

				JSONObject requestData;
				try {
					requestData = new JSONObject(body);
				} catch (Exception e) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Invalid JSON in request body");
					sendJsonResponse(exchange, 400, error);
					return;
				}

				String tag = requestData.optString("tag", null);
				if (tag == null || tag.trim().isEmpty()) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Missing required parameter: tag");
					sendJsonResponse(exchange, 400, error);
					return;
				}

				if (!tag.startsWith("#")) {
					tag = "#" + tag;
				}
				tag = tag.toUpperCase();

				final String finalTag = tag;
				Player p = new Player(finalTag);

				if (!p.IsLinked()) {
					JSONObject error = new JSONObject();
					error.put("success", false);
					error.put("error", "Player is not linked");
					error.put("tag", finalTag);
					sendJsonResponse(exchange, 404, error);
					return;
				}

				String playerName = p.getNameDB();

				// Remove clan membership
				datautil.DBUtil.executeUpdate("DELETE FROM clan_members WHERE player_tag = ?", finalTag);

				// Remove player link
				datautil.DBUtil.executeUpdate("DELETE FROM players WHERE cr_tag = ?", finalTag);

				System.out.println("[LinkAPI] Successfully removed player " + finalTag + " (" + playerName + ")");

				JSONObject response = new JSONObject();
				response.put("success", true);
				response.put("tag", finalTag);
				response.put("playerName", playerName != null ? playerName : "");
				response.put("message", "Player successfully removed");
				sendJsonResponse(exchange, 200, response);

			} catch (Exception e) {
				System.err.println("[LinkAPI] Error in RemovePlayerHandler: " + e.getMessage());
				e.printStackTrace();
				JSONObject error = new JSONObject();
				error.put("success", false);
				error.put("error", "Internal server error");
				sendJsonResponse(exchange, 500, error);
			}
		}
	}
}
