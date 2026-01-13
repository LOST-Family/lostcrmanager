package commands.reminders;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.Connection;
import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class remindersinfo extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("remindersinfo"))
			return;
		event.deferReply().queue();
		String title = "Reminder-Informationen";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter Clan ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (clantag.equals("warteliste")) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
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

		// Get all reminders for this clan
		ArrayList<ReminderInfo> reminders = new ArrayList<>();
		String sql = "SELECT id, channelid, time, weekday FROM reminders WHERE clantag = ?";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, clantag);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int id = rs.getInt("id");
					String channelId = rs.getString("channelid");
					Time time = rs.getTime("time");
					String weekday = rs.getString("weekday");
					reminders.add(new ReminderInfo(id, channelId, time, weekday));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		// Sort reminders with Thursday 10:00 as the starting point
		reminders.sort(new ReminderComparator());

		String desc = "### Reminder für Clan: " + c.getInfoStringDB() + "\n\n";

		if (reminders.isEmpty()) {
			desc += "Keine Reminder konfiguriert.\n";
		} else {
			for (ReminderInfo reminder : reminders) {
				desc += "**ID:** " + reminder.id + " | ";
				desc += "**Kanal:** <#" + reminder.channelId + "> | ";
				desc += "**Zeit:** " + reminder.time.toLocalTime().toString() + " | ";
				desc += "**Wochentag:** " + capitalizeWeekday(reminder.weekday) + "\n";
			}
			desc += "\nReminder werden am konfigurierten Wochentag zur konfigurierten Zeit gesendet.\n";
			desc += "Sie erinnern Spieler, die heute weniger als 4 Decks verwendet haben.";
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("remindersinfo"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocompleteNoWaitlist(input);
			event.replyChoices(choices).queue();
		}
	}

	private static class ReminderComparator implements Comparator<ReminderInfo> {
		// Reference point: Thursday 10:00
		private static final int REFERENCE_DAY = 3; // Thursday (0=Monday, 6=Sunday)
		private static final LocalTime REFERENCE_TIME = LocalTime.of(10, 0);
		
		@Override
		public int compare(ReminderInfo r1, ReminderInfo r2) {
			int offset1 = calculateOffset(r1);
			int offset2 = calculateOffset(r2);
			return Integer.compare(offset1, offset2);
		}
		
		/**
		 * Calculate offset in minutes from the reference point (Thursday 10:00).
		 * Thursday 10:00 = 0, Friday 10:00 = 1440 (24*60), etc.
		 */
		private int calculateOffset(ReminderInfo reminder) {
			int dayOfWeek = getDayOfWeekIndex(reminder.weekday);
			LocalTime localTime = reminder.time.toLocalTime();
			
			// Calculate day offset from Thursday
			int dayOffset = dayOfWeek - REFERENCE_DAY;
			if (dayOffset < 0) {
				dayOffset += 7; // Wrap around to next week
			}
			
			// Calculate time offset from 10:00 on that day
			int timeOffsetMinutes = localTime.getHour() * 60 + localTime.getMinute();
			int referenceTimeMinutes = REFERENCE_TIME.getHour() * 60 + REFERENCE_TIME.getMinute();
			
			// If we're on Thursday but before 10:00, treat it as next week's Thursday
			if (dayOffset == 0 && timeOffsetMinutes < referenceTimeMinutes) {
				dayOffset = 7;
			}
			
			// Total offset in minutes
			return dayOffset * 24 * 60 + timeOffsetMinutes - referenceTimeMinutes;
		}
		
		/**
		 * Convert weekday string to index (0=Monday, 6=Sunday)
		 */
		private int getDayOfWeekIndex(String weekday) {
			switch (weekday.toLowerCase()) {
				case "monday": return 0;
				case "tuesday": return 1;
				case "wednesday": return 2;
				case "thursday": return 3;
				case "friday": return 4;
				case "saturday": return 5;
				case "sunday": return 6;
				default: return 0;
			}
		}
	}

	private static class ReminderInfo {
		int id;
		String channelId;
		Time time;
		String weekday;

		ReminderInfo(int id, String channelId, Time time, String weekday) {
			this.id = id;
			this.channelId = channelId;
			this.time = time;
			this.weekday = weekday;
		}
	}
	
	private static String capitalizeWeekday(String weekday) {
		if (weekday == null || weekday.isEmpty()) {
			return "N/A";
		}
		return weekday.substring(0, 1).toUpperCase() + weekday.substring(1);
	}

}
