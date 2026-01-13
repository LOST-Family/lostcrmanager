package commands.reminders;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.annotation.Nonnull;

import datautil.Connection;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class remindersremove extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("remindersremove"))
			return;
		event.deferReply().queue();
		String title = "Reminder entfernen";

		OptionMapping idOption = event.getOption("id");

		if (idOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ID ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		int id = idOption.getAsInt();

		// Get reminder details before deletion for permission check
		String clantag = null;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement("SELECT clantag FROM reminders WHERE id = ?")) {
			pstmt.setInt(1, id);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					clantag = rs.getString("clantag");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		if (clantag == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Es existiert kein Reminder mit dieser ID.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Der Clan des Reminders existiert nicht mehr.", MessageUtil.EmbedType.ERROR))
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

		// Delete the reminder
		DBUtil.executeUpdate("DELETE FROM reminders WHERE id = ?", id);

		String desc = "### Der Reminder wurde entfernt.\n";
		desc += "Clan: " + c.getInfoStringDB() + "\n";
		desc += "ID: " + id + "\n";
		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
				.queue();
	}

}
