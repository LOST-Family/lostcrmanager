package commands.links;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.APIUtil;
import datautil.DBManager;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class playerinfo extends ListenerAdapter {

	enum ConvertionType {
		USERTOACCS, ACCTOUSER
	}

	/**
	 * Formats player API JSON data into a pretty-printed JSON format
	 * Preserves the exact field order from the API response
	 * @param jsonString The raw JSON string from the API
	 * @return Pretty-printed JSON string with proper indentation, or error message if parsing fails
	 */
	private static String formatPlayerJsonToText(String jsonString) {
		try {
			if (jsonString == null || jsonString.trim().isEmpty()) {
				return "Error: No API data available to format.";
			}
			
			// Manually format JSON while preserving field order
			StringBuilder formatted = new StringBuilder();
			int indentLevel = 0;
			boolean inString = false;
			boolean escapeNext = false;
			
			for (int i = 0; i < jsonString.length(); i++) {
				char c = jsonString.charAt(i);
				
				// Handle escape sequences
				if (escapeNext) {
					formatted.append(c);
					escapeNext = false;
					continue;
				}
				
				if (c == '\\') {
					formatted.append(c);
					escapeNext = true;
					continue;
				}
				
				// Track if we're inside a string
				if (c == '"') {
					formatted.append(c);
					inString = !inString;
					continue;
				}
				
				// Don't format inside strings
				if (inString) {
					formatted.append(c);
					continue;
				}
				
				// Format structural characters
				switch (c) {
					case '{':
					case '[':
						formatted.append(c);
						indentLevel++;
						formatted.append('\n');
						appendIndent(formatted, indentLevel);
						break;
					case '}':
					case ']':
						indentLevel = Math.max(0, indentLevel - 1); // Guard against negative indent
						formatted.append('\n');
						appendIndent(formatted, indentLevel);
						formatted.append(c);
						break;
					case ',':
						formatted.append(c);
						formatted.append('\n');
						appendIndent(formatted, indentLevel);
						break;
					case ':':
						formatted.append(c);
						formatted.append(' ');
						break;
					case ' ':
					case '\t':
					case '\n':
					case '\r':
						// Skip existing whitespace
						break;
					default:
						formatted.append(c);
						break;
				}
			}
			
			return formatted.toString();
			
		} catch (Exception e) {
			e.printStackTrace();
			return "Error formatting JSON from API: " + e.getMessage();
		}
	}
	
	/**
	 * Appends indentation spaces to the StringBuilder
	 * @param sb The StringBuilder to append to
	 * @param level The indentation level (each level = 2 spaces)
	 */
	private static void appendIndent(StringBuilder sb, int level) {
		int spaces = level * 2;
		if (spaces > 0) {
			// Use String.repeat() for efficiency (Java 11+)
			sb.append(" ".repeat(spaces));
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;
		event.deferReply().queue();
		String title = "Spielerinformation";

		OptionMapping userOption = event.getOption("user");
		OptionMapping playerOption = event.getOption("player");
		OptionMapping getApiFileOption = event.getOption("getapifile");

		if (userOption == null && playerOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Einer der beiden Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (userOption != null && playerOption != null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Bitte gib nur einen Parameter an!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		
		boolean getApiFile = false;
		if (getApiFileOption != null) {
			String getApiFileValue = getApiFileOption.getAsString();
			if ("true".equalsIgnoreCase(getApiFileValue)) {
				getApiFile = true;
			} else {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der getapifile Parameter muss entweder \"true\" enthalten oder nicht angegeben sein (false).",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		final boolean getApiFileFinal = getApiFile;
		
		new Thread(() -> {
			String userid = null;
			String playertag = null;
			ArrayList<Player> linkedaccs = new ArrayList<>();

			Player player = null;

			ConvertionType conv = null;

			if (userOption != null) {
				userid = userOption.getAsMentionable().getId();
				linkedaccs = new User(userid).getAllLinkedAccounts();
				conv = ConvertionType.USERTOACCS;
			}
			if (playerOption != null) {
				playertag = playerOption.getAsString();
				player = new Player(playertag);
				if (player.IsLinked()) {
					userid = player.getUser().getUserID();
					conv = ConvertionType.ACCTOUSER;
				} else {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler ist nicht verifiziert.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			String desc = "";

			if (conv == ConvertionType.ACCTOUSER) {
				try {
					desc += "## " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				} catch (Exception e) {
					e.printStackTrace();
				}
				desc += "Verlinkter Discord Account: <@" + userid + ">\n";
				if (player.getClanDB() != null) {
					desc += "Eingetragen in Clan: " + player.getClanDB().getInfoStringDB();
					if (player.isMarked()) {
						desc += " (✗)";
						String note = player.getNote();
						if (note != null && !note.trim().isEmpty()) {
							desc += " - " + note;
						}
					}
					desc += "\n";
				} else {
					desc += "Eingetragen in Clan: ---\n";
				}
				if (player.getClanAPI() != null) {
					desc += "Ingame in Clan: " + player.getClanAPI().getInfoStringAPI() + "\n";
				} else {
					desc += "Ingame in Clan: ---\n";
				}
				desc += "Aktuelle Anzahl Kickpunkte: " + player.getActiveKickpoints().size() + "\n";
				desc += "Ingesamte Anzahl Kickpunkte: " + player.getTotalKickpoints();

				final String uuid = userid;
				MessageChannelUnion channel = event.getChannel();
				MessageUtil.sendUserPingHidden(channel, uuid);
			}
			if (conv == ConvertionType.USERTOACCS) {
				try {
					desc += "## <@" + userid + "> \n";
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (linkedaccs.isEmpty()) {
					desc += "	Keine verlinkten Accounts.\n";
				} else {
					desc += "Verlinkte Accounts: \n";
					for (Player p : linkedaccs) {
						desc += "   \\- " + MessageUtil.unformat(p.getInfoStringDB());
						if(p.isMarked()) {
							desc += " (✗)";
						}
						desc += "\n";
					}
				}
			}
			
			// Send response with or without API file
			// playertag is only set when playerOption is provided, so API file is only available for player lookups
			if (getApiFileFinal && playertag != null) {
				// Fetch the API JSON for the player
				String apiJson = APIUtil.getPlayerJson(playertag);
				if (apiJson != null) {
					// Format the JSON into pretty-printed JSON
					String formattedJson = formatPlayerJsonToText(apiJson);
					ByteArrayInputStream inputStream = new ByteArrayInputStream(formattedJson.getBytes(StandardCharsets.UTF_8));
					// Sanitize filename by removing all non-alphanumeric characters except underscore and hyphen
					String sanitizedTag = playertag.replaceAll("[^a-zA-Z0-9_-]", "");
					// Fallback to default name if sanitization results in empty string
					String filename = (sanitizedTag.isEmpty() ? "player" : sanitizedTag) + "_info.txt";
					event.getHook().editOriginal(inputStream, filename)
							.setEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
							.queue();
					return;
				}
			}
			// Send response without API file (either not requested or API fetch failed)
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
					.queue();
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);

			event.replyChoices(choices).queue();
		} else if (focused.equals("getapifile")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue();
		}
	}

}
