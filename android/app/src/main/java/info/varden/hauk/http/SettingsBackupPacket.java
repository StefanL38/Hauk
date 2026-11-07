package info.varden.hauk.http;

import android.content.Context;

import info.varden.hauk.Constants;

/**
 * PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Sendet einen bereits verschluesselten
 * Einstellungs-Blob (siehe {@link info.varden.hauk.backup.EinstellungenSicherung}) an den
 * Hauk-Server, wo er OPAK (ohne dass der Server ihn lesen kann) gespeichert wird. Nutzt
 * dasselbe Hauk-Server-Passwort wie jede andere Anfrage als Zugriffsschutz - NICHT zu
 * verwechseln mit dem separaten Sicherungs-Passwort, das den Blob-INHALT verschluesselt.
 *
 * @author Stefan (Patch), siehe Hauk-Watchdog-Projekt
 */
public abstract class SettingsBackupPacket extends Packet {
    public SettingsBackupPacket(Context ctx, String server, ConnectionParameters connParams, String serverPasswort, String verschluesselterBlobJson) {
        super(ctx, server, connParams, Constants.URL_PATH_SETTINGS_BACKUP);
        setParameter(Constants.PACKET_PARAM_PASSWORD, serverPasswort);
        setParameter(Constants.PACKET_PARAM_SETTINGS_BLOB, verschluesselterBlobJson);
    }
}
