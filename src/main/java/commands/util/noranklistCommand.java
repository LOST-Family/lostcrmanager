package commands.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import util.MessageUtil;

@SuppressWarnings("null")
public class noranklistCommand extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("noranklist"))
			return;
		event.deferReply().queue();
		String title = "No Rank Liste";

		Thread thread = new Thread(() -> {
			try {
				ArrayList<String> clanTags = DBManager.getAllClans();
				ArrayList<Player> matchedPlayers = new ArrayList<>();
				
				int totalClans = clanTags.size();

				for (int a = 0; a < clanTags.size(); a++) {
					String clantag = clanTags.get(a);
					Clan c = new Clan(clantag);
					ArrayList<Player> playerListClan = c.getPlayersDB();
					for (int i = 0; i < playerListClan.size(); i++) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Lade Spieler " + (i + 1) + " / " + playerListClan.size()
										+ " aus Clan " + (a + 1) + " / " + totalClans + " von Datenbank in den Cache...",
								MessageUtil.EmbedType.LOADING)).queue();
						
						Player p = playerListClan.get(i);
						
						switch (p.getRole()) {
							case ADMIN, LEADER, COLEADER -> {
								continue;
							}
							default -> {}
						}

						Integer rank = p.getPoLLeagueNumber();
						Integer trophies = p.getTrophies();
						
						if (rank != null && rank < 4 && trophies != null && trophies < 12500) {
							matchedPlayers.add(p);
						}
					}
				}

				// Sort by clan name then player name
				matchedPlayers.sort(Comparator.comparing((Player p) -> p.getClanDB().getNameDB()).thenComparing(Player::getNameDB));

				StringBuilder content = new StringBuilder();
				String currentClanName = "";
				for (Player p : matchedPlayers) {
					String clanName = p.getClanDB().getNameDB();
					if (!clanName.equals(currentClanName)) {
						if (!currentClanName.isEmpty()) content.append("\n");
						content.append("--- ").append(clanName).append(" ---\n");
						currentClanName = clanName;
					}
					String discordInfo = "Dc:";
					if (p.getUser() != null) {
						String discordID = p.getUser().getUserID();
						Member member = null;
						try {
							member = Bot.getJda().getGuildById(Bot.guild_id)
									.retrieveMemberById(discordID).submit().get();
						} catch (Exception e) {
							// Ignored
						}
						if (member != null) {
							String nick = member.getEffectiveName();
							String name = member.getUser().getAsTag().replace("#0000", "");
							discordInfo += " " + nick + " (" + name + ")";
						}
						discordInfo += " <@" + discordID + "> | ID: " + discordID;
					} else {
					    discordInfo += " Nicht verlinkt";
					}
					
					content.append(p.getNameDB()).append(" (").append(p.getTag()).append(") | ");
					content.append("Rank: ").append(p.getPoLLeagueNumber()).append(" | ");
					content.append("Trophies: ").append(p.getTrophies()).append("\n");
					content.append("   ").append(discordInfo).append("\n");
				}

				if (content.length() == 0) {
					content.append("Keine Spieler gefunden.");
				}

				ByteArrayInputStream inputStream = new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8));
				String description = "Hier die Liste der Spieler mit Rang < 4 und Trophäen < 12500. (Insgesamt: " + matchedPlayers.size() + " Spieler)";
				event.getHook().editOriginal(inputStream, "noranklist.txt")
						.setEmbeds(MessageUtil.buildEmbed(title, description, MessageUtil.EmbedType.INFO)).queue();
			} catch (Exception e) {
				System.err.println(e.getMessage());
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
			}
		});
		thread.start();
	}
}
