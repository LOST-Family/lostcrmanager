package commands.wins;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import commands.kickpoints.kpadd;
import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.Player;
import datawrapper.User;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class winsfails extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("winsfails"))
			return;
		event.deferReply().queue();
		String title = "Wins-Fails";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping thresholdOption = event.getOption("threshold");
		OptionMapping monthOption = event.getOption("month");
		OptionMapping kpreasonOption = event.getOption("kpreason");
		OptionMapping minThresholdOption = event.getOption("min_threshold");
		OptionMapping excludeLeadersOption = event.getOption("exclude_leaders");

		if (clanOption == null || thresholdOption == null || monthOption == null) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Die Parameter Clan, Threshold & Month sind verpflichtend!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clanInput = clanOption.getAsString();
		
		// Parse comma-separated clan tags
		List<String> clansList = parseClanTags(clanInput);
		if (clansList.isEmpty()) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Keine gültigen Clans angegeben!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		
		// Check for waitlist in any clan
		for (String clantag : clansList) {
			if (clantag.equals("warteliste")) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		// Check permissions for all clans
		User userexecuted = new User(event.getUser().getId());
		for (String clantag : clansList) {
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer aller angegebenen Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		int threshold = thresholdOption.getAsInt();

		Integer minThreshold = null;
		if (minThresholdOption != null) {
			try {
				minThreshold = Integer.parseInt(minThresholdOption.getAsString());
			} catch (NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der min_threshold Parameter muss eine gültige Zahl sein.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

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

		// Parse month parameter
		String monthValue = monthOption.getAsString();
		int year;
		int month;

		if (monthValue.equals("current")) {
			ZoneId zone = ZoneId.of("Europe/Berlin");
			ZonedDateTime now = ZonedDateTime.now(zone);
			year = now.getYear();
			month = now.getMonthValue();
		} else {
			try {
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

		boolean addkp;
		String kpreasonstring;
		KickpointReason kpreason = null;
		// For KP reasons, only validate against the first clan
		if (kpreasonOption != null) {
			kpreasonstring = kpreasonOption.getAsString();
			kpreason = new KickpointReason(kpreasonstring, clansList.get(0));
			if (!kpreason.Exists()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Diese Begründung existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			addkp = true;
		} else {
			addkp = false;
		}

		// Validate all clans exist
		for (String clantag : clansList) {
			Clan c = new Clan(clantag);
			if (!c.ExistsDB()) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title, 
								"Clan " + clantag + " existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		final KickpointReason kpreasontemp = kpreason;
		final List<String> clansListFinal = clansList;
		final Integer minThresholdFinal = minThreshold;
		final boolean excludeLeadersFinal = excludeLeaders;
		final int yearFinal = year;
		final int monthFinal = month;

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

				String monthName = Month.of(monthFinal).getDisplayName(TextStyle.FULL, Locale.GERMAN);
				
				// Collect all players from all clans
				ArrayList<Player> allPlayers = new ArrayList<>();
				for (String clantag : clansListFinal) {
					Clan c = new Clan(clantag);
					ArrayList<Player> clanplayerlist = c.getPlayersDB();
					allPlayers.addAll(clanplayerlist);
				}

				// Calculate wins for each player
				HashMap<String, Integer> tagToWins = new HashMap<>();
				HashMap<String, Boolean> tagToHasWarning = new HashMap<>();

				// Filter players first if exclude_leaders is enabled
				ArrayList<Player> playersToProcess = new ArrayList<>();
				for (Player p : allPlayers) {
					if (excludeLeadersFinal) {
						Player.RoleType role = p.getRole();
						if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
								|| role == Player.RoleType.COLEADER) {
							continue;
						}
					}
					playersToProcess.add(p);
				}

				int currentPlayer = 0;
				int totalPlayers = playersToProcess.size();
				
				for (Player p : playersToProcess) {
					currentPlayer++;
					
					// Update progress message (every 5 players or on last player to avoid rate limiting)
					if (currentPlayer % 5 == 0 || currentPlayer == totalPlayers) {
						try {
							event.getHook()
									.editOriginalEmbeds(MessageUtil.buildEmbed(title,
											"Berechne Wins für Spieler " + currentPlayer + " / " + totalPlayers + "...",
											MessageUtil.EmbedType.LOADING))
									.queue();
						} catch (Exception e) {
							// Silently continue if progress update fails - don't interrupt calculation
						}
					}

					// Calculate monthly wins for this player
					Player.WinsData winsData = getPlayerMonthlyWins(p.getTag(), yearFinal, monthFinal, isCurrentMonth,
							startOfMonth, startOfNextMonth, zone);
					tagToWins.put(p.getTag(), winsData.wins);
					tagToHasWarning.put(p.getTag(), winsData.hasWarning);
				}

				// Process each clan separately if multiple clans
				boolean isFirstClan = true;
				for (String clantag : clansListFinal) {
					Clan c = new Clan(clantag);
					String clanDisplay = c.getInfoStringDB();
					
					String desc = "## Eine Liste aller Spieler aus " + clanDisplay + ", welche unter " + threshold + " Wins im " + monthName + " "
							+ yearFinal + " haben.\n";

					if (addkp) {
						desc += "### Da ein Kickpunkt-Grund ausgewählt wurde, wird dieser auf jeden Spieler der Liste angewandt.\n";
					}

					boolean listempty = true;
					ArrayList<Player> playerdonewrong = new ArrayList<>();

					// Filter players for this specific clan
					for (Player p : allPlayers) {
						// Check if player belongs to current clan
						if (p.getClanDB() == null || !p.getClanDB().getTag().equals(clantag)) {
							continue;
						}

						// Skip leaders/coleaders/admins if exclude_leaders is true
						if (excludeLeadersFinal) {
							Player.RoleType role = p.getRole();
							if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
									|| role == Player.RoleType.COLEADER) {
								continue;
							}
						}

						String playertag = p.getTag();

						if (tagToWins.containsKey(playertag)) {
							int playerWins = tagToWins.get(playertag);

							if (playerWins < threshold) {
								// Apply min_threshold check - only display and add KP if wins are >=
								// min_threshold
								if (minThresholdFinal == null || playerWins >= minThresholdFinal) {
									String playerDisplay = p.getInfoStringDB();
									desc += "**" + playerDisplay + "**:\n";
									desc += " - Wins: " + playerWins;
									boolean playerhaswarning = false;
									if (tagToHasWarning.get(playertag)) {
										playerhaswarning = true;
										desc += " ⚠️";
									}
									desc += "\n";
									if (listempty)
										listempty = false;
									if (addkp && !playerhaswarning)
										playerdonewrong.add(p);
								}
							}
						}
					}

					if (listempty) {
						desc += "**Keine Fehler anzuzeigen.**";
					}

					// Send first clan result as reply, others as separate messages
					if (isFirstClan) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
								.queue();
						isFirstClan = false;
					} else {
						event.getChannel()
								.sendMessageEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
								.queue();
					}

					// Send kickpoint message for this clan if applicable
					if (!listempty && addkp && !playerdonewrong.isEmpty()) {
						String kpmessagedesc = "### Hinzugefügte Kickpunkte für " + clanDisplay + ":\n";
						for (Player p : playerdonewrong) {
							int id = kpadd.addKPtoDB(p.getTag(), Timestamp.from(Instant.now()), kpreasontemp,
									Bot.getJda().getSelfUser().getId());
							kpmessagedesc += MessageUtil.unformat(p.getInfoStringDB()) + ":\n";
							kpmessagedesc += " - ID: " + id + "\n";
						}
						event.getChannel()
								.sendMessageEmbeds(
										MessageUtil.buildEmbed(title, kpmessagedesc, MessageUtil.EmbedType.INFO))
								.queue();
					}
				}
			} catch (Exception e) {
				System.err.println("Fehler beim Verarbeiten des Winsfails-Befehls: " + e.getMessage());
				e.printStackTrace();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
			}
		});
		thread.setName("winsfails-command-" + event.getUser().getId());
		thread.start();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("winsfails"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			// Handle comma-separated autocomplete similar to statslist
			List<Command.Choice> choices = getClanAutocomplete(input, event.getUser().getId());
			event.replyChoices(choices).queue();
		} else if (focused.equals("kpreason")) {
			// For KP reasons, use the first clan from the input
			String clanInput = event.getOption("clan") != null ? event.getOption("clan").getAsString() : "";
			List<String> clansList = parseClanTags(clanInput);
			String firstClan = clansList.isEmpty() ? "" : clansList.get(0);
			List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, firstClan);
			event.replyChoices(choices).queue();
		} else if (focused.equals("exclude_leaders")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue();
		} else if (focused.equals("month")) {
			List<Command.Choice> choices = getMonthAutocomplete(input);
			event.replyChoices(choices).queue();
		}
	}

	// Calculate monthly wins for a player
	private Player.WinsData getPlayerMonthlyWins(String playerTag, int year, int month, boolean isCurrentMonth,
			ZonedDateTime startOfMonth, ZonedDateTime startOfNextMonth, ZoneId zone) {

		Player player = new Player(playerTag);
		return player.getMonthlyWins(year, month, isCurrentMonth, startOfMonth, startOfNextMonth, zone);
	}

	private List<Command.Choice> getMonthAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime now = ZonedDateTime.now(zone);

		// Add "Aktueller Monat" option first
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

		// Get clans from DBManager and filter (excluding waitlist)
		List<Command.Choice> allClans = DBManager.getClansAutocompleteNoWaitlist(lastPart);

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
