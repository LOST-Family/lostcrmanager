package commands.memberlist;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import util.MessageUtil;

public class togglemark extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("togglemark"))
			return;
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");

		if (playeroption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();

		Player player = new Player(playertag);

		if (!player.IsLinked()) {
			event.replyEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Clan playerclan = player.getClanDB();

		if (playerclan == null) {
			event.replyEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Spieler ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = playerclan.getTag();

		User userexecuted = new User(event.getUser().getId());
		if (!clantag.equals("warteliste")) {
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.replyEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		} else {
			boolean b = false;
			for (String clantags : DBManager.getAllClans()) {
				if (userexecuted.getClanRoles().get(clantags) == Player.RoleType.ADMIN
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.LEADER
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.COLEADER) {
					b = true;
					break;
				}
			}
			if (b == false) {
				event.replyEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		// If player is already marked, unmark and clear note
		if (player.isMarked()) {
			event.deferReply().queue();
			new Thread(() -> {
				DBUtil.executeUpdate("UPDATE clan_members SET marked = FALSE, note = NULL WHERE player_tag = ?", playertag);
				String desc = "Der Spieler " + player.getInfoStringDB() + " ist nun nicht mehr markiert.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
			}).start();
		} else {
			// If player is not marked, show modal to add note
			String currentNote = player.getNote();
			TextInput.Builder noteInputBuilder = TextInput.create("note", "Notiz (optional)", TextInputStyle.PARAGRAPH)
					.setPlaceholder("Gib hier eine Notiz ein...")
					.setRequired(false)
					.setMaxLength(1000);
			
			// Only set value if note is not null and not blank to avoid IllegalArgumentException
			if (currentNote != null && !currentNote.trim().isEmpty()) {
				noteInputBuilder.setValue(currentNote);
			}
			
			TextInput noteInput = noteInputBuilder.build();

			Modal modal = Modal.create("togglemark_" + playertag, "Spieler markieren")
					.addActionRows(ActionRow.of(noteInput))
					.build();

			event.replyModal(modal).queue();
		}

	}

	@SuppressWarnings("null")
	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		if (event.getModalId().startsWith("togglemark_")) {
			event.deferReply().queue();
			String title = "Memberverwaltung";
			String playertag = event.getModalId().substring("togglemark_".length());
			String note = event.getValue("note").getAsString();

			new Thread(() -> {
				Player player = new Player(playertag);

				// Update mark and note
				if (note == null || note.trim().isEmpty()) {
					DBUtil.executeUpdate("UPDATE clan_members SET marked = TRUE, note = NULL WHERE player_tag = ?", playertag);
				} else {
					DBUtil.executeUpdate("UPDATE clan_members SET marked = TRUE, note = ? WHERE player_tag = ?", note, playertag);
				}

				String desc = "Der Spieler " + player.getInfoStringDB() + " ist nun markiert.";
				if (note != null && !note.trim().isEmpty()) {
					desc += "\nNotiz: " + note;
				}

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
			}).start();
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("togglemark"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

			event.replyChoices(choices).queue();
		}
	}

}
