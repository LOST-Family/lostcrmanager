package commands.reminders;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.Connection;
import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class remindersadd extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("remindersadd"))
			return;
		event.deferReply().queue();
		String title = "Reminder hinzufügen";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping channelOption = event.getOption("channel");
		OptionMapping timeOption = event.getOption("time");
		OptionMapping weekdayOption = event.getOption("weekday");

		if (clanOption == null || channelOption == null || timeOption == null || weekdayOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Alle Parameter (clan, channel, time, weekday) sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();
		String channelId = channelOption.getAsChannel().getId();
		String timeStr = timeOption.getAsString();
		String weekday = weekdayOption.getAsString().toLowerCase();

		// Validate weekday
		if (!isValidWeekday(weekday)) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiger Wochentag. Erlaubt sind: monday, tuesday, wednesday, thursday, friday, saturday, sunday",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (clantag.equals("warteliste")) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
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
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Parse time
		LocalTime reminderTime;
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
			reminderTime = LocalTime.parse(timeStr, formatter);
		} catch (DateTimeParseException e) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Ungültiges Zeitformat. Bitte nutze HH:mm (z.B. 14:30).", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Verify channel exists
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der angegebene Kanal existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Get available ID
		int id = getAvailableReminderID();

		// Insert reminder
		DBUtil.executeUpdate("INSERT INTO reminders (id, clantag, channelid, time, weekday) VALUES (?, ?, ?, ?, ?)", id,
				clantag, channelId, Time.valueOf(reminderTime), weekday);

		String desc = "### Der Reminder wurde hinzugefügt.\n";
		desc += "Clan: " + c.getInfoStringDB() + "\n";
		desc += "Kanal: <#" + channelId + ">\n";
		desc += "Zeit: " + timeStr + "\n";
		desc += "Wochentag: " + germanWeekday(weekday) + "\n";
		desc += "ID: " + id + "\n";

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("remindersadd"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocompleteNoWaitlist(input);
			event.replyChoices(choices).queue();
		} else if (focused.equals("weekday")) {
			List<Command.Choice> choices = new ArrayList<>();
			String[] weekdays = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" };
			for (String day : weekdays) {
				if (day.startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(germanWeekday(day), day));
				}
			}
			event.replyChoices(choices).queue();
		}
	}

	private static boolean isValidWeekday(String weekday) {
		String[] validWeekdays = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" };
		for (String day : validWeekdays) {
			if (day.equals(weekday)) {
				return true;
			}
		}
		return false;
	}

	private static String germanWeekday(String weekday) {
		if (weekday == null || weekday.isEmpty()) {
			return weekday;
		}
		switch (weekday) {
		case "monday":
			return "Montag";
		case "tuesday":
			return "Dienstag";
		case "wednesday":
			return "Mittwoch";
		case "thursday":
			return "Donnerstag";
		case "friday":
			return "Freitag";
		case "saturday":
			return "Samstag";
		case "sunday":
			return "Sonntag";
		}
		return null;
	}

	private static int getAvailableReminderID() {
		String sql = "SELECT id FROM reminders";
		int available = 0;

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				ArrayList<Integer> used = new ArrayList<>();
				while (rs.next()) {
					used.add(rs.getInt("id"));
				}
				while (used.contains(available)) {
					available++;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return available;
	}

}
