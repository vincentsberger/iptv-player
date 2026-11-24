package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.formatBytes
import de.vincentsberger.iptvplayer.shared.DownloadStartResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.DownloadInfo
import de.vincentsberger.iptvplayer.app.download.Downloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import kotlinx.coroutines.delay

// ============================================================================================
// Tastenleiste: zeigt unten, welche Taste der Fernbedienung beim fokussierten Element was tut.
// ============================================================================================

enum class RemoteKey { OK, OK_HOLD, BACK, UP_DOWN, LEFT_RIGHT, DIGITS }

data class KeyHint(val key: RemoteKey, val label: String)

class KeyHintState {
    var hints by mutableStateOf<List<KeyHint>>(emptyList())
}

val LocalKeyHints = staticCompositionLocalOf { KeyHintState() }

/** Beim Fokussieren dieses Elements diese Tasten unten anzeigen. */
fun Modifier.keyHints(vararg hints: KeyHint): Modifier = composed {
    val state = LocalKeyHints.current
    onFocusChanged { if (it.isFocused) state.hints = hints.toList() }
}

/** Übliche Belegung einer Karte bzw. Zeile: OK öffnet, OK halten zeigt Optionen. */
fun Modifier.itemHints(item: Item): Modifier = keyHints(
    KeyHint(RemoteKey.OK, if (item.isSeriesFolder) "Folgen ansehen" else "Abspielen"),
    KeyHint(RemoteKey.OK_HOLD, "Optionen"),
)

@Composable
fun KeyHintBar(modifier: Modifier = Modifier, start: Dp = 108.dp) {
    val hints = LocalKeyHints.current.hints
    if (hints.isEmpty()) return
    Row(
        // Hintergrund erst neben der Navigationsleiste – sonst verdeckt er deren unterste Einträge.
        modifier.fillMaxWidth()
            .padding(start = (start - 24.dp).coerceAtLeast(0.dp))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.92f), Bg)))
            .padding(start = 24.dp, end = 48.dp, top = 22.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(h.key)
                Text(h.label, fontSize = 17.sp, color = TextColor, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** Gezeichnete Fernbedienungstaste: rund für OK, mit Ring für „halten“, eckig für die übrigen. */
@Composable
fun KeyCap(key: RemoteKey) {
    val text = when (key) {
        RemoteKey.OK, RemoteKey.OK_HOLD -> "OK"
        RemoteKey.UP_DOWN -> "▲▼"
        RemoteKey.LEFT_RIGHT -> "◀▶"
        RemoteKey.DIGITS -> "0–9"
        RemoteKey.BACK -> null
    }
    val round = key == RemoteKey.OK || key == RemoteKey.OK_HOLD
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .then(if (round) Modifier.size(38.dp) else Modifier.height(32.dp).widthIn(min = 40.dp))
                .then(if (key == RemoteKey.OK_HOLD) Modifier.border(2.dp, Signal, CircleShape).padding(3.dp) else Modifier)
                .background(Surface2, if (round) CircleShape else SmallCorner)
                .border(1.dp, Line, if (round) CircleShape else SmallCorner)
                .padding(horizontal = if (round) 0.dp else 8.dp),
            Alignment.Center,
        ) {
            if (text != null) Text(text, fontSize = if (round) 13.sp else 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
            else Icon(Icons.AutoMirrored.Rounded.Undo, tint = TextColor, size = 20.dp)
        }
        if (key == RemoteKey.OK_HOLD) Text("halten", fontSize = 15.sp, color = Signal, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

// ============================================================================================
// Rückmeldung: kurze Einblendung nach einer Aktion („Zu Favoriten hinzugefügt“).
// ============================================================================================

object Feedback {
    var message by mutableStateOf<Pair<String, Long>?>(null); private set
    fun show(text: String) { message = text to System.nanoTime() }
    internal fun clear() { message = null }
}

@Composable
fun BoxScope.FeedbackToast() {
    val msg = Feedback.message
    LaunchedEffect(msg) { if (msg != null) { delay(2600); Feedback.clear() } }
    AnimatedVisibility(
        msg != null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
    ) {
        Text(
            msg?.first.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Bg,
            modifier = Modifier.background(TextColor, Corner).padding(horizontal = 24.dp, vertical = 12.dp).testTag("feedback"),
        )
    }
}

// ============================================================================================
// Optionen-Menü (OK halten): Favorit, Herunterladen, Ausblenden – sichtbar statt versteckt.
// ============================================================================================

data class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

data class OptionsMenu(val title: String, val subtitle: String? = null, val actions: List<MenuAction>)

class MenuState {
    var current by mutableStateOf<OptionsMenu?>(null)
}

val LocalMenu = staticCompositionLocalOf { MenuState() }

/** Optionen für einen Sender, Film, eine Serie oder Folge. */
fun itemMenu(vm: AppState, item: Item): OptionsMenu {
    val fav = item.favKey in vm.favorites
    val dl = Downloads.infoFor(item.url)
    val actions = buildList {
        add(MenuAction(if (fav) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen", if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star) {
            vm.toggleFavorite(item)
            Feedback.show(if (fav) "Aus Favoriten entfernt" else "Zu Favoriten hinzugefügt")
        })
        if (Downloads.canDownload(item)) when {
            dl == null -> add(MenuAction("Herunterladen", Icons.Rounded.Download) {
                Feedback.show(
                    when (Downloads.start(item)) {
                        DownloadStartResult.STARTED -> "Download gestartet – Fortschritt unter „Downloads“"
                        DownloadStartResult.NO_SPACE -> "Zu wenig Speicher frei (noch ${formatBytes(Downloads.freeBytes())})"
                        DownloadStartResult.NOT_POSSIBLE -> "Dieser Titel lässt sich nicht herunterladen"
                        DownloadStartResult.ALREADY -> "Ist schon in den Downloads"
                    }
                )
            })
            dl.state == DownloadInfo.State.DONE -> add(MenuAction("Download löschen", Icons.Rounded.Delete, danger = true) {
                Downloads.remove(item.url); Feedback.show("Download gelöscht")
            })
            else -> add(MenuAction("Download abbrechen", Icons.Rounded.Delete, danger = true) {
                Downloads.remove(item.url); Feedback.show("Download abgebrochen")
            })
        }
    }
    val sub = when (item.section) { Section.LIVE -> "Sender"; Section.MOVIES -> "Film"; Section.SERIES -> if (item.isSeriesFolder) "Serie" else "Folge" }
    return OptionsMenu(splitProviderTag(item.name).name, sub, actions)
}

/** Optionen für eine Kategorie des Anbieters. */
fun categoryMenu(vm: AppState, section: Section, category: Category, afterHide: () -> Unit = {}): OptionsMenu =
    OptionsMenu(
        splitCategoryName(category.name).name, "Kategorie",
        listOf(MenuAction("Kategorie ausblenden", Icons.Rounded.VisibilityOff) {
            vm.toggleHidden(section, category.id); afterHide()
            Feedback.show("Ausgeblendet – wieder einblenden unter Einstellungen")
        }),
    )

/** Hält Menü, Tastenleiste und Rückmeldung für die ganze App bereit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvChrome(content: @Composable () -> Unit) {
    val menu = remember { MenuState() }
    val hints = remember { KeyHintState() }
    val clearance = with(LocalDensity.current) { HintBarClearance.toPx() }
    val scrollSpec = remember(clearance) { ClearOfHintBar(clearance) }
    CompositionLocalProvider(LocalMenu provides menu, LocalKeyHints provides hints, LocalBringIntoViewSpec provides scrollSpec) {
        Box {
            content()
            FeedbackToast()
        }
        menu.current?.let { m -> OptionsDialog(m) { menu.current = null } }
    }
}

@Composable
private fun OptionsDialog(menu: OptionsMenu, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    // Das Menü öffnet sich, während OK noch gehalten wird. Bis die Taste losgelassen (oder neu gedrückt) wird,
    // schlucken wir alles – sonst löst die Tastenwiederholung den ersten Eintrag aus.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { ev ->
                    if (armed) return@onPreviewKeyEvent false
                    if (ev.type == KeyEventType.KeyUp) { armed = true; return@onPreviewKeyEvent true }
                    if (ev.nativeKeyEvent.repeatCount == 0) { armed = true; return@onPreviewKeyEvent false }
                    true
                }
                .width(460.dp).panel().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            menu.subtitle?.let { Kicker(it) }
            Headline(menu.title, 24.sp, maxLines = 2)
            menu.actions.forEachIndexed { i, a ->
                TvButton(
                    a.label,
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier).testTag("menu:$i"),
                    icon = a.icon,
                    style = if (a.danger) ButtonStyle.Danger else ButtonStyle.Secondary,
                ) { onDismiss(); a.onClick() }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyCap(RemoteKey.BACK)
                Text("Schließen", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/**
 * Scrollen bei Fokuswechsel: Ein Element gilt erst als sichtbar, wenn es über der Tastenleiste steht – sonst läge die
 * fokussierte Karte nach dem Wechsel in eine Liste halb unter „OK Abspielen“. Gilt für alle Listen und Raster (in
 * Querreihen bleibt die Karte entsprechend weit vom rechten Rand, die nächste schaut hervor).
 * Sonst wie der Compose-Standard: nur so weit wie nötig scrollen, zu große Elemente am Anfang ausrichten.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ClearOfHintBar(private val clearancePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visible = containerSize - clearancePx
        val trailing = offset + size
        return when {
            size > visible -> offset
            offset >= 0 && trailing <= visible -> 0f
            offset < 0 -> offset
            else -> trailing - visible
        }
    }
}
