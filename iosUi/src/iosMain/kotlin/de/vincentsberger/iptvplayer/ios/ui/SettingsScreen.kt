package de.vincentsberger.iptvplayer.ios.ui

import de.vincentsberger.iptvplayer.ios.ui.design.BackBar
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import de.vincentsberger.iptvplayer.shared.formatBytes
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.flagOf
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.displayName

/**
 * Einstellungen: Anbieter (wechseln, hinzufügen, neu laden, abmelden), Wiedergabe, Listen (Sortierung, ausgeblendete
 * Kategorien, Länder, Startseiten-Kategorien), Downloads, Verlauf, Lizenzen.
 */
@Composable
fun SettingsScreen(app: AppState, onBack: () -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    if (licenses) { LicensesScreen(onBack = { licenses = false }); return }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        BackBar("Start", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            de.vincentsberger.iptvplayer.ios.ui.design.H1("Einstellungen")
            Group("Anbieter") { Accounts(app, onLogout = { confirm = Confirm.LOGOUT }) }

            Group("Wiedergabe") {
                Toggle("Mit dem letzten Sender starten", "Die App öffnet direkt den zuletzt gesehenen Live-Sender.", app.startWithLastChannel) {
                    app.startWithLastChannel = it
                }
                RowDivider()
                Toggle("Nächste Folge automatisch", "Mit 10 Sekunden Countdown – Abbrechen hält an.", app.autoNextEpisode) { app.autoNextEpisode = it }
                RowDivider()
                Toggle("Player immer im Querformat", "Dreht beim Abspielen ins Querformat, auch bei Dreh-Sperre.", app.playerLandscape) { app.playerLandscape = it }
            }

            Group("Listen") {
                Toggle("Alphabetisch sortieren", "Sonst gilt die Reihenfolge deines Anbieters.", app.sortAz) { app.sortAz = it }
                RowDivider()
                Toggle("Filme und Serien: neueste zuerst", "Nach dem Jahr im Titel; ohne Jahr ans Ende.", app.sortByYear) { app.sortByYear = it }
                RowDivider()
                HiddenCategories(app)
            }
            Section.entries.filter { app.availableCountries(it).isNotEmpty() }.forEach { s ->
                Group("Länder in ${s.title}") { Countries(app, s) }
            }
            if (app.supportsDashboard) Group("Startseite") { Dashboard(app) }
            Group("Startseite: Reihen") { HomeRows(app) }

            Group("Downloads") {
                Toggle(
                    "Downloads pausieren beim Streamen", "Viele Anbieter erlauben nur eine Verbindung – sonst ruckelt das Bild.",
                    app.pauseDownloadsWhilePlaying,
                ) { app.pauseDownloadsWhilePlaying = it }
                RowDivider()
                Text(
                    "${IosDownloads.all.size} Titel · ${formatBytes(IosDownloads.usedBytes())} belegt · noch ${formatBytes(IosDownloads.freeBytes())} frei",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            Group("Verlauf") {
                Text(
                    "Zuletzt gesehene Sender und angefangene Filme und Folgen (${app.history.size}).",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
                )
                TextButton({ confirm = Confirm.HISTORY }, enabled = app.history.isNotEmpty()) { Text("Verlauf löschen", color = Danger) }
            }

            Group("Über") {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Version", Modifier.weight(1f))
                    de.vincentsberger.iptvplayer.ios.ui.design.MonoText(appVersion(), color = Muted, size = 14.sp)
                }
                RowDivider()
                NavRow("Lizenzen") { licenses = true }
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("IPTV Player ${appVersion()} · Updates über eine neue Installation (siehe Anleitung).", color = Muted, fontSize = 12.sp)
                Text("Diese App ist ein reiner Player und enthält keine Inhalte. Wiedergabe mit VLC (LGPL).", color = Muted, fontSize = 12.sp)
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = {
                TextButton({
                    confirm = null
                    when (c) {
                        Confirm.LOGOUT -> app.logout()
                        Confirm.HISTORY -> app.clearHistory()
                    }
                }) { Text(c.action, color = Danger) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Abbrechen") } },
            containerColor = Surface1,
        )
    }
}

private enum class Confirm(val title: String, val text: String, val action: String) {
    LOGOUT("Abmelden?", "Zugangsdaten, Favoriten und Verlauf dieses Anbieters werden vom iPhone entfernt.", "Abmelden"),
    HISTORY("Verlauf löschen?", "„Zuletzt gesehen“ und „Weiterschauen“ werden geleert. Favoriten bleiben.", "Löschen"),
}

/** Aktiver Anbieter mit Umfang, weitere Anbieter zum Wechseln. Favoriten und Verlauf bleiben je Anbieter getrennt. */
@Composable
private fun Accounts(app: AppState, onLogout: () -> Unit) {
    app.accounts.forEach { saved ->
        val active = saved.id == app.activeAccountId
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !active) { app.switchAccount(saved.id) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (active) Signal else Muted, modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(saved.displayName(), maxLines = 2)
                if (active) Text(
                    "${formatCount(app.section(Section.LIVE).items.size)} Sender · ${formatCount(app.section(Section.MOVIES).items.size)} Filme · " +
                        "${formatCount(app.section(Section.SERIES).items.size)} Serien",
                    color = Muted, fontSize = 13.sp,
                )
            }
        }
    }
    FlowRow2(Modifier.padding(top = 4.dp)) {
        SecondaryButton("Listen neu laden", icon = Icons.Rounded.Refresh) { app.reloadAll() }
        SecondaryButton("Anbieter hinzufügen", icon = Icons.Rounded.Add) { app.addAccount() }
    }
    TextButton(onLogout) { Text("Abmelden", color = Danger) }
}

/** Ausgeblendete Kategorien mit „Einblenden“. Ausblenden: lange auf eine Kategorie in Live TV, Filme oder Serien drücken. */
@Composable
private fun HiddenCategories(app: AppState) {
    val hidden = Section.entries.flatMap { s -> app.hiddenCategories(s).map { s to it } }
    Text("Ausgeblendete Kategorien", Modifier.padding(top = 10.dp))
    if (hidden.isEmpty()) {
        Text("Keine. Zum Ausblenden lange auf eine Kategorie drücken.", color = Muted, fontSize = 14.sp)
        return
    }
    hidden.forEach { (section, cat) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sectionIcon(section), null, tint = Muted, modifier = Modifier.size(18.dp))
            val tagged = splitCategoryName(cat.name)
            tagged.tag?.let { FlagOrTag(it, Modifier.padding(start = 10.dp)) }
            Text(tagged.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton({ app.toggleHidden(section, cat.id); Feedback.show("Wieder eingeblendet") }) { Text("Einblenden", color = Signal) }
        }
    }
}

/** Länderfilter je Bereich. Ohne Kürzel beim Anbieter (z. B. M3U) gibt es nichts zu wählen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Countries(app: AppState, s: Section) {
    val countries = app.availableCountries(s)
    if (countries.isEmpty()) return
    val chosen = app.countries(s)
    Text("Nur Kategorien dieser Länder zeigen – nichts gewählt heißt: alle.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
    FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        countries.forEach { c ->
            de.vincentsberger.iptvplayer.ios.ui.design.Chip(
                "${c.tag} ${c.count}", c.tag in chosen, { app.toggleCountry(s, c.tag) }, small = true,
                leading = flagOf(c.tag)?.let { f -> { Text(f, fontSize = 15.sp) } },
            )
        }
    }
    if (chosen.isNotEmpty()) TextButton({ app.clearCountries(s) }) { Text("Alle Länder zeigen", color = Muted) }
}

/** Kategorien für „Neue Filme/Folgen“ der Startseite – nur Xtream; aufklappbar, weil es Hunderte sein können. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(app: AppState) {
    if (!app.supportsDashboard) return
    Text("Neue Filme und Folgen der letzten 7 Tage aus diesen Kategorien erscheinen auf der Startseite.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    var open by rememberSaveable { mutableStateOf<Section?>(null) }
    listOf(Section.MOVIES, Section.SERIES).forEach { s ->
        val options = app.dashboardCategoryOptions(s)
        if (options.isEmpty()) return@forEach
        val n = app.dashboardCategories.count { it.startsWith("${s.name}:") }
        Row(
            Modifier.fillMaxWidth().clickable { open = if (open == s) null else s }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(sectionIcon(s), null, tint = Muted, modifier = Modifier.size(18.dp))
            Text("${s.title} ($n gewählt)", Modifier.weight(1f).padding(start = 10.dp))
            Icon(if (open == s) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
        }
        if (open == s) FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { c ->
                de.vincentsberger.iptvplayer.ios.ui.design.Chip(c.name, "${s.name}:${c.id}" in app.dashboardCategories, { app.toggleDashboardCategory(s, c.id) }, small = true)
            }
        }
    }
}

fun sectionIcon(s: Section): ImageVector = when (s) {
    Section.LIVE -> Icons.Rounded.LiveTv
    Section.MOVIES -> Icons.Rounded.Movie
    Section.SERIES -> Icons.Rounded.VideoLibrary
}

/** Abschnitt wie in den iOS-Einstellungen (Entwurf „13“): Rubrik über einer gerahmten Fläche, Zeilen durch Haarlinien getrennt. */
@Composable
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    de.vincentsberger.iptvplayer.ios.ui.design.GroupLabel(title, start = 14.dp)
    Column(
        Modifier.fillMaxWidth().clip(Corner).background(Surface1).border(1.dp, Line, Corner).padding(horizontal = 14.dp, vertical = 2.dp),
        content = content,
    )
}

@Composable
fun RowDivider() = HorizontalDivider(color = Line, thickness = 1.dp)

@Composable
fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            if (hint != null) Text(hint, color = Muted, fontSize = 13.sp)
        }
        de.vincentsberger.iptvplayer.ios.ui.design.Toggle(checked, onChange, label = label)
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Reihen der Startseite ein-/ausblenden und verschieben. */
@Composable
private fun HomeRows(app: AppState) {
    app.homeRowOrder.forEachIndexed { i, row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val on = row !in app.hiddenHomeRows
            Row(Modifier.weight(1f).clickable { app.toggleHomeRow(row) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) Signal else Muted, modifier = Modifier.size(20.dp))
                Text(row.title, Modifier.padding(start = 12.dp), color = if (on) TextColor else Muted)
            }
            IconButton({ app.moveHomeRow(row, up = true) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Nach oben", tint = Muted) }
            IconButton({ app.moveHomeRow(row, up = false) }, enabled = i < app.homeRowOrder.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Nach unten", tint = Muted) }
        }
        if (i < app.homeRowOrder.lastIndex) RowDivider()
    }
    TextButton({ app.resetHomeRows() }) { Text("Zurücksetzen", color = Muted) }
}
