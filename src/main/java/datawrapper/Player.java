package datawrapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostcrmanager.Bot;

public class Player {

	public enum RoleType {
		ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN
	};

	private JSONObject apiresult;
	private String tag;
	private String namedb;
	private String nameapi;
	private User user;
	private Clan clandb;
	private Clan clanapi;
	private String clantagcwdone;
	private Integer cwfame;
	private Integer decksUsed;
	private Integer PathofLegendLeagueNumber;
	private Integer trophies;
	private Integer strtrophies;
	private Integer PathofLegendTrophies;
	private Integer lastPathOfLegendLeagueNumber;
	private Integer lastPathOfLegendTrophies;
	private ArrayList<Kickpoint> kickpoints;
	private Long kickpointstotal;
	private RoleType role;
	private Boolean mark;
	private String note;
	private Integer wins;
	private Integer expLevel;

	public Player(String tag) {
		this.tag = tag;
	}

	public Player refreshData() {
		apiresult = null;
		namedb = null;
		nameapi = null;
		user = null;
		clandb = null;
		clanapi = null;
		kickpoints = null;
		kickpointstotal = null;
		role = null;
		clantagcwdone = null;
		cwfame = null;
		decksUsed = null;
		PathofLegendLeagueNumber = null;
		trophies = null;
		strtrophies = null;
		PathofLegendTrophies = null;
		lastPathOfLegendLeagueNumber = null;
		lastPathOfLegendTrophies = null;
		mark = null;
		note = null;
		wins = null;
		expLevel = null;
		return this;
	}

	public Player setNameDB(String name) {
		this.namedb = name;
		return this;
	}

	public Player setNameAPI(String name) {
		this.nameapi = name;
		return this;
	}

	public Player setUser(User user) {
		this.user = user;
		return this;
	}

	public Player setClanDB(Clan clan) {
		this.clandb = clan;
		return this;
	}

	public Player setClanAPI(Clan clan) {
		this.clanapi = clan;
		return this;
	}

	public Player setKickpoints(ArrayList<Kickpoint> kickpoints) {
		this.kickpoints = kickpoints;
		return this;
	}

	public Player setKickpointsTotal(Long kptotal) {
		this.kickpointstotal = kptotal;
		return this;
	}

	public Player setRole(RoleType role) {
		this.role = role;
		return this;
	}

	public Player setMark(boolean mark) {
		this.mark = mark;
		return this;
	}

	public Player setNote(String note) {
		this.note = note;
		return this;
	}

	public Player setCWFame(Integer fame) {
		this.cwfame = fame;
		return this;
	}

	public Player setDecksUsed(Integer decks) {
		this.decksUsed = decks;
		return this;
	}

	public Player setClantagCWDone(String tag) {
		this.clantagcwdone = tag;
		return this;
	}

	public boolean IsLinked() {
		String sql = "SELECT 1 FROM players WHERE cr_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean AccExists() {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			URL url = URI.create("https://api.clashroyale.com/v1/players/" + encodedTag).toURL();

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");

			int responseCode = connection.getResponseCode();

			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuilder responseContent = new StringBuilder();
				while ((line = in.readLine()) != null) {
					responseContent.append(line);
				}
				in.close();

				return true;
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public String getInfoStringDB() {
		try {
			return getNameDB() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getInfoStringAPI() {
		try {
			return getNameAPI() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getTag() {
		return tag;
	}

	public String getNameDB() {
		if (namedb == null) {
			namedb = DBUtil.getValueFromSQL("SELECT name FROM players WHERE cr_tag = ?", String.class, tag);
		}
		return namedb;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
			return jsonObject.getString("name");
		}
		return nameapi;
	}

	public User getUser() {
		if (user == null) {
			String value = DBUtil.getValueFromSQL("SELECT discord_id FROM players WHERE cr_tag = ?", String.class, tag);
			user = value == null ? null : new User(value);
		}
		return user;
	}

	public Clan getClanDB() {
		if (clandb == null) {
			String value = DBUtil.getValueFromSQL("SELECT clan_tag FROM clan_members WHERE player_tag = ?",
					String.class, tag);
			clandb = value == null ? null : new Clan(value);
		}
		return clandb;
	}

	public Clan getClanAPI() {
		if (clanapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));

			// Prüfen, ob der Schlüssel "clan" vorhanden ist und nicht null
			if (jsonObject.has("clan") && !jsonObject.isNull("clan")) {
				JSONObject clanObject = jsonObject.getJSONObject("clan");
				if (clanObject.has("tag")) {
					clanapi = new Clan(clanObject.getString("tag"));
				}
			}
		}
		return clanapi;
	}

	public ArrayList<Kickpoint> getActiveKickpoints() {
		if (kickpoints == null) {
			kickpoints = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ? ORDER BY expires_at ASC";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				Kickpoint kp = new Kickpoint(id);
				if (kp.getExpirationDate().isAfter(OffsetDateTime.now())) {
					kickpoints.add(kp);
				}
			}
		}
		return kickpoints;
	}

	public long getTotalKickpoints() {
		if (kickpointstotal == null) {
			ArrayList<Kickpoint> a = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				a.add(new Kickpoint(id));
			}
			kickpointstotal = 0L;
			for (Kickpoint kp : a) {
				kickpointstotal = kickpointstotal + kp.getAmount();
			}
		}
		return kickpointstotal;
	}

	public RoleType getRole() {
		if (role == null) {
			if (new Player(tag).getClanDB() == null) {
				return null;
			}
			boolean b = true;
			if (getUser() != null) {
				if (getUser().isAdmin()) {
					role = RoleType.ADMIN;
					b = false;
				}
			}
			if (b) {
				String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
				try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
					pstmt.setString(1, tag);
					try (ResultSet rs = pstmt.executeQuery()) {
						if (rs.next()) {
							String rolestring = rs.getString("clan_role");
							role = rolestring.equals("leader") ? RoleType.LEADER
									: rolestring.equals("coleader") || rolestring.equals("hiddencoleader")
											? RoleType.COLEADER
											: rolestring.equals("elder") ? RoleType.ELDER
													: rolestring.equals("member") ? RoleType.MEMBER : null;
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return role;
	}

	public Integer getTrophies() {
		if (trophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			trophies = apiresult.getInt("trophies");
		}
		return trophies;
	}

	public Integer getSTRTrophies() {
		if (strtrophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			String currentseasonstring = Bot.seasonstringfallback;
			if (apiresult.has("leagueStatistics")) {
				JSONObject leagueStatistics = apiresult.getJSONObject("leagueStatistics");
				if (leagueStatistics.has("previousSeason")) {
					JSONObject previousSeason = leagueStatistics.getJSONObject("previousSeason");
					String previousseasonid = previousSeason.getString("id");
					int previousseasonyear = Integer.valueOf(previousseasonid.split("-")[0]);
					int previousseasonmonth = Integer.valueOf(previousseasonid.split("-")[1]);
					int currentseasonyear;
					int currentseasonmonth;
					if (previousseasonmonth + 1 == 13) {
						currentseasonyear = previousseasonyear + 1;
						currentseasonmonth = 1;
					} else {
						currentseasonyear = previousseasonyear;
						currentseasonmonth = previousseasonmonth + 1;
					}
					currentseasonstring = "" + currentseasonyear;
					if (currentseasonmonth < 10) {
						currentseasonstring += "0" + currentseasonmonth;
					} else {
						currentseasonstring += currentseasonmonth;
					}
				}
			}
			if (currentseasonstring != null) {
				Bot.seasonstringfallback = currentseasonstring;
				if (apiresult.has("progress")) {
					JSONObject progress = apiresult.getJSONObject("progress");
					if (progress.has("seasonal-trophy-road-" + currentseasonstring)) {
						JSONObject seasontrophyroad = progress
								.getJSONObject("seasonal-trophy-road-" + currentseasonstring);
						strtrophies = seasontrophyroad.getInt("trophies");
					}
				}
			}

		}
		return strtrophies;
	}

	public Integer getPoLLeagueNumber() {
		if (PathofLegendLeagueNumber == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("currentPathOfLegendSeasonResult")
					&& !apiresult.isNull("currentPathOfLegendSeasonResult")) {
				JSONObject currentPathOfLegendSeasonResult = apiresult.getJSONObject("currentPathOfLegendSeasonResult");
				PathofLegendLeagueNumber = currentPathOfLegendSeasonResult.getInt("leagueNumber");
			}
		}
		return PathofLegendLeagueNumber;
	}

	public Integer getPoLTrophies() {
		if (PathofLegendTrophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("currentPathOfLegendSeasonResult")
					&& !apiresult.isNull("currentPathOfLegendSeasonResult")) {
				JSONObject currentPathOfLegendSeasonResult = apiresult.getJSONObject("currentPathOfLegendSeasonResult");
				PathofLegendTrophies = currentPathOfLegendSeasonResult.getInt("trophies");
			}
		}
		return PathofLegendTrophies;
	}

	public Integer getLastPathOfLegendLeagueNumber() {
		if (lastPathOfLegendLeagueNumber == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("lastPathOfLegendSeasonResult")
					&& !apiresult.isNull("lastPathOfLegendSeasonResult")) {
				JSONObject lastPathOfLegendSeasonResult = apiresult.getJSONObject("lastPathOfLegendSeasonResult");
				lastPathOfLegendLeagueNumber = lastPathOfLegendSeasonResult.getInt("leagueNumber");
			}
		}
		return lastPathOfLegendLeagueNumber;
	}

	public Integer getLastPathOfLegendTrophies() {
		if (lastPathOfLegendTrophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("lastPathOfLegendSeasonResult")
					&& !apiresult.isNull("lastPathOfLegendSeasonResult")) {
				JSONObject lastPathOfLegendSeasonResult = apiresult.getJSONObject("lastPathOfLegendSeasonResult");
				lastPathOfLegendTrophies = lastPathOfLegendSeasonResult.getInt("trophies");
			}
		}
		return lastPathOfLegendTrophies;
	}

	public Boolean isMarked() {
		if (mark == null) {
			if (getClanDB() != null) {
				Boolean marked = DBUtil.getValueFromSQL("SELECT marked FROM clan_members WHERE player_tag = ?",
						Boolean.class, tag);
				if (marked == null) {
					DBUtil.executeUpdate("UPDATE clan_members SET marked = FALSE WHERE player_tag = ?", tag);
					mark = false;
				} else {
					mark = marked;
				}
			}
		}
		return mark == null ? false : mark;
	}

	public String getNote() {
		if (note == null) {
			if (getClanDB() != null) {
				note = DBUtil.getValueFromSQL("SELECT note FROM clan_members WHERE player_tag = ?",
						String.class, tag);
			}
		}
		return note;
	}

	public Integer getCWFame() {
		if (cwfame == null) {
			if (getClanAPI() != null) {
				Clan c = getClanAPI();
				ArrayList<Player> cwfamelist = c.getCWFamePlayerList();
				for (Player t : cwfamelist) {
					if (t.getTag().equals(tag)) {
						cwfame = t.getCWFame();
						clantagcwdone = t.getClantagCWDone();
						break;
					}
				}
			}
		}
		return cwfame;
	}

	public String getClantagCWDone() {
		if (clantagcwdone == null) {
			// same logic here
			getCWFame();
		}
		return clantagcwdone;
	}

	public Integer getDecksUsed() {
		return decksUsed;
	}

	public boolean isHiddenColeader() {
		if (getClanDB() == null) {
			return false;
		}
		String rolestring = DBUtil.getValueFromSQL("SELECT clan_role FROM clan_members WHERE player_tag = ?",
				String.class, tag);
		return "hiddencoleader".equals(rolestring);
	}

	public Integer getWinsAPI() {
		if (wins == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("wins")) {
				wins = apiresult.getInt("wins");
			}
		}
		return wins;
	}

	public Integer getExpLevelAPI() {
		if (expLevel == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("expLevel")) {
				expLevel = apiresult.getInt("expLevel");
			}
		}
		return expLevel;
	}

	// ============================================================
	// Centralized Wins Calculation Methods
	// ============================================================

	/**
	 * Helper class to hold wins data with warning flag
	 */
	public static class WinsData {
		public final int wins;
		public final boolean hasWarning;

		public WinsData(int wins, boolean hasWarning) {
			this.wins = wins;
			this.hasWarning = hasWarning;
		}
	}

	/**
	 * Helper class to hold wins record from database
	 */
	public static class WinsRecord {
		public final int wins;
		public final OffsetDateTime recordedAt;

		public WinsRecord(int wins, OffsetDateTime recordedAt) {
			this.wins = wins;
			this.recordedAt = recordedAt;
		}
	}

	/**
	 * Calculate monthly wins for a specific month and year
	 * 
	 * @param year             The year
	 * @param month            The month (1-12)
	 * @param isCurrentMonth   Whether this is the current month
	 * @param startOfMonth     Start of the month
	 * @param startOfNextMonth Start of the next month
	 * @param zone             Time zone
	 * @return WinsData containing wins count and warning flag
	 */
	public WinsData getMonthlyWins(int year, int month, boolean isCurrentMonth,
			java.time.ZonedDateTime startOfMonth, java.time.ZonedDateTime startOfNextMonth,
			java.time.ZoneId zone) {

		// Check if any data exists for this player, if not save current data first
		if (!hasAnyWinsData()) {
			savePlayerWins();
		}

		if (isCurrentMonth) {
			// Current month: get start of month data and fetch current wins from API
			WinsRecord startRecord = getWinsAtOrAfter(startOfMonth);

			Integer currentWins = getWinsAPI();
			if (currentWins == null || startRecord == null) {
				return new WinsData(0, true);
			}

			int winsThisMonth = currentWins - startRecord.wins;
			boolean hasWarning = !isStartOfMonth(startRecord.recordedAt, startOfMonth);
			return new WinsData(Math.max(winsThisMonth, 0), hasWarning);
		} else {
			// Past month: get data from start of month and start of next month
			WinsRecord startRecord = getWinsAtOrAfter(startOfMonth);
			WinsRecord endRecord = getWinsAtOrAfter(startOfNextMonth);

			if (startRecord == null || endRecord == null) {
				return new WinsData(0, true);
			}

			int winsInMonth = endRecord.wins - startRecord.wins;
			boolean startIsMonthStart = isStartOfMonth(startRecord.recordedAt, startOfMonth);
			boolean endIsMonthStart = isStartOfMonth(endRecord.recordedAt, startOfNextMonth);
			boolean hasWarning = !startIsMonthStart || !endIsMonthStart;

			return new WinsData(winsInMonth, hasWarning);
		}
	}

	/**
	 * Get wins for the current month
	 * 
	 * @return WinsData containing wins count and warning flag
	 */
	public WinsData getCurrentMonthWins() {
		java.time.ZoneId zone = java.time.ZoneId.of("Europe/Berlin");
		java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);
		int year = now.getYear();
		int month = now.getMonthValue();

		java.time.ZonedDateTime startOfMonth = java.time.ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone);

		return getMonthlyWins(year, month, true, startOfMonth, startOfMonth.plusMonths(1), zone);
	}

	/**
	 * Check if any wins data exists for this player in the database
	 * 
	 * @return true if data exists, false otherwise
	 */
	private boolean hasAnyWinsData() {
		String sql = "SELECT 1 FROM player_wins WHERE player_tag = ? LIMIT 1";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			System.err.println("Fehler beim Prüfen der Wins-Daten für Spieler " + tag + ": " + e.getMessage());
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Get wins record at or after a specific date/time
	 * 
	 * @param dateTime The date/time to search from
	 * @return WinsRecord or null if not found
	 */
	public WinsRecord getWinsAtOrAfter(java.time.ZonedDateTime dateTime) {
		String sql = "SELECT wins, recorded_at FROM player_wins WHERE player_tag = ? AND recorded_at >= ? ORDER BY recorded_at ASC LIMIT 1";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			pstmt.setObject(2, dateTime.toOffsetDateTime());

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					int wins = rs.getInt("wins");
					OffsetDateTime recordedAt = rs.getObject("recorded_at", OffsetDateTime.class);
					return new WinsRecord(wins, recordedAt);
				}
			}
		} catch (SQLException e) {
			System.err.println("Fehler beim Abrufen der Wins-Daten für Spieler " + tag + ": " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Check if a recorded time is at the start of a month
	 * 
	 * @param recordedAt    The recorded time
	 * @param expectedStart The expected start of month
	 * @return true if recorded time is on the first day of the month
	 */
	public boolean isStartOfMonth(OffsetDateTime recordedAt, java.time.ZonedDateTime expectedStart) {
		java.time.ZonedDateTime recordedZoned = recordedAt.atZoneSameInstant(expectedStart.getZone());
		return recordedZoned.toLocalDate().equals(expectedStart.toLocalDate());
	}

	/**
	 * Save current wins for this player to the database
	 */
	public void savePlayerWins() {
		try {
			Integer wins = getWinsAPI();
			if (wins != null) {
				OffsetDateTime now = OffsetDateTime.now(java.time.ZoneId.of("Europe/Berlin"));
				String sql = "INSERT INTO player_wins (player_tag, recorded_at, wins) VALUES (?, ?, ?) "
						+ "ON CONFLICT (player_tag, recorded_at) DO UPDATE SET wins = ?";
				DBUtil.executeUpdate(sql, tag, now, wins, wins);
				System.out.println("Wins gespeichert für " + tag + ": " + wins);
			}
		} catch (Exception e) {
			System.err.println("Fehler beim Speichern der Wins für " + tag + ": " + e.getMessage());
		}
	}

}
