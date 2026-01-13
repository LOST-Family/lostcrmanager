package commands.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class checkroles extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("checkroles"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Rollen-Check";

			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}

			if (!hasPermission) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			OptionMapping clanOption = event.getOption("clan");
			OptionMapping ignoreHiddenColeadersOption = event.getOption("ignore_hiddencoleaders");

			if (clanOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Parameter 'clan' ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = clanOption.getAsString();
			
			boolean ignoreHiddenColeaders = false;
			if (ignoreHiddenColeadersOption != null) {
				String ignoreHiddenColeadersValue = ignoreHiddenColeadersOption.getAsString();
				if ("true".equalsIgnoreCase(ignoreHiddenColeadersValue)) {
					ignoreHiddenColeaders = true;
				} else {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Der ignore_hiddencoleaders Parameter muss entweder \"true\" enthalten oder nicht angegeben sein (false).",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			performRoleCheck(event.getHook(), event.getGuild(), title, clantag, ignoreHiddenColeaders);

		}, "CheckRolesCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("checkroles"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				event.replyChoices(choices).queue();
			} else if (focused.equals("ignore_hiddencoleaders")) {
				List<Command.Choice> choices = new ArrayList<>();
				if ("true".startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice("true", "true"));
				}
				event.replyChoices(choices).queue();
			}
		}, "CheckRolesAutocomplete-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("checkroles_"))
			return;

		event.deferEdit().queue();

		// Parse the button ID: checkroles_{clantag}_{ignoreHiddenColeaders}
		String remainder = id.substring("checkroles_".length());
		String clantag;
		boolean ignoreHiddenColeaders = false;
		
		int lastUnderscore = remainder.lastIndexOf("_");
		if (lastUnderscore != -1) {
			clantag = remainder.substring(0, lastUnderscore);
			String ignoreHiddenColeadersStr = remainder.substring(lastUnderscore + 1);
			ignoreHiddenColeaders = "true".equals(ignoreHiddenColeadersStr);
		} else {
			// Fallback for old button IDs without ignore_hiddencoleaders
			clantag = remainder;
		}
		
		String title = "Rollen-Check";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		final boolean ignoreHiddenColeadersFinal = ignoreHiddenColeaders;
		new Thread(() -> {
			performRoleCheck(event.getHook(), event.getGuild(), title, clantag, ignoreHiddenColeadersFinal);
		}, "CheckRolesRefresh-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	private void performRoleCheck(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild, String title,
			String clantag, boolean ignoreHiddenColeaders) {

		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Clan clan = new Clan(clantag);
		ArrayList<Player> playerlist = clan.getPlayersDB();

		if (playerlist == null || playerlist.isEmpty()) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Mitglieder in diesem Clan gefunden.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Build description with members missing roles
		StringBuilder description = new StringBuilder();
		description.append("## ").append(clan.getInfoStringDB()).append("\n\n");

		int totalMembers = 0;
		int membersWithoutRole = 0;
		int linkedMembers = 0;
		int unlinkedMembers = 0;

		List<String> missingRolesList = new ArrayList<>();

		for (Player p : playerlist) {
			Player.RoleType roleDB = p.getRole();
			if (roleDB == null || roleDB == Player.RoleType.NOTINCLAN) {
				continue;
			}

			// Skip hidden coleaders if ignore_hiddencoleaders is true
			if (ignoreHiddenColeaders && p.isHiddenColeader()) {
				continue;
			}

			totalMembers++;

			User user = p.getUser();
			if (user == null) {
				unlinkedMembers++;
				continue;
			}

			linkedMembers++;

			// Always check the MEMBER role first
			String memberRoleId = clan.getRoleID(Clan.Role.MEMBER);
			
			// Get expected Discord role ID based on clan role (for higher roles)
			String expectedRoleId = null;
			switch (roleDB) {
			case LEADER:
				expectedRoleId = clan.getRoleID(Clan.Role.LEADER);
				break;
			case COLEADER:
				expectedRoleId = clan.getRoleID(Clan.Role.COLEADER);
				break;
			case ELDER:
				expectedRoleId = clan.getRoleID(Clan.Role.ELDER);
				break;
			case MEMBER:
				expectedRoleId = memberRoleId;
				break;
			default:
				break;
			}

			// Check if Discord user has the expected role(s)
			Member member = guild.getMemberById(user.getUserID());
			if (member == null) {
				// User is linked but not in the Discord server
				missingRolesList.add(String.format("%s - **%s** - <@%s> (nicht auf dem Server)", p.getInfoStringDB(),
						getRoleDisplayName(roleDB), user.getUserID()));
				membersWithoutRole++;
			} else {
				// Check member role first (for everyone)
				if (memberRoleId != null) {
					Role memberRole = guild.getRoleById(memberRoleId);
					if (memberRole != null && !member.getRoles().contains(memberRole)) {
						missingRolesList.add(String.format("%s - **%s** - <@%s> (fehlt: %s)", p.getInfoStringDB(),
								getRoleDisplayName(roleDB), user.getUserID(), memberRole.getAsMention()));
						membersWithoutRole++;
						continue; // Skip checking other roles if member role is missing
					}
				}
				
				// Check additional role for non-members (leader, coleader, elder)
				if (expectedRoleId != null && !expectedRoleId.equals(memberRoleId)) {
					Role expectedRole = guild.getRoleById(expectedRoleId);
					if (expectedRole == null) {
						// Role doesn't exist in Discord server
						missingRolesList.add(String.format("%s - **%s** - <@%s> (Rolle nicht konfiguriert)",
								p.getInfoStringDB(), getRoleDisplayName(roleDB), user.getUserID()));
						membersWithoutRole++;
					} else if (!member.getRoles().contains(expectedRole)) {
						// Member doesn't have the expected role
						missingRolesList.add(String.format("%s - **%s** - <@%s> (fehlt: %s)", p.getInfoStringDB(),
								getRoleDisplayName(roleDB), user.getUserID(), expectedRole.getAsMention()));
						membersWithoutRole++;
					}
				}
			}
		}

		// Summary statistics
		description.append("**Statistik:**\n");
		description.append("Gesamte Mitglieder: ").append(totalMembers).append("\n");
		description.append("Verlinkte Mitglieder: ").append(linkedMembers).append("\n");
		description.append("Nicht verlinkte Mitglieder: ").append(unlinkedMembers).append("\n");
		description.append("Mitglieder ohne korrekte Rolle: ").append(membersWithoutRole).append("\n\n");

		// List members missing roles
		if (missingRolesList.isEmpty()) {
			description.append("**✅ Alle verlinkten Mitglieder haben die korrekte Discord-Rolle!**\n");
		} else {
			description.append("**Mitglieder ohne korrekte Discord-Rolle:**\n");
			for (String member : missingRolesList) {
				description.append(member).append("\n");
			}
		}

		// Create refresh button
		Button refreshButton = Button.secondary("checkroles_" + clantag + "_" + ignoreHiddenColeaders, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

		// Add timestamp
		ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
		String formatiert = jetzt.format(formatter);

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, description.toString(), MessageUtil.EmbedType.INFO,
				"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
	}

	private String getRoleDisplayName(Player.RoleType roleType) {
		switch (roleType) {
		case LEADER:
			return "Anführer";
		case COLEADER:
			return "Vize-Anführer";
		case ELDER:
			return "Ältester";
		case MEMBER:
			return "Mitglied";
		default:
			return "Unbekannt";
		}
	}
}
