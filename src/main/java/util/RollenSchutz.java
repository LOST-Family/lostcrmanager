package util;

import datawrapper.Player;

/**
 * Schutz der bisherigen Rolle beim Bearbeiten eines Mitglieds: Anführer darf nur
 * ein Admin umstufen, Vize-Anführer (auch versteckte) nur Admin oder Anführer.
 * Dieselbe Regel wie beim Entfernen und Verschieben und wie im LOST Manager
 * (editmember) – ohne sie konnte ein Vize den Anführer zum Mitglied machen.
 */
public final class RollenSchutz {

	public enum Sperre {
		KEINE, NUR_ADMIN, NUR_ADMIN_ODER_ANFUEHRER
	}

	private RollenSchutz() {
	}

	/**
	 * @param alteRolle     gespeicherte Rolle des Mitglieds (leader, coleader,
	 *                      hiddencoleader, elder, member) oder null
	 * @param ausfuehrender Rolle des Ausführenden in diesem Clan
	 */
	public static Sperre pruefe(String alteRolle, Player.RoleType ausfuehrender) {
		if (alteRolle == null)
			return Sperre.KEINE;
		if (alteRolle.equals("leader") && ausfuehrender != Player.RoleType.ADMIN)
			return Sperre.NUR_ADMIN;
		if ((alteRolle.equals("coleader") || alteRolle.equals("hiddencoleader"))
				&& ausfuehrender != Player.RoleType.ADMIN && ausfuehrender != Player.RoleType.LEADER)
			return Sperre.NUR_ADMIN_ODER_ANFUEHRER;
		return Sperre.KEINE;
	}
}
