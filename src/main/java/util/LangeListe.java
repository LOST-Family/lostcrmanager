package util;

/**
 * Eine Liste, die laenger sein kann, als Discord in einer Embed-Beschreibung
 * erlaubt (4096 Zeichen). Statt mitten in einer Zeile abzuschneiden - bei nach
 * Wert sortierten Listen fehlt dann ausgerechnet das untere Ende -, endet der
 * Embed-Teil an einer Zeilengrenze mit einem Hinweis, und die ganze Liste geht
 * als Textdatei mit.
 */
public final class LangeListe {

	/** Sicherheitsabstand zu den 4096 Zeichen von Discord. */
	public static final int GRENZE = 4000;

	public final String embedText;
	/** null, wenn alles in den Embed passt. */
	public final String dateiText;

	private LangeListe(String embedText, String dateiText) {
		this.embedText = embedText;
		this.dateiText = dateiText;
	}

	public boolean istGekuerzt() {
		return dateiText != null;
	}

	public static LangeListe aus(String voll) {
		return aus(voll, GRENZE);
	}

	public static LangeListe aus(String voll, int grenze) {
		if (voll == null) {
			return new LangeListe("", null);
		}
		if (voll.length() <= grenze) {
			return new LangeListe(voll, null);
		}
		String hinweisMuster = "\n… und %d weitere Zeilen – die vollständige Liste hängt als Datei an.";
		// Platz fuer den Hinweis (Zahl grosszuegig gerechnet) freihalten.
		int platz = grenze - hinweisMuster.length() - 8;
		int schnitt = platz > 0 ? voll.lastIndexOf('\n', platz) : -1;
		if (schnitt <= 0) {
			// Keine Zeilengrenze im erlaubten Bereich: hart kuerzen, Datei traegt alles.
			String kopf = voll.substring(0, Math.max(0, Math.min(voll.length(), grenze - 1))) + "…";
			return new LangeListe(kopf, alsKlartext(voll));
		}
		String rest = voll.substring(schnitt + 1);
		int weitere = 0;
		for (String zeile : rest.split("\n")) {
			if (!zeile.isBlank()) {
				weitere++;
			}
		}
		String kopf = voll.substring(0, schnitt) + String.format(hinweisMuster, weitere);
		return new LangeListe(kopf, alsKlartext(voll));
	}

	/** Nimmt die Discord-Auszeichnung heraus, die in einer Textdatei nur stoert. */
	static String alsKlartext(String s) {
		return s.replace("**", "").replace("\\", "");
	}
}
