package commands.kickpoints;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class kplistreasons extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("kplistreasons"))
            return;
        event.deferReply().queue();
        String title = "Kickpunkt-Gründe Liste";

        OptionMapping clanOption = event.getOption("clan");

        if (clanOption == null) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Der Parameter Clan ist erforderlich!", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        String clantag = clanOption.getAsString();
        Clan clan = new Clan(clantag);

        if (!clan.ExistsDB()) {
            event.getHook()
                    .editOriginalEmbeds(
                            MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        if (clantag.equals("warteliste")) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Diesen Befehl kannst du nicht auf die Warteliste ausführen.",
                            MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        ArrayList<KickpointReason> reasons = clan.getKickpointReasons();
        String desc = "Liste aller Kickpunkt-Gründe für " + clan.getInfoStringDB() + ":\n\n";

        if (reasons.isEmpty()) {
            desc += "Keine Gründe vorhanden.";
        } else {
            desc += "```\n";
            desc += String.format("%-6s %-25s %s\n", "Index", "Name", "Anzahl");
            desc += "----------------------------------------------\n";
            for (KickpointReason reason : reasons) {
                Integer index = reason.getIndex();
                String indexStr = (index != null) ? index.toString() : "-";
                desc += String.format("%-6s %-25s %d\n", indexStr, reason.getReason(), reason.getAmount());
            }
            desc += "```";
        }

        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("kplistreasons"))
            return;

        String focused = event.getFocusedOption().getName();
        String input = event.getFocusedOption().getValue();

        if (focused.equals("clan")) {
            List<Command.Choice> choices = DBManager.getClansAutocompleteNoWaitlist(input);
            event.replyChoices(choices).queue();
        }
    }
}
