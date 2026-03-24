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

            switch (action) {
                case "create" ->                     {
                        if (signoff.isActive()) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Dieser Spieler ist bereits abgemeldet. Nutze die 'extend' oder 'end' Action, um die Abmeldung zu ändern.",
                                    MessageUtil.EmbedType.ERROR))
                                    .queue();
                            return;
                        }       OptionMapping startdateOption = event.getOption("startdate");
                        OptionMapping daysOption = event.getOption("days");
                        OptionMapping reasonOption = event.getOption("reason");
                        OptionMapping pingsOption = event.getOption("pings");
                        Timestamp startDateObj = Timestamp.from(java.time.Instant.now());
                        LocalDateTime startDateTimeLocal = LocalDateTime.now(ZoneId.of("Europe/Berlin"));
                        if (startdateOption != null) {
                            try {
                                DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                                startDateTimeLocal = java.time.LocalDate.parse(startdateOption.getAsString(), format).atStartOfDay();
                                startDateObj = Timestamp.valueOf(startDateTimeLocal);
                            } catch (final Exception e) {
                                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                        "Ungültiges Startdatum. Bitte im Format DD.MM.YYYY angeben.", MessageUtil.EmbedType.ERROR))
                                        .queue();
                                return;
                            }
                        }       Timestamp endDate = null;
                        String durationText = "unbegrenzt";
                        if (daysOption != null) {
                            int days = daysOption.getAsInt();
                            if (days <= 0) {
                                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                        "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                                        .queue();
                                return;
                            }
                            LocalDateTime endDateTime = startDateTimeLocal.plusDays(days);
                            endDate = Timestamp.valueOf(endDateTime);
                            durationText = days + " Tag" + (days == 1 ? "" : "e");
                        }       String reason = reasonOption != null ? reasonOption.getAsString() : null;
                        boolean receivePings = pingsOption != null && pingsOption.getAsBoolean();
                        MemberSignoff.create(playertag, startDateObj, endDate, reason, event.getUser().getId(), receivePings);
                        String desc = "### Abmeldung erfolgreich erstellt.\n";
                        desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                        desc += "Clan: " + c.getInfoStringDB() + "\n";
                        desc += "Startdatum: " + startDateTimeLocal.format(DateTimeFormatter.ofPattern("dd.MM.yyyy" + (startdateOption == null ? " 'um' HH:mm 'Uhr'" : ""))) + "\n";
                        desc += "Dauer: " + durationText + "\n";
                        if (reason != null) {
                            desc += "Grund: " + reason + "\n";
                        }       desc += "\n**Während der Abmeldung:**\n";
                        desc += "- Keine automatischen Kickpunkte (cwfails/winsfails)\n";
                        if (receivePings) {
                            desc += "- **Reminder-Pings AKTIVIERT**\n";
                        } else {
                            desc += "- Keine CW-Reminder-Pings\n";
                        }       event.getHook()
                                .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                                .queue();
                    }
                case "end" ->                     {
                        if (!signoff.isActive()) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.ERROR))
                                    .queue();
                            return;
                        }       MemberSignoff.remove(playertag);
                        String desc = "### Abmeldung erfolgreich beendet.\n";
                        desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                        desc += "Clan: " + c.getInfoStringDB() + "\n";
                        event.getHook()
                                .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                                .queue();
                    }
                case "extend" ->                     {
                        if (!signoff.isActive()) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Dieser Spieler ist aktuell nicht abgemeldet. Nutze die 'create' Action.",
                                    MessageUtil.EmbedType.ERROR))
                                    .queue();
                            return;
                        }       OptionMapping daysOption = event.getOption("days");
                        if (daysOption == null) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Der Parameter 'days' ist erforderlich für die 'extend' Action.",
                                    MessageUtil.EmbedType.ERROR))
                                    .queue();
                            return;
                        }       int days = daysOption.getAsInt();
                        if (days <= 0) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                                    .queue();
                            return;
                        }       Timestamp newEndDate;
                        if (signoff.isUnlimited()) {
                            // If unlimited, extend from now
                            LocalDateTime endDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusDays(days);
                            newEndDate = Timestamp.valueOf(endDateTime);
                        } else {
                            // If has end date, extend from that date
                            LocalDateTime currentEnd = signoff.getEndDate().toLocalDateTime();
                            LocalDateTime newEnd = currentEnd.plusDays(days);
                            newEndDate = Timestamp.valueOf(newEnd);
                        }       signoff.update(newEndDate);
                        String desc = "### Abmeldung erfolgreich verlängert.\n";
                        desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                        desc += "Clan: " + c.getInfoStringDB() + "\n";
                        desc += "Verlängert um: " + days + " Tag" + (days == 1 ? "" : "e") + "\n";
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                        desc += "Neues Enddatum: " + newEndDate.toLocalDateTime().format(formatter) + "\n";
                        event.getHook()
                                .editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                                .queue();
                    }
                case "info" ->                     {
                        if (!signoff.isActive()) {
                            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                    "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.INFO))
                                    .queue();
                            return;
                        }       String desc = "### Abmeldungs-Information\n";
                        desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                        desc += "Clan: " + c.getInfoStringDB() + "\n";
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                        desc += "Startdatum: " + signoff.getStartDate().toLocalDateTime().format(formatter) + "\n";
                        if (signoff.isUnlimited()) {
                            desc += "Dauer: Unbegrenzt\n";
                        } else {
                            desc += "Enddatum: " + signoff.getEndDate().toLocalDateTime().format(formatter) + "\n";
                        }       if (signoff.getReason() != null) {
                            desc += "Grund: " + signoff.getReason() + "\n";
                        }       desc += "Pings: " + (signoff.isReceivePings() ? "Aktiviert" : "Deaktiviert") + "\n";
                        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
                                .queue();
                    }
                default -> {
                }
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

            switch (focused) {
                case "player" ->                     {
                        List<Command.Choice> choices = DBManager.getPlayerlistAutocompleteNoWaitlist(input,
                                DBManager.InClanType.INCLAN);
                        event.replyChoices(choices).queue(_ -> {
                        }, _ -> {
                        });                        }
                case "action" ->                     {
                        List<Command.Choice> choices = List.of(
                                new Command.Choice("Erstellen", "create"),
                                new Command.Choice("Beenden", "end"),
                                new Command.Choice("Verlängern", "extend"),
                                new Command.Choice("Info", "info"));
                        event.replyChoices(choices).queue(_ -> {
                        }, _ -> {
                        });                        }
                case "startdate" ->                     {
                        List<Command.Choice> choices = new java.util.ArrayList<>();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Berlin"));
                        if (input.isEmpty()) {
                            choices.add(new Command.Choice(now.format(formatter) + " (Heute)", now.format(formatter)));
                            choices.add(new Command.Choice(now.plusDays(1).format(formatter) + " (Morgen)", now.plusDays(1).format(formatter)));
                            choices.add(new Command.Choice(now.plusDays(2).format(formatter) + " (Übermorgen)", now.plusDays(2).format(formatter)));
                            choices.add(new Command.Choice(now.plusDays(3).format(formatter), now.plusDays(3).format(formatter)));
                            choices.add(new Command.Choice(now.plusDays(4).format(formatter), now.plusDays(4).format(formatter)));
                        } else {
                            try {
                                String[] parts = input.replace(".", " ").trim().split(" ");
                                if (parts.length > 0 && !parts[0].isEmpty()) {
                                    int day = Integer.parseInt(parts[0]);
                                    int month = parts.length > 1 ? Integer.parseInt(parts[1]) : now.getMonthValue();
                                    int year = parts.length > 2 ? Integer.parseInt(parts[2]) : now.getYear();
                                    
                                    if (parts.length == 1 && day < now.getDayOfMonth()) {
                                        month++;
                                        if (month > 12) {
                                            month = 1;
                                            year++;
                                        }
                                    } else if (parts.length == 2 && (month < now.getMonthValue() || (month == now.getMonthValue() && day < now.getDayOfMonth()))) {
                                        year++;
                                    }
                                    
                                    try {
                                        java.time.LocalDate date = java.time.LocalDate.of(year, month, day);
                                        choices.add(new Command.Choice(date.format(formatter), date.format(formatter)));
                                    } catch (final Exception e) {}
                                }
                            } catch (final NumberFormatException e) {}
                        }       event.replyChoices(choices).queue(_ -> {}, _ -> {});
                    }
                default -> {
                }
            }
        }, "SignoffAutocomplete-" + event.getUser().getId()).start();
    }
}
