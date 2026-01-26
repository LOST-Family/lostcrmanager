package commands.admin;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class copyreasons extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("copyreasons"))
            return;
        event.deferReply().queue();
        String title = "Kickpunkt-Gründe kopieren";

        OptionMapping clanOption = event.getOption("clan");

        if (clanOption == null) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Der Clan-Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        String sourceClantag = clanOption.getAsString();
        Clan sourceClan = new Clan(sourceClantag);

        if (!sourceClan.ExistsDB()) {
            event.getHook()
                    .editOriginalEmbeds(
                            MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        if (sourceClantag.equals("warteliste")) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Du kannst nicht von der Warteliste kopieren.",
                            MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        User userexecuted = new User(event.getUser().getId());
        if (!userexecuted.isAdmin()) {
            event.getHook()
                    .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        // Get all kickpoint reasons from source clan
        ArrayList<KickpointReason> sourceReasons = sourceClan.getKickpointReasons();

        if (sourceReasons.isEmpty()) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Der Quell-Clan hat keine Kickpunkt-Gründe.",
                            MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        // Get all clans and copy reasons to each (except warteliste and source clan)
        ArrayList<String> allClans = DBManager.getAllClans();
        int clansUpdated = 0;

        for (String targetClantag : allClans) {
            // Skip warteliste and source clan
            if (targetClantag.equals("warteliste") || targetClantag.equals(sourceClantag)) {
                continue;
            }

            // Delete all existing kickpoint reasons for target clan
            DBUtil.executeUpdate("DELETE FROM kickpoint_reasons WHERE clan_tag = ?", targetClantag);

            // Copy all reasons from source clan
            for (KickpointReason reason : sourceReasons) {
                DBUtil.executeUpdate(
                        "INSERT INTO kickpoint_reasons (name, clan_tag, amount, index) VALUES (?, ?, ?, ?)",
                        reason.getReason(), targetClantag, reason.getAmount(), reason.getIndex());
            }

            clansUpdated++;
        }

        String desc = "Die Kickpunkt-Gründe wurden erfolgreich kopiert.\n\n";
        desc += "**Quell-Clan:** " + sourceClan.getInfoStringDB() + "\n";
        desc += "**Anzahl Gründe:** " + sourceReasons.size() + "\n";
        desc += "**Aktualisierte Clans:** " + clansUpdated;

        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("copyreasons"))
            return;

        String focused = event.getFocusedOption().getName();
        String input = event.getFocusedOption().getValue();

        if (focused.equals("clan")) {
            List<Command.Choice> choices = DBManager.getClansAutocompleteNoWaitlist(input);
            event.replyChoices(choices).queue();
        }
    }
}
