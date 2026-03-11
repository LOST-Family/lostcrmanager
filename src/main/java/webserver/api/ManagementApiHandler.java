package webserver.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import commands.wins.wins;
import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Kickpoint;
import datawrapper.KickpointReason;
import datawrapper.Player;
import datawrapper.User;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class ManagementApiHandler implements HttpHandler {

	private String apiToken;

	public ManagementApiHandler(String apiToken) {
		this.apiToken = apiToken;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		if ("OPTIONS".equals(exchange.getRequestMethod())) {
			addCorsHeaders(exchange);
			exchange.sendResponseHeaders(204, -1);
			return;
		}

		if (!"POST".equals(exchange.getRequestMethod())) {
			sendResponse(exchange, 405, new JSONObject().put("error", "Method Not Allowed").toString());
			return;
		}

		if (!validateApiToken(exchange)) {
			sendResponse(exchange, 401,
					new JSONObject().put("error", "Unauthorized - Invalid or missing API token").toString());
			return;
		}

		try {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			JSONObject json = new JSONObject(body);

			String path = exchange.getRequestURI().getPath();
			String subPath = path.replaceFirst("^/api/manage/?", "");

			JSONObject response;

			switch (subPath) {
				case "members/add":
					response = handleAddMember(json);
					break;
				case "members/edit":
					response = handleEditMember(json);
					break;
				case "members/remove":
					response = handleRemoveMember(json);
					break;
				case "members/transfer":
					response = handleTransferMember(json);
					break;
				case "kickpoints/add":
					response = handleKickpointAdd(json);
					break;
				case "kickpoints/edit":
					response = handleKickpointEdit(json);
					break;
				case "kickpoints/remove":
					response = handleKickpointRemove(json);
					break;
				case "clanconfig":
					response = handleClanconfig(json);
					break;
				case "kickpoint-reasons/add":
					response = handleKickpointReasonAdd(json);
					break;
				case "kickpoint-reasons/edit":
					response = handleKickpointReasonEdit(json);
					break;
				case "kickpoint-reasons/remove":
					response = handleKickpointReasonRemove(json);
					break;
				case "links/link":
					response = handleLink(json);
					break;
				case "links/relink":
					response = handleRelink(json);
					break;
				case "links/unlink":
					response = handleUnlink(json);
					break;
				case "copyreasons":
					response = handleCopyReasons(json);
					break;
				case "restart":
					response = handleRestart(json);
					break;
				default:
					sendResponse(exchange, 404, new JSONObject().put("error", "Unknown endpoint").toString());
					return;
			}

			int status = response.optBoolean("success", false) ? 200 : response.optInt("statusCode", 400);
			response.remove("statusCode");
			sendResponse(exchange, status, response.toString());

		} catch (org.json.JSONException e) {
			sendResponse(exchange, 400, new JSONObject().put("error", "Invalid JSON body").toString());
		} catch (Exception e) {
			handleException(exchange, e);
		}
	}

	// ==================== Member Management ====================

	@SuppressWarnings("null")
	private JSONObject handleAddMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String clanTag = json.optString("clanTag", null);
		String role = json.optString("role", null);

		if (discordUserId == null || playerTag == null || clanTag == null || role == null) {
			return error("Missing required fields: discordUserId, playerTag, clanTag, role", 400);
		}

		Clan c = new Clan(clanTag);
		if (!c.ExistsDB()) {
			return error("Clan not found", 404);
		}

		User user = new User(discordUserId);
		if (!clanTag.equals("warteliste")) {
			if (!user.isColeaderOrHigherInClan(clanTag)) {
				return error("Insufficient permissions - must be coleader or higher in the clan", 403);
			}
		} else {
			if (!user.isColeaderOrHigher()) {
				return error("Insufficient permissions - must be coleader or higher in any clan", 403);
			}
		}

		if (!clanTag.equals("warteliste")) {
			if (!(role.equals("leader") || role.equals("coleader") || role.equals("hiddencoleader")
					|| role.equals("elder") || role.equals("member"))) {
				return error("Invalid role. Valid: leader, coleader, hiddencoleader, elder, member", 400);
			}
			if (role.equals("leader") && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
				return error("Must be admin to assign leader role", 403);
			}
			if (role.equals("coleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
					|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
				return error("Must be admin or leader to assign coleader role", 403);
			}
			if (role.equals("hiddencoleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
					|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
				return error("Must be admin or leader to assign hidden coleader role", 403);
			}
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}
		if (p.getClanDB() != null) {
			return error("Player is already in a clan", 400);
		}

		DBUtil.executeUpdate("INSERT INTO clan_members (player_tag, clan_tag, clan_role) VALUES (?, ?, ?)",
				playerTag, clanTag, role);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player added to clan");
		JSONArray roleChanges = new JSONArray();

		if (!clanTag.equals("warteliste")) {
			String userid = p.getUser().getUserID();
			Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
			if (guild != null) {
				Member member = guild.getMemberById(userid);
				if (member != null) {
					String memberroleid = c.getRoleID(Clan.Role.MEMBER);
					Role memberrole = memberroleid != null ? guild.getRoleById(memberroleid) : null;

					if (!role.equals("hiddencoleader") && memberrole != null
							&& !member.getRoles().contains(memberrole)) {
						guild.addRoleToMember(member, memberrole).queue();
						roleChanges.put(new JSONObject().put("type", "added").put("roleId", memberroleid)
								.put("userId", userid));
					}
				}
			}
		}

		response.put("roleChanges", roleChanges);
		return response;
	}

	private JSONObject handleEditMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String role = json.optString("role", null);

		if (discordUserId == null || playerTag == null || role == null) {
			return error("Missing required fields: discordUserId, playerTag, role", 400);
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}

		Clan c = p.getClanDB();
		if (c == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = c.getTag();
		if (clanTag.equals("warteliste")) {
			return error("Cannot edit members on the waitlist", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		if (!(role.equals("leader") || role.equals("coleader") || role.equals("hiddencoleader")
				|| role.equals("elder") || role.equals("member"))) {
			return error("Invalid role. Valid: leader, coleader, hiddencoleader, elder, member", 400);
		}
		if (role.equals("leader") && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to assign leader role", 403);
		}
		if (role.equals("coleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign coleader role", 403);
		}
		if (role.equals("hiddencoleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign hidden coleader role", 403);
		}

		DBUtil.executeUpdate("UPDATE clan_members SET clan_role = ? WHERE player_tag = ?", role, playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Member role updated");
		return response;
	}

	@SuppressWarnings("null")
	private JSONObject handleRemoveMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);

		if (discordUserId == null || playerTag == null) {
			return error("Missing required fields: discordUserId, playerTag", 400);
		}

		Player player = new Player(playerTag);
		if (!player.IsLinked()) {
			return error("Player is not linked", 404);
		}

		Player.RoleType role = player.getRole();
		Clan playerclan = player.getClanDB();
		if (playerclan == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = playerclan.getTag();
		User user = new User(discordUserId);
		if (!clanTag.equals("warteliste")) {
			if (!user.isColeaderOrHigherInClan(clanTag)) {
				return error("Insufficient permissions - must be coleader or higher in the clan", 403);
			}
		} else {
			if (!user.isColeaderOrHigher()) {
				return error("Insufficient permissions - must be coleader or higher in any clan", 403);
			}
		}

		if (role == Player.RoleType.LEADER && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to remove a leader", 403);
		}
		if (role == Player.RoleType.COLEADER && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to remove a coleader", 403);
		}

		DBUtil.executeUpdate("DELETE FROM clan_members WHERE player_tag = ?", playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Member removed from clan");

		if (!clanTag.equals("warteliste")) {
			String userid = player.getUser().getUserID();
			Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
			if (guild != null) {
				Member member = guild.getMemberById(userid);
				if (member != null) {
					String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
					Role memberrole = memberroleid != null ? guild.getRoleById(memberroleid) : null;
					boolean hasRole = memberrole != null && member.getRoles().contains(memberrole);
					response.put("memberRoleStatus", hasRole ? "still_has_role" : "no_role");
					response.put("memberRoleId", memberroleid);
				}
			}
		}

		return response;
	}

	@SuppressWarnings("null")
	private JSONObject handleTransferMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String newClanTag = json.optString("newClanTag", null);

		if (discordUserId == null || playerTag == null || newClanTag == null) {
			return error("Missing required fields: discordUserId, playerTag, newClanTag", 400);
		}

		Clan newclan = new Clan(newClanTag);
		if (!newclan.ExistsDB()) {
			return error("Destination clan not found", 404);
		}

		Player player = new Player(playerTag);
		if (!player.IsLinked()) {
			return error("Player is not linked", 404);
		}

		Player.RoleType role = player.getRole();
		Clan playerclan = player.getClanDB();
		if (playerclan == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = playerclan.getTag();
		User user = new User(discordUserId);

		// Permission check for source clan
		if (!clanTag.equals("warteliste")) {
			if (!user.isColeaderOrHigherInClan(clanTag)) {
				return error("Insufficient permissions - must be coleader or higher in the source clan", 403);
			}
		} else {
			if (!user.isColeaderOrHigher()) {
				return error("Insufficient permissions - must be coleader or higher in any clan", 403);
			}
		}

		// Permission check for destination clan
		if (!newClanTag.equals("warteliste")) {
			if (!user.isColeaderOrHigherInClan(newClanTag)) {
				return error(
						"Insufficient permissions - must be coleader or higher in the destination clan", 403);
			}
		} else {
			if (!user.isColeaderOrHigher()) {
				return error("Insufficient permissions - must be coleader or higher in any clan", 403);
			}
		}

		if (clanTag.equals(newClanTag)) {
			return error("Cannot transfer a player to the same clan", 400);
		}

		if (role == Player.RoleType.LEADER && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to transfer a leader", 403);
		}
		if (role == Player.RoleType.COLEADER && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to transfer a coleader", 403);
		}

		DBUtil.executeUpdate("UPDATE clan_members SET clan_tag = ?, clan_role = ? WHERE player_tag = ?",
				newClanTag, "member", playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player transferred");
		JSONArray roleChanges = new JSONArray();

		String userid = player.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		if (guild != null) {
			Member member = guild.getMemberById(userid);
			if (member != null) {
				if (!clanTag.equals("warteliste")) {
					String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
					Role memberrole = memberroleid != null ? guild.getRoleById(memberroleid) : null;
					if (memberrole != null && member.getRoles().contains(memberrole)) {
						guild.removeRoleFromMember(member, memberrole).queue();
						roleChanges.put(new JSONObject().put("type", "removed")
								.put("roleId", memberroleid).put("userId", userid));
					}
				}
				if (!newClanTag.equals("warteliste")) {
					String newmemberroleid = newclan.getRoleID(Clan.Role.MEMBER);
					Role newmemberrole = newmemberroleid != null ? guild.getRoleById(newmemberroleid) : null;
					if (newmemberrole != null && !member.getRoles().contains(newmemberrole)) {
						guild.addRoleToMember(member, newmemberrole).queue();
						roleChanges.put(new JSONObject().put("type", "added")
								.put("roleId", newmemberroleid).put("userId", userid));
					}
				}
			}
		}

		response.put("roleChanges", roleChanges);
		return response;
	}

	// ==================== Kickpoint Management ====================

	private JSONObject handleKickpointAdd(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String reason = json.optString("reason", null);
		String date = json.optString("date", null);

		if (discordUserId == null || playerTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, playerTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");

		if (date == null) {
			date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
		}

		Player p = new Player(playerTag);
		Clan c = p.getClanDB();
		if (c == null) {
			return error("Player not found or not in a clan", 404);
		}

		String clanTag = c.getTag();
		if (clanTag.equals("warteliste")) {
			return error("Cannot add kickpoints for waitlist players", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		if (c.getDaysKickpointsExpireAfter() == null || c.getMaxKickpoints() == null) {
			return error("Clan config must be set first (use clanconfig)", 400);
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		LocalDate localdate;
		try {
			localdate = LocalDate.parse(date, formatter);
		} catch (DateTimeParseException e) {
			return error("Invalid date format. Use dd.MM.yyyy", 400);
		}

		LocalDateTime dateTime = localdate.atStartOfDay();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime zonedDateTime = dateTime.atZone(zone);
		Timestamp timestampcreated = Timestamp.from(zonedDateTime.toInstant());
		Timestamp timestampexpires = Timestamp.valueOf(dateTime.plusDays(c.getDaysKickpointsExpireAfter()));
		Timestamp timestampnow = Timestamp.from(Instant.now());

		int id = DBManager.getAvailableKPID();

		DBUtil.executeUpdate(
				"INSERT INTO kickpoints (id, player_tag, date, amount, description, created_by_discord_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				id, playerTag, timestampcreated, amount, reason, discordUserId, timestampnow, timestampexpires);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint added");
		response.put("kickpointId", id);

		JSONArray warnings = new JSONArray();

		if (timestampexpires.before(Timestamp.from(Instant.now()))) {
			warnings.put("Kickpoint is already expired based on the given date");
		}

		long kptotal = 0;
		for (Kickpoint kp : p.getActiveKickpoints()) {
			kptotal += kp.getAmount();
		}
		if (kptotal >= c.getMaxKickpoints()) {
			warnings.put("Player has reached maximum kickpoints");
		}

		if (warnings.length() > 0) {
			response.put("warnings", warnings);
		}

		return response;
	}

	private JSONObject handleKickpointEdit(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String reason = json.optString("reason", null);
		String date = json.optString("date", null);

		if (discordUserId == null || !json.has("id") || reason == null || date == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, id, reason, amount, date", 400);
		}

		int id = json.getInt("id");
		int amount = json.getInt("amount");

		Kickpoint kp = new Kickpoint(id);
		if (kp.getDescription() == null) {
			return error("Kickpoint not found", 404);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		LocalDate localdate;
		try {
			localdate = LocalDate.parse(date, formatter);
		} catch (DateTimeParseException e) {
			return error("Invalid date format. Use dd.MM.yyyy", 400);
		}

		LocalDateTime dateTime = localdate.atStartOfDay();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime zonedDateTime = dateTime.atZone(zone);
		Timestamp timestampcreated = Timestamp.from(zonedDateTime.toInstant());

		DBUtil.executeUpdate("UPDATE kickpoints SET description = ?, amount = ?, date = ? WHERE id = ?",
				reason, amount, timestampcreated, id);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint updated");
		return response;
	}

	private JSONObject handleKickpointRemove(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);

		if (discordUserId == null || !json.has("id")) {
			return error("Missing required fields: discordUserId, id", 400);
		}

		int id = json.getInt("id");

		Kickpoint kp = new Kickpoint(id);
		if (kp.getPlayer() == null) {
			return error("Kickpoint not found", 404);
		}

		if (kp.getPlayer().getClanDB() == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = kp.getPlayer().getClanDB().getTag();
		if (clanTag.equals("warteliste")) {
			return error("Cannot remove kickpoints for waitlist players", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		if (kp.getDescription() == null) {
			return error("Kickpoint not found", 404);
		}

		DBUtil.executeUpdate("DELETE FROM kickpoints WHERE id = ?", id);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint removed");
		return response;
	}

	// ==================== Clan Config ====================

	private JSONObject handleClanconfig(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);

		if (discordUserId == null || clanTag == null || !json.has("maxKickpoints")
				|| !json.has("kickpointsExpireAfterDays")) {
			return error("Missing required fields: discordUserId, clanTag, maxKickpoints, kickpointsExpireAfterDays",
					400);
		}

		int maxKickpoints = json.getInt("maxKickpoints");
		int kickpointsExpireAfterDays = json.getInt("kickpointsExpireAfterDays");

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Clan c = new Clan(clanTag);
		if (!c.ExistsDB()) {
			return error("Clan not found", 404);
		}

		if (clanTag.equals("warteliste")) {
			return error("Cannot configure the waitlist", 400);
		}

		if (c.getDaysKickpointsExpireAfter() == null) {
			DBUtil.executeUpdate(
					"INSERT INTO clan_settings (clan_tag, max_kickpoints, kickpoints_expire_after_days) VALUES (?, ?, ?)",
					clanTag, maxKickpoints, kickpointsExpireAfterDays);
		} else {
			DBUtil.executeUpdate(
					"UPDATE clan_settings SET max_kickpoints = ?, kickpoints_expire_after_days = ? WHERE clan_tag = ?",
					maxKickpoints, kickpointsExpireAfterDays, clanTag);
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Clan config updated");
		return response;
	}

	// ==================== Kickpoint Reasons ====================

	private JSONObject handleKickpointReasonAdd(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, clanTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");
		Integer index = json.has("index") ? json.getInt("index") : null;

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		if (clanTag.equals("warteliste")) {
			return error("Cannot add kickpoint reasons for the waitlist", 400);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (kpreason.Exists()) {
			return error("Kickpoint reason already exists", 400);
		}

		if (index == null) {
			String sql = "SELECT MAX(index) FROM kickpoint_reasons WHERE clan_tag = ?";
			Integer maxIndex = DBUtil.getValueFromSQL(sql, Integer.class, clanTag);
			index = (maxIndex == null) ? 1 : maxIndex + 1;
		}

		DBUtil.executeUpdate("INSERT INTO kickpoint_reasons (name, clan_tag, amount, index) VALUES (?, ?, ?, ?)",
				reason, clanTag, amount, index);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason added");
		response.put("index", index);
		return response;
	}

	private JSONObject handleKickpointReasonEdit(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, clanTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		if (clanTag.equals("warteliste")) {
			return error("Cannot edit kickpoint reasons for the waitlist", 400);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (!kpreason.Exists()) {
			return error("Kickpoint reason not found", 404);
		}

		if (json.has("index")) {
			int index = json.getInt("index");
			DBUtil.executeUpdate(
					"UPDATE kickpoint_reasons SET amount = ?, index = ? WHERE name = ? AND clan_tag = ?",
					amount, index, reason, clanTag);
		} else {
			DBUtil.executeUpdate("UPDATE kickpoint_reasons SET amount = ? WHERE name = ? AND clan_tag = ?",
					amount, reason, clanTag);
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason updated");
		return response;
	}

	private JSONObject handleKickpointReasonRemove(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null) {
			return error("Missing required fields: discordUserId, clanTag, reason", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		if (clanTag.equals("warteliste")) {
			return error("Cannot remove kickpoint reasons for the waitlist", 400);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (!kpreason.Exists()) {
			return error("Kickpoint reason not found", 404);
		}

		DBUtil.executeUpdate("DELETE FROM kickpoint_reasons WHERE name = ? AND clan_tag = ?", reason, clanTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason removed");
		return response;
	}

	// ==================== Link Management ====================

	private JSONObject handleLink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String targetUserId = json.optString("targetUserId", null);

		if (discordUserId == null || playerTag == null || targetUserId == null) {
			return error("Missing required fields: discordUserId, playerTag, targetUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		if (!playerTag.startsWith("#")) {
			playerTag = "#" + playerTag;
		}

		Player p = new Player(playerTag);
		if (!p.AccExists()) {
			return error("Player does not exist or API error", 404);
		}
		if (p.IsLinked()) {
			return error(
					"Player is already linked to user " + p.getUser().getUserID() + ". Use relink instead.",
					400);
		}

		String playername = null;
		try {
			playername = p.getNameAPI();
		} catch (Exception e) {
			e.printStackTrace();
		}

		DBUtil.executeUpdate("INSERT INTO players (cr_tag, discord_id, name) VALUES (?, ?, ?)",
				playerTag, targetUserId, playername);

		final String finalTag = playerTag;
		Thread saveWinsThread = new Thread(() -> {
			wins.savePlayerWins(finalTag);
		});
		saveWinsThread.setDaemon(true);
		saveWinsThread.start();

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player linked to user");
		return response;
	}

	private JSONObject handleRelink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String targetUserId = json.optString("targetUserId", null);

		if (discordUserId == null || playerTag == null || targetUserId == null) {
			return error("Missing required fields: discordUserId, playerTag, targetUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		playerTag = playerTag.toUpperCase();
		if (!playerTag.startsWith("#")) {
			playerTag = "#" + playerTag;
		}

		Player p = new Player(playerTag);
		if (!p.AccExists()) {
			return error("Player does not exist or API error", 404);
		}
		if (!p.IsLinked()) {
			return error("Player is not linked. Use link instead.", 400);
		}

		DBUtil.executeUpdate("UPDATE players SET discord_id = ? WHERE cr_tag = ?", targetUserId, playerTag);

		final String finalTag = playerTag;
		Thread saveWinsThread = new Thread(() -> {
			wins.savePlayerWins(finalTag);
		});
		saveWinsThread.setDaemon(true);
		saveWinsThread.start();

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player relinked to user");
		return response;
	}

	private JSONObject handleUnlink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);

		if (discordUserId == null || playerTag == null) {
			return error("Missing required fields: discordUserId, playerTag", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}

		DBUtil.executeUpdate("DELETE FROM players WHERE cr_tag = ?", playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player unlinked");
		return response;
	}

	// ==================== Admin ====================

	private JSONObject handleCopyReasons(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String sourceClanTag = json.optString("sourceClanTag", null);

		if (discordUserId == null || sourceClanTag == null) {
			return error("Missing required fields: discordUserId, sourceClanTag", 400);
		}

		User user = new User(discordUserId);
		if (!user.isAdmin()) {
			return error("Insufficient permissions - must be admin", 403);
		}

		Clan sourceClan = new Clan(sourceClanTag);
		if (!sourceClan.ExistsDB()) {
			return error("Source clan not found", 404);
		}

		if (sourceClanTag.equals("warteliste")) {
			return error("Cannot copy from the waitlist", 400);
		}

		ArrayList<KickpointReason> sourceReasons = sourceClan.getKickpointReasons();
		if (sourceReasons.isEmpty()) {
			return error("Source clan has no kickpoint reasons", 400);
		}

		ArrayList<String> allClans = DBManager.getAllClans();
		int clansUpdated = 0;

		for (String targetClanTag : allClans) {
			if (targetClanTag.equals("warteliste") || targetClanTag.equals(sourceClanTag)) {
				continue;
			}

			DBUtil.executeUpdate("DELETE FROM kickpoint_reasons WHERE clan_tag = ?", targetClanTag);

			for (KickpointReason reason : sourceReasons) {
				DBUtil.executeUpdate(
						"INSERT INTO kickpoint_reasons (name, clan_tag, amount, index) VALUES (?, ?, ?, ?)",
						reason.getReason(), targetClanTag, reason.getAmount(), reason.getIndex());
			}

			clansUpdated++;
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reasons copied to " + clansUpdated + " clans");
		response.put("reasonsCopied", sourceReasons.size());
		response.put("clansUpdated", clansUpdated);
		return response;
	}

	private JSONObject handleRestart(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);

		if (discordUserId == null) {
			return error("Missing required field: discordUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isAdmin()) {
			return error("Insufficient permissions - must be admin", 403);
		}

		new Thread(() -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.exit(0);
		}, "RestartApi").start();

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Bot restart initiated");
		return response;
	}

	// ==================== Utility Methods ====================

	private JSONObject error(String message, int statusCode) {
		JSONObject response = new JSONObject();
		response.put("success", false);
		response.put("error", message);
		response.put("statusCode", statusCode);
		return response;
	}

	private boolean validateApiToken(HttpExchange exchange) {
		if (apiToken == null || apiToken.isEmpty()) {
			return true;
		}
		String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
		if (authHeader != null) {
			if (authHeader.startsWith("Bearer ")) {
				return apiToken.equals(authHeader.substring(7));
			}
			return apiToken.equals(authHeader);
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

	private void addCorsHeaders(HttpExchange exchange) {
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
				"Content-Type, Authorization, X-API-Token");
	}

	private void handleException(HttpExchange exchange, Exception e) {
		boolean isClientDisconnect = false;
		if (e instanceof IOException) {
			String msg = e.getMessage();
			if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe")
					|| msg.contains("insufficient bytes written"))) {
				isClientDisconnect = true;
			}
		}
		if (isClientDisconnect) {
			System.out.println("Client disconnected in ManagementApiHandler: " + e.getMessage());
		} else {
			System.err.println("Error in ManagementApiHandler: " + e.getMessage());
			e.printStackTrace();
			try {
				sendResponse(exchange, 500,
						new JSONObject().put("error", "Internal Server Error").toString());
			} catch (IOException ignore) {
			}
		}
	}
}
