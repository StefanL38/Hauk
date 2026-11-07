package info.varden.hauk.http;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
import info.varden.hauk.struct.Version;

/**
 * PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Fragt beim Hauk-Server ab, ob eine
 * gesicherte, verschluesselte Einstellungs-Kopie vorliegt, und laedt sie herunter (weiterhin
 * verschluesselt - die Entschluesselung mit dem Sicherungs-Passwort passiert erst lokal in der
 * App, siehe {@link info.varden.hauk.backup.EinstellungenSicherung}).
 *
 * @author Stefan (Patch), siehe Hauk-Watchdog-Projekt
 */
public abstract class SettingsRestorePacket extends Packet {
    public SettingsRestorePacket(Context ctx, String server, ConnectionParameters connParams, String serverPasswort) {
        super(ctx, server, connParams, Constants.URL_PATH_SETTINGS_RESTORE);
        setParameter(Constants.PACKET_PARAM_PASSWORD, serverPasswort);
    }

    /**
     * Wird aufgerufen, wenn der Server eine gesicherte, verschluesselte Kopie zurueckgeliefert
     * hat.
     *
     * @param verschluesselterBlob Der verschluesselte Blob (Salt/IV/Geheimtext als JSON).
     */
    protected abstract void onGefunden(JSONObject verschluesselterBlob);

    /**
     * Wird aufgerufen, wenn der Server bestaetigt hat, dass (noch) KEINE Sicherung vorliegt -
     * das ist ein normaler, erwarteter Zustand (z. B. beim allerersten Aufruf), KEIN Fehler.
     */
    protected abstract void onKeineSicherungVorhanden();

    @Override
    protected final void onSuccess(String[] data, Version backendVersion) throws ServerException {
        if (data.length < 1) {
            throw new ServerException(getContext(), R.string.err_empty);
        }
        if ("NONE".equals(data[0])) {
            onKeineSicherungVorhanden();
            return;
        }
        if (!"OK".equals(data[0]) || data.length < 2) {
            StringBuilder err = new StringBuilder();
            for (String line : data) {
                err.append(line);
                err.append(System.lineSeparator());
            }
            throw new ServerException(err.toString());
        }
        try {
            onGefunden(new JSONObject(data[1]));
        } catch (JSONException e) {
            throw new ServerException("Vom Server erhaltene Sicherung ist kein gueltiges JSON: " + e.getMessage()); //NON-NLS
        }
    }
}
