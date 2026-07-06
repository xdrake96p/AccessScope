/**
 * Modalità di tema grafico selezionabile dall'utente.
 */
package dev.accessscope.scanner.ui.theme

/**
 * Preferenza tema dell'interfaccia AccessScope.
 *
 * @property label Etichetta mostrata nelle impostazioni.
 * @property emoji Simbolo visivo per il selettore tema.
 */
enum class AppThemeMode(val label: String, val emoji: String) {
    /** Tema chiaro ad alto contrasto (WCAG AAA). */
    LIGHT("Chiaro", "☀️"),

    /** Tema scuro con sfondo notte e superfici lavagna. */
    DARK("Scuro", "🌙"),

    /** Segue le impostazioni tema del sistema operativo Android. */
    SYSTEM("Predefinito di sistema", "📱"),
}
