package info.varden.hauk.backup;

import android.util.Base64;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.json.JSONObject;

import info.varden.hauk.Constants;
import info.varden.hauk.http.security.CertificateValidationPolicy;
import info.varden.hauk.system.preferences.IndexedEnum;
import info.varden.hauk.system.preferences.PreferenceManager;
import info.varden.hauk.system.preferences.indexresolver.NightModeStyle;
import info.varden.hauk.system.preferences.indexresolver.ProxyTypeResolver;
import info.varden.hauk.utils.Log;

/**
 * PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Exportiert/importiert die kompletten
 * App-Einstellungen als JSON UND verschluesselt/entschluesselt diesen Text mit einem vom
 * Nutzer selbst gewaehlten Sicherungs-Passwort (bewusst GETRENNT vom Hauk-Server-Passwort und
 * von der optionalen Ende-zu-Ende-Verschluesselung der Standortdaten). Nutzt dieselbe bereits
 * etablierte Verschluesselungsmethode wie die Standort-Ende-zu-Ende-Verschluesselung
 * (PBKDF2WithHmacSHA1 -> AES-256/CBC/PKCS5Padding, siehe {@link info.varden.hauk.struct.KeyDerivable}),
 * damit hier KEINE neue, eigene Kryptographie-Implementierung entsteht. Der Hauk-Server
 * speichert NUR das Ergebnis dieser Verschluesselung (Salt + Initialisierungsvektor + Geheimtext
 * als ein JSON-Objekt) - er kennt das Sicherungs-Passwort nie und kann den Inhalt nicht lesen.
 *
 * @author Stefan (Patch), siehe Hauk-Watchdog-Projekt
 */
public final class EinstellungenSicherung {

    private EinstellungenSicherung() {
        // Nur statische Methoden.
    }

    /**
     * Sammelt alle relevanten App-Einstellungen aus dem {@link PreferenceManager} in ein
     * JSON-Objekt. Nutzt bewusst die TYPISIERTE get()-Methode (nicht die rohen
     * SharedPreferences direkt), weil einige Werte (z. B. das Server-Passwort) auf dem Geraet
     * zusaetzlich ueber den Android-Keystore verschluesselt abgelegt sind (siehe
     * Preference.EncryptedString) - der Keystore-Schluessel wird bei einer Neuinstallation
     * GELOESCHT, ein roher Export/Import dieser Werte wuerde deshalb nach einer
     * Neuinstallation nicht mehr entschluesselbar sein. Die get()-Methode entschluesselt
     * transparent, sodass hier immer der tatsaechliche Klartextwert landet.
     */
    public static JSONObject exportieren(PreferenceManager prefs) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(Constants.PREF_SERVER_ENCRYPTED.getKey(), prefs.get(Constants.PREF_SERVER_ENCRYPTED));
        json.put(Constants.PREF_USERNAME_ENCRYPTED.getKey(), prefs.get(Constants.PREF_USERNAME_ENCRYPTED));
        json.put(Constants.PREF_PASSWORD_ENCRYPTED.getKey(), prefs.get(Constants.PREF_PASSWORD_ENCRYPTED));
        json.put(Constants.PREF_DURATION.getKey(), prefs.get(Constants.PREF_DURATION));
        json.put(Constants.PREF_INTERVAL.getKey(), prefs.get(Constants.PREF_INTERVAL));
        json.put(Constants.PREF_NO_GNSS_FALLBACK.getKey(), prefs.get(Constants.PREF_NO_GNSS_FALLBACK));
        json.put(Constants.PREF_UPDATE_DISTANCE.getKey(), prefs.get(Constants.PREF_UPDATE_DISTANCE));
        json.put(Constants.PREF_CUSTOM_ID.getKey(), prefs.get(Constants.PREF_CUSTOM_ID));
        json.put(Constants.PREF_ENABLE_E2E.getKey(), prefs.get(Constants.PREF_ENABLE_E2E));
        json.put(Constants.PREF_E2E_PASSWORD.getKey(), prefs.get(Constants.PREF_E2E_PASSWORD));
        json.put(Constants.PREF_NICKNAME.getKey(), prefs.get(Constants.PREF_NICKNAME));
        json.put(Constants.PREF_DURATION_UNIT.getKey(), prefs.get(Constants.PREF_DURATION_UNIT));
        json.put(Constants.PREF_ALLOW_ADOPTION.getKey(), prefs.get(Constants.PREF_ALLOW_ADOPTION));
        json.put(Constants.PREF_NIGHT_MODE.getKey(), prefs.get(Constants.PREF_NIGHT_MODE).getIndex());
        json.put(Constants.PREF_CONFIRM_STOP.getKey(), prefs.get(Constants.PREF_CONFIRM_STOP));
        json.put(Constants.PREF_HIDE_LOGO.getKey(), prefs.get(Constants.PREF_HIDE_LOGO));
        json.put(Constants.PREF_PROXY_TYPE.getKey(), prefs.get(Constants.PREF_PROXY_TYPE).getIndex());
        json.put(Constants.PREF_PROXY_HOST.getKey(), prefs.get(Constants.PREF_PROXY_HOST));
        json.put(Constants.PREF_PROXY_PORT.getKey(), prefs.get(Constants.PREF_PROXY_PORT));
        json.put(Constants.PREF_CONNECTION_TIMEOUT.getKey(), prefs.get(Constants.PREF_CONNECTION_TIMEOUT));
        json.put(Constants.PREF_CERTIFICATE_VALIDATION.getKey(), prefs.get(Constants.PREF_CERTIFICATE_VALIDATION).getIndex());
        return json;
    }

    /**
     * Wendet ein zuvor per {@link #exportieren(PreferenceManager)} erzeugtes JSON-Objekt wieder
     * auf die App-Einstellungen an. Fehlt ein einzelnes Feld (z. B. weil eine aeltere Sicherung
     * aus einer Version mit weniger Einstellungen wiederhergestellt wird) oder laesst es sich
     * nicht anwenden, wird GENAU dieses Feld protokolliert und uebersprungen statt die ganze
     * Wiederherstellung abzubrechen (siehe CLAUDE.md "Stilles Versagen verhindern").
     */
    public static void importieren(PreferenceManager prefs, JSONObject json) {
        setzeString(prefs, json, Constants.PREF_SERVER_ENCRYPTED);
        setzeString(prefs, json, Constants.PREF_USERNAME_ENCRYPTED);
        setzeString(prefs, json, Constants.PREF_PASSWORD_ENCRYPTED);
        setzeInt(prefs, json, Constants.PREF_DURATION);
        setzeInt(prefs, json, Constants.PREF_INTERVAL);
        setzeInt(prefs, json, Constants.PREF_NO_GNSS_FALLBACK);
        setzeFloat(prefs, json, Constants.PREF_UPDATE_DISTANCE);
        setzeString(prefs, json, Constants.PREF_CUSTOM_ID);
        setzeBoolean(prefs, json, Constants.PREF_ENABLE_E2E);
        setzeString(prefs, json, Constants.PREF_E2E_PASSWORD);
        setzeString(prefs, json, Constants.PREF_NICKNAME);
        setzeInt(prefs, json, Constants.PREF_DURATION_UNIT);
        setzeBoolean(prefs, json, Constants.PREF_ALLOW_ADOPTION);
        setzeBoolean(prefs, json, Constants.PREF_CONFIRM_STOP);
        setzeBoolean(prefs, json, Constants.PREF_HIDE_LOGO);
        setzeString(prefs, json, Constants.PREF_PROXY_HOST);
        setzeInt(prefs, json, Constants.PREF_PROXY_PORT);
        setzeInt(prefs, json, Constants.PREF_CONNECTION_TIMEOUT);

        // Enum-Einstellungen: jeweils ueber die konkrete Klasse und die OEFFENTLICHE statische
        // IndexedEnum.fromIndex(Class, int)-Methode aufgeloest (die Instanzmethode fromIndex()
        // ist bewusst paketprivat und von hier aus nicht erreichbar).
        setzeEnum(prefs, json, Constants.PREF_NIGHT_MODE, NightModeStyle.class);
        setzeEnum(prefs, json, Constants.PREF_PROXY_TYPE, ProxyTypeResolver.class);
        setzeEnum(prefs, json, Constants.PREF_CERTIFICATE_VALIDATION, CertificateValidationPolicy.class);
    }

    private static void setzeString(PreferenceManager prefs, JSONObject json, info.varden.hauk.system.preferences.Preference<java.lang.String> pref) {
        if (!uebersprungenFallsFehlt(json, pref.getKey())) return;
        try {
            prefs.set(pref, json.getString(pref.getKey()));
        } catch (JSONException e) {
            Log.w("Sicherung: Feld '%s' (String) konnte nicht gelesen werden (%s) - wird uebersprungen.", pref.getKey(), e.getMessage()); //NON-NLS
        }
    }

    private static void setzeInt(PreferenceManager prefs, JSONObject json, info.varden.hauk.system.preferences.Preference<java.lang.Integer> pref) {
        if (!uebersprungenFallsFehlt(json, pref.getKey())) return;
        try {
            prefs.set(pref, json.getInt(pref.getKey()));
        } catch (JSONException e) {
            Log.w("Sicherung: Feld '%s' (Integer) konnte nicht gelesen werden (%s) - wird uebersprungen.", pref.getKey(), e.getMessage()); //NON-NLS
        }
    }

    private static void setzeFloat(PreferenceManager prefs, JSONObject json, info.varden.hauk.system.preferences.Preference<java.lang.Float> pref) {
        if (!uebersprungenFallsFehlt(json, pref.getKey())) return;
        try {
            prefs.set(pref, (float) json.getDouble(pref.getKey()));
        } catch (JSONException e) {
            Log.w("Sicherung: Feld '%s' (Float) konnte nicht gelesen werden (%s) - wird uebersprungen.", pref.getKey(), e.getMessage()); //NON-NLS
        }
    }

    private static void setzeBoolean(PreferenceManager prefs, JSONObject json, info.varden.hauk.system.preferences.Preference<java.lang.Boolean> pref) {
        if (!uebersprungenFallsFehlt(json, pref.getKey())) return;
        try {
            prefs.set(pref, json.getBoolean(pref.getKey()));
        } catch (JSONException e) {
            Log.w("Sicherung: Feld '%s' (Boolean) konnte nicht gelesen werden (%s) - wird uebersprungen.", pref.getKey(), e.getMessage()); //NON-NLS
        }
    }

    private static <T extends IndexedEnum<T>> void setzeEnum(PreferenceManager prefs, JSONObject json, info.varden.hauk.system.preferences.Preference<T> pref, Class<T> typ) {
        if (!uebersprungenFallsFehlt(json, pref.getKey())) return;
        try {
            int index = json.getInt(pref.getKey());
            T wert = IndexedEnum.fromIndex(typ, index);
            prefs.set(pref, wert);
        } catch (JSONException | IllegalAccessException | InstantiationException e) {
            Log.w("Sicherung: Feld '%s' (Enum) konnte nicht gelesen werden (%s) - wird uebersprungen.", pref.getKey(), e.getMessage()); //NON-NLS
        }
    }

    private static boolean uebersprungenFallsFehlt(JSONObject json, String schluessel) {
        if (json.has(schluessel)) return true;
        Log.w("Sicherung enthaelt kein Feld '%s' - wird uebersprungen, bisheriger Wert bleibt unveraendert.", schluessel); //NON-NLS
        return false;
    }

    /**
     * Verschluesselt den gegebenen Klartext (das JSON aus {@link #exportieren(PreferenceManager)},
     * als String) mit einem aus dem Sicherungs-Passwort abgeleiteten AES-256-Schluessel.
     * Erzeugt einen NEUEN, zufaelligen Salt UND Initialisierungsvektor und liefert alle drei
     * Teile (Salt, IV, Geheimtext) in einem einzigen JSON-Objekt zurueck - das ist genau der
     * "Blob", den der Server opak (ohne ihn zu verstehen) speichert.
     */
    public static JSONObject verschluesseln(String klartext, String sicherungsPasswort)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException, JSONException {
        SecureRandom rand = new SecureRandom();
        byte[] salt = new byte[Constants.SETTINGS_BACKUP_SALT_LAENGE_BYTES];
        rand.nextBytes(salt);
        byte[] iv = new byte[Constants.SETTINGS_BACKUP_IV_LAENGE_BYTES];
        rand.nextBytes(iv);

        SecretKeySpec key = leiteSchluesselAb(sicherungsPasswort, salt);
        Cipher cipher = Cipher.getInstance(Constants.E2E_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] geheimtext = cipher.doFinal(klartext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JSONObject blob = new JSONObject();
        blob.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        blob.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        blob.put("geheimtext", Base64.encodeToString(geheimtext, Base64.NO_WRAP));
        return blob;
    }

    /**
     * Kehrt {@link #verschluesseln(String, String)} um: liest Salt/IV/Geheimtext aus dem Blob,
     * leitet denselben Schluessel aus dem (vom Nutzer erneut eingegebenen) Sicherungs-Passwort
     * ab und entschluesselt. Ein FALSCHES Passwort fuehrt zu einer {@link BadPaddingException}
     * (durch die PKCS5-Padding-Pruefung erkennbar falsch) - das wird vom Aufrufer explizit als
     * "falsches Passwort" behandelt, nicht als allgemeiner Fehler.
     */
    public static String entschluesseln(JSONObject blob, String sicherungsPasswort)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException, JSONException {
        byte[] salt = Base64.decode(blob.getString("salt"), Base64.NO_WRAP);
        byte[] iv = Base64.decode(blob.getString("iv"), Base64.NO_WRAP);
        byte[] geheimtext = Base64.decode(blob.getString("geheimtext"), Base64.NO_WRAP);

        SecretKeySpec key = leiteSchluesselAb(sicherungsPasswort, salt);
        Cipher cipher = Cipher.getInstance(Constants.E2E_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] klartext = cipher.doFinal(geheimtext);
        return new String(klartext, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static SecretKeySpec leiteSchluesselAb(String passwort, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec ks = new PBEKeySpec(passwort.toCharArray(), salt, Constants.SETTINGS_BACKUP_PBKDF2_ITERATIONEN, Constants.E2E_AES_KEY_SIZE);
        SecretKeyFactory kf = SecretKeyFactory.getInstance(Constants.E2E_KD_FUNCTION);
        byte[] keyBytes = kf.generateSecret(ks).getEncoded();
        return new SecretKeySpec(keyBytes, Constants.E2E_KEY_SPEC);
    }
}
