package info.varden.hauk.system.preferences.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import info.varden.hauk.BuildConfig;
import info.varden.hauk.Constants;
import info.varden.hauk.R;
import info.varden.hauk.backup.EinstellungenSicherung;
import info.varden.hauk.http.ConnectionParameters;
import info.varden.hauk.http.ServerException;
import info.varden.hauk.http.SettingsBackupPacket;
import info.varden.hauk.http.SettingsRestorePacket;
import info.varden.hauk.http.security.CertificateValidationPolicy;
import info.varden.hauk.struct.Version;
import info.varden.hauk.system.launcher.OpenLinkListener;
import info.varden.hauk.system.preferences.PreferenceHandler;
import info.varden.hauk.system.preferences.ui.listener.CascadeBindListener;
import info.varden.hauk.system.preferences.ui.listener.CascadeChangeListener;
import info.varden.hauk.system.preferences.ui.listener.FloatBoundChangeListener;
import info.varden.hauk.system.preferences.ui.listener.HintBindListener;
import info.varden.hauk.system.preferences.ui.listener.InputTypeBindListener;
import info.varden.hauk.system.preferences.ui.listener.IntegerBoundChangeListener;
import info.varden.hauk.system.preferences.ui.listener.NightModeChangeListener;
import info.varden.hauk.system.preferences.ui.listener.ProxyPreferenceChangeListener;
import info.varden.hauk.utils.TimeUtils;
import info.varden.hauk.utils.Log;

/**
 * Settings activity that allows the user to change app preferences.
 *
 * @author Marius Lindvall
 */
public final class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Fix toolbar for edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRootLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.leftMargin = insets.left;
            mlp.bottomMargin = insets.bottom;
            mlp.rightMargin = insets.right;
            mlp.topMargin = insets.top;
            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static final class SettingsFragment extends PreferenceFragmentCompat {

        private Context ctx = null;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            PreferenceManager manager = getPreferenceManager();
            info.varden.hauk.system.preferences.PreferenceManager prefs = new info.varden.hauk.system.preferences.PreferenceManager(this.ctx);

            // Intercept all reads and writes so that values are properly validated and encrypted if
            // required by Preference.
            manager.setPreferenceDataStore(new PreferenceHandler(this.ctx));

            // Load the preferences layout.
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Set InputType and other attributes for text edit boxes.
            setTextEditParams(manager, Constants.PREF_SERVER_ENCRYPTED, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI), new HintBindListener(R.string.pref_cryptServer_hint));
            setTextEditParams(manager, Constants.PREF_USERNAME_ENCRYPTED, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME), new HintBindListener(R.string.pref_cryptUsername_hint));
            setTextEditParams(manager, Constants.PREF_PASSWORD_ENCRYPTED, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            setTextEditParams(manager, Constants.PREF_E2E_PASSWORD, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            setTextEditParams(manager, Constants.PREF_INTERVAL, new InputTypeBindListener(InputType.TYPE_CLASS_NUMBER));
            setTextEditParams(manager, Constants.PREF_UPDATE_DISTANCE, new InputTypeBindListener(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
            setTextEditParams(manager, Constants.PREF_CUSTOM_ID, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE), new HintBindListener(R.string.pref_requestLink_hint));
            setTextEditParams(manager, Constants.PREF_PROXY_HOST, new InputTypeBindListener(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
            setTextEditParams(manager, Constants.PREF_PROXY_PORT, new InputTypeBindListener(InputType.TYPE_CLASS_NUMBER));
            setTextEditParams(manager, Constants.PREF_CONNECTION_TIMEOUT, new InputTypeBindListener(InputType.TYPE_CLASS_NUMBER));

            // Set value bounds checks.
            setChangeListeners(manager, Constants.PREF_INTERVAL, new IntegerBoundChangeListener(1, Integer.MAX_VALUE));
            setChangeListeners(manager, Constants.PREF_UPDATE_DISTANCE, new FloatBoundChangeListener(0.0F, Float.MAX_VALUE));
            setChangeListeners(manager, Constants.PREF_PROXY_PORT, new IntegerBoundChangeListener(Constants.PORT_MIN, Constants.PORT_MAX));
            setChangeListeners(manager, Constants.PREF_CONNECTION_TIMEOUT, new IntegerBoundChangeListener(1, Integer.MAX_VALUE));

            // Set proxy settings disabled if proxy is set to default or none.
            setChangeListeners(manager, Constants.PREF_PROXY_TYPE, new ProxyPreferenceChangeListener(new Preference[]{
                    manager.findPreference(Constants.PREF_PROXY_HOST.getKey()),
                    manager.findPreference(Constants.PREF_PROXY_PORT.getKey())
            }));
            Preference proxyTypePref = manager.findPreference(Constants.PREF_PROXY_TYPE.getKey());
            if (proxyTypePref != null) proxyTypePref.callChangeListener(String.valueOf(prefs.get(Constants.PREF_PROXY_TYPE).getIndex()));

            // Update night mode when its preference is changed.
            setChangeListeners(manager, Constants.PREF_NIGHT_MODE, new NightModeChangeListener());

            manager.findPreference("dummy_version").setSummary(BuildConfig.VERSION_NAME);
            manager.findPreference("dummy_sourceCode").setOnPreferenceClickListener(new OpenLinkListener(this.ctx, R.string.label_source_link));
            manager.findPreference("dummy_reportIssue").setOnPreferenceClickListener(new OpenLinkListener(this.ctx, R.string.link_issue_tracker));

            // PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Einstellungen
            // sichern/wiederherstellen.
            manager.findPreference("dummy_sicherungErstellen").setOnPreferenceClickListener(p -> {
                sicherungErstellen(prefs);
                return true;
            });
            manager.findPreference("dummy_sicherungWiederherstellen").setOnPreferenceClickListener(p -> {
                sicherungWiederherstellenAnfragen(prefs);
                return true;
            });
        }

        /**
         * PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Fragt ein Sicherungs-Passwort ab,
         * exportiert alle Einstellungen als JSON, verschluesselt sie damit und schickt das
         * Ergebnis an den Hauk-Server. Das Server-Passwort (zum Zugriffsschutz des Endpunkts,
         * NICHT zur Verschluesselung des Inhalts) wird aus den bereits gespeicherten
         * Einstellungen gelesen.
         */
        private void sicherungErstellen(info.varden.hauk.system.preferences.PreferenceManager prefs) {
            EditText eingabe = new EditText(this.ctx);
            eingabe.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new AlertDialog.Builder(this.ctx)
                    .setTitle(R.string.sicherung_passwort_dialog_titel_sichern)
                    .setMessage(R.string.sicherung_passwort_dialog_hinweis)
                    .setView(eingabe)
                    .setPositiveButton(R.string.sicherung_ok_button, (dialog, which) -> {
                        String sicherungsPasswort = eingabe.getText().toString();
                        try {
                            JSONObject export = EinstellungenSicherung.exportieren(prefs);
                            JSONObject blob = EinstellungenSicherung.verschluesseln(export.toString(), sicherungsPasswort);
                            String server = normalisierteServerUrl(prefs);
                            String serverPasswort = prefs.get(Constants.PREF_PASSWORD_ENCRYPTED);
                            ConnectionParameters connParams = baueVerbindungsParameter(prefs);
                            new SettingsBackupPacket(this.ctx, server, connParams, serverPasswort, blob.toString()) {
                                @Override
                                protected void onSuccess(String[] data, Version backendVersion) {
                                    Toast.makeText(SettingsFragment.this.ctx, R.string.sicherung_erfolg_gesichert, Toast.LENGTH_LONG).show();
                                }

                                @Override
                                protected void onFailure(Exception ex) {
                                    Log.e("Sicherung konnte nicht zum Server gesendet werden", ex); //NON-NLS
                                    Toast.makeText(SettingsFragment.this.ctx, getString(R.string.sicherung_fehler_netzwerk, ex.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                                }
                            }.send();
                        } catch (Exception ex) {
                            Log.e("Sicherung konnte nicht verschluesselt werden", ex); //NON-NLS
                            Toast.makeText(this.ctx, getString(R.string.sicherung_fehler_verschluesselung, ex.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(R.string.sicherung_abbrechen_button, null)
                    .show();
        }

        /**
         * PATCH (Stefan, 07.11.2026, Projekt Hauk_Watchdog): Fragt zuerst OHNE Passwort beim
         * Server an, ob eine Sicherung vorliegt (nur mit dem normalen Server-Passwort
         * geschuetzt). Erst wenn eine gefunden wurde, wird das Sicherungs-Passwort zur
         * Entschluesselung abgefragt - entspricht genau dem von Stefan gewuenschten Ablauf.
         */
        private void sicherungWiederherstellenAnfragen(info.varden.hauk.system.preferences.PreferenceManager prefs) {
            String server = normalisierteServerUrl(prefs);
            String serverPasswort = prefs.get(Constants.PREF_PASSWORD_ENCRYPTED);
            ConnectionParameters connParams = baueVerbindungsParameter(prefs);
            new SettingsRestorePacket(this.ctx, server, connParams, serverPasswort) {
                @Override
                protected void onGefunden(JSONObject verschluesselterBlob) {
                    // PATCH-Hinweis: ConnectionThread (AsyncTask) ruft onSuccess()/onFailure()
                    // bereits auf dem UI-Thread auf (onPostExecute()) - kein zusaetzliches
                    // runOnUiThread() hier noetig, analog zum restlichen Packet-Code in dieser App.
                    sicherungEntschluesselnUndAnwenden(prefs, verschluesselterBlob);
                }

                @Override
                protected void onKeineSicherungVorhanden() {
                    Toast.makeText(SettingsFragment.this.ctx, R.string.sicherung_keine_vorhanden, Toast.LENGTH_LONG).show();
                }

                @Override
                protected void onFailure(Exception ex) {
                    Log.e("Abfrage einer Sicherung beim Server fehlgeschlagen", ex); //NON-NLS
                    Toast.makeText(SettingsFragment.this.ctx, getString(R.string.sicherung_fehler_netzwerk, ex.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                }
            }.send();
        }

        private void sicherungEntschluesselnUndAnwenden(info.varden.hauk.system.preferences.PreferenceManager prefs, JSONObject verschluesselterBlob) {
            EditText eingabe = new EditText(this.ctx);
            eingabe.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new AlertDialog.Builder(this.ctx)
                    .setTitle(R.string.sicherung_passwort_dialog_titel_wiederherstellen)
                    .setMessage(R.string.sicherung_passwort_dialog_hinweis)
                    .setView(eingabe)
                    .setPositiveButton(R.string.sicherung_ok_button, (dialog, which) -> {
                        String sicherungsPasswort = eingabe.getText().toString();
                        try {
                            String klartextJson = EinstellungenSicherung.entschluesseln(verschluesselterBlob, sicherungsPasswort);
                            EinstellungenSicherung.importieren(prefs, new JSONObject(klartextJson));
                            Toast.makeText(this.ctx, R.string.sicherung_erfolg_wiederhergestellt, Toast.LENGTH_LONG).show();
                        } catch (javax.crypto.BadPaddingException ex) {
                            Toast.makeText(this.ctx, R.string.sicherung_falsches_passwort, Toast.LENGTH_LONG).show();
                        } catch (Exception ex) {
                            Log.e("Sicherung konnte nicht entschluesselt/angewendet werden", ex); //NON-NLS
                            Toast.makeText(this.ctx, getString(R.string.sicherung_fehler_verschluesselung, ex.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(R.string.sicherung_abbrechen_button, null)
                    .show();
        }

        private String normalisierteServerUrl(info.varden.hauk.system.preferences.PreferenceManager prefs) {
            String server = prefs.get(Constants.PREF_SERVER_ENCRYPTED).trim();
            return server.endsWith("/") ? server : server + "/";
        }

        /**
         * PATCH (Stefan, 07.11.2026): Bewusst vereinfacht ohne asynchrone Proxy-Hostnamen-
         * Aufloesung (siehe ProxyHostnameResolverImpl, die fuer den normalen Standort-
         * Teilen-Weg verwendet wird) - diese Sicherungs-/Wiederherstellungs-Funktion ist eine
         * einmalige Wartungsaktion, kein dauerhaft laufender Standort-Kanal. Falls Stefan
         * tatsaechlich einen Proxy fuer den Hauk-Server nutzt, wird dieser HIER NICHT
         * beruecksichtigt (direkte Verbindung wird erzwungen).
         */
        private ConnectionParameters baueVerbindungsParameter(info.varden.hauk.system.preferences.PreferenceManager prefs) {
            int timeout = prefs.get(Constants.PREF_CONNECTION_TIMEOUT) * (int) TimeUtils.MILLIS_PER_SECOND;
            CertificateValidationPolicy tlsPolicy = prefs.get(Constants.PREF_CERTIFICATE_VALIDATION);
            return new ConnectionParameters(null, null, timeout, tlsPolicy);
        }


        private static void setTextEditParams(PreferenceManager manager, info.varden.hauk.system.preferences.Preference<?> preference, EditTextPreference.OnBindEditTextListener... listeners) {
            EditTextPreference pref = manager.findPreference(preference.getKey());
            if (pref != null) {
                pref.setOnBindEditTextListener(new CascadeBindListener(listeners));
            } else {
                Log.wtf("Could not find setting for preference %s setting OnBindEditTextListener", preference); //NON-NLS
            }
        }

        private static void setChangeListeners(PreferenceManager manager, info.varden.hauk.system.preferences.Preference<?> preference, Preference.OnPreferenceChangeListener... listeners) {
            Preference pref = manager.findPreference(preference.getKey());
            if (pref != null) {
                pref.setOnPreferenceChangeListener(new CascadeChangeListener(listeners));
            } else {
                Log.wtf("Could not find setting for preference %s when setting OnPreferenceChangeListener", preference); //NON-NLS
            }
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            this.ctx = context;
        }
    }
}