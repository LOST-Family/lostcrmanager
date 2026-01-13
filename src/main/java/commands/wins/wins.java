package commands.wins;

import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class wins extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;
		event.deferReply().queue();
		String title = "Wins-Statistik";

		OptionMapping playerOption = event.getOption("player");
		OptionMapping clanOption = event.getOption("clan");
		OptionMapping monthOption = event.getOption("month");
		OptionMapping excludeLeadersOption = event.getOption("exclude_leaders");

		// Check that at least one of player or clan is provided
		if (playerOption == null && clanOption == null) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst entweder einen Spieler oder einen Clan angeben.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Check that month is provided
		if (monthOption == null) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Du musst einen Monat angeben.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String monthValue = monthOption.getAsString();
		int year;
		int month;

		// Handle special "current" value for "Aktueller Monat"
		if (monthValue.equals("current")) {
			ZoneId zone = ZoneId.of("Europe/Berlin");
			ZonedDateTime now = ZonedDateTime.now(zone);
			year = now.getYear();
			month = now.getMonthValue();
		} else {
			try {
				// Parse month value in format "YYYY-MM"
				String[] parts = monthValue.split("-");
				year = Integer.parseInt(parts[0]);
				month = Integer.parseInt(parts[1]);
			} catch (Exception e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültiges Monat-Format. Bitte wähle einen Monat aus der Liste.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		// Parse exclude_leaders option
		boolean excludeLeaders = false;
		if (excludeLeadersOption != null) {
			String excludeLeadersValue = excludeLeadersOption.getAsString();
			if ("true".equalsIgnoreCase(excludeLeadersValue)) {
				excludeLeaders = true;
			} else {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der exclude_leaders Parameter muss entweder \"true\" enthalten oder nicht angegeben sein (false).",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		// Run heavy processing in a separate thread to not block the main bot instance
		final int yearFinal = year;
		final int monthFinal = month;
		final boolean excludeLeadersFinal = excludeLeaders;
		Thread thread = new Thread(() -> {
			try {
				ZoneId zone = ZoneId.of("Europe/Berlin");
				ZonedDateTime now = ZonedDateTime.now(zone);
				int currentYear = now.getYear();
				int currentMonth = now.getMonthValue();
				boolean isCurrentMonth = (yearFinal == currentYear && monthFinal == currentMonth);

				// Start of the selected month
				ZonedDateTime startOfMonth = ZonedDateTime.of(yearFinal, monthFinal, 1, 0, 0, 0, 0, zone);
				// Start of the next month (end boundary)
				ZonedDateTime startOfNextMonth = startOfMonth.plusMonths(1);

				if (playerOption != null) {
					// Single player mode
					String playerTag = playerOption.getAsString();
					Player player = new Player(playerTag);

					if (!player.IsLinked()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR)).queue();
						return;
					}

					String result = getPlayerWinsForMonth(player, yearFinal, monthFinal, isCurrentMonth, startOfMonth,
							startOfNextMonth, zone);

					// Create refresh button with player and month info
					Button refreshButton = Button.secondary("wins_player_" + playerTag + "_" + monthValue, "\u200B")
							.withEmoji(Emoji.fromUnicode("🔁"));

					ZonedDateTime jetzt = ZonedDateTime.now(zone);
					DateTimeFormatter buttonFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
					String formatiert = jetzt.format(buttonFormatter);

					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, result, MessageUtil.EmbedType.INFO,
							"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
				} else {
					// Clan mode
					String clanInput = clanOption.getAsString();

					// Parse comma-separated clan tags
					List<String> clansList = parseClanTags(clanInput);
					if (clansList.isEmpty()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Keine gültigen Clans angegeben!", MessageUtil.EmbedType.ERROR)).queue();
						return;
					}

					// Validate all clans exist
					for (String clanTag : clansList) {
						Clan clan = new Clan(clanTag);
						if (!clan.ExistsDB()) {
							event.getHook()
									.editOriginalEmbeds(MessageUtil.buildEmbed(title,
											"Clan " + clanTag + " existiert nicht.", MessageUtil.EmbedType.ERROR))
									.queue();
							return;
						}
					}

					// Collect players from all selected clans
					ArrayList<Player> allPlayers = new ArrayList<>();
					for (String clanTag : clansList) {
						Clan clan = new Clan(clanTag);
						ArrayList<Player> clanPlayers = clan.getPlayersDB();
						allPlayers.addAll(clanPlayers);
					}

					if (allPlayers.isEmpty()) {
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Die angegebenen Clans haben keine Mitglieder.", MessageUtil.EmbedType.ERROR))
								.queue();
						return;
					}

					// Filter out hidden coleaders and optionally exclude leaders/coleaders/admins
					filterPlayersForClanWins(allPlayers, excludeLeadersFinal);

					// Collect wins data for all players with compact format
					int i = 0;
					ArrayList<PlayerWinsResult> results = new ArrayList<>();
					for (Player player : allPlayers) {
						i++;
						PlayerWinsResult result = getPlayerWinsCompact(player, yearFinal, monthFinal, isCurrentMonth,
								startOfMonth, startOfNextMonth, zone);
						results.add(result);
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Wins werden geladen: Spieler " + i + "/" + allPlayers.size(),
										MessageUtil.EmbedType.LOADING))
								.setActionRows().queue();
					}

					// Sort by wins descending
					results.sort(Comparator.comparingInt((PlayerWinsResult r) -> r.wins).reversed());

					StringBuilder sb = new StringBuilder();
					String monthName = Month.of(monthFinal).getDisplayName(TextStyle.FULL, Locale.GERMAN);

					// Build clan display for title
					String clanDisplay;
					if (clansList.size() == 1) {
						Clan c = new Clan(clansList.get(0));
						clanDisplay = c.getInfoStringDB();
					} else {
						clanDisplay = clansList.size() + " Clans ("
								+ String.join(", ",
										clansList.stream().map(tag -> new Clan(tag).getNameDB()).toArray(String[]::new))
								+ ")";
					}

					sb.append("**Wins für " + clanDisplay + " im " + monthName + " " + yearFinal + ":**\n\n");

					for (PlayerWinsResult result : results) {
						sb.append(MessageUtil.unformat(result.playerInfo));
						// Add clan name in brackets if multiple clans
						if (clansList.size() > 1) {
							Player p = new Player(result.playerTag);
							if (p.getClanDB() != null) {
								sb.append(" [" + p.getClanDB().getNameDB() + "]");
							}
						}
						sb.append(": **" + result.wins + "**");
						if (result.hasWarning) {
							sb.append(" ⚠️");
						}
						sb.append("\n");
					}

					// Split message if too long
					String fullMessage = sb.toString();
					if (fullMessage.length() > 4000) {
						fullMessage = fullMessage.substring(0, 3997) + "...";
					}

					// Create refresh button - encode multiple clans in button ID
					String clansEncoded = String.join(",", clansList);
					Button refreshButton = Button
							.secondary("wins_clan_" + clansEncoded + "_" + monthValue + "_" + excludeLeadersFinal,
									"\u200B")
							.withEmoji(Emoji.fromUnicode("🔁"));

					ZonedDateTime jetzt = ZonedDateTime.now(zone);
					DateTimeFormatter buttonFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
					String formatiert = jetzt.format(buttonFormatter);

					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, fullMessage,
							MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
							.setActionRow(refreshButton).queue();
				}
			} catch (Exception e) {
				System.err.println("Fehler beim Verarbeiten des Wins-Befehls: " + e.getMessage());
				e.printStackTrace();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
			}
		});
		thread.setName("wins-command-" + event.getUser().getId());
		thread.start();
	}

	private String getPlayerWinsForMonth(Player player, int year, int month, boolean isCurrentMonth,
			ZonedDateTime startOfMonth, ZonedDateTime startOfNextMonth, ZoneId zone) {

		String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

		// Use centralized wins calculation from Player object
		Player.WinsData winsData = player.getMonthlyWins(year, month, isCurrentMonth, startOfMonth, startOfNextMonth,
				zone);

		if (isCurrentMonth) {
			Integer currentWins = player.getWinsAPI();
			if (currentWins == null) {
				return "Fehler beim Abrufen der aktuellen Wins von der API.\n";
			}

			// Get start record for display purposes
			Player.WinsRecord startRecord = player.getWinsAtOrAfter(startOfMonth);
			if (startRecord == null) {
				return "Keine Daten für den Monatsanfang verfügbar. Aktuelle Wins: " + currentWins + "\n";
			}

			String startTimeFormatted = startRecord.recordedAt.atZoneSameInstant(zone).format(formatter);

			if (!winsData.hasWarning) {
				return "### " + player.getInfoStringDB() + "\n" + "Wins im " + monthName + " " + year + ": **"
						+ winsData.wins + "**\n" + "(Von " + startRecord.wins + " am Monatsanfang auf " + currentWins
						+ " aktuell)\n";
			} else {
				return "### " + player.getInfoStringDB() + "\n" + "Wins seit " + startTimeFormatted + ": **"
						+ winsData.wins + "**\n" + "(Von " + startRecord.wins + " auf " + currentWins + " aktuell)\n"
						+ "⚠️ Daten sind nicht vom Monatsanfang, sondern vom Zeitpunkt der Verlinkung.\n";
			}
		} else {
			// Past month
			Player.WinsRecord startRecord = player.getWinsAtOrAfter(startOfMonth);
			Player.WinsRecord endRecord = player.getWinsAtOrAfter(startOfNextMonth);

			if (startRecord == null) {
				return "### " + player.getInfoStringDB() + "\n" + "Keine Daten für " + monthName + " " + year
						+ " verfügbar.\n";
			}

			if (endRecord == null) {
				return "### " + player.getInfoStringDB() + "\n" + "Keine Enddaten für " + monthName + " " + year
						+ " verfügbar.\n";
			}

			String startTimeFormatted = startRecord.recordedAt.atZoneSameInstant(zone).format(formatter);
			String endTimeFormatted = endRecord.recordedAt.atZoneSameInstant(zone).format(formatter);

			boolean startIsMonthStart = player.isStartOfMonth(startRecord.recordedAt, startOfMonth);
			boolean endIsMonthStart = player.isStartOfMonth(endRecord.recordedAt, startOfNextMonth);

			StringBuilder result = new StringBuilder();
			result.append("### " + player.getInfoStringDB() + "\n");
			result.append("Wins im " + monthName + " " + year + ": **" + winsData.wins + "**\n");
			result.append("(Von " + startRecord.wins + " auf " + endRecord.wins + ")\n");

			if (!startIsMonthStart) {
				result.append("⚠️ Startdaten vom " + startTimeFormatted + " (nicht Monatsanfang)\n");
			}
			if (!endIsMonthStart) {
				result.append("⚠️ Enddaten vom " + endTimeFormatted + " (nicht Monatsanfang)\n");
			}

			return result.toString();
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);
			event.replyChoices(choices).queue();
		} else if (focused.equals("clan")) {
			// Handle comma-separated autocomplete similar to statslist (no "Alle Clans")
			List<Command.Choice> choices = getClanAutocomplete(input, event.getUser().getId());
			event.replyChoices(choices).queue();
		} else if (focused.equals("month")) {
			List<Command.Choice> choices = getMonthAutocomplete(input);
			event.replyChoices(choices).queue();
		} else if (focused.equals("exclude_leaders")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue();
		}
	}

	private List<Command.Choice> getMonthAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime now = ZonedDateTime.now(zone);

		// Add "Aktueller Monat" option first - uses special value "current" that gets
		// resolved dynamically
		String currentMonthDisplay = "Aktueller Monat";
		if (currentMonthDisplay.toLowerCase().contains(input.toLowerCase())) {
			choices.add(new Command.Choice(currentMonthDisplay, "current"));
		}

		// Offer last 12 months
		for (int i = 0; i < 12; i++) {
			ZonedDateTime monthDate = now.minusMonths(i);
			int year = monthDate.getYear();
			int month = monthDate.getMonthValue();

			String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
			String display = monthName + " " + year;
			String value = year + "-" + String.format("%02d", month);

			if (display.toLowerCase().contains(input.toLowerCase()) || value.contains(input)) {
				choices.add(new Command.Choice(display, value));
				if (choices.size() >= 25) {
					break;
				}
			}
		}
		return choices;
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("wins_"))
			return;

		event.deferEdit().queue();
		String title = "Wins-Statistik";

		// Parse the button ID: wins_player_<tag>_<year-month> or
		// wins_clan_<tag>_<year-month>_<excludeLeaders>

		String type;
		String tag;
		String monthValue;
		boolean excludeLeaders = false;

		// Regular parsing for player or clan
		String[] parts = id.split("_", 5);
		if (parts.length < 4) {
			return;
		}

		type = parts[1];
		tag = parts[2];
		monthValue = parts[3];

		// Parse exclude_leaders for clan buttons (5th part if present)
		if (type.equals("clan") && parts.length >= 5) {
			excludeLeaders = "true".equals(parts[4]);
		}

		int year;
		int month;
		// Handle special "current" value for "Aktueller Monat"
		if (monthValue.equals("current")) {
			ZoneId zone = ZoneId.of("Europe/Berlin");
			ZonedDateTime now = ZonedDateTime.now(zone);
			year = now.getYear();
			month = now.getMonthValue();
		} else {
			try {
				String[] monthParts = monthValue.split("-");
				year = Integer.parseInt(monthParts[0]);
				month = Integer.parseInt(monthParts[1]);
			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.buildEmbed(title, "Ungültiges Monat-Format.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		// Run heavy processing in a separate thread to not block the main bot instance
		final int yearFinal = year;
		final int monthFinal = month;
		final boolean excludeLeadersFinal = excludeLeaders;
		Thread thread = new Thread(() -> {
			try {
				ZoneId zone = ZoneId.of("Europe/Berlin");
				ZonedDateTime now = ZonedDateTime.now(zone);
				int currentYear = now.getYear();
				int currentMonth = now.getMonthValue();
				boolean isCurrentMonth = (yearFinal == currentYear && monthFinal == currentMonth);

				// Start of the selected month
				ZonedDateTime startOfMonth = ZonedDateTime.of(yearFinal, monthFinal, 1, 0, 0, 0, 0, zone);
				// Start of the next month (end boundary)
				ZonedDateTime startOfNextMonth = startOfMonth.plusMonths(1);

				if (type.equals("player")) {
					Player player = new Player(tag);

					if (!player.IsLinked()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR)).queue();
						return;
					}

					String result = getPlayerWinsForMonth(player, yearFinal, monthFinal, isCurrentMonth, startOfMonth,
							startOfNextMonth, zone);

					// Create refresh button with player and month info
					Button refreshButton = Button.secondary("wins_player_" + tag + "_" + monthValue, "\u200B")
							.withEmoji(Emoji.fromUnicode("🔁"));

					ZonedDateTime jetzt = ZonedDateTime.now(zone);
					DateTimeFormatter buttonFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
					String formatiert = jetzt.format(buttonFormatter);

					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, result, MessageUtil.EmbedType.INFO,
							"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
				} else if (type.equals("clan")) {
					// Parse comma-separated clan tags from button tag parameter
					List<String> clansList = parseClanTags(tag);
					if (clansList.isEmpty()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Keine gültigen Clans angegeben!", MessageUtil.EmbedType.ERROR)).queue();
						return;
					}

					// Validate all clans exist
					for (String clanTag : clansList) {
						Clan clan = new Clan(clanTag);
						if (!clan.ExistsDB()) {
							event.getHook()
									.editOriginalEmbeds(MessageUtil.buildEmbed(title,
											"Clan " + clanTag + " existiert nicht.", MessageUtil.EmbedType.ERROR))
									.queue();
							return;
						}
					}

					// Collect players from all selected clans
					ArrayList<Player> allPlayers = new ArrayList<>();
					for (String clanTag : clansList) {
						Clan clan = new Clan(clanTag);
						ArrayList<Player> clanPlayers = clan.getPlayersDB();
						allPlayers.addAll(clanPlayers);
					}

					if (allPlayers.isEmpty()) {
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Die angegebenen Clans haben keine Mitglieder.", MessageUtil.EmbedType.ERROR))
								.queue();
						return;
					}

					// Filter out hidden coleaders and optionally exclude leaders/coleaders/admins
					filterPlayersForClanWins(allPlayers, excludeLeadersFinal);

					// Collect wins data for all players with compact format
					ArrayList<PlayerWinsResult> results = new ArrayList<>();
					int i = 0;
					for (Player player : allPlayers) {
						i++;
						PlayerWinsResult result = getPlayerWinsCompact(player, yearFinal, monthFinal, isCurrentMonth,
								startOfMonth, startOfNextMonth, zone);
						results.add(result);

						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Wins werden geladen: Spieler " + i + "/" + allPlayers.size(),
										MessageUtil.EmbedType.LOADING))
								.setActionRows().queue();
					}

					// Sort by wins descending
					results.sort(Comparator.comparingInt((PlayerWinsResult r) -> r.wins).reversed());

					StringBuilder sb = new StringBuilder();
					String monthName = Month.of(monthFinal).getDisplayName(TextStyle.FULL, Locale.GERMAN);

					// Build clan display for title
					String clanDisplay;
					if (clansList.size() == 1) {
						Clan c = new Clan(clansList.get(0));
						clanDisplay = c.getInfoStringDB();
					} else {
						clanDisplay = clansList.size() + " Clans (" + String.join(", ",
								clansList.stream().map(clanTag -> new Clan(clanTag).getNameDB()).toArray(String[]::new))
								+ ")";
					}

					sb.append("**Wins für " + clanDisplay + " im " + monthName + " " + yearFinal + ":**\n\n");

					for (PlayerWinsResult result : results) {
						sb.append(MessageUtil.unformat(result.playerInfo));
						// Add clan name in brackets if multiple clans
						if (clansList.size() > 1) {
							Player p = new Player(result.playerTag);
							if (p.getClanDB() != null) {
								sb.append(" [" + p.getClanDB().getNameDB() + "]");
							}
						}
						sb.append(": **" + result.wins + "**");
						if (result.hasWarning) {
							sb.append(" ⚠️");
						}
						sb.append("\n");
					}

					// Split message if too long
					String fullMessage = sb.toString();
					if (fullMessage.length() > 4000) {
						fullMessage = fullMessage.substring(0, 3997) + "...";
					}

					// Create refresh button - encode multiple clans in button ID
					String clansEncoded = String.join(",", clansList);
					Button refreshButton = Button
							.secondary("wins_clan_" + clansEncoded + "_" + monthValue + "_" + excludeLeadersFinal,
									"\u200B")
							.withEmoji(Emoji.fromUnicode("🔁"));

					ZonedDateTime jetzt = ZonedDateTime.now(zone);
					DateTimeFormatter buttonFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
					String formatiert = jetzt.format(buttonFormatter);

					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, fullMessage,
							MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
							.setActionRow(refreshButton).queue();
				}
			} catch (Exception e) {
				System.err.println("Fehler beim Verarbeiten des Wins-Refresh-Befehls: " + e.getMessage());
				e.printStackTrace();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
			}
		});
		thread.setName("wins-refresh-" + event.getUser().getId());
		thread.start();
	}

	// Helper method to filter players for clan wins display
	private void filterPlayersForClanWins(ArrayList<Player> players, boolean excludeLeaders) {
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get(i);
			if (p.isHiddenColeader()) {
				players.remove(i);
				i--;
				continue;
			}
			if (excludeLeaders) {
				Player.RoleType role = p.getRole();
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					players.remove(i);
					i--;
				}
			}
		}
	}

	// Helper class to hold compact player wins result for clan display
	private static class PlayerWinsResult {
		String playerInfo;
		String playerTag;
		int wins;
		boolean hasWarning;

		PlayerWinsResult(String playerInfo, String playerTag, String clanName, int wins, boolean hasWarning) {
			this.playerInfo = playerInfo;
			this.playerTag = playerTag;
			this.wins = wins;
			this.hasWarning = hasWarning;
		}
	}

	// Compact method to get player wins for clan display
	private PlayerWinsResult getPlayerWinsCompact(Player player, int year, int month, boolean isCurrentMonth,
			ZonedDateTime startOfMonth, ZonedDateTime startOfNextMonth, ZoneId zone) {

		String playerInfo = player.getInfoStringDB();
		String playerTag = player.getTag();
		String clanName = player.getClanDB() != null ? player.getClanDB().getNameDB() : "";

		// Use centralized wins calculation from Player object
		Player.WinsData winsData = player.getMonthlyWins(year, month, isCurrentMonth, startOfMonth, startOfNextMonth,
				zone);

		return new PlayerWinsResult(playerInfo, playerTag, clanName, winsData.wins, winsData.hasWarning);
	}

	// Static method to save wins for a player (called from scheduler and link
	// command)
	public static void savePlayerWins(String playerTag) {
		Player player = new Player(playerTag);
		player.savePlayerWins();
	}

	// Static method to save wins for all linked players (called from scheduler)
	public static void saveAllPlayerWins() {
		String sql = "SELECT cr_tag FROM players";
		ArrayList<String> playerTags = DBUtil.getArrayListFromSQL(sql, String.class);

		for (String tag : playerTags) {
			try {
				savePlayerWins(tag);
				// Delay to avoid API rate limiting (500ms between requests)
				Thread.sleep(500);
			} catch (Exception e) {
				System.err.println("Fehler beim Speichern der Wins für " + tag + ": " + e.getMessage());
			}
		}
	}

	private List<String> parseClanTags(String input) {
		List<String> clanTags = new ArrayList<>();
		if (input == null || input.trim().isEmpty()) {
			return clanTags;
		}

		String[] parts = input.split(",");
		for (String part : parts) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				clanTags.add(trimmed);
			}
		}

		return clanTags;
	}

	private List<Command.Choice> getClanAutocomplete(String input, String userId) {
		List<Command.Choice> choices = new ArrayList<>();

		// Split by comma and get the last part
		String[] parts = input.split(",");
		String displayPrefix = "";
		String valuePrefix = "";
		String lastPart = "";

		// Process all parts before the last comma
		if (parts.length > 1) {
			StringBuilder displayBuilder = new StringBuilder();
			StringBuilder valueBuilder = new StringBuilder();

			for (int i = 0; i < parts.length - 1; i++) {
				String trimmed = parts[i].trim();
				if (!trimmed.isEmpty()) {
					if (i > 0) {
						displayBuilder.append(", ");
						valueBuilder.append(",");
					}
					// Add the display string as-is
					displayBuilder.append(trimmed);
					// Extract and add just the tag to value
					String extractedTag = extractClanTag(trimmed);
					valueBuilder.append(extractedTag);
				}
			}
			displayPrefix = displayBuilder.toString();
			valuePrefix = valueBuilder.toString();
		}

		// The last part is what user is currently typing
		if (parts.length > 0) {
			lastPart = parts[parts.length - 1].trim();
		}

		// Get already selected tags to avoid duplicates
		List<String> alreadySelectedTags = new ArrayList<>();
		for (int i = 0; i < parts.length - 1; i++) {
			String trimmed = parts[i].trim();
			if (!trimmed.isEmpty()) {
				String extractedTag = extractClanTag(trimmed);
				if (!extractedTag.isEmpty()) {
					alreadySelectedTags.add(extractedTag);
				}
			}
		}

		// Get clans from DBManager and filter
		List<Command.Choice> allClans = DBManager.getClansAutocomplete(lastPart);

		// Build the autocomplete choices
		for (Command.Choice clan : allClans) {
			String clanTag = clan.getAsString(); // Just the tag like #xxx
			String clanDisplay = clan.getName(); // Full display like "LOST (#xxx)"

			// Skip if already selected
			if (alreadySelectedTags.contains(clanTag)) {
				continue;
			}

			// Build the display name (what user sees)
			String choiceName = displayPrefix.isEmpty() ? clanDisplay : displayPrefix + ", " + clanDisplay;

			// Build the value (tags only, for command submission)
			String choiceValue = valuePrefix.isEmpty() ? clanTag : valuePrefix + "," + clanTag;

			choices.add(new Command.Choice(choiceName, choiceValue));

			if (choices.size() >= 25) {
				break;
			}
		}

		return choices;
	}

	// Helper method to extract clan tag from display string
	private String extractClanTag(String displayString) {
		// Format is either "ClanName (#TAG)" or just "#TAG"
		int startBracket = displayString.indexOf('(');
		int endBracket = displayString.indexOf(')');

		if (startBracket != -1 && endBracket != -1 && endBracket > startBracket) {
			// Extract the tag from within parentheses
			return displayString.substring(startBracket + 1, endBracket).trim();
		}

		// If no parentheses, assume the whole thing is the tag
		return displayString.trim();
	}
}
