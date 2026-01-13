package commands.kickpoints;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class kpaddreason extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpaddreason"))
			return;
		event.deferReply().queue();
		String title = "Kickpunkt-Grund Vorlage";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping reasonoption = event.getOption("reason");
		OptionMapping amountoption = event.getOption("amount");

		if (clanOption == null || reasonoption == null || amountoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String reason = reasonoption.getAsString();
		String clantag = clanOption.getAsString();
		int amount = amountoption.getAsInt();
		Clan clan = new Clan(clantag);

		if (!clan.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		
		if(clantag.equals("warteliste")) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		User userexecuted = new User(event.getUser().getId());
		if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
			event.getHook()
			.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		KickpointReason kpreason = new KickpointReason(reason, clantag);

		if (kpreason.Exists()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Diese Begründung existiert schon.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		DBUtil.executeUpdate("INSERT INTO kickpoint_reasons (name, clan_tag, amount) VALUES (?, ?, ?)", reason, clantag,
				amount);

		String desc = "Der Kickpunkt-Grund wurde als Vorlage hinzugefügt.\n";
		desc += "Grund: " + reason + "\n";
		desc += "Clan: " + clan.getInfoStringDB() + "\n";
		desc += "Anzahl: " + amount;

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpaddreason"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocompleteNoWaitlist(input);

			event.replyChoices(choices).queue();
		}
	}

}
