package commands.memberlist;

import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

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

public class signofflist extends ListenerAdapter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("signofflist"))
            return;
        event.deferReply().queue();

        new Thread(() -> {
            String title = "Abmeldungsliste";

            OptionMapping monthOption = event.getOption("month");
            if (monthOption == null) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.buildEmbed(title, "Der Parameter 'month' ist erforderlich!",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            String monthValue = monthOption.getAsString();
            int year, month;

            if ("current".equals(monthValue)) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
                year = now.getYear();
                month = now.getMonthValue();
            } else {
                try {
                    String[] parts = monthValue.split("-");
                    year = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]);
                } catch (final NumberFormatException e) {
                    event.getHook().editOriginalEmbeds(
                            MessageUtil.buildEmbed(title,
                                    "Ungültiges Monatsformat. Bitte nutze die Autovervollständigung.",
                                    MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }
            }

            // Determine if user is coleader or higher (in any clan)
            User userExecuted = new User(event.getUser().getId());
            boolean isColeader = userExecuted.isColeaderOrHigher();

            OptionMapping showReasonsOption = event.getOption("showreasons");
            boolean showReasonsChosen = showReasonsOption != null && showReasonsOption.getAsBoolean();

            if (showReasonsChosen && !isColeader) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.buildEmbed(title,
                                "Nur Vize-Anführer können die Begründungen sehen!",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            boolean showReasons = isColeader && showReasonsChosen;

            generateAndSendList(event.getHook(), year, month, showReasons, false);

        }, "SignofflistCommand-" + event.getUser().getId()).start();
    }

    private void generateAndSendList(net.dv8tion.jda.api.interactions.InteractionHook hook, int year, int month,
            boolean showReasons, boolean isRefresh) {
        String title = "Abmeldungsliste";

        if (isRefresh) {
            hook.editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Wird aktualisiert...", MessageUtil.EmbedType.LOADING)).queue();
        }

        // Query signoffs for the month
        List<MemberSignoff> signoffs = MemberSignoff.getSignoffsForMonth(year, month);

        if (signoffs.isEmpty()) {
            String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
            hook.editOriginalEmbeds(
                    MessageUtil.buildEmbed(title,
                            "Keine Abmeldungen im " + monthName + " " + year + " gefunden.",
                            MessageUtil.EmbedType.INFO))
                    .queue();
            return;
        }

        class SignoffData {
            MemberSignoff signoff;
            String playerName;
            Long clanIndex;
            String clanName;
        }

        List<SignoffData> dataList = new ArrayList<>();
        for (final MemberSignoff s : signoffs) {
            SignoffData d = new SignoffData();
            d.signoff = s;
            Player p = new Player(s.getPlayerTag());
            d.playerName = p.getNameDB();
            if (d.playerName == null) {
                d.playerName = s.getPlayerTag();
            }
            Clan clan = p.getClanDB();
            if (clan != null && clan.getIndex() != null) {
                d.clanIndex = clan.getIndex();
            } else {
                d.clanIndex = Long.MAX_VALUE;
            }
            d.clanName = clan != null && clan.getNameDB() != null ? clan.getNameDB() : "Kein Clan";
            dataList.add(d);
        }

        dataList.sort((d1, d2) -> {
            int cmp = Long.compare(d1.clanIndex, d2.clanIndex);
            if (cmp == 0) {
                return d1.playerName.compareToIgnoreCase(d2.playerName);
            }
            return cmp;
        });

        // Build the output
        String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
        StringBuilder desc = new StringBuilder();
        desc.append("### Abmeldungen im ").append(monthName).append(" ").append(year).append("\n\n");

        for (final SignoffData d : dataList) {
            MemberSignoff signoff = d.signoff;

            desc.append("**").append(MessageUtil.unformat(d.playerName)).append("** (")
                    .append(signoff.getPlayerTag()).append(") - ").append(d.clanName).append("\n");

            // Start date
            desc.append("Von: ").append(signoff.getStartDate().toLocalDateTime().format(DATE_FORMAT));

            // End date
            if (signoff.getEndDate() == null) {
                desc.append(" → Unbegrenzt");
            } else {
                desc.append(" → ").append(signoff.getEndDate().toLocalDateTime().format(DATE_FORMAT));
            }
            desc.append("\n");

            // Reason (coleader+ only)
            if (showReasons && signoff.getReason() != null) {
                desc.append("Grund: ").append(signoff.getReason()).append("\n");
            }

            desc.append("\n");
        }

        // Discord embed description limit is 4096 chars
        String description = desc.toString();
        if (description.length() > 4000) {
            description = description.substring(0, 3997) + "...";
        }

        ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
        String formatiert = jetzt.format(DATE_FORMAT);

        hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, description, MessageUtil.EmbedType.INFO,
                "Zuletzt aktualisiert am " + formatiert))
                .setActionRow(net.dv8tion.jda.api.interactions.components.buttons.Button
                        .secondary("signofflist_refresh_" + year + "_" + month + "_" + showReasons, "\u200B")
                        .withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔁")))
                .queue();
    }

    @Override
    public void onButtonInteraction(
            @Nonnull net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("signofflist_refresh_"))
            return;

        event.deferEdit().queue();

        new Thread(() -> {
            String[] parts = id.split("_");
            int year = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[3]);
            boolean requestedShowReasons = Boolean.parseBoolean(parts[4]);

            User userExecuted = new User(event.getUser().getId());
            boolean isColeader = userExecuted.isColeaderOrHigher();

            if (requestedShowReasons && !isColeader) {
                event.getHook().sendMessage(
                        "Nur Vize-Anführer können diese Liste aktualisieren (da Begründungen abgefragt werden).")
                        .setEphemeral(true).queue();
                return;
            }

            generateAndSendList(event.getHook(), year, month, requestedShowReasons, true);

        }, "SignofflistRefresh-" + event.getUser().getId()).start();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("signofflist"))
            return;

        new Thread(() -> {
            String focused = event.getFocusedOption().getName();
            String input = event.getFocusedOption().getValue();

            if (focused.equals("month")) {
                List<Command.Choice> choices = getMonthAutocomplete(input);
                event.replyChoices(choices).queue(_ -> {
                }, _ -> {
                });
            }
        }, "SignofflistAutocomplete-" + event.getUser().getId()).start();
    }

    private List<Command.Choice> getMonthAutocomplete(String input) {
        List<Command.Choice> choices = new ArrayList<>();
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime now = ZonedDateTime.now(zone);

        // Add "Aktueller Monat" option first
        String currentMonthDisplay = "Aktueller Monat";
        if (currentMonthDisplay.toLowerCase().contains(input.toLowerCase())) {
            choices.add(new Command.Choice(currentMonthDisplay, "current"));
        }

        // Offer last 12 months
        for (int i = 0; i < 12; i++) {
            ZonedDateTime monthDate = now.minusMonths(i);
            int year = monthDate.getYear();
            int month = monthDate.getMonthValue();

            String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
            String display = monthName + " " + year;
            String value = year + "-" + String.format("%02d", month);

            if (display.toLowerCase().contains(input.toLowerCase()) || value.contains(input)) {
                choices.add(new Command.Choice(display, value));
                if (choices.size() >= 25) {
                    break;
                }
            }
        }
        return choices;
    }
}
