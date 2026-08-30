package datautil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.json.JSONObject;

import lostcrmanager.Bot;

public class APIUtil {

	// Gemeinsamer Client mit Timeouts, damit hängende CR-API-Aufrufe keine
	// Threads (z.B. die REST-API-Worker) dauerhaft blockieren können
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	public static ApiResponse raw(String method, String path, Map<String, String> query, String jsonBody) {
		StringBuilder urlBuilder = new StringBuilder("https://api.clashroyale.com/v1");
		urlBuilder.append(path);
		if (!query.isEmpty()) {
			urlBuilder.append("?");
			boolean first = true;
			for (Map.Entry<String, String> entry : query.entrySet()) {
				if (!first) urlBuilder.append("&");
				urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
				urlBuilder.append("=");
				urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
				first = false;
			}
		}

		HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
			.uri(URI.create(urlBuilder.toString()))
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", "Bearer " + Bot.api_key)
			.header("Accept", "application/json");

		if ("POST".equals(method) && jsonBody != null) {
			reqBuilder.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
		} else {
			reqBuilder.GET();
		}

		try {
			HttpResponse<String> response = CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			return new ApiResponse(response.statusCode(), response.body());
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			return new ApiResponse(-1, e.getMessage());
		}
	}


	/**
	 * Liefert die Clan-Daten als JSONObject. Anders als {@link #getClanJson(String)}
	 * wird bei einem fehlgeschlagenen API-Aufruf eine ApiUnavailableException
	 * geworfen statt null zurueckgegeben.
	 */
	public static JSONObject getClanJsonObject(String clanTag) {
		return toJsonObject(getClanJson(clanTag), "Clan", clanTag);
	}

	/**
	 * Liefert die Spieler-Daten als JSONObject. Anders als
	 * {@link #getPlayerJson(String)} wird bei einem fehlgeschlagenen API-Aufruf
	 * eine ApiUnavailableException geworfen statt null zurueckgegeben.
	 */
	public static JSONObject getPlayerJsonObject(String playerTag) {
		return toJsonObject(getPlayerJson(playerTag), "Spieler", playerTag);
	}

	private static JSONObject toJsonObject(String json, String type, String tag) {
		if (json == null) {
			throw new ApiUnavailableException(
					type + "-Daten fuer " + tag + " konnten nicht von der Clash-Royale-API abgerufen werden.");
		}
		return new JSONObject(json);
	}

	public static String getClanJson(String clanTag) {
		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(clanTag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashroyale.com/v1/clans/" + encodedTag;

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		HttpResponse<String> response;
		try {
			response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			return null;
		}

		if (response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			return responseBody;
		} else {
			System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
			System.err.println("Antwort: " + response.body());
			return null;
		}
	}

	public static String getPlayerJson(String playerTag) {
		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(playerTag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashroyale.com/v1/players/" + encodedTag;

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		HttpResponse<String> response;
		try {
			response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			return null;
		}

		if (response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			return responseBody;
		} else {
			System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
			System.err.println("Antwort: " + response.body());
			return null;
		}
	}

	public static String getCurrentRiverRaceJson(String clanTag) {
		// URL-kodieren des Clan-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(clanTag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashroyale.com/v1/clans/" + encodedTag + "/currentriverrace";

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		HttpResponse<String> response;
		try {
			response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			return null;
		}

		if (response.statusCode() == 200) {
			String responseBody = response.body();
			return responseBody;
		} else {
			System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
			System.err.println("Antwort: " + response.body());
			return null;
		}
	}

}
