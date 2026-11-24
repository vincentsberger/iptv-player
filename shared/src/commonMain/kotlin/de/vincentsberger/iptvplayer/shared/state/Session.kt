package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.data.Repository

/**
 * Der aktive Anbieter, geteilt von AppState und den Stores. Ein Store, der etwas lädt, merkt sich das Repository beim
 * Start und trägt das Ergebnis nur ein, wenn es danach noch dasselbe ist (Anbieter inzwischen gewechselt → verwerfen).
 */
class Session {
    var repo: Repository? = null
}
