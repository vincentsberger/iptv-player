package de.vincentsberger.iptvplayer.app.ui

import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import androidx.compose.foundation.background
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

/**
 * Senderliste über dem laufenden Bild (Live TV): links die Kategorien, rechts die Sender der
 * fokussierten Kategorie mit „Jetzt läuft“. Der Stream läuft weiter, bis man mit OK einen Sender wählt.
 */
@Composable
fun ChannelGuide(
    vm: AppState,
    playing: Item,
    /** Kategorie, aus der der Player gestartet wurde (Startpunkt der Liste). */
    startCategory: String,
    onPick: (List<Item>, Int) -> Unit,
    /** Vergangene Sendung mit Archiv aus dem Raster nachträglich abspielen. */
    onCatchUp: (Item, EpgEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start in der Kategorie des laufenden Senders (sonst dort, wo man in Live TV zuletzt war).
    var browsing by remember {
        mutableStateOf(playing.categoryId.takeIf { id -> vm.section(Section.LIVE).categories.any { it.id == id } } ?: startCategory)
    }
    val specials = remember(vm.history.isNotEmpty()) {
        buildList {
            if (vm.history.any { it.item.section == Section.LIVE }) add(Category(AppState.LIVE_RECENT, "Zuletzt gesehen"))
            add(Category(AppState.LIVE_FAV, "Favoriten"))
            add(Category(AppState.LIVE_ALL, "Alle Sender"))
        }
    }
    val categories = vm.section(Section.LIVE).categories
    val channels = vm.liveChannelsOf(browsing)
    // Beim Öffnen steht der Fokus im Raster auf der laufenden Sendung des laufenden Senders.
    val gridFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var gridFocused by remember { mutableStateOf(false) }
    var okLabel by remember { mutableStateOf("Umschalten") }
    LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }
    // Raster braucht Platz: Kategorien schmaler, solange der Fokus im Raster ist.
    val categoryWidth by animateDpAsState(if (gridFocused) 200.dp else 290.dp, label = "categories")

    Column(
        modifier.fillMaxSize()
            .background(Brush.horizontalGradient(0f to Shade.copy(alpha = 0.96f), 1f to Shade.copy(alpha = 0.88f)))
            .padding(start = 40.dp, top = 32.dp, bottom = 24.dp, end = 64.dp),
    ) {
    Row(Modifier.weight(1f)) {
        // Kategorien und Länder: Fokus wählt aus, die Senderliste rechts folgt.
        Column(Modifier.width(categoryWidth).fillMaxHeight()) {
            Kicker("Kategorien")
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                @Composable
                fun Entry(cat: Category) = CategoryItem(
                    name = cat.name, selected = cat.id == browsing,
                    modifier = (if (cat.id == browsing) Modifier.focusRequester(categoryFocus) else Modifier).testTag("guide:category:${cat.id}"),
                    onFocus = { browsing = cat.id },
                )
                items(specials + categories, key = { it.id }) { Entry(it) }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Kicker(splitCategoryName(specials.plus(categories).firstOrNull { it.id == browsing }?.name.orEmpty()).name)
            Text("${channels.size} Sender", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (channels.isEmpty()) {
                Text("Keine Sender in dieser Kategorie.", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
            GuideGrid(
                vm, channels, playing,
                onSwitch = { i -> onPick(channels, i) },
                onCatchUp = onCatchUp,
                onExitLeft = { runCatching { categoryFocus.requestFocus() }.onFailure { focusManager.moveFocus(FocusDirection.Left) } },
                focusRequester = gridFocus,
                onOkLabel = { okLabel = it },
                modifier = Modifier.weight(1f).fillMaxWidth().onFocusChanged { gridFocused = it.hasFocus },
            )
        }
    }
    // Tastenhinweise unter beiden Spalten – dort ist genug Platz, nichts bricht um.
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(RemoteKey.UP_DOWN to "Sender", RemoteKey.LEFT_RIGHT to "Zeit", RemoteKey.OK to okLabel, RemoteKey.BACK to "Schließen").forEach { (k, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(k)
                Text(t, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
    }
}
