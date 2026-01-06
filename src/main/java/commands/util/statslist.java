package commands.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class statslist extends ListenerAdapter {

	// Available stat fields
	private static final String[] AVAILABLE_FIELDS = { "Wins", "Trophies", "UC-Trophies", "Ranked-Liga",
			"Letzte Ranked-Liga", "Letzte UC-Trophies" };

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("statslist"))
			return;
		event.deferReply().queue();
		String title = "Stats Liste";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping displayFieldsOption = event.getOption("display_fields");
		OptionMapping sortFieldsOption = event.getOption("sort_fields");
		OptionMapping rolesSortingOption = event.getOption("roles_sorting");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter Clan ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clanInput = clanOption.getAsString();
		String rolesSortingValue = rolesSortingOption != null ? rolesSortingOption.getAsString() : "false";
		boolean rolesSorting = "true".equalsIgnoreCase(rolesSortingValue)
				|| "clans".equalsIgnoreCase(rolesSortingValue);
		boolean clanSorting = "clans".equalsIgnoreCase(rolesSortingValue);

		// Parse clan tags
		List<String> clansList = parseClanTags(clanInput);
		if (clansList.isEmpty()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Keine gültigen Clans angegeben!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Validate that all specified clans exist
		for (String clanTag : clansList) {
			Clan clan = new Clan(clanTag);
			if (!clan.ExistsDB()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Clan " + clanTag + " existiert nicht.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		// Parse display fields - now required
		List<String> displayFields = new ArrayList<>();
		if (displayFieldsOption != null) {
			String fieldsInput = displayFieldsOption.getAsString();
			displayFields = parseFields(fieldsInput);
			if (displayFields.isEmpty()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Keine gültigen Anzeigefelder angegeben!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		} else {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Der Parameter display_fields ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Parse sort fields - default to alphabetically by name
		List<String> sortFields = new ArrayList<>();
		if (sortFieldsOption != null) {
			String sortInput = sortFieldsOption.getAsString();
			sortFields = parseFields(sortInput);
			if (sortFields.isEmpty()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Keine gültigen Sortierfelder angegeben!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}
		// If no sort fields specified, default is alphabetical (empty list means sort
		// by name)

		// Convert the List to ArrayList for compatibility with existing code
		final ArrayList<String> clans = new ArrayList<>(clansList);
		final List<String> finalDisplayFields = displayFields;
		final List<String> finalSortFields = sortFields;
		final boolean finalRolesSorting = rolesSorting;
		final boolean finalClanSorting = clanSorting;

		Thread thread = new Thread(() -> {
			try {
				String content = generateStatsList(clans, finalDisplayFields, finalSortFields, finalRolesSorting,
						finalClanSorting, event, title);

				ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
				String description = "Hier die Stats-Liste";
				if (clans.size() == 1) {
					Clan c = new Clan(clans.get(0));
					description += " für " + c.getInfoStringDB();
				} else {
					description += " für " + clans.size() + " Clans";
				}
				description += ".";
				event.getHook().editOriginal(inputStream, "StatsList.txt")
						.setEmbeds(MessageUtil.buildEmbed(title, description, MessageUtil.EmbedType.INFO)).queue();
			} catch (Exception e) {
				e.printStackTrace();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
			}
		});
		thread.start();
	}

	private String generateStatsList(ArrayList<String> clanTags, List<String> displayFields, List<String> sortFields,
			boolean rolesSorting, boolean clanSorting, SlashCommandInteractionEvent event, String title) {

		ArrayList<Player> allPlayers = new ArrayList<>();
		String content = "";

		// Load all players
		for (int a = 0; a < clanTags.size(); a++) {
			String clantag = clanTags.get(a);
			Clan c = new Clan(clantag);
			ArrayList<Player> playerListClan = c.getPlayersDB();
			for (int i = 0; i < playerListClan.size(); i++) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Lade Spieler " + (i + 1) + " / " + playerListClan.size()
										+ (clanTags.size() > 1 ? " aus Clan " + (a + 1) + " / " + clanTags.size() : "")
										+ " von Datenbank in den Cache...",
								MessageUtil.EmbedType.LOADING))
						.queue();
				allPlayers.add(playerListClan.get(i));
			}
		}
		int playerCount = 0;
		int fieldCount = 0;
		int fieldSize = displayFields.contains("Wins") ? displayFields.size() - 1 : displayFields.size();
		for (Player p : allPlayers) {
			playerCount++;
			for (String field : displayFields) {
				if (field.equals("Wins")) {
					continue;
				}
				fieldCount++;
				getFieldValue(p, field);
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Lade Feld " + fieldCount + "/" + fieldSize + " von Spieler " + playerCount + "/"
										+ allPlayers.size() + " in den Cache...",
								MessageUtil.EmbedType.LOADING))
						.queue();
			}
			fieldCount = 0;
		}

		// Sort players
		if (rolesSorting && clanSorting && clanTags.size() > 1) {
			// When roles_sorting with "clans" is enabled and multiple clans selected,
			// organize by clan and role
			allPlayers = sortPlayersByRolesAndFields(allPlayers, sortFields);

			// Group by clan with role-based sections
			content = generateRoleSortedContent(allPlayers, clanTags, displayFields, event, title);
		} else if (rolesSorting) {
			// Single clan with role sorting OR multiple clans with role sorting but no clan
			// grouping
			allPlayers = sortPlayersByRolesAndFields(allPlayers, sortFields);
			if (clanTags.size() > 1) {
				// All clans with role sorting - show all players in one sorted list by role
				content = generateAllClansContent(allPlayers, displayFields, event, title);
			} else {
				content = generateSingleClanContent(allPlayers, clanTags.get(0), displayFields, true, event, title);
			}
		} else {
			// No role sorting - just sort by specified fields (or alphabetically if no
			// fields)
			allPlayers = sortPlayersByFields(allPlayers, sortFields);
			if (clanTags.size() > 1) {
				// All clans without role sorting - show all players in one sorted list
				content = generateAllClansContent(allPlayers, displayFields, event, title);
			} else {
				content = generateSingleClanContent(allPlayers, clanTags.get(0), displayFields, false, event, title);
			}
		}

		return content;
	}

	private String generateRoleSortedContent(ArrayList<Player> allPlayers, ArrayList<String> clanTags,
			List<String> displayFields, SlashCommandInteractionEvent event, String title) {
		StringBuilder content = new StringBuilder();

		int totalPlayers = allPlayers.size();
		int processedPlayers = 0;

		// Group players by clan
		for (String clanTag : clanTags) {
			Clan clan = new Clan(clanTag);
			content.append(clan.getInfoStringDB()).append("\n\n");

			// Filter players for this clan
			ArrayList<Player> clanPlayers = allPlayers.stream().filter(p -> {
				Clan playerClan = p.getClanDB();
				return playerClan != null && playerClan.getTag().equals(clanTag);
			}).collect(Collectors.toCollection(ArrayList::new));

			// Group by role
			ArrayList<Player> admins = new ArrayList<>();
			ArrayList<Player> leaders = new ArrayList<>();
			ArrayList<Player> coleaders = new ArrayList<>();
			ArrayList<Player> regular = new ArrayList<>();

			for (Player p : clanPlayers) {
				if (p.isHiddenColeader()) {
					continue;
				}
				if (p.getRole() == Player.RoleType.ADMIN) {
					admins.add(p);
				} else if (p.getRole() == Player.RoleType.LEADER) {
					leaders.add(p);
				} else if (p.getRole() == Player.RoleType.COLEADER) {
					coleaders.add(p);
				} else {
					regular.add(p);
				}
			}

			int counter = 1;

			// Add leaders/coleaders section
			boolean leadersExist = !admins.isEmpty() || !leaders.isEmpty() || !coleaders.isEmpty();
			if (leadersExist) {
				for (Player p : admins) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				for (Player p : leaders) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				for (Player p : coleaders) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				content.append("----------------------------\n\n");
			}

			// Add regular members
			for (Player p : regular) {
				updateProgress(event, title, ++processedPlayers, totalPlayers);
				content.append("#").append(counter++).append(" ");
				content.append(formatPlayerLine(p, displayFields));
			}

			content.append("-----------------------------------------------------------\n\n");
		}

		return content.toString();
	}

	private String generateSingleClanContent(ArrayList<Player> players, String clanTag, List<String> displayFields,
			boolean rolesSorting, SlashCommandInteractionEvent event, String title) {
		StringBuilder content = new StringBuilder();
		Clan clan = new Clan(clanTag);
		content.append(clan.getInfoStringDB()).append("\n\n");

		// Filter out hidden coleaders
		players = players.stream().filter(p -> !p.isHiddenColeader()).collect(Collectors.toCollection(ArrayList::new));

		int totalPlayers = players.size();
		int processedPlayers = 0;

		if (rolesSorting) {
			// Group by role
			ArrayList<Player> admins = new ArrayList<>();
			ArrayList<Player> leaders = new ArrayList<>();
			ArrayList<Player> coleaders = new ArrayList<>();
			ArrayList<Player> regular = new ArrayList<>();

			for (Player p : players) {
				if (p.getRole() == Player.RoleType.ADMIN) {
					admins.add(p);
				} else if (p.getRole() == Player.RoleType.LEADER) {
					leaders.add(p);
				} else if (p.getRole() == Player.RoleType.COLEADER) {
					coleaders.add(p);
				} else {
					regular.add(p);
				}
			}

			int counter = 1;

			// Add leaders/coleaders section
			boolean leadersExist = !admins.isEmpty() || !leaders.isEmpty() || !coleaders.isEmpty();
			if (leadersExist) {
				for (Player p : admins) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				for (Player p : leaders) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				for (Player p : coleaders) {
					updateProgress(event, title, ++processedPlayers, totalPlayers);
					content.append("#").append(counter++).append(" ");
					content.append(formatPlayerLine(p, displayFields));
				}
				content.append("----------------------------\n\n");
			}

			// Add regular members
			for (Player p : regular) {
				updateProgress(event, title, ++processedPlayers, totalPlayers);
				content.append("#").append(counter++).append(" ");
				content.append(formatPlayerLine(p, displayFields));
			}
		} else {
			// No role sorting - just list in order
			int counter = 1;
			for (Player p : players) {
				updateProgress(event, title, ++processedPlayers, totalPlayers);
				content.append("#").append(counter++).append(" ");
				content.append(formatPlayerLine(p, displayFields));
			}
		}

		content.append("-----------------------------------------------------------\n\n");

		return content.toString();
	}

	private String generateAllClansContent(ArrayList<Player> players, List<String> displayFields,
			SlashCommandInteractionEvent event, String title) {
		StringBuilder content = new StringBuilder();
		content.append("Mehrere Clans - Sortiert nach gewählten Kriterien\n\n");

		// Filter out hidden coleaders
		players = players.stream().filter(p -> !p.isHiddenColeader()).collect(Collectors.toCollection(ArrayList::new));

		int totalPlayers = players.size();
		int processedPlayers = 0;
		int counter = 1;
		for (Player p : players) {
			updateProgress(event, title, ++processedPlayers, totalPlayers);
			content.append("#").append(counter++).append(" ");
			content.append(formatPlayerLine(p, displayFields));
		}

		content.append("-----------------------------------------------------------\n\n");

		return content.toString();
	}

	private String formatPlayerLine(Player p, List<String> displayFields) {
		boolean isMarked = p.isMarked();

		// Status
		String status;
		if (p.getClanDB() == null) {
			status = "[Warteschlange]";
		} else {
			String role = "";
			if (p.getRole() == Player.RoleType.ADMIN) {
				role = "Admin";
			} else if (p.getRole() == Player.RoleType.LEADER) {
				role = "Anführer";
			} else if (p.getRole() == Player.RoleType.COLEADER) {
				role = "Vize-Anführer";
			} else if (p.getRole() == Player.RoleType.ELDER) {
				role = "Ältester";
			} else if (p.getRole() == Player.RoleType.MEMBER) {
				role = "Mitglied";
			}
			status = "[" + role + " " + p.getClanDB().getNameDB() + "]";
		}
		String discordInfo = "Dc:";
		if (p.getUser() != null) {
			String discordID = p.getUser().getUserID();
			Member member = null;
			try {
				member = Bot.getJda().getGuildById(Bot.guild_id)
						.retrieveMember(Bot.getJda().retrieveUserById(discordID).submit().get()).submit().get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			if (member != null) {
				String nick = member.getEffectiveName();
				String name = member.getUser().getAsTag().replace("#0000", "");
				discordInfo += " " + nick + " (" + name + ")";
			}
			discordInfo += " <@" + discordID + ">";
		}

		String noteInfo = "";
		if (isMarked) {
			status += " [MARKIERT]";
			String note = p.getNote();
			if (note != null && !note.trim().isEmpty()) {
				noteInfo = " Notiz: " + note + "\n";
			}
		}

		// Build field output
		StringBuilder fields = new StringBuilder();
		for (String field : displayFields) {
			Object value = getFieldValue(p, field);
			fields.append(" ").append(getFieldDisplayName(field)).append(": ").append(value).append("\n");
		}

		return String.format("%s (%s) %s %s\n%s%s\n", p.getNameDB(), p.getTag(), status, discordInfo, noteInfo,
				fields.toString());
	}

	private Object getFieldValue(Player p, String field) {
		switch (field) {
		case "Wins":
			Player.WinsData winsResult = p.getCurrentMonthWins();
			String winsDisplay = String.valueOf(winsResult.wins);
			if (winsResult.hasWarning) {
				winsDisplay += " ⚠️";
			}
			return winsDisplay;
		case "Trophies":
			Integer trophies = p.getTrophies();
			return trophies != null ? trophies : 0;
		case "UC-Trophies":
			Integer polTrophies = p.getPoLTrophies();
			return polTrophies != null ? polTrophies : 0;
		case "Ranked-Liga":
			Integer leagueNumber = p.getPoLLeagueNumber();
			return leagueNumber != null ? leagueNumber : 0;
		case "Letzte Ranked-Liga":
			Integer lastLeagueNumber = p.getLastPathOfLegendLeagueNumber();
			return lastLeagueNumber != null ? lastLeagueNumber : 0;
		case "Letzte UC-Trophies":
			Integer lastLeagueTrophies = p.getLastPathOfLegendTrophies();
			return lastLeagueTrophies != null ? lastLeagueTrophies : 0;
		default:
			return "N/A";
		}
	}

	private String getFieldDisplayName(String field) {
		switch (field) {
		case "Wins":
			return "Wins (Monat)";
		case "Trophies":
			return "Trophäen";
		case "UC-Trophies":
			return "UC-Trophäen";
		case "Ranked-Liga":
			return "Ranked-Liga";
		case "Letzte Ranked-Liga":
			return "Letzte Ranked-Liga";
		case "Letzte UC-Trophies":
			return "Letzte UC-Trophies";
		default:
			return field;
		}
	}

	private ArrayList<Player> sortPlayersByRolesAndFields(ArrayList<Player> players, List<String> sortFields) {
		Comparator<Player> comparator = Comparator
				// First by role (Admin > Leader > CoLeader > others)
				.comparingInt((Player p) -> {
					if (p.getRole() == Player.RoleType.ADMIN)
						return 0;
					if (p.getRole() == Player.RoleType.LEADER)
						return 1;
					if (p.getRole() == Player.RoleType.COLEADER)
						return 2;
					return 3;
				});

		// Add sort fields or default to alphabetical
		if (sortFields.isEmpty()) {
			// Default: sort alphabetically by name
			comparator = comparator.thenComparing(Player::getNameDB, String.CASE_INSENSITIVE_ORDER);
		} else {
			for (String field : sortFields) {
				comparator = comparator.thenComparing(getFieldComparator(field).reversed());
			}
		}

		return players.stream().sorted(comparator).collect(Collectors.toCollection(ArrayList::new));
	}

	private ArrayList<Player> sortPlayersByFields(ArrayList<Player> players, List<String> sortFields) {
		Comparator<Player> comparator = null;

		if (sortFields.isEmpty()) {
			// Default: sort alphabetically by name
			comparator = Comparator.comparing(Player::getNameDB, String.CASE_INSENSITIVE_ORDER);
		} else {
			for (int i = 0; i < sortFields.size(); i++) {
				String field = sortFields.get(i);
				Comparator<Player> fieldComparator = getFieldComparator(field).reversed();
				if (i == 0) {
					comparator = fieldComparator;
				} else {
					comparator = comparator.thenComparing(fieldComparator);
				}
			}
		}

		return players.stream().sorted(comparator).collect(Collectors.toCollection(ArrayList::new));
	}

	private Comparator<Player> getFieldComparator(String field) {
		switch (field) {
		case "Wins":
			return Comparator.comparingInt((Player p) -> p.getCurrentMonthWins().wins);
		case "Trophies":
			return Comparator.comparingInt((Player p) -> {
				Integer trophies = p.getTrophies();
				return trophies != null ? trophies : 0;
			});
		case "UC-Trophies":
			return Comparator.comparingInt((Player p) -> {
				Integer polTrophies = p.getPoLTrophies();
				return polTrophies != null ? polTrophies : 0;
			});
		case "Ranked-Liga":
			return Comparator.comparingInt((Player p) -> {
				Integer leagueNumber = p.getPoLLeagueNumber();
				return leagueNumber != null ? leagueNumber : 0;
			});
		case "Letzte Ranked-Liga":
			return Comparator.comparingInt((Player p) -> {
				Integer lastLeagueNumber = p.getLastPathOfLegendLeagueNumber();
				return lastLeagueNumber != null ? lastLeagueNumber : 0;
			});
		case "Letzte UC-Trophies":
			return Comparator.comparingInt((Player p) -> {
				Integer lastLeagueTrophies = p.getLastPathOfLegendTrophies();
				return lastLeagueTrophies != null ? lastLeagueTrophies : 0;
			});
		default:
			return Comparator.comparingInt((_) -> 0);
		}
	}

	private List<String> parseFields(String input) {
		List<String> fields = new ArrayList<>();
		if (input == null || input.trim().isEmpty()) {
			return fields;
		}

		String[] parts = input.split(",");
		for (String part : parts) {
			String trimmed = part.trim();
			// Check if it's a valid field
			for (String availableField : AVAILABLE_FIELDS) {
				if (availableField.equalsIgnoreCase(trimmed)) {
					fields.add(availableField);
					break;
				}
			}
		}

		return fields;
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

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("statslist"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			// Handle comma-separated autocomplete similar to fields
			List<Command.Choice> choices = getClanAutocomplete(input, event.getUser().getId());
			event.replyChoices(choices).queue();
		} else if (focused.equals("display_fields") || focused.equals("sort_fields")) {
			// Handle comma-separated autocomplete
			List<Command.Choice> choices = getFieldAutocomplete(input);
			event.replyChoices(choices).queue();
		} else if (focused.equals("roles_sorting")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".toLowerCase().contains(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			if ("clans".toLowerCase().contains(input.toLowerCase())) {
				choices.add(new Command.Choice("clans", "clans"));
			}
			event.replyChoices(choices).queue();
		}
	}

	private List<Command.Choice> getFieldAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		// Split by comma and get the last part
		String[] parts = input.split(",");
		String prefix = "";
		String lastPart = "";

		if (parts.length > 0) {
			lastPart = parts[parts.length - 1].trim();
			// Build prefix from all parts except the last one
			if (parts.length > 1) {
				StringBuilder prefixBuilder = new StringBuilder();
				for (int i = 0; i < parts.length - 1; i++) {
					if (i > 0)
						prefixBuilder.append(",");
					prefixBuilder.append(parts[i].trim());
				}
				prefix = prefixBuilder.toString() + ",";
			}
		}

		// Get already selected fields to avoid duplicates
		List<String> alreadySelected = new ArrayList<>();
		for (int i = 0; i < parts.length - 1; i++) {
			String trimmed = parts[i].trim();
			for (String field : AVAILABLE_FIELDS) {
				if (field.equalsIgnoreCase(trimmed)) {
					alreadySelected.add(field);
					break;
				}
			}
		}

		// Autocomplete the last part
		for (String field : AVAILABLE_FIELDS) {
			if (!alreadySelected.contains(field) && field.toLowerCase().startsWith(lastPart.toLowerCase())) {
				String displayValue = prefix.isEmpty() ? field : prefix + field;
				choices.add(new Command.Choice(displayValue, displayValue));
				if (choices.size() >= 25) {
					break;
				}
			}
		}

		// If no matches with startsWith, try contains
		if (choices.isEmpty()) {
			for (String field : AVAILABLE_FIELDS) {
				if (!alreadySelected.contains(field) && field.toLowerCase().contains(lastPart.toLowerCase())) {
					String displayValue = prefix.isEmpty() ? field : prefix + field;
					choices.add(new Command.Choice(displayValue, displayValue));
					if (choices.size() >= 25) {
						break;
					}
				}
			}
		}

		return choices;
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

	private void updateProgress(SlashCommandInteractionEvent event, String title, int current, int total) {
		// Update progress every 5 players or on last player to avoid rate limiting
		if (current % 5 == 0 || current == total) {
			try {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Generiere Stats-Liste: " + current + " / " + total + " Spieler verarbeitet...",
								MessageUtil.EmbedType.LOADING))
						.queue();
			} catch (Exception e) {
				// Silently continue if progress update fails - don't interrupt stats generation
			}
		}
	}
}
