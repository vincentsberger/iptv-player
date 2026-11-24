package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.design.AppButton
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonStyle
import de.vincentsberger.iptvplayer.ios.ui.design.Chip
import de.vincentsberger.iptvplayer.ios.ui.design.GroupRow
import de.vincentsberger.iptvplayer.ios.ui.design.H1
import de.vincentsberger.iptvplayer.ios.ui.design.H3
import de.vincentsberger.iptvplayer.ios.ui.design.ListGroup
import de.vincentsberger.iptvplayer.ios.ui.design.MonoText
import de.vincentsberger.iptvplayer.ios.ui.design.ProgressBar
import de.vincentsberger.iptvplayer.ios.ui.design.ProgressRing
import de.vincentsberger.iptvplayer.ios.ui.design.RowIcon
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.Tag
import de.vincentsberger.iptvplayer.ios.ui.design.TinyText
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.OverviewRow
import de.vincentsberger.iptvplayer.shared.connectionsLabel
import de.vincentsberger.iptvplayer.shared.countLabel
import de.vincentsberger.iptvplayer.shared.countryNameOf
import de.vincentsberger.iptvplayer.shared.flagOf
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.overviewNote
import de.vincentsberger.iptvplayer.shared.standLabel
import de.vincentsberger.iptvplayer.shared.subscriptionLabel
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.math.roundToInt

/** So viele Länder bietet die Übersicht zur Auswahl an (die häufigsten zuerst); alle weiteren in den Einstellungen. */
private const val MAX_COUNTRY_CHIPS = 12

/**
 * Ladebildschirm nach Anmeldung/Start (Entwurf „2a“): Gesamtbalken über die fertigen Bereiche und je Bereich, ob er
 * fertig ist, gerade lädt oder wartet. Live TV lässt sich schon öffnen, sobald es fertig ist.
 */
@Composable
fun LoadingScreen(app: AppState) {
    Page(top = 120.dp, spacing = 24.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            de.vincentsberger.iptvplayer.ios.ui.design.Kicker(app.accountLabel)
            H1("Playlist wird geladen")
            Text(
                "Große Playlists brauchen beim ersten Laden etwas länger. Danach startet die App aus dem Zwischenspeicher.",
                color = Muted, fontSize = 16.sp, lineHeight = 22.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gesamt", color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                MonoText("${(app.startupProgress * 100).roundToInt()} %", color = Muted)
            }
            ProgressBar(app.startupProgress, Modifier.fillMaxWidth(), height = 4.dp)
        }
        // Die Bereiche laden nacheinander: der erste noch ladende „lädt“, die übrigen „warten“.
        val active = Section.entries.firstOrNull { app.sections[it]?.loading != false }
        ListGroup(Section.entries.map { s -> { SectionLoadRow(app, s, isActive = s == active) } })
        if (app.canOpenLiveEarly) SmallText("Du kannst die App schon benutzen – Live TV ist bereit.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Abbrechen", { app.cancelStartup() }, Modifier.weight(1f))
            if (app.canOpenLiveEarly) AppButton("Live TV", { app.openLiveEarly() }, Modifier.weight(1f), icon = Icons.Rounded.Tv)
        }
    }
}

@Composable
private fun SectionLoadRow(app: AppState, s: Section, isActive: Boolean) {
    val raw = app.sections[s]
    when {
        raw != null && !raw.loading && raw.error == null -> GroupRow(
            s.title, leading = { RowIcon(Icons.Rounded.CheckCircle, TextColor) },
            value = "${formatCount(app.section(s).items.size)} ${countLabel(s)}", valueMono = true,
        )
        raw?.error != null -> GroupRow(s.title, leading = { RowIcon(Icons.Rounded.ErrorOutline, Danger) }, value = "Fehler", valueColor = Danger)
        isActive -> GroupRow(s.title, leading = { ProgressRing(null) }, value = "lädt")
        else -> GroupRow(s.title, leading = { RowIcon(Icons.Rounded.Schedule) }, titleColor = Muted, value = "wartet")
    }
}

/**
 * Übersicht nach dem Laden (Entwurf „2b“): Anzahl je Bereich, Hinweise zu Abo und Datenqualität, die Länderwahl –
 * wartet auf „Weiter“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(app: AppState, rows: List<OverviewRow>) {
    Page(top = 28.dp, spacing = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Check, null, tint = TextColor, modifier = Modifier.size(14.dp))
                de.vincentsberger.iptvplayer.ios.ui.design.Kicker("Fertig geladen")
            }
            H1("Das bietet dein Anbieter")
            val stand = rows.mapNotNull { it.loadedAt }.minOrNull()?.let { " · Stand: ${standLabel(it)}" }.orEmpty()
            SmallText(app.accountLabel + stand)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row -> CountTile(row, Modifier.weight(1f)) }
        }
        val facts = overviewFacts(app)
        if (facts.isNotEmpty()) ListGroup(facts)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            H3("Wähle, was dich interessiert")
            SmallText("Nur Kategorien dieser Länder zeigen – später unter Einstellungen → Länder änderbar.")
            val options = remember(app.section(Section.LIVE).items.size) { app.availableCountries(Section.LIVE).take(MAX_COUNTRY_CHIPS) }
            val chosen = app.countries(Section.LIVE)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { c ->
                    Chip(
                        countryNameOf(c.tag), c.tag in chosen, { app.toggleCountry(Section.LIVE, c.tag) }, small = true,
                        leading = flagOf(c.tag)?.let { f -> { Text(f, fontSize = 15.sp) } },
                    )
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Neu laden", { app.reloadFromNetwork() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
            AppButton("Weiter", { app.continueFromOverview() }, Modifier.weight(1f), ButtonStyle.Primary)
        }
    }
}

/** Kachel je Bereich: Zahl in Plex Mono, darunter das Wort; Veränderung oder Warnung klein darunter. */
@Composable
private fun CountTile(row: OverviewRow, modifier: Modifier) {
    Column(modifier.background(Surface1, Corner).border(1.dp, Line, Corner).padding(12.dp)) {
        MonoText(formatCount(row.count), size = 19.sp, weight = FontWeight.Medium)
        SmallText(countLabel(row.section))
        val note = overviewNote(row)
        if (note.isNotEmpty()) TinyText(note, Modifier.padding(top = 4.dp), color = if (row.warning != null) Danger else Muted)
    }
}

/** Hinweise unter den Zahlen: Abo, Namenszusätze als Kennzeichen, zusammengefasste doppelte Sender. */
private fun overviewFacts(app: AppState): List<@Composable () -> Unit> = buildList {
    app.accountStatus?.info?.let { info ->
        add { GroupRow(subscriptionLabel(info), leading = { RowIcon(Icons.Rounded.Schedule) }, subtitle = connectionsLabel(info)) }
    }
    if (app.hasNameBadges(Section.LIVE)) add {
        GroupRow(
            "Namenszusätze als Kennzeichen", leading = { Tag("RAW", highlight = true) },
            subtitle = "„ᴿᴬᵂ“, „ᴴᴰ“, „4K“ stehen jetzt neben dem Namen.",
        )
    }
    val duplicates = app.collapsedDuplicates(Section.LIVE)
    if (duplicates > 0) add {
        GroupRow(
            "${formatCount(duplicates)} doppelte Sender", leading = { RowIcon(Icons.AutoMirrored.Rounded.List) },
            subtitle = "zusammengefasst – Qualität wählst du im Player.",
        )
    }
}

@Composable
private fun Page(top: Dp, spacing: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = top, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
