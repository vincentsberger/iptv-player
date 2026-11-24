package de.vincentsberger.iptvplayer.shared.brand

import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Section

/** Ladeschritt eines Bereichs; [active] = erster noch nicht fertiger Bereich (lädt, auch wenn sein Zustand noch fehlt). */
fun loadStep(raw: SectionState?, active: Boolean): LoadStep = when {
    raw == null -> if (active) LoadStep.LOADING else LoadStep.WAITING
    raw.loading -> LoadStep.LOADING
    raw.error != null -> LoadStep.FAILED
    else -> LoadStep.DONE
}

/** Überschrift für den ersten noch offenen Bereich (LIVE, MOVIES, SERIES); `null` = nichts offen, die UI behält die letzte. */
fun loadingHeadline(sections: Map<Section, SectionState?>): String? {
    val open = Section.entries.firstOrNull { s -> sections[s].let { it == null || it.loading } } ?: return null
    return when (open) {
        Section.LIVE -> "Sender werden gesucht"
        Section.MOVIES -> "Filme werden gesucht"
        Section.SERIES -> "Serien werden gesucht"
    }
}
