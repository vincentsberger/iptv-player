package de.vincentsberger.iptvplayer.app.ui

import de.vincentsberger.iptvplayer.shared.channelNumberOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier,
) {
    val menu = LocalMenu.current
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
    val playingIndex = channels.indexOfFirst { it.url == playing.url }

    // Beim Öffnen steht der Fokus auf dem laufenden Sender (falls er in der Liste ist), sonst auf dem ersten.
    val channelFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (playingIndex - 2).coerceAtLeast(0))
    LaunchedEffect(Unit) { runCatching { channelFocus.requestFocus() } }

    Column(
        modifier.fillMaxHeight()
            .background(Brush.horizontalGradient(0f to Color(0xF2101317), 0.75f to Color(0xD9101317), 1f to Color.Transparent))
            .padding(start = 40.dp, top = 32.dp, bottom = 24.dp, end = 64.dp),
    ) {
    Row(Modifier.weight(1f)) {
        // Kategorien und Länder: Fokus wählt aus, die Senderliste rechts folgt.
        Column(Modifier.width(290.dp).fillMaxHeight()) {
            Kicker("Kategorien")
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                @Composable
                fun Entry(cat: Category) = CategoryItem(
                    name = cat.name, selected = cat.id == browsing,
                    modifier = Modifier.testTag("guide:category:${cat.id}"),
                    onFocus = { browsing = cat.id },
                )
                items(specials + categories, key = { it.id }) { Entry(it) }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.width(580.dp).fillMaxHeight()) {
            Kicker(splitProviderTag(specials.plus(categories).firstOrNull { it.id == browsing }?.name.orEmpty()).name)
            Text("${channels.size} Sender", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (channels.isEmpty()) {
                Text("Keine Sender in dieser Kategorie.", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
            LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(channels, key = { i, ch -> "$i:${ch.url}" }) { i, ch ->
                    LaunchedEffect(ch.url) { vm.requestEpg(ch) }
                    val focusHere = if (playingIndex >= 0) i == playingIndex else i == 0
                    ChannelRow(
                        number = channelNumberOf(ch, channels), item = ch, now = vm.nowFor(ch), favorite = ch.favKey in vm.favorites,
                        modifier = (if (focusHere) Modifier.focusRequester(channelFocus) else Modifier).testTag("guide:channel:$i"),
                        onFocus = {}, onClick = { onPick(channels, i) },
                        onLongClick = { menu.current = itemMenu(vm, ch) },
                    )
                }
            }
        }
    }
    // Tastenhinweise unter beiden Spalten – dort ist genug Platz, nichts bricht um.
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(RemoteKey.UP_DOWN to "Blättern", RemoteKey.LEFT_RIGHT to "Kategorien / Sender", RemoteKey.OK to "Umschalten", RemoteKey.BACK to "Schließen").forEach { (k, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(k)
                Text(t, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
    }
}
