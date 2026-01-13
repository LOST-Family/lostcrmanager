package commands.memberlist;

import java.util.ArrayList;
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

public class addmember extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("addmember"))
			return;
		event.deferReply().queue();
		String title = "Memberverwaltung";

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping playeroption = event.getOption("player");
		OptionMapping roleoption = event.getOption("role");

		if (clanOption == null || playeroption == null || roleoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();
		String clantag = clanOption.getAsString();
		String role = roleoption.getAsString();

		User userexecuted = new User(event.getUser().getId());
		if (!clantag.equals("warteliste")) {
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		} else {
			boolean b = false;
			for (String clantags : DBManager.getAllClans()) {
				if (userexecuted.getClanRoles().get(clantags) == Player.RoleType.ADMIN
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.LEADER
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.COLEADER) {
					b = true;
					break;
				}
			}
			if (b == false) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		if (!(role.equals("leader") || role.equals("coleader") || role.equals("hiddencoleader") || role.equals("elder") || role.equals("member"))) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Gib eine gültige Rolle an.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role.equals("leader") && userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Leader hinzuzufügen, musst du Admin sein.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role.equals("coleader") && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Vize-Anführer hinzuzufügen, musst du Admin oder Anführer sein.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role.equals("hiddencoleader") && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Vize-Anführer (versteckt) hinzuzufügen, musst du Admin oder Anführer sein.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		new Thread(() -> {
			Player p = new Player(playertag);
			Clan c = new Clan(clantag);

			if (!p.IsLinked()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (!c.ExistsDB()) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (new Player(playertag).getClanDB() != null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Dieser Spieler ist bereits in einem Clan.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			DBUtil.executeUpdate("INSERT INTO clan_members (player_tag, clan_tag, clan_role) VALUES (?, ?, ?)", playertag,
					clantag, role);
			String rolestring = role.equals("leader") ? "Anführer"
					: role.equals("coleader") ? "Vize-Anführer"
							: role.equals("hiddencoleader") ? "Vize-Anführer (versteckt)"
								: role.equals("elder") ? "Ältester" : role.equals("member") ? "Mitglied" : null;

			String desc = "";
			if (!clantag.equals("warteliste")) {
				try {
					desc += "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB()) + " wurde erfolgreich dem Clan "
							+ new Clan(clantag).getInfoStringDB() + " als " + rolestring + " hinzugefügt.";
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				try {
					desc += "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB())
							+ " wurde erfolgreich der Warteliste hinzugefügt.";
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (!clantag.equals("warteliste")) {
				String userid = p.getUser().getUserID();
				Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
				Member member = guild.getMemberById(userid);
				String memberroleid = c.getRoleID(Clan.Role.MEMBER);
				Role memberrole = guild.getRoleById(memberroleid);
				if (member != null) {
					if (member.getRoles().contains(memberrole)) {
						desc += "\n\n**Der User <@" + userid + "> hat bereits die Rolle <@&" + memberroleid + ">.**";
					} else {
						guild.addRoleToMember(member, memberrole).queue();
						desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + memberroleid + "> hinzugefügt.**";
					}
				} else {
					desc += "\n\n**Der User <@" + userid
							+ "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt.**";
				}

				MessageChannelUnion channel = event.getChannel();
				MessageUtil.sendUserPingHidden(channel, userid);
			}

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("addmember"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.NOTINCLAN);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("role")) {
			if (!event.getOption("clan").getAsString().equals("warteliste")) {
				List<Command.Choice> choices = new ArrayList<>();
				choices.add(new Command.Choice("Anführer", "leader"));
				choices.add(new Command.Choice("Vize-Anführer", "coleader"));
				choices.add(new Command.Choice("Vize-Anführer (versteckt)", "hiddencoleader"));
				choices.add(new Command.Choice("Ältester", "elder"));
				choices.add(new Command.Choice("Mitglied", "member"));
				event.replyChoices(choices).queue();
			} else {
				List<Command.Choice> choices = new ArrayList<>();
				choices.add(new Command.Choice("Wartend", "member"));
				event.replyChoices(choices).queue();
			}
		}
	}
}
