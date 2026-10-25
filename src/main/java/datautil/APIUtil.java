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

	/**
	 * Basisadresse der Clash Royale-API.
	 *
	 * Konfigurierbar, weil Supercell-Keys an feste IP-Adressen gebunden sind:
	 * an einem Anschluss mit wechselnder IP funktioniert ein solcher Key nicht.
	 * Mit CR_API_BASE_URL=https://proxy.royaleapi.dev/v1 laeuft der
	 * Verkehr ueber den RoyaleAPI-Proxy, dessen feste Adresse (45.79.218.79)
	 * beim Erstellen des Keys hinterlegt wird. Ohne gesetzte Variable bleibt
	 * alles wie bisher.
	 */
	public static final String BASIS_URL = System.getenv()
			.getOrDefault("CR_API_BASE_URL", "https://api.clashroyale.com/v1");

	/**
	 * Umschalter fuer Basisadresse und Key, ohne Neustart.
	 *
	 * Steht in API_UMSCHALTER der Pfad einer Datei, wird sie gelesen (hoechstens
	 * alle zehn Sekunden auf eine Aenderung geprueft). Zeilen "url=..." und
	 * "key=..." ueberschreiben die Werte aus der Umgebung; fehlt die Datei oder
	 * eine Zeile, gilt die Umgebung. Anlass: am 25.10.2026 fiel der RoyaleAPI-Proxy
	 * aus, und der Wechsel auf einen direkten Supercell-Key haette sonst einen
	 * Neustart gebraucht. Umgeschaltet wird mit ~/lost/api-umschalten.sh.
	 */
	private static final String UMSCHALTER = System.getenv("API_UMSCHALTER");
	private static volatile String[] umschalterWerte = { null, null };
	private static volatile long umschalterStand = Long.MIN_VALUE;
	private static volatile long umschalterGeprueft = 0;

	private static String[] umschalter() {
		if (UMSCHALTER == null || UMSCHALTER.isBlank()) return umschalterWerte;
		long jetzt = System.currentTimeMillis();
		if (jetzt - umschalterGeprueft < 10_000) return umschalterWerte;
		synchronized (APIUtil.class) {
			if (jetzt - umschalterGeprueft < 10_000) return umschalterWerte;
			umschalterGeprueft = jetzt;
			java.nio.file.Path datei = java.nio.file.Path.of(UMSCHALTER);
			try {
				long stand = java.nio.file.Files.exists(datei)
						? java.nio.file.Files.getLastModifiedTime(datei).toMillis() : -1;
				if (stand == umschalterStand) return umschalterWerte;
				String url = null, key = null;
				if (stand >= 0) {
					for (String zeile : java.nio.file.Files.readAllLines(datei)) {
						zeile = zeile.strip();
						if (zeile.startsWith("url=") && zeile.length() > 4) url = zeile.substring(4).strip();
						if (zeile.startsWith("key=") && zeile.length() > 4) key = zeile.substring(4).strip();
					}
				}
				umschalterWerte = new String[] { url, key };
				umschalterStand = stand;
				System.out.println("API-Umschalter: " + (url != null ? url : "Adresse aus Umgebung")
						+ ", " + (key != null ? "Key aus Datei" : "Key aus Umgebung"));
			} catch (Exception e) {
				System.err.println("API-Umschalter nicht lesbar, bleibe beim letzten Stand: " + e.getMessage());
			}
		}
		return umschalterWerte;
	}

	/** Aktuelle Basisadresse der API. */
	public static String basisUrl() {
		String url = umschalter()[0];
		return url != null ? url : BASIS_URL;
	}

	/** Aktueller API-Key. */
	public static String apiKey() {
		String key = umschalter()[1];
		return key != null ? key : Bot.api_key;
	}

	// Gemeinsamer Client mit Timeouts, damit hängende CR-API-Aufrufe keine
	// Threads (z.B. die REST-API-Worker) dauerhaft blockieren können
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	public static ApiResponse raw(String method, String path, Map<String, String> query, String jsonBody) {
		StringBuilder urlBuilder = new StringBuilder(basisUrl());
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
			.header("Authorization", "Bearer " + apiKey())
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

		String url = basisUrl() + "/clans/" + encodedTag;

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + apiKey()).header("Accept", "application/json").GET().build();

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

		String url = basisUrl() + "/players/" + encodedTag;

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + apiKey()).header("Accept", "application/json").GET().build();

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

		String url = basisUrl() + "/clans/" + encodedTag + "/currentriverrace";

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + apiKey()).header("Accept", "application/json").GET().build();

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
