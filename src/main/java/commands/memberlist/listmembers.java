package commands.memberlist;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

public class listmembers extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;
		event.deferReply().queue();
		String title = "Memberliste";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		// Handle "noclan" option specially
		if (clantag.equals("noclan")) {
			new Thread(() -> {
				handleNoClanOptionForSlashCommand(event, title);
			}).start();
			return;
		}

		Clan c = new Clan(clantag);

		new Thread(() -> {
			ArrayList<Player> playerlist = c.getPlayersDB();

			playerlist.sort(Comparator.comparing(Player::isMarked).reversed().thenComparing((p1, p2) -> {
				String name1 = p1.getNameDB() != null ? p1.getNameDB() : p1.getNameAPI();
				String name2 = p2.getNameDB() != null ? p2.getNameDB() : p2.getNameAPI();
				if (name1 == null && name2 == null)
					return 0;
				if (name1 == null)
					return 1; // nulls last
				if (name2 == null)
					return -1;
				return name1.compareTo(name2);
			}));

			String adminlist = "";
			String leaderlist = "";
			String coleaderlist = "";
			String elderlist = "";
			String memberlist = "";
			int clanSizeCount = 0;

			for (Player p : playerlist) {
				boolean isHidden = p.isHiddenColeader();
				if (!isHidden) {
					clanSizeCount++;
				}

				if (p.getRole() == Player.RoleType.ADMIN) {
					adminlist += p.getInfoStringDB();
					if (p.isMarked()) {
						adminlist += " (✗)";
					}
					adminlist += "\n";
				}
				if (p.getRole() == Player.RoleType.LEADER) {
					leaderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						leaderlist += " (✗)";
					}
					leaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.COLEADER) {
					coleaderlist += p.getInfoStringDB();
					if (isHidden) {
						coleaderlist += " (versteckt)";
					}
					if (p.isMarked()) {
						coleaderlist += " (✗)";
					}
					coleaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.ELDER) {
					elderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						elderlist += " (✗)";
					}
					elderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.MEMBER) {
					memberlist += p.getInfoStringDB();
					if (p.isMarked()) {
						memberlist += " (✗)";
					}
					memberlist += "\n";
				}
			}
			String desc = "## " + c.getInfoStringDB() + "\n";
			if (!clantag.equals("warteliste")) {
				desc += "**Admin:**\n";
				desc += adminlist == "" ? "---\n\n" : MessageUtil.unformat(adminlist) + "\n";
				desc += "**Anführer:**\n";
				desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
				desc += "**Vize-Anführer:**\n";
				desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
				desc += "**Ältester:**\n";
				desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
				desc += "**Mitglied:**\n";
				desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
				desc += "\nInsgesamte Mitglieder des Clans: " + clanSizeCount;
			} else {
				desc += "**Wartend:**\n";
				desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
				desc += "\nInsgesamte Spieler auf der Warteliste: " + playerlist.size();
			}

			Button refreshButton = Button.secondary("listmembers_" + clantag, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
			choices.add(new Command.Choice("Kein Clan zugewiesen", "noclan"));
			event.replyChoices(choices).queue();
		}
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("listmembers_"))
			return;

		event.deferEdit().queue();

		String title = "Memberliste";
		String clantag = id.substring("listmembers_".length());

		// Handle "noclan" option specially
		if (clantag.equals("noclan")) {
			new Thread(() -> {
				handleNoClanOptionForButtonEvent(event, title);
			}).start();
			return;
		}

		Clan c = new Clan(clantag);

		new Thread(() -> {
			ArrayList<Player> playerlist = c.getPlayersDB();

			playerlist.sort(Comparator.comparing(Player::isMarked).reversed().thenComparing((p1, p2) -> {
				String name1 = p1.getNameDB() != null ? p1.getNameDB() : p1.getNameAPI();
				String name2 = p2.getNameDB() != null ? p2.getNameDB() : p2.getNameAPI();
				if (name1 == null && name2 == null)
					return 0;
				if (name1 == null)
					return 1; // nulls last
				if (name2 == null)
					return -1;
				return name1.compareTo(name2);
			}));

			String adminlist = "";
			String leaderlist = "";
			String coleaderlist = "";
			String elderlist = "";
			String memberlist = "";
			int clanSizeCount = 0;

			for (Player p : playerlist) {
				boolean isHidden = p.isHiddenColeader();
				if (!isHidden) {
					clanSizeCount++;
				}

				if (p.getRole() == Player.RoleType.ADMIN) {
					adminlist += p.getInfoStringDB();
					if (p.isMarked()) {
						adminlist += " (✗)";

					}
					adminlist += "\n";
				}
				if (p.getRole() == Player.RoleType.LEADER) {
					leaderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						leaderlist += " (✗)";

					}
					leaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.COLEADER) {
					coleaderlist += p.getInfoStringDB();
					if (isHidden) {
						coleaderlist += " (versteckt)";
					}
					if (p.isMarked()) {
						coleaderlist += " (✗)";

					}
					coleaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.ELDER) {
					elderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						elderlist += " (✗)";

					}
					elderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.MEMBER) {
					memberlist += p.getInfoStringDB();
					if (p.isMarked()) {
						memberlist += " (✗)";

					}
					memberlist += "\n";
				}
			}
			String desc = "## " + c.getInfoStringDB() + "\n";
			if (!clantag.equals("warteliste")) {
				desc += "**Admin:**\n";
				desc += adminlist == "" ? "---\n\n" : MessageUtil.unformat(adminlist) + "\n";
				desc += "**Anführer:**\n";
				desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
				desc += "**Vize-Anführer:**\n";
				desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
				desc += "**Ältester:**\n";
				desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
				desc += "**Mitglied:**\n";
				desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
				desc += "\nInsgesamte Mitglieder des Clans: " + clanSizeCount;
			} else {
				desc += "**Wartend:**\n";
				desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
				desc += "\nInsgesamte Spieler auf der Warteliste: " + playerlist.size();
			}

			Button refreshButton = Button.secondary("listmembers_" + clantag, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();
	}

	private void handleNoClanOptionForSlashCommand(SlashCommandInteractionEvent event, String title) {
		handleNoClanOptionGeneric(event.getHook(), title);
	}

	private void handleNoClanOptionForButtonEvent(ButtonInteractionEvent event, String title) {
		handleNoClanOptionGeneric(event.getHook(), title);
	}

	private void handleNoClanOptionGeneric(net.dv8tion.jda.api.interactions.InteractionHook hook, String title) {
		// Get all linked players
		String sql = "SELECT cr_tag FROM players";
		ArrayList<String> allPlayerTags = DBUtil.getArrayListFromSQL(sql, String.class);

		// Filter players without clan and build output
		StringBuilder desc = new StringBuilder();
		desc.append("## Kein Clan zugewiesen\n\n");
		desc.append("**Spieler ohne Clan:**\n");

		int count = 0;
		for (String tag : allPlayerTags) {
			Player p = new Player(tag);
			if (p.getClanDB() == null) {
				count++;
				desc.append(p.getInfoStringDB());
				if (p.getUser() != null) {
					desc.append(" <@").append(p.getUser().getUserID()).append(">");
				}
				desc.append("\n");
			}
		}

		if (count == 0) {
			desc.append("Keine Spieler ohne Clan gefunden.\n");
		} else {
			desc.append("\nInsgesamt ").append(count).append(" Spieler ohne Clan.");
		}

		String finalDesc = desc.toString();

		// Check if message exceeds 4000 characters
		if (finalDesc.length() > 4000) {
			// Send as text file
			ByteArrayInputStream inputStream = new ByteArrayInputStream(finalDesc.getBytes(StandardCharsets.UTF_8));
			String description = "Hier ist die Liste der Spieler ohne Clanzugehörigkeit. Die Liste wurde als Datei gesendet, da sie zu lang für eine Nachricht ist.";
			hook.editOriginal(inputStream, "Spieler_ohne_Clan.txt")
					.setEmbeds(MessageUtil.buildEmbed(title, description, MessageUtil.EmbedType.INFO)).queue();
		} else {
			// Send as embed
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, finalDesc, MessageUtil.EmbedType.INFO)).queue();
		}
	}

}
