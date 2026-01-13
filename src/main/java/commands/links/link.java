package commands.links;

import javax.annotation.Nonnull;

import commands.wins.wins;
import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class link extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("link"))
			return;
		event.deferReply().queue();
		String title = "User-Link";

		boolean b = false;
		User userexecuted = new User(event.getUser().getId());
		for (String clantag : DBManager.getAllClans()) {
			if (userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER) {
				b = true;
				break;
			}
		}
		if (b == false) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		OptionMapping tagOption = event.getOption("tag");
		OptionMapping useroption = event.getOption("user");
		OptionMapping useridoption = event.getOption("userid");

		if (tagOption == null || (useroption == null && useridoption == null)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Der Tag und einer der anderen Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String tag = tagOption.getAsString();
		if (!tag.startsWith("#")) {
			tag = "#" + tag;
		}
		String userid;
		if (useroption != null) {
			userid = useroption.getAsMentionable().getId();
		} else {
			userid = useridoption.getAsString();
		}

		final String finalTag = tag;
		final String finalUserid = userid;
		new Thread(() -> {
			Player p = new Player(finalTag);

			if (p.AccExists()) {
				String playername = null;
				try {
					playername = p.getNameAPI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (!p.IsLinked()) {
					DBUtil.executeUpdate("INSERT INTO players (cr_tag, discord_id, name) VALUES (?, ?, ?)", finalTag, finalUserid,
							playername);
					
					// Save initial wins data for the newly linked player (async to not block response)
					Thread saveWinsThread = new Thread(() -> {
						wins.savePlayerWins(finalTag);
					});
					saveWinsThread.setDaemon(true);
					saveWinsThread.start();
					
					Player player = new Player(finalTag);
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
							+ " wurde erfolgreich mit dem User <@" + finalUserid + "> verknüpft.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
					MessageUtil.sendUserPingHidden(event.getChannel(), finalUserid);
				} else {
					Player player = new Player(finalTag);
					String linkeduserid = player.getUser().getUserID();
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " ist bereits mit <@"
							+ linkeduserid + "> verknüpft. Bitte verwende zuerst ``/unlink``.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
							.queue();
					MessageUtil.sendUserPingHidden(event.getChannel(), linkeduserid);
				}
			} else {
				String desc = "Der Spieler mit dem Tag " + finalTag + " existiert nicht oder es ist ein API-Fehler aufgetreten.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}
		}).start();

	}

}
