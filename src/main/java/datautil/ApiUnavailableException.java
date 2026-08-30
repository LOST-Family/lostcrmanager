package datautil;

/**
 * Wird geworfen, wenn ein Aufruf der Clash-Royale-API fehlgeschlagen ist
 * (Timeout, Netzwerkfehler oder HTTP-Status != 200). Vorher lieferte
 * {@link APIUtil} in diesen Faellen null zurueck, was bei den Aufrufern zu
 * einer NullPointerException im JSON-Parser fuehrte.
 */
public class ApiUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ApiUnavailableException(String message) {
		super(message);
	}
}
