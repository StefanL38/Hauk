package info.varden.hauk.utils;

/**
 * PATCH (Stefan, 06.11.2026, Projekt Hauk_Watchdog): Einfacher, statischer Zwischenspeicher
 * fuer Informationen, die auf dem Hauptbildschirm angezeigt werden sollen, aber NICHT direkt
 * beim Entstehen (in LocationUpdatePacket, einer Hintergrund-Netzwerk-Klasse ohne eigene
 * Sichtbarkeit auf die UI) dorthin geschrieben werden koennen. MainActivity liest diese Werte
 * regelmaessig aus und zeigt sie an. Bewusst simple statische Felder statt eines komplexeren
 * Callback-/Observer-Mechanismus, um das Risiko fuer den Rest der App (insbesondere die
 * bestehende, bereits fragile Service-Lebenszyklus-Logik aus GitHub-Issue #242) so klein wie
 * moeglich zu halten.
 *
 * @author Stefan (Patch), siehe Hauk_Watchdog-Projekt
 */
public final class PatchStatus {

    private PatchStatus() {
        // Nur statische Felder, keine Instanzen.
    }

    // Die letzte, vollstaendige Rohantwort des Servers auf ein Standort-Update (alle Zeilen,
    // durch " | " verbunden), oder null, falls noch kein Update erfolgreich war.
    public static volatile String letzteServerAntwort = null;

    // Zeitpunkt (System.currentTimeMillis()) der letzten erfolgreichen Server-Antwort, oder 0.
    public static volatile long letzteServerAntwortZeit = 0;

    // true, solange die letzte Server-Antwort die erwartete Patch-Kennzeichnung ENTHIELT.
    // Startwert true (optimistisch), damit vor dem allerersten Update keine Warnung ohne
    // jeden Anlass angezeigt wird.
    public static volatile boolean serverKennzeichnungVorhanden = true;
}
