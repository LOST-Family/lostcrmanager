package commands.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import lostcrmanager.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class leaguetrophylist extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("leaguetrophylist"))
			return;
		event.deferReply().queue();
		String title = "League-Trophy Liste";

		OptionMapping timeOption = event.getOption("timestamp");

		if (timeOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Der Parameter Timestamp ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String time = timeOption.getAsString();

		long millis;

		try {
			millis = Long.parseLong(time);
		} catch (Exception ex) {
			if (time.equals("create")) {
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
				if (!b) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl mit diesem Parameter ausführen zu können.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
				saveNewList();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Eine neue League-Trophy Liste wird im Hintergrund erstellt.", MessageUtil.EmbedType.INFO))
						.queue();
				return;
			} else {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der angegebene Timestamp ist ungültig!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}
		String filename = "Liste_" + millis + ".txt";
		File folder = new File(getRunningJarDirectory(), "CRManager_ListFiles");

		File file = new File(folder, filename);
		if (file.exists()) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy 'um' HH:mm 'Uhr'");
			ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("Europe/Berlin"));
			String formattedDate = formatter.format(dateTime);
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Hier die Liste vom " + formattedDate + ".", MessageUtil.EmbedType.INFO)).addFile(file).queue();
			return;
		} else {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Zu dem angegebenen Timestamp wurde keine Liste gefunden!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		/*
		 * String desc = ""; ArrayList<String> allclantags = null;
		 * 
		 * switch (clantagoption) { case "all": boolean b = false; User userexecuted =
		 * new User(event.getUser().getId()); for (String clantag :
		 * DBManager.getAllClans()) { if (userexecuted.getClanRoles().get(clantag) ==
		 * Player.RoleType.ADMIN || userexecuted.getClanRoles().get(clantag) ==
		 * Player.RoleType.LEADER || userexecuted.getClanRoles().get(clantag) ==
		 * Player.RoleType.COLEADER) { b = true; break; } } if (!b) {
		 * event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
		 * "Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl mit diesem Parameterausführen zu können."
		 * , MessageUtil.EmbedType.ERROR)).queue(); return; } allclantags =
		 * DBManager.getAllClans(); desc =
		 * "Hier eine Liste aller Mitglieder von Lost Clans inklusive Warteliste sortiert nach LeagueNumber bzw. Trophies."
		 * ; break; default: allclantags = new ArrayList<>(); Clan c = new
		 * Clan(clantagoption); if (c.ExistsDB()) { allclantags.add(clantagoption); }
		 * else { event.getHook().editOriginalEmbeds( MessageUtil.buildEmbed(title,
		 * "Diese Auswahl funktioniert nicht.", MessageUtil.EmbedType.ERROR)) .queue();
		 * return; } desc = "Hier eine Liste aller Mitglieder des Clans " +
		 * c.getInfoStringDB() + " sortiert nach LeagueNumber bzw. Trophies."; break; }
		 * final ArrayList<String> clans = allclantags;
		 * 
		 * final String description = desc;
		 * 
		 * Thread thread = new Thread(() -> { ArrayList<Player> allplayers = new
		 * ArrayList<>(); String content = ""; for (int a = 0; a < clans.size(); a++) {
		 * String clantag = clans.get(a); Clan c = new Clan(clantag); ArrayList<Player>
		 * playerlistclan = c.getPlayersDB(); for (int i = 0; i < playerlistclan.size();
		 * i++) { event.getHook() .editOriginalEmbeds(MessageUtil.buildEmbed(title,
		 * "Lade Spieler " + (i + 1) + " / " + playerlistclan.size() + (clans.size() > 1
		 * ? " aus Clan " + (a + 1) + " / " + clans.size() : "") +
		 * " von Datenbank in den Cache...", MessageUtil.EmbedType.LOADING)) .queue();
		 * allplayers.add(playerlistclan.get(i)); } }
		 * 
		 * if (clantagoption.equals("all")) {
		 * 
		 * allplayers = sortPlayers(allplayers, event.getHook());
		 * 
		 * HashMap<String, Integer> clantagtomembercount = new HashMap<>();
		 * 
		 * HashMap<String, ArrayList<String>> clantagtoadminstrings = new HashMap<>();
		 * HashMap<String, ArrayList<String>> clantagtoleaderstrings = new HashMap<>();
		 * HashMap<String, ArrayList<String>> clantagtocoleaderstrings = new
		 * HashMap<>();
		 * 
		 * HashMap<String, ArrayList<String>> clantagtomarkedstrings = new HashMap<>();
		 * 
		 * ArrayList<String> playerstringssorted = new ArrayList<>();
		 * 
		 * for (int i = 0; i < allplayers.size(); i++) { event.getHook()
		 * .editOriginalEmbeds(MessageUtil.buildEmbed(title, "Lade Spieler " + (i + 1) +
		 * " / " + allplayers.size() + " in die Listen...",
		 * MessageUtil.EmbedType.LOADING)) .queue(); Player p = allplayers.get(i);
		 * String clantag = p.getClanDB().getTag();
		 * 
		 * if (p.getRole() == Player.RoleType.ADMIN) { ArrayList<String> adminstrings =
		 * clantagtoadminstrings.getOrDefault(clantag, new ArrayList<>());
		 * adminstrings.add(formatPlayerLine(p)); clantagtoadminstrings.put(clantag,
		 * adminstrings); clantagtomembercount.put(clantag,
		 * clantagtomembercount.getOrDefault(clantag, 0) + 1); } else if (p.getRole() ==
		 * Player.RoleType.LEADER) { ArrayList<String> leaderstrings =
		 * clantagtoleaderstrings.getOrDefault(clantag, new ArrayList<>());
		 * leaderstrings.add(formatPlayerLine(p)); clantagtoleaderstrings.put(clantag,
		 * leaderstrings); clantagtomembercount.put(clantag,
		 * clantagtomembercount.getOrDefault(clantag, 0) + 1); } else if (p.getRole() ==
		 * Player.RoleType.COLEADER) { ArrayList<String> coleaderstrings =
		 * clantagtocoleaderstrings .getOrDefault(p.getClanDB().getTag(), new
		 * ArrayList<>()); coleaderstrings.add(formatPlayerLine(p));
		 * clantagtocoleaderstrings.put(clantag, coleaderstrings);
		 * clantagtomembercount.put(clantag, clantagtomembercount.getOrDefault(clantag,
		 * 0) + 1); } else if (p.isMarked()) { ArrayList<String> markedstrings =
		 * clantagtomarkedstrings.getOrDefault(clantag, new ArrayList<>());
		 * markedstrings.add(formatPlayerLine(p)); clantagtomarkedstrings.put(clantag,
		 * markedstrings); clantagtomembercount.put(clantag,
		 * clantagtomembercount.getOrDefault(clantag, 0) + 1); } else {
		 * playerstringssorted.add(formatPlayerLine(p)); } }
		 * 
		 * int counter = 1;
		 * 
		 * for (String tags : clans) { Clan c = new Clan(tags);
		 * event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
		 * "Konstruire Clan " + c.getInfoStringDB() + " in der Datei...",
		 * MessageUtil.EmbedType.LOADING)) .queue(); content += c.getInfoStringDB() +
		 * "\n\n";
		 * 
		 * boolean coleadersexist = false;
		 * 
		 * for (String s : clantagtoadminstrings.getOrDefault(tags, new ArrayList<>()))
		 * { if (!coleadersexist) coleadersexist = true; content += "#" + counter + " ";
		 * counter++; content += s; }
		 * 
		 * for (String s : clantagtoleaderstrings.getOrDefault(tags, new ArrayList<>()))
		 * { if (!coleadersexist) coleadersexist = true; content += "#" + counter + " ";
		 * counter++; content += s; }
		 * 
		 * for (String s : clantagtocoleaderstrings.getOrDefault(tags, new
		 * ArrayList<>())) { if (!coleadersexist) coleadersexist = true; content += "#"
		 * + counter + " "; counter++; content += s; }
		 * 
		 * if (coleadersexist) { content += "----------------------------\n\n"; }
		 * 
		 * boolean markedsexist = false;
		 * 
		 * for (String s : clantagtomarkedstrings.getOrDefault(tags, new ArrayList<>()))
		 * { if (!markedsexist) markedsexist = true; content += "#" + counter + " ";
		 * counter++; content += s; }
		 * 
		 * if (markedsexist) { content += "----------------------------\n\n"; }
		 * 
		 * int fillup;
		 * 
		 * if (!tags.equalsIgnoreCase("warteliste")) { fillup = 50 -
		 * clantagtomembercount.getOrDefault(tags, 0); } else { fillup =
		 * playerstringssorted.size() - 1; }
		 * 
		 * for (int j = 0; j < fillup && playerstringssorted.size() > 0; j++) { content
		 * += "#" + counter + " "; counter++; content += playerstringssorted.get(0);
		 * playerstringssorted.remove(0); }
		 * 
		 * content += "-----------------------------------------------------------\n\n";
		 * }
		 * 
		 * ByteArrayInputStream inputStream = new
		 * ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		 * event.getHook().editOriginal(inputStream, "Liste.txt")
		 * .setEmbeds(MessageUtil.buildEmbed(title, description,
		 * MessageUtil.EmbedType.INFO)).queue(); } else { HashMap<String, String>
		 * clantocontentstring = new HashMap<>(); HashMap<String, Integer> clantocounter
		 * = new HashMap<>(); event.getHook() .editOriginalEmbeds(
		 * MessageUtil.buildEmbed(title, "Lade Daten der API...",
		 * MessageUtil.EmbedType.LOADING)) .queue(); allplayers.sort(Comparator
		 * .comparingInt((Player p) -> p.getRole() == Player.RoleType.ELDER ||
		 * p.getRole() == Player.RoleType.MEMBER ? Player.RoleType.ELDER.ordinal() :
		 * p.getRole().ordinal())
		 * .thenComparing(Comparator.comparing(Player::isMarked).reversed()) // true
		 * zuerst .thenComparing(Comparator.comparingInt((Player p) ->
		 * p.getPoLLeagueNumber() != null ? (p.getPoLLeagueNumber() != 1 ?
		 * p.getPoLLeagueNumber() : Integer.MIN_VALUE) : Integer.MIN_VALUE).reversed())
		 * .thenComparing(Comparator.comparingInt((Player p) -> p.getPoLTrophies() !=
		 * null ? (p.getPoLTrophies() != 0 ? p.getPoLTrophies() : Integer.MIN_VALUE) :
		 * Integer.MIN_VALUE).reversed()) .thenComparing(Comparator.comparingInt((Player
		 * p) -> p.getSTRTrophies() != null ? (p.getSTRTrophies() != 10000 ?
		 * p.getSTRTrophies() : Integer.MIN_VALUE) : Integer.MIN_VALUE).reversed())
		 * .thenComparing(Comparator.comparingInt(Player::getTrophies).reversed()));
		 * 
		 * for (int i = 1; i <= allplayers.size(); i++) { event.getHook()
		 * .editOriginalEmbeds(MessageUtil.buildEmbed(title, "Lade Spieler " + i + " / "
		 * + allplayers.size() + " in die Datei...", MessageUtil.EmbedType.LOADING))
		 * .queue(); Player p = allplayers.get(i - 1); String clanplayercontent = "";
		 * String marked = p.isMarked() ? " (✗)" : ""; String role = p.getRole() ==
		 * Player.RoleType.ADMIN ? " [Admin]" : p.getRole() == Player.RoleType.LEADER ?
		 * " [Leader]" : p.getRole() == Player.RoleType.COLEADER ? " [Co-Leader]" :
		 * p.getRole() == Player.RoleType.ELDER ? " [Elder]" : p.getRole() ==
		 * Player.RoleType.MEMBER ? " [Member]" : ""; if (p.getTrophies() == 10000) { if
		 * (p.getPoLLeagueNumber() == 7) { clanplayercontent += p.getInfoString() + role
		 * + marked + ":" + System.lineSeparator() + "  LeagueNumber: " +
		 * p.getPoLLeagueNumber() + System.lineSeparator() +
		 * "  Aktuelle PathOfLegendSeason-Trophäen: " + p.getPoLTrophies() +
		 * System.lineSeparator() + "  Aktuelle Seasonal-Trophy-Road-Trophäen: " +
		 * p.getSTRTrophies() + System.lineSeparator() + System.lineSeparator(); } else
		 * { clanplayercontent += p.getInfoString() + role + marked + ":" +
		 * System.lineSeparator() + "  LeagueNumber: " + p.getPoLLeagueNumber() +
		 * System.lineSeparator() + "  Aktuelle Seasonal-Trophy-Road-Trophäen: " +
		 * p.getSTRTrophies() + System.lineSeparator() + System.lineSeparator(); } }
		 * else { clanplayercontent += p.getInfoString() + role + marked + ":" +
		 * System.lineSeparator() + "  Aktuelle Trophäen: " + p.getTrophies() +
		 * System.lineSeparator() + System.lineSeparator(); } String clancontent =
		 * clantocontentstring.getOrDefault(p.getClanDB().getTag(), ""); int counter =
		 * clantocounter.getOrDefault(p.getClanDB().getTag(), 1); clancontent += "#" +
		 * counter + " | " + clanplayercontent; counter++;
		 * clantocounter.put(p.getClanDB().getTag(), counter);
		 * clantocontentstring.put(p.getClanDB().getTag(), clancontent); }
		 * 
		 * for (String key : clans) { if (clantocontentstring.containsKey(key)) { Clan c
		 * = new Clan(key); content += "### " + c.getInfoStringDB() + ":\n"; content +=
		 * "\n"; content += clantocontentstring.get(key); content += "\n"; content +=
		 * "---------------------\n"; content += "\n"; } } ByteArrayInputStream
		 * inputStream = new
		 * ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		 * event.getHook().editOriginal(inputStream, "Liste.txt")
		 * .setEmbeds(MessageUtil.buildEmbed(title, description,
		 * MessageUtil.EmbedType.INFO)).queue(); } }); thread.start();
		 */

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("leaguetrophylist"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue().toLowerCase(); // Groß-/Kleinschreibung ignorieren

		if (focused.equals("timestamp")) {
			List<Command.Choice> choices = new ArrayList<>();

			boolean hasRights = false;
			User userexecuted = new User(event.getUser().getId());
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userexecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasRights = true;
					break;
				}
			}

			// Datumsbasierte Choices sammeln
			File folder = new File(getRunningJarDirectory(), "CRManager_ListFiles");
			Pattern pattern = Pattern.compile("Liste_(\\d+)\\.txt");
			Map<String, Long> zeitpunktMap = new HashMap<>();

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy 'um' HH:mm 'Uhr'")
					.withZone(ZoneId.of("Europe/Berlin"));

			if (folder.exists() && folder.isDirectory()) {
				File[] files = folder.listFiles();
				if (files != null) {
					for (File file : files) {
						Matcher matcher = pattern.matcher(file.getName());
						if (matcher.matches()) {
							long millis = Long.parseLong(matcher.group(1));
							String formattedDate = formatter.format(
									ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("Europe/Berlin")));
							if (formattedDate.toLowerCase().contains(input) || String.valueOf(millis).contains(input)) {
								zeitpunktMap.put(formattedDate, millis);
							}
						}
					}
				}
			}

			// Nach Datum sortieren, limitieren und in Choices umwandeln
			List<Command.Choice> dateChoices = zeitpunktMap.entrySet().stream()
					.sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(24)
					.map(entry -> new Command.Choice(entry.getKey(), entry.getValue())).collect(Collectors.toList());

			// Rechte-Option "Erstelle neue Liste" oben hinzufügen, falls relevant
			if (hasRights) {
				choices.add(new Command.Choice("Erstelle neue Liste", "create"));
			}

			choices.addAll(dateChoices);

			// Antworte mit der kombinierten Liste
			event.replyChoices(choices).queue();
		}
	}

	@SuppressWarnings("null")
	private static String formatPlayerLine(Player p) {
		boolean isMarked = p.isMarked();
		// Status bestimmen
		String status;
		if (p.getClanDB() == null) {
			status = "[Warteschlange]";
		} else {
			String role = "";
			if (p.getRole() == Player.RoleType.ADMIN) {
				role = "Admin";
			} else if (p.getRole() == Player.RoleType.LEADER) {
				role = "Anführer";
			} else if (p.getRole() == Player.RoleType.COLEADER) {
				role = "Vize-Anführer";
			} else if (p.getRole() == Player.RoleType.ELDER) {
				role = "Ältester";
			} else if (p.getRole() == Player.RoleType.MEMBER) {
				role = "Mitglied";
			}
			status = "[" + role + " " + p.getClanDB().getNameDB() + "]";
		}
		String discordInfo = "Dc:";
		if (p.getUser() != null) {
			String discordID = p.getUser().getUserID();
			Member member = null;
			try {
				member = Bot.getJda().getGuildById(Bot.guild_id).retrieveMemberById(discordID).submit().get();
			} catch (ExecutionException e) {
				if (e.getCause() instanceof net.dv8tion.jda.api.exceptions.ErrorResponseException) {
					net.dv8tion.jda.api.exceptions.ErrorResponseException ex = (net.dv8tion.jda.api.exceptions.ErrorResponseException) e
							.getCause();
					if (ex.getErrorCode() != 10007 && ex.getErrorCode() != 10013) {
						e.printStackTrace();
					}
				} else {
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				// Member nicht gefunden
			}
			if (member != null) {
				String nick = member.getEffectiveName();
				String name = member.getUser().getAsTag().replace("#0000", "");
				discordInfo += " " + nick + " (" + name + ")";
			}
			discordInfo += " <@" + discordID + ">";
		}
		String noteInfo = "";
		if (isMarked) {
			status += " [MARKIERT]";
			String note = p.getNote();
			if (note != null && !note.trim().isEmpty()) {
				noteInfo = " Notiz: " + note + "\n";
			}
		}

		// Trophäen- und Liga-Infos
		int poLTrophies = p.getPoLTrophies() != null ? p.getPoLTrophies() : 0;
		int strTrophies = p.getSTRTrophies() != null ? p.getSTRTrophies() : 0;
		int trophies = p.getTrophies() != null ? p.getTrophies() : 0;
		int leagueNumber = p.getPoLLeagueNumber() != null ? p.getPoLLeagueNumber() : 0;

		// Wenn trophies == 10.000: STR-Trophies ausgeben
		int displayTrophies = trophies == 10000 ? strTrophies : trophies;

		// Ausgabeformat wie im Beispiel
		return String.format(
				"%s (%s) %s %s\n%s LeagueNumber: %d\n Aktuelle PathOfLegendSeason-Trophäen: %d\n Aktuelle Seasonal-Trophy-Road-Trophäen: %d\n\n",
				p.getNameDB(), p.getTag(), status, discordInfo, noteInfo, leagueNumber, poLTrophies, displayTrophies);
	}

	public static ArrayList<Player> sortPlayers(ArrayList<Player> players) {
		List<Player> sortedList = players.stream().sorted(new Comparator<Player>() {

			@Override
			public int compare(Player p1, Player p2) {
				int league1 = p1.getPoLLeagueNumber() != null ? p1.getPoLLeagueNumber() : 0;
				int league2 = p2.getPoLLeagueNumber() != null ? p2.getPoLLeagueNumber() : 0;

				// Primär: LeagueNumber absteigend
				if (league1 != league2) {
					return Integer.compare(league2, league1);
				}

				// In Liga 7: nur nach Medaillen sortieren
				if (league1 == 7 && league2 == 7) {
					int medals1 = p1.getPoLTrophies() != null ? p1.getPoLTrophies() : 0;
					int medals2 = p2.getPoLTrophies() != null ? p2.getPoLTrophies() : 0;
					return Integer.compare(medals2, medals1);
				}

				// Sonst: Trophäen (bei 10.000 STR-Trophäen)
				int trophies1 = (p1.getTrophies() != null && p1.getTrophies() == 10000)
						? (p1.getSTRTrophies() != null ? p1.getSTRTrophies() : 0)
						: (p1.getTrophies() != null ? p1.getTrophies() : 0);
				int trophies2 = (p2.getTrophies() != null && p2.getTrophies() == 10000)
						? (p2.getSTRTrophies() != null ? p2.getSTRTrophies() : 0)
						: (p2.getTrophies() != null ? p2.getTrophies() : 0);

				return Integer.compare(trophies2, trophies1);
			}
		}).collect(Collectors.toList());

		return new ArrayList<>(sortedList);
	}

	public static void saveNewList() {

		final ArrayList<String> clans = DBManager.getAllClans();

		Thread thread = new Thread(() -> {
			ArrayList<Player> allplayers = new ArrayList<>();
			String content = "";
			for (int a = 0; a < clans.size(); a++) {
				String clantag = clans.get(a);
				Clan c = new Clan(clantag);
				ArrayList<Player> playerlistclan = c.getPlayersDB();
				for (int i = 0; i < playerlistclan.size(); i++) {
					allplayers.add(playerlistclan.get(i));
				}
			}

			allplayers = sortPlayers(allplayers);

			HashMap<String, Integer> clantagtomembercount = new HashMap<>();

			HashMap<String, ArrayList<String>> clantagtoadminstrings = new HashMap<>();
			HashMap<String, ArrayList<String>> clantagtoleaderstrings = new HashMap<>();
			HashMap<String, ArrayList<String>> clantagtocoleaderstrings = new HashMap<>();

			ArrayList<String> playerstringssorted = new ArrayList<>();

			for (int i = 0; i < allplayers.size(); i++) {
				Player p = allplayers.get(i);
				if (p.isHiddenColeader()) {
					continue;
				}
				// Skip players without a clan to avoid NPE when calling getClanDB().getTag()
				if (p.getClanDB() == null) {
					continue;
				}
				String clantag = p.getClanDB().getTag();

				if (p.getRole() == Player.RoleType.ADMIN) {
					ArrayList<String> adminstrings = clantagtoadminstrings.getOrDefault(clantag, new ArrayList<>());
					adminstrings.add(formatPlayerLine(p));
					clantagtoadminstrings.put(clantag, adminstrings);
					clantagtomembercount.put(clantag, clantagtomembercount.getOrDefault(clantag, 0) + 1);
				} else if (p.getRole() == Player.RoleType.LEADER) {
					ArrayList<String> leaderstrings = clantagtoleaderstrings.getOrDefault(clantag, new ArrayList<>());
					leaderstrings.add(formatPlayerLine(p));
					clantagtoleaderstrings.put(clantag, leaderstrings);
					clantagtomembercount.put(clantag, clantagtomembercount.getOrDefault(clantag, 0) + 1);
				} else if (p.getRole() == Player.RoleType.COLEADER) {
					ArrayList<String> coleaderstrings = clantagtocoleaderstrings.getOrDefault(clantag,
							new ArrayList<>());
					coleaderstrings.add(formatPlayerLine(p));
					clantagtocoleaderstrings.put(clantag, coleaderstrings);
					clantagtomembercount.put(clantag, clantagtomembercount.getOrDefault(clantag, 0) + 1);
				} else {
					playerstringssorted.add(formatPlayerLine(p));
				}
			}

			int counter = 1;

			for (String tags : clans) {
				Clan c = new Clan(tags);
				content += c.getInfoStringDB() + "\n\n";

				boolean coleadersexist = false;

				for (String s : clantagtoadminstrings.getOrDefault(tags, new ArrayList<>())) {
					if (!coleadersexist)
						coleadersexist = true;
					content += "#" + counter + " ";
					counter++;
					content += s;
				}

				for (String s : clantagtoleaderstrings.getOrDefault(tags, new ArrayList<>())) {
					if (!coleadersexist)
						coleadersexist = true;
					content += "#" + counter + " ";
					counter++;
					content += s;
				}

				for (String s : clantagtocoleaderstrings.getOrDefault(tags, new ArrayList<>())) {
					if (!coleadersexist)
						coleadersexist = true;
					content += "#" + counter + " ";
					counter++;
					content += s;
				}

				if (coleadersexist) {
					content += "----------------------------\n\n";
				}

				int fillup;

				if (!tags.equalsIgnoreCase("warteliste")) {
					fillup = 50 - clantagtomembercount.getOrDefault(tags, 0);
				} else {
					fillup = playerstringssorted.size() - 1;
				}

				for (int j = 0; j < fillup && playerstringssorted.size() > 0; j++) {
					content += "#" + counter + " ";
					counter++;
					content += playerstringssorted.get(0);
					playerstringssorted.remove(0);
				}

				content += "-----------------------------------------------------------\n\n";
			}

			ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
			File folder = new File(getRunningJarDirectory(), "CRManager_ListFiles");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			long millis = java.time.ZonedDateTime.now(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();

			File file = new File(folder, "Liste_" + millis + ".txt");

			// FileOutputStream zum Schreiben in die Datei
			try (FileOutputStream fos = new FileOutputStream(file)) {
				byte[] buffer = new byte[1024];
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					fos.write(buffer, 0, length);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Neue League-Trophy Liste gespeichert: " + file.getAbsolutePath());
		});
		thread.start();
	}

	public static File getRunningJarDirectory() {
		try {
			// Der Ort, von dem die Klasse oder JAR geladen wurde
			File jarFile = new File(Bot.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			File dir;
			if (jarFile.isFile()) {
				// Wenn die Anwendung als JAR gestartet wurde, ist jarFile eine Datei
				dir = jarFile.getParentFile();
			} else {
				// Wenn dies aus IDE oder als .class Datei läuft, ist es ein Verzeichnis
				dir = jarFile;
			}
			return dir;
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return null; // oder Fallback
		}
	}
}
