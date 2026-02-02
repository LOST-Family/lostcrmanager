package datautil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import datawrapper.Player;
import net.dv8tion.jda.api.interactions.commands.Command;
import util.Triplet;
import util.Tuple;

public class DBManager {

	private static ArrayList<Tuple<String, String>> clans;
	private static Boolean clanslocked;
	private static ArrayList<Triplet<String, String, String>> players;
	private static Boolean playerslocked;

	public enum InClanType {
		INCLAN, NOTINCLAN, ALL
	}

	public static ArrayList<String> getAllClans() {
		return DBUtil.getArrayListFromSQL("SELECT tag FROM clans ORDER BY index ASC", String.class);
	}

	public static List<Command.Choice> getKPReasonsAutocomplete(String input, String clantag) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT name, clan_tag FROM kickpoint_reasons WHERE clan_tag = ?";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, clantag);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString("name");

					if (name.toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(name, name));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

	public static int getAvailableKPID() {
		String sql = "SELECT id FROM kickpoints";
		int available = 0;

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				ArrayList<Integer> used = new ArrayList<>();
				while (rs.next()) {
					used.add(rs.getInt("id"));
				}
				while (used.contains(available)) {
					available++;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return available;
	}

	public static List<Command.Choice> getClansAutocomplete(String input) {
		return getClansAutocompleteWithMarked(input, false);
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getClansAutocompleteWithMarked(String input, boolean includeMarked) {
		if (clans == null) {
			cacheClans();
		}

		List<Command.Choice> choices = new ArrayList<>();

		for (Tuple<String, String> available : clans) {
			String display = available.getFirst();
			String tag = available.getSecond();
			if (display.toLowerCase().contains(input.toLowerCase())
					|| tag.toLowerCase().startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice(display, tag));

				if (choices.size() >= 25) {
					break;
				}
			}
		}

		if (includeMarked && "alle markierten spieler".contains(input.toLowerCase())) {
			choices.add(new Command.Choice("Alle markierten Spieler", "all_marked"));
		}
		Thread thread = new Thread(() -> {
			if (!clanslocked)
				cacheClans();
		});
		thread.start();

		return choices;
	}

	private static void cacheClans() {
		ArrayList<Tuple<String, String>> list = new ArrayList<>();

		String sql = "SELECT name, tag FROM clans ORDER BY index ASC";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String name = rs.getString("name");

					String display = name;
					if (!tag.equals("warteliste")) {
						display += " (" + tag + ")";
					}

					list.add(new Tuple<String, String>(display, tag));

				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		clans = list;
		clanslocked = true;
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				clanslocked = false;
			}
		}, 10000);
	}

	public static List<Command.Choice> getClansAutocompleteNoWaitlist(String input) {
		if (clans == null) {
			cacheClans();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Tuple<String, String> available : clans) {
			String display = available.getFirst();
			String tag = available.getSecond();
			if (!tag.equals("warteliste")) {
				if (display.toLowerCase().contains(input.toLowerCase())
						|| tag.toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(display, tag));
					if (choices.size() == 25) {
						break;
					}
				}
			}
		}
		Thread thread = new Thread(() -> {
			if (!clanslocked)
				cacheClans();
		});
		thread.start();

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getPlayerlistAutocomplete(String input, InClanType inclantype) {
		if (players == null) {
			cachePlayers();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Triplet<String, String, String> available : players) {
			String display = available.getFirst();
			String clanName = available.getSecond();
			String tag = available.getThird();

			if (inclantype == InClanType.NOTINCLAN) {
				if (clanName == null || clanName.isEmpty()) {
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			} else if (inclantype == InClanType.INCLAN) {
				if (clanName != null && !clanName.isEmpty()) {
					display += " - " + clanName;
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break; // Max 25 Vorschläge
						}
					}
				}
			} else if (inclantype == InClanType.ALL) {
				if (clanName != null && !clanName.isEmpty()) {
					display += " - " + clanName;
				}

				// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
				if (display.toLowerCase().contains(input.toLowerCase())
						|| tag.toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(display, tag));
					if (choices.size() == 25) {
						break; // Max 25 Vorschläge
					}
				}
			}

		}
		Thread thread = new Thread(() -> {
			if (!playerslocked)
				cachePlayers();
		});
		thread.start();

		return choices;
	}

	private static void cachePlayers() {
		ArrayList<Triplet<String, String, String>> list = new ArrayList<>();

		String sql = "SELECT players.cr_tag AS tag, players.name AS player_name, clans.name AS clan_name "
				+ "FROM players " + "LEFT JOIN clan_members ON clan_members.player_tag = players.cr_tag "
				+ "LEFT JOIN clans ON clans.tag = clan_members.clan_tag";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String clanName = rs.getString("clan_name");

					String display = new Player(tag).getInfoStringDB();

					list.add(new Triplet<String, String, String>(display, clanName, tag));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		players = list;
		playerslocked = true;
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				playerslocked = false;
			}
		}, 10000);
	}

	public static List<Command.Choice> getPlayerlistAutocompleteNoWaitlist(String input, InClanType inclantype) {
		if (players == null) {
			cachePlayers();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Triplet<String, String, String> available : players) {
			String display = available.getFirst();
			String clanName = available.getSecond();
			String tag = available.getThird();
			if (!tag.equals("warteliste")) {
				if (inclantype == InClanType.NOTINCLAN) {
					if (clanName == null || clanName.isEmpty()) {
						if (display.toLowerCase().contains(input.toLowerCase())
								|| tag.toLowerCase().startsWith(input.toLowerCase())) {
							choices.add(new Command.Choice(display, tag));
							if (choices.size() == 25) {
								break;
							}
						}
					}
				} else if (inclantype == InClanType.INCLAN) {
					if (clanName != null && !clanName.isEmpty()) {
						display += " - " + clanName;
						if (display.toLowerCase().contains(input.toLowerCase())
								|| tag.toLowerCase().startsWith(input.toLowerCase())) {
							choices.add(new Command.Choice(display, tag));
							if (choices.size() == 25) {
								break; // Max 25 Vorschläge
							}
						}
					}
				} else if (inclantype == InClanType.ALL) {
					if (clanName != null && !clanName.isEmpty()) {
						display += " - " + clanName;
					}

					// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break; // Max 25 Vorschläge
						}
					}
				}
			}

		}
		Thread thread = new Thread(() -> {
			if (!playerslocked)
				cachePlayers();
		});
		thread.start();

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getPlayerlistAutocompleteAllLostClans(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT players.cr_tag AS tag, players.name AS player_name, clans.name AS clan_name, clans.tag AS clan_tag "
				+ "FROM players "
				+ "LEFT JOIN clan_members ON clan_members.player_tag = players.cr_tag "
				+ "LEFT JOIN clans ON clans.tag = clan_members.clan_tag "
				+ "WHERE clans.tag IS NOT NULL AND clans.tag != 'warteliste'";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String playerName = rs.getString("player_name");
					String clanName = rs.getString("clan_name");

					String display = playerName + " (" + tag + ")";
					if (clanName != null && !clanName.isEmpty()) {
						display += " [" + clanName + "]";
					}

					// Filter with input
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getTrackChannelsAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();
		String sql = "SELECT id, name FROM trackchannels";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int id = rs.getInt("id");
					String name = rs.getString("name");
					String display = id + " | " + name;

					if (display.toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(display, String.valueOf(id)));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

}
