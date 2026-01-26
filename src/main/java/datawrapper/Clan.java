package datawrapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostcrmanager.Bot;

public class Clan {

	private String clan_tag;
	private String namedb;
	private String descriptiondb;
	private String descriptionapi;
	private String nameapi;
	private ArrayList<Player> playerlistdb;
	private ArrayList<Player> playerlistapi;
	private ArrayList<Player> cwfameplayerlist;
	private Long max_kickpoints;
	private Long index;
	private Integer kickpoints_expire_after_days;
	private ArrayList<KickpointReason> kickpoint_reasons;

	public enum Role {
		LEADER, COLEADER, ELDER, MEMBER
	}

	public Clan(String clantag) {
		clan_tag = clantag;
	}

	public boolean ExistsDB() {
		String sql = "SELECT 1 FROM clans WHERE tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, clan_tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public String getRoleID(Role role) {
		switch (role) {
			case LEADER:
				return DBUtil.getValueFromSQL("SELECT leader_roleid FROM clans WHERE tag = ?", String.class, clan_tag);
			case COLEADER:
				return DBUtil.getValueFromSQL("SELECT coleader_roleid FROM clans WHERE tag = ?", String.class,
						clan_tag);
			case ELDER:
				return DBUtil.getValueFromSQL("SELECT elder_roleid FROM clans WHERE tag = ?", String.class, clan_tag);
			case MEMBER:
				return DBUtil.getValueFromSQL("SELECT member_roleid FROM clans WHERE tag = ?", String.class, clan_tag);
		}
		return null;
	}

	public String getInfoStringAPI() {
		if (!clan_tag.equals("warteliste")) {
			return getNameAPI() + " (" + clan_tag + ")";
		} else {
			return getNameAPI();
		}
	}

	public String getInfoStringDB() {
		if (!clan_tag.equals("warteliste")) {
			return getNameDB() + " (" + clan_tag + ")";
		} else {
			return getNameDB();
		}
	}

	public String getTag() {
		return clan_tag;
	}

	public Long getIndex() {
		if (index == null) {
			String sql = "SELECT index FROM clans WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, clan_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						index = rs.getLong("index");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return index;
	}

	public ArrayList<Player> getPlayersDB() {
		if (playerlistdb == null) {
			String sql = "SELECT player_tag FROM clan_members WHERE clan_tag = ? ";
			ArrayList<String> result = DBUtil.getArrayListFromSQL(sql, String.class, clan_tag);

			playerlistdb = new ArrayList<>();
			for (String tags : result) {
				playerlistdb.add(new Player(tags));
			}
		}
		return playerlistdb;
	}

	public ArrayList<Player> getPlayersAPI() {
		if (playerlistapi == null) {
			playerlistapi = new ArrayList<>();
			JSONObject jsonObject = new JSONObject(APIUtil.getClanJson(clan_tag));

			JSONArray members = jsonObject.getJSONArray("memberList");

			for (int i = 0; i < members.length(); i++) {
				JSONObject member = members.getJSONObject(i);
				if (member.has("tag") && member.has("name")) {
					playerlistapi.add(new Player(member.getString("tag")).setNameAPI(member.getString("name")));
				}
			}
		}
		return playerlistapi;
	}

	public Long getMaxKickpoints() {
		if (max_kickpoints == null) {
			String sql = "SELECT max_kickpoints FROM clan_settings WHERE clan_tag = ?";
			max_kickpoints = DBUtil.getValueFromSQL(sql, Long.class, clan_tag);
		}
		return max_kickpoints;
	}

	public Integer getDaysKickpointsExpireAfter() {
		if (kickpoints_expire_after_days == null) {
			String sql = "SELECT kickpoints_expire_after_days FROM clan_settings WHERE clan_tag = ?";
			kickpoints_expire_after_days = DBUtil.getValueFromSQL(sql, Integer.class, clan_tag);
		}
		return kickpoints_expire_after_days;
	}

	public ArrayList<KickpointReason> getKickpointReasons() {
		if (kickpoint_reasons == null) {
			kickpoint_reasons = new ArrayList<>();

			String sql = "SELECT name, clan_tag FROM kickpoint_reasons WHERE clan_tag = ? ORDER BY index ASC";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setObject(1, clan_tag);

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						kickpoint_reasons.add(new KickpointReason(rs.getString("name"), rs.getString("clan_tag")));
					}
					Statement stmt = rs.getStatement();
					rs.close();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return kickpoint_reasons;
	}

	public String getNameDB() {
		if (namedb == null) {
			String sql = "SELECT name FROM clans WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, clan_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						namedb = rs.getString("name");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return namedb;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getClanJson(clan_tag));
			nameapi = jsonObject.getString("name");
		}
		return nameapi;
	}

	public ArrayList<Player> getCWFamePlayerList() {
		if (cwfameplayerlist == null) {
			// URL-kodieren des Spieler-Tags (# -> %23)
			String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

			String url = "https://api.clashroyale.com/v1/clans/" + encodedTag + "/riverracelog?limit=1";

			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET()
					.build();

			HttpResponse<String> response = null;
			try {
				response = client.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				return null;
			}

			if (response.statusCode() == 200) {
				String responseBody = response.body();
				// Einfacher JSON-Name-Parser ohne Bibliotheken:
				JSONObject json = new JSONObject(responseBody);

				JSONArray items = json.getJSONArray("items");
				JSONObject item = items.getJSONObject(0);
				JSONArray standings = item.getJSONArray("standings");

				for (int i = 0; i < standings.length(); i++) {
					JSONObject standing = standings.getJSONObject(i);
					JSONObject clan = standing.getJSONObject("clan");
					String tag = clan.getString("tag");
					if (tag.equals(clan_tag)) {

						JSONArray participants = clan.getJSONArray("participants");

						cwfameplayerlist = new ArrayList<>();
						for (int j = 0; j < participants.length(); j++) {
							JSONObject currentplayer = participants.getJSONObject(j);
							Player p = new Player(currentplayer.getString("tag"));
							p.setCWFame(currentplayer.getInt("fame"));
							p.setClantagCWDone(clan_tag);
							cwfameplayerlist.add(p);
						}
						break;
					}
				}

			} else {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
			}
		}
		return cwfameplayerlist;
	}

	public String getDescriptionDB() {
		if (descriptiondb == null) {
			String sql = "SELECT description FROM clans WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, clan_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						descriptiondb = rs.getString("description");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return descriptiondb;
	}

	public String getDescriptionAPI() {
		if (descriptionapi == null) {
			JSONObject jsonobject = new JSONObject(APIUtil.getClanJson(clan_tag));
			if (jsonobject.has("description") && !jsonobject.isNull("description")) {
				descriptionapi = jsonobject.getString("description");
			}
		}
		return descriptionapi;
	}
}
