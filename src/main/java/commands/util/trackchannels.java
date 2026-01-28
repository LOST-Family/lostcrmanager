package commands.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import datautil.DBManager;
import util.MessageUtil;
import datawrapper.Player;
import datawrapper.TrackChannel;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

@SuppressWarnings("null")
public class trackchannels extends ListenerAdapter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        if (commandName.equals("trackchanneladd")) {
            handleAdd(event);
        } else if (commandName.equals("trackchannelremove")) {
            handleRemove(event);
        } else if (commandName.equals("trackchannellist")) {
            handleList(event);
        } else if (commandName.equals("trackchanneltime")) {
            handleTime(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        if (!new User(event.getUser().getId()).isAdmin()) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "Du hast keine Berechtigung für diesen Befehl.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        String channelId = event.getOption("channelid").getAsString();

        TrackChannel.add(name, channelId);
        event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel",
                "Kanal `" + name + "` (ID: " + channelId + ") wurde erfolgreich hinzugefügt.",
                MessageUtil.EmbedType.SUCCESS)).queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        if (!new User(event.getUser().getId()).isAdmin()) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "Du hast keine Berechtigung für diesen Befehl.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        int id = Integer.parseInt(event.getOption("trackchannel").getAsString());
        TrackChannel tc = TrackChannel.getById(id);

        if (tc == null) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "TrackChannel mit ID " + id + " nicht gefunden.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        TrackChannel.remove(id);
        event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "TrackChannel `" + tc.getName() + "` wurde entfernt.",
                MessageUtil.EmbedType.SUCCESS)).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        if (!isColeaderOrHigher(event.getUser().getId())) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "Du hast keine Berechtigung für diesen Befehl.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        ArrayList<TrackChannel> all = TrackChannel.getAll();
        if (all.isEmpty()) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "Es sind keine TrackChannels konfiguriert.",
                    MessageUtil.EmbedType.INFO)).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (TrackChannel tc : all) {
            String timeStr = tc.getTimestamp() != null
                    ? tc.getTimestamp().atZoneSameInstant(BERLIN_ZONE).format(FORMATTER)
                    : "Kein Timestamp";
            sb.append("**ID: ").append(tc.getId()).append("** | Name: ").append(tc.getName()).append(" | Channel: <#")
                    .append(tc.getChannelId()).append("> | Zeit: ").append(timeStr).append("\n");
        }

        event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel Liste", sb.toString(), MessageUtil.EmbedType.INFO))
                .queue();
    }

    private void handleTime(SlashCommandInteractionEvent event) {
        if (!isColeaderOrHigher(event.getUser().getId())) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "Du hast keine Berechtigung für diesen Befehl.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        int id = Integer.parseInt(event.getOption("trackchannel").getAsString());
        String timeInput = event.getOption("timestamp").getAsString();
        TrackChannel tc = TrackChannel.getById(id);

        if (tc == null) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel", "TrackChannel mit ID " + id + " nicht gefunden.",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(timeInput, FORMATTER);
            OffsetDateTime offsetDateTime = localDateTime.atZone(BERLIN_ZONE).toOffsetDateTime();
            tc.setTimestamp(offsetDateTime);
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel",
                    "Zeit für `" + tc.getName() + "` auf `" + timeInput + "` gesetzt.", MessageUtil.EmbedType.SUCCESS))
                    .queue();
        } catch (DateTimeParseException e) {
            event.replyEmbeds(MessageUtil.buildEmbed("TrackChannel",
                    "Ungültiges Zeitformat. Bitte verwende `dd.MM.yyyy HH:mm` (z.B. 29.01.2026 14:30).",
                    MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
        }
    }

    private boolean isColeaderOrHigher(String userId) {
        User user = new User(userId);
        if (user.isAdmin())
            return true;

        for (Player.RoleType role : user.getClanRoles().values()) {
            if (role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER || role == Player.RoleType.ADMIN) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        String focusedOption = event.getFocusedOption().getName();
        String input = event.getFocusedOption().getValue();

        if (commandName.equals("trackchannelremove") || commandName.equals("trackchanneltime")) {
            if (focusedOption.equals("trackchannel")) {
                event.replyChoices(DBManager.getTrackChannelsAutocomplete(input)).queue();
            } else if (focusedOption.equals("timestamp") && commandName.equals("trackchanneltime")) {
                event.replyChoices(getTimestampAutocomplete(input)).queue();
            }
        }
    }

    private List<Command.Choice> getTimestampAutocomplete(String input) {
        List<String> suggestions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(BERLIN_ZONE);

        // Always suggest next 7 days at current hour if input is short or empty
        if (input.isEmpty()) {
            for (int i = 0; i < 7; i++) {
                suggestions.add(now.plusDays(i).withMinute(0).format(FORMATTER));
            }
        } else if (input.length() <= 2) {
            // If user started typing a day, suggest days of current month
            try {
                int day = Integer.parseInt(input);
                if (day >= 1 && day <= 31) {
                    suggestions.add(String.format("%02d.%02d.%d 12:00", day, now.getMonthValue(), now.getYear()));
                    suggestions.add(String.format("%02d.%02d.%d 20:00", day, now.getMonthValue(), now.getYear()));
                }
            } catch (NumberFormatException ignored) {
            }
        } else {
            // Basic filtering if they typ more
            // For simplicity, we just offer a few next hours from now that match the input
            // start
            for (int i = 0; i < 24 * 7; i++) {
                String s = now.plusHours(i).withMinute(0).format(FORMATTER);
                if (s.startsWith(input)) {
                    suggestions.add(s);
                }
                if (suggestions.size() >= 25)
                    break;
            }
        }

        return suggestions.stream()
                .distinct()
                .limit(25)
                .map(s -> new Command.Choice(s, s))
                .collect(Collectors.toList());
    }
}
