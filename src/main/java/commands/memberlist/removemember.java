package commands.memberlist;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class removemember extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("removemember"))
			return;
		event.deferReply().queue();
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");

		if (playeroption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playeroptionStr = playeroption.getAsString().toUpperCase().replace("O", "0");

		java.util.ArrayList<String> playerlist = new java.util.ArrayList<>();

		if (playeroptionStr.contains(",")) {
			String[] players = playeroptionStr.split(",");
			for (String t : players) {
				playerlist.add(t.trim());
			}
		} else {
			playerlist.add(playeroptionStr);
		}

		new Thread(() -> {
			boolean firstTime = true;
			for (String playertag : playerlist) {
				Player player = new Player(playertag);

				if (!player.IsLinked()) {
					if (firstTime) {
						event.getHook().editOriginalEmbeds(
								MessageUtil.buildEmbed(title,
										"Der Spieler mit dem Tag " + playertag + " ist nicht verlinkt.",
										MessageUtil.EmbedType.ERROR))
								.queue();
						firstTime = false;
					} else {
						event.getChannel().sendMessageEmbeds(
								MessageUtil.buildEmbed(title,
										"Der Spieler mit dem Tag " + playertag + " ist nicht verlinkt.",
										MessageUtil.EmbedType.ERROR))
								.queue();
					}
					continue;
				}

				Player.RoleType role = player.getRole();

				Clan playerclan = player.getClanDB();

				if (playerclan == null) {
					if (firstTime) {
						event.getHook().editOriginalEmbeds(
								MessageUtil.buildEmbed(title,
										"Der Spieler mit dem Tag " + playertag + " ist in keinem Clan.",
										MessageUtil.EmbedType.ERROR))
								.queue();
						firstTime = false;
					} else {
						event.getChannel().sendMessageEmbeds(
								MessageUtil.buildEmbed(title,
										"Der Spieler mit dem Tag " + playertag + " ist in keinem Clan.",
										MessageUtil.EmbedType.ERROR))
								.queue();
					}
					continue;
				}

				String clantag = playerclan.getTag();

				User userexecuted = new User(event.getUser().getId());
				if (!userexecuted.isColeaderOrHigher()) {
					if (firstTime) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können (Spieler: "
										+ playertag + ").",
								MessageUtil.EmbedType.ERROR)).queue();
						firstTime = false;
					} else {
						event.getChannel().sendMessageEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können (Spieler: "
										+ playertag + ").",
								MessageUtil.EmbedType.ERROR)).queue();
					}
					continue;
				}

				if (role == Player.RoleType.LEADER
						&& userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
					if (firstTime) {
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Um jemanden als Leader zu entfernen, musst du Admin sein (Spieler: "
												+ playertag + ").",
										MessageUtil.EmbedType.ERROR))
								.queue();
						firstTime = false;
					} else {
						event.getChannel()
								.sendMessageEmbeds(MessageUtil.buildEmbed(title,
										"Um jemanden als Leader zu entfernen, musst du Admin sein (Spieler: "
												+ playertag + ").",
										MessageUtil.EmbedType.ERROR))
								.queue();
					}
					continue;
				}
				if (role == Player.RoleType.COLEADER
						&& !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
								|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
					if (firstTime) {
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Um jemanden als Vize-Anführer zu entfernen, musst du Admin oder Anführer sein (Spieler: "
												+ playertag + ").",
										MessageUtil.EmbedType.ERROR))
								.queue();
						firstTime = false;
					} else {
						event.getChannel()
								.sendMessageEmbeds(MessageUtil.buildEmbed(title,
										"Um jemanden als Vize-Anführer zu entfernen, musst du Admin oder Anführer sein (Spieler: "
												+ playertag + ").",
										MessageUtil.EmbedType.ERROR))
								.queue();
					}
					continue;
				}

				String clanname = playerclan.getNameDB();

				DBUtil.executeUpdate("DELETE FROM clan_members WHERE player_tag = ?", playertag);
				String desc = "";
				if (!playerclan.getTag().equals("warteliste")) {
					try {
						desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde aus dem Clan "
								+ clanname + " entfernt.";
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					try {
						desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
								+ " wurde aus der Warteliste entfernt.";
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				if (!playerclan.getTag().equals("warteliste")) {
					String userid = player.getUser().getUserID();
					Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
					Member member = guild.getMemberById(userid);
					String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
					Role memberrole = guild.getRoleById(memberroleid);
					if (member != null) {
						if (member.getRoles().contains(memberrole)) {
							desc += "\n\n";
							desc += "**Der User <@" + userid + "> hat die Rolle <@&" + memberroleid
									+ "> noch. Nehme sie ihm manuell, falls erwünscht.**\n";
						} else {
							desc += "\n\n";
							desc += "**Der User <@" + userid + "> hat die Rolle <@&" + memberroleid
									+ "> bereits nicht mehr.**\n";
						}
					} else {
						desc += "\n\n**Der User <@" + userid + "> ist nicht auf dem Server.**\n";
					}
					MessageChannelUnion channel = event.getChannel();
					MessageUtil.sendUserPingHidden(channel, userid);
				}

				if (firstTime) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
					firstTime = false;
				} else {
					event.getChannel()
							.sendMessageEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
				}
			} // close for loop
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("removemember"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

			event.replyChoices(choices).queue();
		}
	}

}
