package datautil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class ApiRegistry {

    private static Endpoint.Param pp(String name) {
        return new Endpoint.Param(name, name, OptionType.STRING, true, true, List.of());
    }

    private static Endpoint.Param pq(String name, OptionType type) {
        return new Endpoint.Param(name, name, type, false, false, List.of());
    }

    private static Endpoint.Param rq(String name, OptionType type) {
        return new Endpoint.Param(name, name, type, true, false, List.of());
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
        // clans
        new Endpoint("clans", "search", "Search clans", "GET", "/clans", List.of(
            pq("name", OptionType.STRING), pq("location_id", OptionType.STRING),
            pq("min_members", OptionType.INTEGER), pq("max_members", OptionType.INTEGER),
            pq("min_score", OptionType.INTEGER), pq("limit", OptionType.INTEGER),
            pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "get", "Get clan by tag", "GET", "/clans/{tag}", List.of(pp("tag"))),
        new Endpoint("clans", "members", "List clan members", "GET", "/clans/{tag}/members", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "warlog", "Get clan war log", "GET", "/clans/{tag}/warlog", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "currentwar", "Get current war", "GET", "/clans/{tag}/currentwar", List.of(pp("tag"))),
        new Endpoint("clans", "riverracelog", "Get river race log", "GET", "/clans/{tag}/riverracelog", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "currentriverrace", "Get current river race", "GET", "/clans/{tag}/currentriverrace", List.of(pp("tag"))),
        // players
        new Endpoint("players", "get", "Get player by tag", "GET", "/players/{tag}", List.of(pp("tag"))),
        new Endpoint("players", "battlelog", "Get player battle log", "GET", "/players/{tag}/battlelog", List.of(pp("tag"))),
        new Endpoint("players", "upcomingchests", "Get upcoming chests", "GET", "/players/{tag}/upcomingchests", List.of(pp("tag"))),
        new Endpoint("players", "verifytoken", "Verify player token", "POST", "/players/{tag}/verifytoken", List.of(
            pp("tag"), rq("token", OptionType.STRING)
        )),
        // cards
        new Endpoint("cards", "list", "List all cards", "GET", "/cards", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // tournaments
        new Endpoint("tournaments", "search", "Search tournaments", "GET", "/tournaments", List.of(
            pq("name", OptionType.STRING), pq("limit", OptionType.INTEGER),
            pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("tournaments", "get", "Get tournament by tag", "GET", "/tournaments/{tag}", List.of(pp("tag"))),
        // globaltournaments
        new Endpoint("globaltournaments", "list", "List global tournaments", "GET", "/globaltournaments", List.of()),
        // challenges
        new Endpoint("challenges", "list", "List challenges", "GET", "/challenges", List.of()),
        // locations
        new Endpoint("locations", "list", "List locations", "GET", "/locations", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "get", "Get location", "GET", "/locations/{location_id}", List.of(pp("location_id"))),
        new Endpoint("locations", "rankings-clans", "Clan rankings", "GET", "/locations/{location_id}/rankings/clans", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-players", "Player rankings", "GET", "/locations/{location_id}/rankings/players", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-clanwars", "Clan wars rankings", "GET", "/locations/{location_id}/rankings/clanwars", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "pathoflegend-players", "Path of Legend rankings", "GET", "/locations/{location_id}/pathoflegend/players", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // leaderboards
        new Endpoint("leaderboards", "list", "List leaderboards", "GET", "/leaderboards", List.of()),
        new Endpoint("leaderboards", "get", "Get leaderboard", "GET", "/leaderboards/{leaderboard_id}", List.of(
            pp("leaderboard_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        ))
    );

    public static Endpoint find(String group, String name) {
        for (Endpoint e : ENDPOINTS) {
            if (e.group().equals(group) && e.name().equals(name)) return e;
        }
        return null;
    }

    @SuppressWarnings("null")
    public static SlashCommandData buildSlashCommand() {
        SlashCommandData cmd = Commands.slash("api", "Clash Royale API");

        Map<String, List<Endpoint>> byGroup = new LinkedHashMap<>();
        for (Endpoint e : ENDPOINTS) {
            byGroup.computeIfAbsent(e.group(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Endpoint>> entry : byGroup.entrySet()) {
            SubcommandGroupData group = new SubcommandGroupData(entry.getKey(), entry.getKey() + " endpoints");
            for (Endpoint endpoint : entry.getValue()) {
                SubcommandData sub = new SubcommandData(endpoint.name(), endpoint.description());
                for (Endpoint.Param param : endpoint.params()) {
                    OptionData opt = new OptionData(param.type(), param.name(), param.description(), param.required());
                    if (!param.choices().isEmpty()) {
                        opt.addChoices(param.choices());
                    }
                    sub.addOptions(opt);
                }
                group.addSubcommands(sub);
            }
            cmd.addSubcommandGroups(group);
        }

        return cmd;
    }
}
