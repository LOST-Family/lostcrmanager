package lostcrmanager;

import java.io.File;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import commands.admin.restart;
import commands.kickpoints.clanconfig;
import commands.kickpoints.kpadd;
import commands.kickpoints.kpaddreason;
import commands.kickpoints.kpclan;
import commands.kickpoints.kpedit;
import commands.kickpoints.kpeditreason;
import commands.kickpoints.kpinfo;
import commands.kickpoints.kpmember;
import commands.kickpoints.kpremove;
import commands.kickpoints.kpremovereason;
import commands.links.link;
import commands.links.playerinfo;
import commands.links.unlink;
import commands.memberlist.addmember;
import commands.memberlist.editmember;
import commands.memberlist.listmembers;
import commands.memberlist.memberstatus;
import commands.memberlist.removemember;
import commands.memberlist.togglemark;
import commands.memberlist.transfermember;
import commands.reminders.remindersadd;
import commands.reminders.remindersinfo;
import commands.reminders.remindersremove;
import commands.util.checkroles;
import commands.util.cwfails;
import commands.util.leaguetrophylist;
import commands.util.statslist;
import commands.wins.wins;
import commands.wins.winsfails;
import datautil.APIUtil;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import webserver.LinkWebServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Bot extends ListenerAdapter {

	private final static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static final int MIN_LEVEL_FOR_PING = 45;

	private static JDA jda;
	public static String VERSION;
	public static String guild_id;
	public static String api_key;
	public static String url;
	public static String user;
	public static String password;
	public static String exmemberroleid;
	public static String seasonstringfallback;

	public static void main(String[] args) throws Exception {
		VERSION = "1.2.5";
		guild_id = System.getenv("CR_MANAGER_GUILD_ID");
		api_key = System.getenv("CR_MANAGER_API_KEY");
		url = System.getenv("CR_MANAGER_DB_URL");
		user = System.getenv("CR_MANAGER_DB_USER");
		password = System.getenv("CR_MANAGER_DB_PASSWORD");
		exmemberroleid = System.getenv("CR_MANAGER_EXMEMBER_ROLEID");

		String token = System.getenv("CR_MANAGER_TOKEN");

		if (datautil.Connection.checkDB()) {
			System.out.println("Verbindung zur Datenbank funktioniert.");
		} else {
			System.out.println("Verbindung zur Datenbank fehlgeschlagen.");
		}

		datautil.Connection.tablesExists();
		datautil.Connection.migrateRemindersTable();
		datautil.Connection.migrateClanMembersTable();
		
		// Start REST API server
		LinkWebServer.start();
		
		startNameUpdates();
		startLoadingLists();
		startReminders();
		startMonthlyWinsSave();

		JDABuilder.createDefault(token).enableIntents(GatewayIntent.GUILD_MEMBERS)
				.setMemberCachePolicy(MemberCachePolicy.ALL).setChunkingFilter(ChunkingFilter.ALL)
				.setActivity(Activity.playing("mit deinen Kickpunkten"))
				.addEventListeners(new Bot(), new link(), new unlink(), new restart(), new addmember(),
						new removemember(), new listmembers(), new editmember(), new playerinfo(), new memberstatus(),
						new kpaddreason(), new kpremovereason(), new kpeditreason(), new kpadd(), new kpmember(),
						new kpremove(), new kpedit(), new kpinfo(), new kpclan(), new clanconfig(),
						new leaguetrophylist(), new transfermember(), new togglemark(), new cwfails(),
						new remindersadd(), new remindersremove(), new remindersinfo(), new wins(), new statslist(),
						new checkroles(), new winsfails())
				.build();
	}

	public static void registerCommands(JDA jda, String guildId) {
		Guild guild = jda.getGuildById(guildId);
		if (guild != null) {
			guild.updateCommands().addCommands(Commands
					.slash("link", "Verlinke einen Clash Royale Account mit einem Discord User oder einer UserID.")
					.addOption(OptionType.STRING, "tag", "Der Tag des Clash Royale Accounts", true)
					.addOption(OptionType.MENTIONABLE, "user", "Der User, mit dem der Account verlinkt werden soll.")
					.addOption(OptionType.STRING, "userid",
							"Die ID des Users, mit dem der Account verlinkt werden soll."),
					Commands.slash("unlink", "Lösche eine Verlinkung eines Clash Royale Accounts.")
							.addOptions(new OptionData(OptionType.STRING, "tag",
									"Der Spieler, wessen Verknüpfung entfernt werden soll", true)
									.setAutoComplete(true)),
					Commands.slash("restart", "Startet den Bot neu."),
					Commands.slash("addmember", "Füge einen Spieler zu einem Clan hinzu.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, zu welchem der Spieler hinzugefügt werden soll", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher hinzugefügt werden soll", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "role",
									"Die Rolle, welche der Spieler bekommen soll", true).setAutoComplete(true)),
					Commands.slash("removemember", "Entferne einen Spieler aus seinem Clan.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher entfernt werden soll", true).setAutoComplete(true)),
					Commands.slash("togglemark", "Schaltet die Markierung eines Spielers in einem Clan an/aus.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher markiert/entmarkiert werden soll", true)
									.setAutoComplete(true)),
					Commands.slash("listmembers", "Liste aller Spieler in einem Clan.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher ausgegeben werden soll.", true).setAutoComplete(true)),
					Commands.slash("editmember", "Ändere die Rolle eines Mitglieds.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher bearbeitet werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "role",
									"Die Rolle, welcher der Spieler sein soll.", true).setAutoComplete(true)),
					Commands.slash("playerinfo",
							"Info eines Spielers. Bei Eingabe eines Parameters werden Infos über diesen Nutzer aufgelistet.")
							.addOptions(new OptionData(OptionType.MENTIONABLE, "user",
									"Der User, über welchem Informationen über verlinkte Accounts gesucht sind."))
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, über welchem Informationen gesucht sind.").setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "getapifile",
									"(Optional) Wenn 'true', wird die API-Datei des Spielers mitgesendet")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("memberstatus",
							"Status über einen Clan, welche Spieler keine Mitglieder sind und welche Mitglieder fehlen.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher ausgegeben werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
									"(Optional) Wenn 'true', werden Leader, Co-Leader und Admins von der Prüfung ausgeschlossen")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("kpaddreason", "Erstelle einen vorgefertigten Kickpunktgrund.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true))
							.addOptions(
									new OptionData(OptionType.INTEGER, "amount", "Die Anzahl der Kickpunkte.", true)),
					Commands.slash("kpremovereason", "Lösche einen vorgefertigten Kickpunktgrund.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true)
									.setAutoComplete(true)),
					Commands.slash("kpeditreason", "Aktualisiere die Anzahl der Kickpunkte für eine Grund-Vorlage.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.INTEGER, "amount", "Die Anzahl.", true)),
					Commands.slash("kpadd", "Gebe einem Spieler Kickpunkte.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher die Kickpunkte erhält.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Die Grund-Vorlage.")
									.setAutoComplete(true)),
					Commands.slash("kpmember", "Zeige alle Kickpunkte eines Spielers an.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("kpremove", "Lösche einen Kickpunkt.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),
					Commands.slash("kpedit", "Editiere einen Kickpunkt.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),
					Commands.slash("kpinfo", "Infos über Kickpunkt-Gründe eines Clans.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Die Clan, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("kpclan", "Zeige die Kickpunktanzahlen aller Spieler in einem Clan.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("clanconfig", "Ändere Einstellungen an einem Clan.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher bearbeitet werden soll.", true).setAutoComplete(true)),
					Commands.slash("leaguetrophylist", "Sortierte Rangliste.")
							.addOptions(new OptionData(OptionType.STRING, "timestamp",
									"Der Zeitpunkt der gespeicherten Liste", true).setAutoComplete(true)),
					Commands.slash("transfermember", "Transferiere einen Spieler in einen anderen Clan.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher transferiert werden soll", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, zu welchem der Spieler hinzugefügt werden soll", true)
									.setAutoComplete(true)),
					Commands.slash("cwfails",
							"Überprüfe einen Clan auf CW-Punkte mit einer Hürde, die zu überwinden gilt.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher überprüft werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "threshold",
									"Die Hürde, welche jeder Spieler überwunden haben sollte", true))
							.addOptions(new OptionData(OptionType.STRING, "kpreason",
									"(Optional) Der Kickpunkt-Grund für jeden Spieler, der die Hürde nicht erreicht hat.")
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "min_threshold",
									"(Optional) Minimale Punktzahl - Spieler darunter werden nicht angezeigt")
									.setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
									"(Optional) Wenn 'true', werden Leader, Co-Leader und Admins von der Prüfung ausgeschlossen")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("remindersadd", "Erstelle einen Reminder für Clan War Beteiligung.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für welchen der Reminder erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.CHANNEL, "channel",
									"Der Kanal, in dem der Reminder gesendet wird.", true))
							.addOptions(new OptionData(OptionType.STRING, "time",
									"Die Uhrzeit für den Reminder (Format: HH:mm, z.B. 14:30)", true))
							.addOptions(new OptionData(OptionType.STRING, "weekday",
									"Der Wochentag für den Reminder (z.B. monday, tuesday, ...)", true)
									.setAutoComplete(true)),
					Commands.slash("remindersremove", "Entferne einen Reminder.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Reminders. Ist unter /remindersinfo zu sehen.", true)),
					Commands.slash("remindersinfo", "Zeige alle Reminder für einen Clan an.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für welchen Reminder angezeigt werden sollen.", true)
									.setAutoComplete(true)),
					Commands.slash("wins", "Zeige die Wins-Statistik für einen Spieler oder Clan in einem bestimmten Monat.")
							.addOptions(new OptionData(OptionType.STRING, "month",
									"Der Monat, für den die Wins angezeigt werden sollen.", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, für den die Wins angezeigt werden sollen.")
									.setAutoComplete(true).setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, für den die Wins angezeigt werden sollen.")
									.setAutoComplete(true).setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
									"(Optional) Wenn 'true', werden Leader, Co-Leader und Admins von der Liste ausgeschlossen")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("statslist", "Erstelle eine konfigurierbare Stats-Liste für einen oder mehrere Clans.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Clan(s) kommagetrennt, z.B. #CLANTAG1,#CLANTAG2", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "display_fields",
									"Anzuzeigende Felder (kommagetrennt, z.B. Wins,Trophies,PoLLeagueNumber)", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "sort_fields",
									"Sortierfelder (kommagetrennt, z.B. PoLLeagueNumber,Trophies). Standard: alphabetisch")
									.setAutoComplete(true).setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "roles_sorting",
									"(Optional) 'true' für Rollen-Sortierung, 'clans' für Clans+Rollen-Sortierung")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("checkroles",
							"Überprüfe, ob Clan-Mitglieder die korrekten Discord-Rollen haben.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher überprüft werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "ignore_hiddencoleaders",
									"(Optional) Wenn 'true', werden versteckte Vize-Anführer ignoriert")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("winsfails",
							"Überprüfe einen Clan auf monatliche Wins mit einer Hürde, die zu überwinden gilt.")
							.addOptions(new OptionData(OptionType.STRING, "clan",
									"Der Clan, welcher überprüft werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "month",
									"Der Monat, für den die Wins überprüft werden sollen.", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "threshold",
									"Die Hürde, welche jeder Spieler überwunden haben sollte", true))
							.addOptions(new OptionData(OptionType.STRING, "kpreason",
									"(Optional) Der Kickpunkt-Grund für jeden Spieler, der die Hürde nicht erreicht hat.")
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "min_threshold",
									"(Optional) Minimale Wins - Spieler darunter werden nicht angezeigt")
									.setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
									"(Optional) Wenn 'true', werden Leader, Co-Leader und Admins von der Prüfung ausgeschlossen")
									.setAutoComplete(true).setRequired(false)))
					.queue();
		}
	}

	@Override
	public void onReady(ReadyEvent event) {
		setJda(event.getJDA());
		registerCommands(event.getJDA(), guild_id);
	}

	@Override
	public void onShutdown(ShutdownEvent event) {
		LinkWebServer.stop();
		stopScheduler();
	}

	public static void setJda(JDA instance) {
		jda = instance;
	}

	public static JDA getJda() {
		return jda;
	}

	public static void startLoadingLists() {
		System.out.println("Jede Stunde wird nun die Leaguetrophylist erstellt. " + System.currentTimeMillis());
		Runnable task = () -> {
			Thread thread = new Thread(() -> {
				File folder = new File(leaguetrophylist.getRunningJarDirectory(), "CRManager_ListFiles");
				Pattern pattern = Pattern.compile("Liste_(\\d+)\\.txt");

				ZonedDateTime twoMonthsAgo = ZonedDateTime.now().minusMonths(2);
				long twoMonthsAgoMillis = twoMonthsAgo.toInstant().toEpochMilli();

				if (folder.exists() && folder.isDirectory()) {
					File[] files = folder.listFiles();
					if (files != null) {
						for (File file : files) {
							Matcher matcher = pattern.matcher(file.getName());
							if (matcher.matches()) {
								long millis = Long.parseLong(matcher.group(1));
								if (millis < twoMonthsAgoMillis) {
									boolean deleted = file.delete();
									if (deleted) {
										System.out.println("Gelöscht: " + file.getName());
									} else {
										System.err.println("Konnte nicht löschen: " + file.getName());
									}
								}
							}
						}
					}
				} else {
					System.out.println("Der Ordner existiert nicht oder ist kein Verzeichnis");
				}
				leaguetrophylist.saveNewList();

			});
			thread.start();
		};
		scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.HOURS);
	}

	public static void startNameUpdates() {
		System.out.println("Alle 2h werden nun die Namen aktualisiert. " + System.currentTimeMillis());
		Runnable task = () -> {
			Thread thread = new Thread(() -> {

				String sql = "SELECT cr_tag FROM players";
				for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
					try {
						Player p = new Player(tag);
						DBUtil.executeUpdate("UPDATE players SET name = ? WHERE cr_tag = ?", p.getNameAPI(), tag);
					} catch (Exception e) {
						System.out.println(
								"Beim Updaten des Namens von Spieler mit Tag " + tag + " ist ein Fehler aufgetreten.");
					}
				}

			});
			thread.start();
		};
		scheduler.scheduleAtFixedRate(task, 0, 2, TimeUnit.HOURS);
	}

	public static void startReminders() {
		System.out.println("Reminder-Check wird gestartet. Prüfung alle 5 Minuten. " + System.currentTimeMillis());
		Runnable task = () -> {
			Thread thread = new Thread(() -> {
				checkReminders();
			});
			thread.start();
		};
		scheduler.scheduleAtFixedRate(task, 0, 5, TimeUnit.MINUTES);
	}

	private static void checkReminders() {
		ZoneId zoneId = ZoneId.of("Europe/Berlin");
		ZonedDateTime now = ZonedDateTime.now(zoneId);
		DayOfWeek today = now.getDayOfWeek();
		LocalTime currentTime = now.toLocalTime();
		LocalDate currentDate = now.toLocalDate();

		// Get all reminders
		String sql = "SELECT id, clantag, channelid, time, last_sent_date, weekday FROM reminders";
		try (PreparedStatement pstmt = datautil.Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int reminderId = rs.getInt("id");
					String clantag = rs.getString("clantag");
					String channelId = rs.getString("channelid");
					Time reminderTime = rs.getTime("time");
					Date lastSentDate = rs.getDate("last_sent_date");
					String weekday = rs.getString("weekday");
					LocalTime reminderLocalTime = reminderTime.toLocalTime();

					// Check if today matches the configured weekday
					if (weekday != null && !isDayOfWeek(today, weekday)) {
						continue; // Not the configured day
					}

					// Check if already sent today
					if (lastSentDate != null) {
						LocalDate lastSentLocalDate = lastSentDate.toLocalDate();
						if (lastSentLocalDate.equals(currentDate)) {
							// Already sent today, skip
							continue;
						}
					}

					// Check if reminder should be sent (within 5 minute window)
					long minutesDiff = java.time.Duration.between(reminderLocalTime, currentTime).toMinutes();
					if (minutesDiff >= 0 && minutesDiff < 5) {
						sendReminder(reminderId, clantag, channelId);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static boolean isDayOfWeek(DayOfWeek dayOfWeek, String weekdayString) {
		if (weekdayString == null) {
			return false;
		}
		String normalized = weekdayString.toLowerCase();
		switch (normalized) {
		case "monday":
			return dayOfWeek == DayOfWeek.MONDAY;
		case "tuesday":
			return dayOfWeek == DayOfWeek.TUESDAY;
		case "wednesday":
			return dayOfWeek == DayOfWeek.WEDNESDAY;
		case "thursday":
			return dayOfWeek == DayOfWeek.THURSDAY;
		case "friday":
			return dayOfWeek == DayOfWeek.FRIDAY;
		case "saturday":
			return dayOfWeek == DayOfWeek.SATURDAY;
		case "sunday":
			return dayOfWeek == DayOfWeek.SUNDAY;
		default:
			return false;
		}
	}

	private static void sendReminder(int reminderId, String clantag, String channelId) {
		try {
			// Get clan info
			Clan clan = new Clan(clantag);
			if (!clan.ExistsDB()) {
				System.out.println("Clan " + clantag + " existiert nicht mehr.");
				return;
			}

			// Get current river race data
			String json = APIUtil.getCurrentRiverRaceJson(clantag);
			if (json == null) {
				System.out.println("Konnte River Race Daten für " + clantag + " nicht abrufen.");
				return;
			}

			JSONObject data = new JSONObject(json);
			JSONObject clanData = data.getJSONObject("clan");
			if(clanData.has("finishTime")) {
				System.out.println("Der Clan War für " + clantag + " ist bereits beendet.");
				updateLastSentDate(reminderId);
				return;
			}
			JSONArray participants = clanData.getJSONArray("participants");

			// Build a map of player tag to decksUsedToday from API
			java.util.HashMap<String, Integer> apiDecksUsedMap = new java.util.HashMap<>();
			for (int i = 0; i < participants.length(); i++) {
				JSONObject participant = participants.getJSONObject(i);
				String playerTag = participant.getString("tag");
				int decksUsedToday = participant.getInt("decksUsedToday");
				apiDecksUsedMap.put(playerTag, decksUsedToday);
			}

			// Get database memberlist and set decksUsed for each player
			ArrayList<Player> playersWithDecksUsed = new ArrayList<>();
			ArrayList<Player> dbMembers = clan.getPlayersDB();
			for (Player player : dbMembers) {
				String playerTag = player.getTag();
				if (apiDecksUsedMap.containsKey(playerTag)) {
					// Player is in clan, set their decks used
					player.setDecksUsed(apiDecksUsedMap.get(playerTag));
				} else {
					// Player is not in API list (not in clan)
					player.setDecksUsed(null);
				}
				playersWithDecksUsed.add(player);
			}

			// Iterate through players to create reminder list
			ArrayList<String> reminderList = new ArrayList<>();
			for (Player player : playersWithDecksUsed) {
				Integer decksUsed = player.getDecksUsed();
				// Include players not in clan (decksUsed == null) or with <4 decks
				if (decksUsed == null || decksUsed < 4) {
					if (player.isHiddenColeader()) {
						continue; // Skip hidden co-leaders
					}
					String playerName = player.getNameDB();
					// Only ping players with level >= MIN_LEVEL_FOR_PING
					Integer expLevel = player.getExpLevelAPI();
					boolean canPing = expLevel != null && expLevel >= MIN_LEVEL_FOR_PING;
					if (player.getUser() != null && canPing) {
						String userId = player.getUser().getUserID();
						if (decksUsed == null) {
							reminderList.add("<@" + userId + "> " + playerName + " - nicht im Clan");
						} else {
							reminderList.add("<@" + userId + "> " + playerName + " - " + decksUsed + "/4");
						}
					} else {
						if (decksUsed == null) {
							reminderList.add(playerName + " - nicht im Clan");
						} else {
							reminderList.add(playerName + " - " + decksUsed + "/4");
						}
					}
				}
			}

			if (reminderList.isEmpty()) {
				System.out.println("Keine Spieler mit weniger als 4 Decks für " + clantag);
				return;
			}

			// Send reminder message
			if (jda != null) {
				Guild guild = jda.getGuildById(guild_id);
				if (guild != null) {
					TextChannel channel = guild.getTextChannelById(channelId);
					if (channel != null) {
						// Build messages with split logic
						ArrayList<String> messages = new ArrayList<>();
						StringBuilder currentMessage = new StringBuilder();
						currentMessage.append("⚠️ **Clan War Reminder - " + clan.getNameDB() + "**\n\n");
						currentMessage.append("Folgende Spieler haben heute weniger als 4 Decks verwendet:\n\n");

						for (String playerInfo : reminderList) {
							String line = "• " + playerInfo + "\n";
							// Check if adding this line would exceed 1900 characters
							if (currentMessage.length() + line.length() > 1900) {
								// Save current message and start a new one
								messages.add(currentMessage.toString());
								currentMessage = new StringBuilder();
							}
							currentMessage.append(line);
						}

						// Add the final message
						if (currentMessage.length() > 0) {
							messages.add(currentMessage.toString());
						}

						// Send all messages
						sendMessagesSequentially(channel, messages, reminderId, clantag);
					} else {
						System.err.println("Kanal " + channelId + " nicht gefunden.");
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Fehler beim Senden des Reminders für " + clantag + ": " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void sendMessagesSequentially(TextChannel channel, ArrayList<String> messages, int reminderId,
			String clantag) {
		sendMessagesSequentially(channel, messages, 0, reminderId, clantag);
	}

	private static void sendMessagesSequentially(TextChannel channel, ArrayList<String> messages, int index,
			int reminderId, String clantag) {
		if (index >= messages.size()) {
			// All messages sent successfully
			System.out.println("Reminder erfolgreich gesendet für " + clantag);
			updateLastSentDate(reminderId);
			return;
		}

		channel.sendMessage(messages.get(index)).queue(_ -> {
			// Send next message
			sendMessagesSequentially(channel, messages, index + 1, reminderId, clantag);
		}, error -> {
			System.err
					.println("Fehler beim Senden des Reminders (Nachricht " + (index + 1) + "): " + error.getMessage());
		});
	}

	private static void updateLastSentDate(int reminderId) {
		try {
			LocalDate currentDate = LocalDate.now();
			Date sqlDate = Date.valueOf(currentDate);
			String sql = "UPDATE reminders SET last_sent_date = ? WHERE id = ?";
			try (PreparedStatement pstmt = datautil.Connection.getConnection().prepareStatement(sql)) {
				pstmt.setDate(1, sqlDate);
				pstmt.setInt(2, reminderId);
				pstmt.executeUpdate();
				System.out.println("Updated last_sent_date for reminder ID: " + reminderId);
			}
		} catch (SQLException e) {
			System.err.println("Fehler beim Aktualisieren von last_sent_date: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void startMonthlyWinsSave() {
		System.out.println("Monthly Wins-Save wird gestartet. Prüfung jede Stunde. " + System.currentTimeMillis());
		Runnable task = () -> {
			Thread thread = new Thread(() -> {
				checkAndSaveMonthlyWins();
			});
			thread.start();
		};
		scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.HOURS);
	}

	private static void checkAndSaveMonthlyWins() {
		ZoneId zoneId = ZoneId.of("Europe/Berlin");
		ZonedDateTime now = ZonedDateTime.now(zoneId);
		int dayOfMonth = now.getDayOfMonth();

		// Delete data older than a year
		cleanupOldWinsData(zoneId, now);

		// Only save on the 1st day of the month (or 2nd day to be safe with timezones)
		if (dayOfMonth == 1 || dayOfMonth == 2) {
			// Check if we already saved this month
			LocalDate today = now.toLocalDate();
			LocalDate firstOfMonth = today.withDayOfMonth(1);

			String sql = "SELECT COUNT(*) as cnt FROM player_wins WHERE recorded_at >= ? AND recorded_at < ?";
			try (PreparedStatement pstmt = datautil.Connection.getConnection().prepareStatement(sql)) {
				ZonedDateTime startOfDay = firstOfMonth.atStartOfDay(zoneId);
				ZonedDateTime endOfDay = firstOfMonth.plusDays(2).atStartOfDay(zoneId);
				pstmt.setObject(1, startOfDay.toOffsetDateTime());
				pstmt.setObject(2, endOfDay.toOffsetDateTime());

				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						int count = rs.getInt("cnt");
						if (count > 0) {
							System.out.println("Wins für diesen Monat bereits gespeichert (" + count + " Einträge). Überspringe.");
							return;
						}
					}
				}
			} catch (SQLException e) {
				System.err.println("Fehler beim Prüfen der monatlichen Wins: " + e.getMessage());
				e.printStackTrace();
			}

			// Save wins for all players
			System.out.println("Speichere Wins für alle Spieler zum Monatsanfang...");
			wins.saveAllPlayerWins();
			System.out.println("Wins für alle Spieler gespeichert.");
		}
	}

	private static void cleanupOldWinsData(ZoneId zoneId, ZonedDateTime now) {
		// Delete data older than 1 year
		ZonedDateTime oneYearAgo = now.minusYears(1);
		String deleteSql = "DELETE FROM player_wins WHERE recorded_at < ?";
		try (PreparedStatement pstmt = datautil.Connection.getConnection().prepareStatement(deleteSql)) {
			pstmt.setObject(1, oneYearAgo.toOffsetDateTime());
			int deleted = pstmt.executeUpdate();
			if (deleted > 0) {
				System.out.println("Alte Wins-Daten gelöscht: " + deleted + " Einträge älter als 1 Jahr.");
			}
		} catch (SQLException e) {
			System.err.println("Fehler beim Löschen alter Wins-Daten: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void stopScheduler() {
		scheduler.shutdown();
	}

}
