package webserver.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import datawrapper.Player;
import datawrapper.User;
import datawrapper.Clan;
import datautil.DBManager;
import java.util.ArrayList;
import java.util.List;
import webserver.api.dto.ClanDTO;
import webserver.api.dto.PlayerDTO;
import webserver.api.dto.UserDTO;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import lostcrmanager.Bot;

/**
 * REST API Server (ported from lostmanager) Clan-specific endpoints removed.
 */
public class RestApiServer {

	private HttpServer server;
	private int port;
	private ObjectMapper objectMapper;
	private String apiToken;

	public RestApiServer(int port) {
		this.port = port;
		this.objectMapper = new ObjectMapper();
		this.apiToken = System.getenv("REST_API_TOKEN");

		if (this.apiToken == null || this.apiToken.isEmpty()) {
			System.err.println(
					"WARNING: LOSTCRMANAGER_API_SECRET is not set. API endpoints will be accessible without authentication.");
		}
	}

	public void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);

		// Register API endpoints
		// More specific paths should be registered before more general ones
		server.createContext("/api/clans/", new ClanSpecificHandler());
		server.createContext("/api/clans", new ClansHandler());
		server.createContext("/api/players/", new PlayerHandler());
		server.createContext("/api/users/", new UserHandler());
		server.createContext("/api/coleaders", new ColeadersHandler());

		server.setExecutor(Executors.newFixedThreadPool(10));
		server.start();

		System.out.println("REST API Server started on port " + port);
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
			System.out.println("REST API Server stopped");
		}
	}

	/**
	 * Handler for GET /api/players/{tag}
	 */
	private class PlayerHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				addCorsHeaders(exchange);
				exchange.sendResponseHeaders(204, -1);
				return;
			}

			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			// Validate API token
			if (!validateApiToken(exchange)) {
				sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
				return;
			}

			try {
				String path = exchange.getRequestURI().getPath();
				String[] parts = path.split("/");

				if (parts.length < 4) {
					sendResponse(exchange, 400, "{\"error\":\"Invalid path format. Expected /api/players/{tag}\"}");
					return;
				}

				String playerTag = parts[3];

				Player player = new Player(playerTag);

				if (!player.IsLinked()) {
					sendResponse(exchange, 404, "{\"error\":\"Player not found\"}");
					return;
				}

				PlayerDTO playerDTO = new PlayerDTO(player);
				String json = objectMapper.writeValueAsString(playerDTO);
				sendJsonResponse(exchange, 200, json);

			} catch (Exception e) {
				handleException(exchange, "PlayerHandler", e);
			}
		}
	}

	/**
	 * Handler for GET /api/users/{userId}
	 */
	private class UserHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				addCorsHeaders(exchange);
				exchange.sendResponseHeaders(204, -1);
				return;
			}

			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			if (!validateApiToken(exchange)) {
				sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
				return;
			}

			try {
				String path = exchange.getRequestURI().getPath();
				String[] parts = path.split("/");

				if (parts.length < 4) {
					sendResponse(exchange, 400, "{\"error\":\"Invalid path format. Expected /api/users/{userId}\"}");
					return;
				}

				String userId = parts[3];

				User user = new User(userId);

				java.util.ArrayList<Player> linkedPlayers = user.getAllLinkedAccounts();
				if ((linkedPlayers == null || linkedPlayers.isEmpty()) && !user.isAdmin()) {
					sendResponse(exchange, 404, "{\"error\":\"User not found or has no linked accounts\"}");
					return;
				}

				UserDTO userDTO = new UserDTO(user);
				String json = objectMapper.writeValueAsString(userDTO);
				sendJsonResponse(exchange, 200, json);

			} catch (Exception e) {
				handleException(exchange, "UserHandler", e);
			}
		}
	}

	/**
	 * Handle exceptions from handlers, suppressing connection reset errors
	 */
	private void handleException(HttpExchange exchange, String handlerName, Exception e) {
		boolean isClientDisconnect = false;
		if (e instanceof IOException) {
			String msg = e.getMessage();
			if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe")
					|| msg.contains("insufficient bytes written"))) {
				isClientDisconnect = true;
			}
		}

		if (isClientDisconnect) {
			System.out.println("Client disconnected in " + handlerName + ": " + e.getMessage());
		} else {
			System.err.println("Error in " + handlerName + ": " + e.getMessage());
			e.printStackTrace();
			try {
				sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
			} catch (IOException ignore) {
				// Failed to send error response
			}
		}
	}

	private boolean validateApiToken(HttpExchange exchange) {
		if (apiToken == null || apiToken.isEmpty()) {
			return true;
		}

		String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
		if (authHeader != null) {
			if (authHeader.startsWith("Bearer ")) {
				String token = authHeader.substring(7);
				return apiToken.equals(token);
			} else {
				return apiToken.equals(authHeader);
			}
		}

		String apiTokenHeader = exchange.getRequestHeaders().getFirst("X-API-Token");
		if (apiTokenHeader != null) {
			return apiToken.equals(apiTokenHeader);
		}

		return false;
	}

	private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		addCorsHeaders(exchange);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		addCorsHeaders(exchange);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private void addCorsHeaders(HttpExchange exchange) {
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token");
	}

	/**
	 * Handler for clan-specific endpoints:
	 * - GET /api/clans/{tag} - clan info
	 * - GET /api/clans/{tag}/members - clan members (DB only)
	 */
	private class ClanSpecificHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				addCorsHeaders(exchange);
				exchange.sendResponseHeaders(204, -1);
				return;
			}

			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			// Validate API token
			if (!validateApiToken(exchange)) {
				sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
				return;
			}

			try {
				String path = exchange.getRequestURI().getPath();
				String[] parts = path.split("/");

				if (parts.length < 4) {
					sendResponse(exchange, 400, "{\"error\":\"Invalid path format\"}");
					return;
				}

				String clanTag = parts[3];

				// Validate clan exists (DB only)
				Clan clan = new Clan(clanTag);
				if (!clan.ExistsDB()) {
					sendResponse(exchange, 404, "{\"error\":\"Clan not found\"}");
					return;
				}

				// Route based on sub-path
				if (parts.length >= 5) {
					String subPath = parts[4];
					if ("members".equals(subPath)) {
						handleClanMembers(exchange, clan);
						return;
					} else {
						sendResponse(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
						return;
					}
				} else {
					// Return clan info (DB only)
					ClanDTO clanDTO = new ClanDTO(clan);
					String json = objectMapper.writeValueAsString(clanDTO);
					sendJsonResponse(exchange, 200, json);
				}

			} catch (Exception e) {
				handleException(exchange, "ClanSpecificHandler", e);
			}
		}

		private void handleClanMembers(HttpExchange exchange, Clan clan) throws Exception {
			ArrayList<Player> members = clan.getPlayersDB();

			if (members == null) {
				sendJsonResponse(exchange, 200, "[]");
				return;
			}

			List<PlayerDTO> playerDTOs = new ArrayList<>();
			for (Player player : members) {
				try {
					playerDTOs.add(new PlayerDTO(player));
				} catch (Exception e) {
					System.err.println("Error processing player " + player.getTag() + ": " + e.getMessage());
				}
			}

			String json = objectMapper.writeValueAsString(playerDTOs);
			sendJsonResponse(exchange, 200, json);
		}
	}

	/**
	 * Handler for GET /api/clans Returns all available clans
	 */
	private class ClansHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				addCorsHeaders(exchange);
				exchange.sendResponseHeaders(204, -1);
				return;
			}

			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			// Validate API token
			if (!validateApiToken(exchange)) {
				sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
				return;
			}

			try {
				// Get all clan tags from database
				ArrayList<String> clanTags = DBManager.getAllClans();

				// Convert to DTOs
				List<ClanDTO> clans = new ArrayList<>();
				for (String tag : clanTags) {
					try {
						Clan clan = new Clan(tag);
						clans.add(new ClanDTO(clan));
					} catch (Exception e) {
						System.err.println("Error processing clan " + tag + ": " + e.getMessage());
					}
				}

				String json = objectMapper.writeValueAsString(clans);
				sendJsonResponse(exchange, 200, json);

			} catch (Exception e) {
				handleException(exchange, "ClansHandler", e);
			}
		}
	}

	/**
	 * Handler for GET /api/coleaders
	 * Returns a list of Discord users who are Leader or Co-Leader in a registered
	 * clan,
	 * along with their highest level linked account that is currently in a clan.
	 */
	private class ColeadersHandler implements HttpHandler {
		@SuppressWarnings("null")
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				addCorsHeaders(exchange);
				exchange.sendResponseHeaders(204, -1);
				return;
			}

			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			if (!validateApiToken(exchange)) {
				sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
				return;
			}

			try {
				Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
				if (guild == null) {
					sendResponse(exchange, 500, "{\"error\":\"Guild not found\"}");
					return;
				}

				ArrayList<String> allClans = DBManager.getAllClans();
				Set<String> targetRoleIds = new HashSet<>();

				// Collect all leader and coleader role IDs
				for (String clantag : allClans) {
					Clan clan = new Clan(clantag);
					String leaderRole = clan.getRoleID(Clan.Role.LEADER);
					String coleaderRole = clan.getRoleID(Clan.Role.COLEADER);
					if (leaderRole != null)
						targetRoleIds.add(leaderRole);
					if (coleaderRole != null)
						targetRoleIds.add(coleaderRole);
				}

				Set<Member> distinctMembers = new HashSet<>();
				for (String roleId : targetRoleIds) {
					Role role = guild.getRoleById(roleId);
					if (role != null) {
						distinctMembers.addAll(guild.getMembersWithRoles(role));
					}
				}

				List<Map<String, Object>> resultList = new ArrayList<>();

				for (Member member : distinctMembers) {
					String userId = member.getId();
					User user = new User(userId);
					ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

					if (linkedAccounts == null || linkedAccounts.isEmpty()) {
						continue;
					}

					Player bestAccount = null;
					int maxLevel = -1;

					// Filter: must be in a clan (getClanDB() != null) and find highest level
					for (Player p : linkedAccounts) {
						// Only consider accounts that are actually in a clan (per DB)
						if (p.getClanDB() != null) {
							Integer level = p.getExpLevelAPI();
							if (level != null && level > maxLevel) {
								maxLevel = level;
								bestAccount = p;
							}
						}
					}

					if (bestAccount != null) {
						Map<String, Object> entry = new HashMap<>();
						entry.put("userId", userId);
						entry.put("highestAccountTag", bestAccount.getTag());
						entry.put("highestAccountName", bestAccount.getNameAPI());

						resultList.add(entry);
					}
				}

				String json = objectMapper.writeValueAsString(resultList);
				sendJsonResponse(exchange, 200, json);

			} catch (Exception e) {
				handleException(exchange, "ColeadersHandler", e);
			}
		}
	}
}
