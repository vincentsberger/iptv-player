package de.vincentsberger.iptvplayer.shared

/**
 * Farben aller Oberflächen (Fernseher, PC, iPhone) als ARGB – die eine Stelle für die Palette. Die Apps machen daraus
 * ihre Compose-Farben (`Color(Palette.Bg)`); eine neue Palette heißt: nur diese Datei ändern. [PaletteTest] prüft
 * die Kontraste, bevor etwas Unlesbares auf den Bildschirm kommt.
 *
 * „Anthrazit & Bernstein“ (v2.1): warmes Anthrazit wie ein abgedunkelter Kinosaal, Bernstein als einziger Akzent.
 * Der Fokus ist warmweiß statt reinweiß – aus 3 m Abstand das deutlichste Signal, blendet abends aber weniger.
 */
object Palette {
    /** Dunkelste Fläche: Seitenleiste am PC. */
    const val BgDeep = 0xFF141210
    /** Grundfläche aller Bildschirme. */
    const val Bg = 0xFF1A1816
    /** Zwischen Grund und Karte: Platzhalter beim Laden, Verläufe. */
    const val Surface0 = 0xFF1F1D1A
    /** Karten, Listenzeilen. */
    const val Surface1 = 0xFF25221F
    /** Erhobene Flächen: Panels, Chips, Eingabefelder. */
    const val Surface2 = 0xFF2F2B27
    /** Trennlinien, Spur von Fortschrittsbalken. */
    const val Line = 0xFF3B3631
    const val Text = 0xFFEEE7DE
    /** Zweitrangiger Text auf Video und in Inhaltsangaben – heller als [Muted], ruhiger als [Text]. */
    const val TextSoft = 0xFFD6CDC1
    const val Muted = 0xFFA99F93
    /** Akzent: Auswahl, Live, Fortschritt. */
    const val Signal = 0xFFFFB224
    /** Text auf [Signal]-Flächen. */
    const val OnSignal = 0xFF1A1205
    /** Fehler – klar rot, damit er sich vom Bernstein-Akzent abhebt. */
    const val Danger = 0xFFFF6B5E
    const val FocusBg = 0xFFFFF3DF
    const val FocusFg = 0xFF1E1912
    /** Abdunkelung über Bildern und Video (mit Alpha verwenden). */
    const val Shade = 0xFF0B0A09
}
