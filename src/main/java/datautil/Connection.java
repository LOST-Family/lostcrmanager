package datautil;

import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import lostcrmanager.Bot;

public class Connection {

	public static String url;
	public static String user;
	public static String password;

	private static java.sql.Connection connection;

	public static boolean checkDB() {

		url = Bot.url;
		user = Bot.user;
		password = Bot.password;

		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			if (conn != null) {
				connection = conn;
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			System.out.println("Verbindungsfehler: " + e.getMessage());
			return false;
		}
	}

	public static void tablesExists() {
		ArrayList<String> tableNames = new ArrayList<>();
		tableNames.add("clans");
		tableNames.add("users");
		tableNames.add("players");
		tableNames.add("clan_members");
		tableNames.add("clan_settings");
		tableNames.add("kickpoint_reasons");
		tableNames.add("kickpoints");
		tableNames.add("reminders");
		tableNames.add("player_wins");
		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			DatabaseMetaData dbm = conn.getMetaData();

			for (String tableName : tableNames) {
				try (ResultSet tables = dbm.getTables(null, null, tableName, null)) {
					if (tables.next()) {
						System.out.println("Tabelle '" + tableName + "' existiert schon.");
					} else {
						System.out.println("Tabelle '" + tableName + "' existiert nicht. Erstelle sie jetzt...");
						String createTableSQL = null;
						switch (tableName) {
							case "clans":
								createTableSQL = "CREATE TABLE " + tableName + " (tag TEXT PRIMARY KEY," + "name TEXT,"
										+ "index BIGINT," + "guild_id CHARACTER VARYING(19),"
										+ "leader_roleid CHARACTER VARYING(19),"
										+ "coleader_roleid CHARACTER VARYING(19),"
										+ "elder_roleid CHARACTER VARYING(19),"
										+ "member_roleid CHARACTER VARYING(19))";
								break;
							case "users":
								createTableSQL = "CREATE TABLE " + tableName
										+ " (discord_id CHARACTER VARYING(19) PRIMARY KEY," + "is_admin BOOLEAN)";
								break;
							case "players":
								createTableSQL = "CREATE TABLE " + tableName + " (cr_tag TEXT PRIMARY KEY,"
										+ "discord_id CHARACTER VARYING(19), name TEXT)";
								break;
							case "clan_members":
								createTableSQL = "CREATE TABLE " + tableName + " (player_tag TEXT PRIMARY KEY,"
										+ "clan_tag TEXT," + "clan_role TEXT)";
								break;
							case "clan_settings":
								createTableSQL = "CREATE TABLE " + tableName + " (clan_tag TEXT PRIMARY KEY,"
										+ "max_kickpoints BIGINT," + "kickpoints_expire_after_days SMALLINT)";
								break;
							case "kickpoint_reasons":
								createTableSQL = "CREATE TABLE " + tableName + " (name TEXT," + "clan_tag text,"
										+ "amount SMALLINT," + "PRIMARY KEY (name, clan_tag))";
								break;
							case "kickpoints":
								createTableSQL = "CREATE TABLE " + tableName + " (id BIGINT PRIMARY KEY,"
										+ "player_tag CHARACTER VARYING(19)," + "date TIMESTAMPTZ," + "amount BIGINT,"
										+ "description CHARACTER VARYING(100),"
										+ "created_by_discord_id CHARACTER VARYING(19)," + "created_at TIMESTAMPTZ,"
										+ "expires_at TIMESTAMPTZ)";
								break;
							case "reminders":
								createTableSQL = "CREATE TABLE " + tableName + " (id BIGINT PRIMARY KEY,"
										+ "clantag TEXT," + "channelid TEXT," + "time TIME," + "last_sent_date DATE)";
								break;
							case "player_wins":
								createTableSQL = "CREATE TABLE " + tableName + " (player_tag TEXT,"
										+ "recorded_at TIMESTAMPTZ," + "wins INTEGER,"
										+ "PRIMARY KEY (player_tag, recorded_at))";
								break;
						}

						try (Statement stmt = conn.createStatement()) {
							stmt.executeUpdate(createTableSQL);
							System.out.println("Tabelle '" + tableName + "' wurde erstellt.");
						}
					}

				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public static void migrateRemindersTable() {
		// Add last_sent_date column to reminders table if it doesn't exist
		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			DatabaseMetaData dbm = conn.getMetaData();
			try (ResultSet columns = dbm.getColumns(null, null, "reminders", "last_sent_date")) {
				if (!columns.next()) {
					// Column doesn't exist, add it
					System.out.println("Adding 'last_sent_date' column to reminders table...");
					String alterTableSQL = "ALTER TABLE reminders ADD COLUMN last_sent_date DATE";
					try (Statement stmt = conn.createStatement()) {
						stmt.executeUpdate(alterTableSQL);
						System.out.println("Column 'last_sent_date' added successfully.");
					}
				} else {
					System.out.println("Column 'last_sent_date' already exists in reminders table.");
				}
			}

			// Add weekday column to reminders table if it doesn't exist
			try (ResultSet columns = dbm.getColumns(null, null, "reminders", "weekday")) {
				if (!columns.next()) {
					// Column doesn't exist, add it
					System.out.println("Adding 'weekday' column to reminders table...");
					String alterTableSQL = "ALTER TABLE reminders ADD COLUMN weekday TEXT";
					try (Statement stmt = conn.createStatement()) {
						stmt.executeUpdate(alterTableSQL);
						System.out.println("Column 'weekday' added successfully.");
					}
				} else {
					System.out.println("Column 'weekday' already exists in reminders table.");
				}
			}
		} catch (SQLException e) {
			System.err.println("Error migrating reminders table: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void migrateClanMembersTable() {
		// Add note column to clan_members table if it doesn't exist
		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			DatabaseMetaData dbm = conn.getMetaData();
			try (ResultSet columns = dbm.getColumns(null, null, "clan_members", "note")) {
				if (!columns.next()) {
					// Column doesn't exist, add it
					System.out.println("Adding 'note' column to clan_members table...");
					String alterTableSQL = "ALTER TABLE clan_members ADD COLUMN note TEXT";
					try (Statement stmt = conn.createStatement()) {
						stmt.executeUpdate(alterTableSQL);
						System.out.println("Column 'note' added successfully.");
					}
				} else {
					System.out.println("Column 'note' already exists in clan_members table.");
				}
			}
		} catch (SQLException e) {
			System.err.println("Error migrating clan_members table: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void migrateKickpointReasonsTable() {
		// Add index column to kickpoint_reasons table if it doesn't exist
		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			DatabaseMetaData dbm = conn.getMetaData();
			try (ResultSet columns = dbm.getColumns(null, null, "kickpoint_reasons", "index")) {
				if (!columns.next()) {
					// Column doesn't exist, add it
					System.out.println("Adding 'index' column to kickpoint_reasons table...");
					String alterTableSQL = "ALTER TABLE kickpoint_reasons ADD COLUMN index SMALLINT";
					try (Statement stmt = conn.createStatement()) {
						stmt.executeUpdate(alterTableSQL);
						System.out.println("Column 'index' added successfully.");
					}
				} else {
					System.out.println("Column 'index' already exists in kickpoint_reasons table.");
				}
			}
		} catch (SQLException e) {
			System.err.println("Error migrating kickpoint_reasons table: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static java.sql.Connection getConnection() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(url, user, password);
		}
		return connection;
	}

}
