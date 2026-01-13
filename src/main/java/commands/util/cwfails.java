package commands.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

public class cwfails extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("cwfails"))
			return;
		event.deferReply().queue();
		String title = "CW-Fails";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping thresholdOption = event.getOption("threshold");
		OptionMapping kpreasonOption = event.getOption("kpreason");
		OptionMapping minThresholdOption = event.getOption("min_threshold");
		OptionMapping excludeLeadersOption = event.getOption("exclude_leaders");

		if (clanOption == null || thresholdOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Die Parameter Clan & Threshold sind verpflichtend!", MessageUtil.EmbedType.ERROR)).queue();
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

		Thread thread = new Thread(() -> {

			// Collect CW fame data from all clans
			ArrayList<Player> allCwfamePlayers = new ArrayList<>();
			for (String clantag : clansListFinal) {
				Clan c = new Clan(clantag);
				ArrayList<Player> cwfameplayerlist = c.getCWFamePlayerList();
				allCwfamePlayers.addAll(cwfameplayerlist);
			}

			// Build clan display for title
			String clanDisplay;
			if (clansListFinal.size() == 1) {
				Clan c = new Clan(clansListFinal.get(0));
				clanDisplay = c.getInfoStringDB();
			} else {
				clanDisplay = clansListFinal.size() + " Clans (" + 
					String.join(", ", clansListFinal.stream()
						.map(tag -> new Clan(tag).getNameDB())
						.toArray(String[]::new)) + ")";
			}

			String desc = "## Eine Liste aller Spieler aus " + clanDisplay + ", welche unter " + threshold + " Punkte im CW haben.\n";

			if (addkp) {
				desc += "### Da ein Kickpunkt-Grund ausgewählt wurde, wird dieser auf jeden Spieler der Liste angewandt.\n";
			}

			boolean listempty = true;

			// Collect all players from all clans
			ArrayList<Player> allClanPlayers = new ArrayList<>();
			for (String clantag : clansListFinal) {
				Clan c = new Clan(clantag);
				ArrayList<Player> clanplayerlist = c.getPlayersDB();
				allClanPlayers.addAll(clanplayerlist);
			}

			HashMap<String, Integer> tagtocwfame = new HashMap<>();
			HashMap<String, String> tagtoclantagcwdone = new HashMap<>();

			for (Player p : allCwfamePlayers) {
				tagtocwfame.put(p.getTag(), p.getCWFame());
				tagtoclantagcwdone.put(p.getTag(), p.getClantagCWDone());
			}

			ArrayList<Player> playerdonewrong = new ArrayList<>();

			for (Player p : allClanPlayers) {
				String playertag = p.getTag();

				// Skip leaders/coleaders/admins if exclude_leaders is true
				if (excludeLeadersFinal) {
					Player.RoleType role = p.getRole();
					if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
							|| role == Player.RoleType.COLEADER) {
						continue;
					}
				}

				if (tagtocwfame.containsKey(playertag)) {
					if (tagtoclantagcwdone.get(playertag).equals(p.getClanDB().getTag())) {
						if (tagtocwfame.get(playertag) < threshold) {
							if (tagtoclantagcwdone.get(playertag).equals(p.getClanDB().getTag())) {
								// Apply min_threshold check - only display and add KP if points are >=
								// min_threshold
								int playerPoints = tagtocwfame.get(playertag);
								if (minThresholdFinal == null || playerPoints >= minThresholdFinal) {
									// Display with clan name if multiple clans
									String playerDisplay = p.getInfoStringDB();
									if (clansListFinal.size() > 1 && p.getClanDB() != null) {
										playerDisplay += " [" + p.getClanDB().getNameDB() + "]";
									}
									desc += "**" + playerDisplay + "**:\n";
									desc += " - Punkte: " + p.getCWFame() + ".\n";
									if (listempty)
										listempty = false;
									if (addkp)
										playerdonewrong.add(p);
								}
							}
						}
					} else {
						// Player didn't do CW in clan - only display and add KP if min_threshold is not
						// set or is 0
						if (minThresholdFinal == null || minThresholdFinal <= 0) {
							// Display with clan name if multiple clans
							String playerDisplay = p.getInfoStringDB();
							if (clansListFinal.size() > 1 && p.getClanDB() != null) {
								playerDisplay += " [" + p.getClanDB().getNameDB() + "]";
							}
							desc += "**" + playerDisplay + "**:\n";
							desc += " - Nicht im Clan gemacht.\n";
							if (listempty)
								listempty = false;
							if (addkp)
								playerdonewrong.add(p);
						}
					}
				} else {
					// Player didn't do CW in clan - only display and add KP if min_threshold is not
					// set or is 0
					if (minThresholdFinal == null || minThresholdFinal <= 0) {
						// Display with clan name if multiple clans
						String playerDisplay = p.getInfoStringDB();
						if (clansListFinal.size() > 1 && p.getClanDB() != null) {
							playerDisplay += " [" + p.getClanDB().getNameDB() + "]";
						}
						desc += "**" + playerDisplay + "**:\n";
						desc += " - Nicht im Clan gemacht.\n";
						if (listempty)
							listempty = false;
						if (addkp)
							playerdonewrong.add(p);
					}
				}
			}

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();

			if (!listempty) {
				if (addkp) {
					String kpmessagedesc = "### Hinzugefügte Kickpunkte:\n";
					for (Player p : playerdonewrong) {
						int id = kpadd.addKPtoDB(p.getTag(), Timestamp.from(Instant.now()), kpreasontemp,
								Bot.getJda().getSelfUser().getId());
						kpmessagedesc += MessageUtil.unformat(p.getInfoStringDB()) + ":\n";
						kpmessagedesc += " - ID: " + id + "\n";
					}
					event.getChannel()
							.sendMessageEmbeds(MessageUtil.buildEmbed(title, kpmessagedesc, MessageUtil.EmbedType.INFO))
							.queue();
				}
			} else {
				desc += "**Keine Fehler anzuzeigen.**";
			}

		});
		thread.start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("cwfails"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			// Handle comma-separated autocomplete similar to statslist
			List<Command.Choice> choices = getClanAutocomplete(input, event.getUser().getId());
			event.replyChoices(choices).queue();
		}
		if (focused.equals("kpreason")) {
			// For KP reasons, use the first clan from the input
			String clanInput = event.getOption("clan") != null ? event.getOption("clan").getAsString() : "";
			List<String> clansList = parseClanTags(clanInput);
			String firstClan = clansList.isEmpty() ? "" : clansList.get(0);
			List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, firstClan);
			event.replyChoices(choices).queue();
		}
		if (focused.equals("exclude_leaders")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue();
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
