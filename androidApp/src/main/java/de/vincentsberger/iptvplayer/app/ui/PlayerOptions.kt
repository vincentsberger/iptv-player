package de.vincentsberger.iptvplayer.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.data.Storage
import java.util.Locale

/** Eine wählbare Ton- oder Untertitelspur. [group]/[index] zeigen auf die Spur im Player. */
data class TrackOption(
    val key: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
    val group: Tracks.Group? = null,
    val index: Int = 0,
)

/** Alle abspielbaren Spuren eines Typs (C.TRACK_TYPE_AUDIO / C.TRACK_TYPE_TEXT), mit lesbarem Namen. */
internal fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    val list = mutableListOf<TrackOption>()
    tracks.groups.forEachIndexed { g, group ->
        if (group.type != type) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            list += TrackOption("$g:$i", trackLabel(f, list.size), f.language, group.isTrackSelected(i), group, i)
        }
    }
    return list
}

/** „Englisch · 5.1“, „Director's Commentary · Englisch“ oder notfalls „Spur 2“. */
@OptIn(UnstableApi::class)
internal fun trackLabel(f: Format, position: Int): String {
    val lang = languageName(f.language)
    val label = f.label?.trim()?.takeIf { it.isNotEmpty() }
    val parts = buildList {
        add(label ?: lang)
        if (label != null && lang != null && !label.contains(lang, ignoreCase = true)) add(lang)
        add(channelLabel(f.channelCount))
    }.filterNotNull()
    return parts.joinToString(" · ").ifEmpty { "Spur ${position + 1}" }
}

/** Sprachcode → deutscher Name („de“ → „Deutsch“); unbekannte Codes in Großbuchstaben, „und“ → null. */
internal fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.GERMAN)
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase()
    else name.replaceFirstChar { it.titlecase(Locale.GERMAN) }
}

private fun channelLabel(count: Int): String? = when (count) {
    1 -> "Mono"
    6 -> "5.1"
    8 -> "7.1"
    else -> null
}

/** Startwerte des Players aus den gespeicherten Vorlieben (gilt auch für den nächsten Film). */
internal fun preferredTrackParameters(base: TrackSelectionParameters, audio: String?, subtitles: String?): TrackSelectionParameters {
    val b = base.buildUpon()
    if (audio != null) b.setPreferredAudioLanguage(audio)
    when (subtitles) {
        null -> Unit
        Storage.SUBTITLES_OFF -> b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else -> b.setPreferredTextLanguage(subtitles).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    }
    return b.build()
}

/** Spur auswählen; null schaltet den Typ ab (nur für Untertitel sinnvoll). */
internal fun selectTrack(player: Player, type: Int, option: TrackOption?) {
    val b = player.trackSelectionParameters.buildUpon()
    val group = option?.group
    if (option == null || group == null) {
        b.setTrackTypeDisabled(type, true)
    } else {
        b.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.index))
        option.language?.let { if (type == C.TRACK_TYPE_AUDIO) b.setPreferredAudioLanguage(it) else b.setPreferredTextLanguage(it) }
    }
    player.trackSelectionParameters = b.build()
}

/**
 * Optionen im Player (Menütaste ☰): Tonspur, Untertitel, Schlaf-Timer und weitere Einträge über [extra].
 * Zurück oder ☰ schließt das Menü wieder.
 */
@Composable
internal fun PlayerOptionsPanel(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    sleepLeft: Int?,
    onAudio: (TrackOption) -> Unit,
    onSubtitles: (TrackOption?) -> Unit,
    onSleep: () -> Unit,
    statsOpen: Boolean,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val first = remember { FocusRequester() }
    val subtitlesOff = subtitles.none { it.selected }
    val firstKey = audio.firstOrNull { it.selected }?.key ?: audio.firstOrNull()?.key ?: "sub:off"
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun Modifier.initial(key: String) = if (key == firstKey) focusRequester(first) else this

    Column(
        modifier.width(460.dp).heightIn(max = 480.dp).panel().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Kicker("Tonspur")
        if (audio.isEmpty()) Text("Keine Auswahl verfügbar", color = Muted, fontSize = 15.sp)
        audio.forEach { o ->
            TvButton(o.label, Modifier.fillMaxWidth().initial(o.key).testTag("audio:${o.key}"), selected = o.selected) { onAudio(o) }
        }
        Kicker("Untertitel", modifier = Modifier.padding(top = 12.dp))
        TvButton("Aus", Modifier.fillMaxWidth().initial("sub:off").testTag("sub:off"), selected = subtitlesOff) { onSubtitles(null) }
        subtitles.forEach { o ->
            TvButton(o.label, Modifier.fillMaxWidth().testTag("sub:${o.key}"), selected = o.selected) { onSubtitles(o) }
        }
        extra()
        Kicker("Statistik", modifier = Modifier.padding(top = 12.dp))
        TvButton(
            if (statsOpen) "Verbindungsstatistik ausblenden" else "Verbindungsstatistik anzeigen",
            Modifier.fillMaxWidth().testTag("option:stats"), selected = statsOpen,
        ) { onStats() }
        Kicker("Schlaf-Timer", modifier = Modifier.padding(top = 12.dp))
        TvButton(
            sleepLeft?.let { "Noch $it min – OK für länger / aus" } ?: "Aus – OK für 30 min",
            Modifier.fillMaxWidth().testTag("option:sleep"), icon = Icons.Rounded.Bedtime, selected = sleepLeft != null,
        ) { onSleep() }
    }
}

/** Bildformat im Player-Menü: Einpassen (schwarze Ränder), Füllen (beschneidet), Strecken (verzerrt). */
@Composable
internal fun ColumnScope.VideoFitOption(fit: String, onChange: (String) -> Unit) {
    Kicker("Bildformat", modifier = Modifier.padding(top = 12.dp))
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("fit" to "Einpassen", "zoom" to "Füllen", "fill" to "Strecken").forEach { (key, label) ->
            TvButton(label, Modifier.testTag("fit:$key"), selected = fit == key) { onChange(key) }
        }
    }
}

/** Quelle wählen, wenn der Anbieter den Titel mehrfach führt (siehe AppState.sourcesOf). */
@Composable
internal fun ColumnScope.SourceOption(count: Int, selected: Int, onChange: (Int) -> Unit) {
    if (count < 2) return
    Kicker("Quelle", modifier = Modifier.padding(top = 12.dp))
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i -> TvButton("${i + 1}", Modifier.testTag("source:$i"), selected = selected == i) { onChange(i) } }
    }
}
