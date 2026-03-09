package commands.memberlist;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.MemberSignoff;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class signoff extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("signoff"))
            return;
        event.deferReply().queue();

        new Thread(() -> {
            String title = "Abmeldung";

            OptionMapping playerOption = event.getOption("player");
            OptionMapping actionOption = event.getOption("action");

            if (playerOption == null || actionOption == null) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.buildEmbed(title, "Die Parameter Player und Action sind erforderlich!",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            String playertag = playerOption.getAsString();
            String action = actionOption.getAsString();
            Player p = new Player(playertag);

            if (p.getClanDB() == null) {
                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                        "Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            Clan c = p.getClanDB();

            User userExecuted = new User(event.getUser().getId());
            if (!userExecuted.isColeaderOrHigher()) {
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            MemberSignoff signoff = new MemberSignoff(playertag);

            if ("create".equals(action)) {
                if (signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist bereits abgemeldet. Nutze die 'extend' oder 'end' Action, um die Abmeldung zu ändern.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                OptionMapping daysOption = event.getOption("days");
                OptionMapping reasonOption = event.getOption("reason");
                OptionMapping pingsOption = event.getOption("pings");

                Timestamp endDate = null;
                String durationText = "unbegrenzt";

                if (daysOption != null) {
                    int days = daysOption.getAsInt();
                    if (days <= 0) {
                        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                                .queue();
                        return;
                    }
                    LocalDateTime endDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusDays(days);
                    endDate = Timestamp.valueOf(endDateTime);
                    durationText = days + " Tag" + (days == 1 ? "" : "e");
                }

                String reason = reasonOption != null ? reasonOption.getAsString() : null;
                boolean receivePings = pingsOption != null && pingsOption.getAsBoolean();

                MemberSignoff.create(playertag, endDate, reason, event.getUser().getId(), receivePings);

                String desc = "### Abmeldung erfolgreich erstellt.\n";
                desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                desc += "Clan: " + c.getInfoStringDB() + "\n";
                desc += "Dauer: " + durationText + "\n";
                if (reason != null) {
                    desc += "Grund: " + reason + "\n";
                }
                desc += "\n**Während der Abmeldung:**\n";
                desc += "- Keine automatischen Kickpunkte (cwfails/winsfails)\n";
                if (receivePings) {
                    desc += "- **Reminder-Pings AKTIVIERT**\n";
                } else {
                    desc += "- Keine CW-Reminder-Pings\n";
                }

                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                        .queue();

            } else if ("end".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                MemberSignoff.remove(playertag);

                String desc = "### Abmeldung erfolgreich beendet.\n";
                desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                desc += "Clan: " + c.getInfoStringDB() + "\n";

                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                        .queue();

            } else if ("extend".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet. Nutze die 'create' Action.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                OptionMapping daysOption = event.getOption("days");
                if (daysOption == null) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Der Parameter 'days' ist erforderlich für die 'extend' Action.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                int days = daysOption.getAsInt();
                if (days <= 0) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                Timestamp newEndDate;
                if (signoff.isUnlimited()) {
                    // If unlimited, extend from now
                    LocalDateTime endDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusDays(days);
                    newEndDate = Timestamp.valueOf(endDateTime);
                } else {
                    // If has end date, extend from that date
                    LocalDateTime currentEnd = signoff.getEndDate().toLocalDateTime();
                    LocalDateTime newEnd = currentEnd.plusDays(days);
                    newEndDate = Timestamp.valueOf(newEnd);
                }

                signoff.update(newEndDate);

                String desc = "### Abmeldung erfolgreich verlängert.\n";
                desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                desc += "Clan: " + c.getInfoStringDB() + "\n";
                desc += "Verlängert um: " + days + " Tag" + (days == 1 ? "" : "e") + "\n";
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                desc += "Neues Enddatum: " + newEndDate.toLocalDateTime().format(formatter) + "\n";

                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                        .queue();

            } else if ("info".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.INFO))
                            .queue();
                    return;
                }

                String desc = "### Abmeldungs-Information\n";
                desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                desc += "Clan: " + c.getInfoStringDB() + "\n";

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                desc += "Startdatum: " + signoff.getStartDate().toLocalDateTime().format(formatter) + "\n";

                if (signoff.isUnlimited()) {
                    desc += "Dauer: Unbegrenzt\n";
                } else {
                    desc += "Enddatum: " + signoff.getEndDate().toLocalDateTime().format(formatter) + "\n";
                }

                if (signoff.getReason() != null) {
                    desc += "Grund: " + signoff.getReason() + "\n";
                }

                desc += "Pings: " + (signoff.isReceivePings() ? "Aktiviert" : "Deaktiviert") + "\n";

                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
                        .queue();
            }

        }, "SignoffCommand-" + event.getUser().getId()).start();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("signoff"))
            return;

        new Thread(() -> {
            String focused = event.getFocusedOption().getName();
            String input = event.getFocusedOption().getValue();

            if (focused.equals("player")) {
                List<Command.Choice> choices = DBManager.getPlayerlistAutocompleteNoWaitlist(input,
                        DBManager.InClanType.INCLAN);
                event.replyChoices(choices).queue(_ -> {
                }, _ -> {
                });
            } else if (focused.equals("action")) {
                List<Command.Choice> choices = List.of(
                        new Command.Choice("Erstellen", "create"),
                        new Command.Choice("Beenden", "end"),
                        new Command.Choice("Verlängern", "extend"),
                        new Command.Choice("Info", "info"));
                event.replyChoices(choices).queue(_ -> {
                }, _ -> {
                });
            }
        }, "SignoffAutocomplete-" + event.getUser().getId()).start();
    }
}
