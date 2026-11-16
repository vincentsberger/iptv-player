package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import de.vincentsberger.iptvplayer.shared.ChannelNumberInput
import kotlinx.coroutines.delay
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.LocalContentColor
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.delay
import java.util.Calendar

/** Live TV in drei Spalten: Kategorien · Senderliste · Info mit „Jetzt/Danach“. */
@Composable
fun LiveTvScreen(vm: AppState) {
    val state = vm.section(Section.LIVE)
    when {
        state.loading -> LiveSkeleton()
        state.error != null -> ErrorView(state.error.orEmpty()) { vm.reloadAll() }
        else -> LiveContent(vm, state.categories)
    }
}

@Composable
private fun LiveContent(vm: AppState, categories: List<Category>) {
    val channels = vm.liveChannels
    var focused by remember { mutableStateOf<Item?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val restoreKey = remember { vm.focusKey }
    val restore = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val channelState = rememberLazyListState()

    val allCategories = remember(categories, vm.history.isNotEmpty()) {
        buildList {
            if (vm.history.any { it.item.section == Section.LIVE }) add(Category(AppState.LIVE_RECENT, "Zuletzt gesehen"))
            add(Category(AppState.LIVE_FAV, "Favoriten"))
            add(Category(AppState.LIVE_ALL, "Alle Sender"))
            addAll(categories)
        }
    }
    val categoryName = allCategories.firstOrNull { it.id == vm.liveCategory }?.name ?: ""

    LaunchedEffect(Unit) {
        delay(120)
        val ok = restoreKey != null && runCatching { restore.requestFocus() }.isSuccess
        if (!ok) runCatching { categoryFocus.requestFocus() }
    }

    LaunchedEffect(focused) {
        epg = emptyList()
        val item = focused ?: return@LaunchedEffect
        delay(350)
        epg = vm.nowPlaying(item)
    }

    // Spalten als Verhältnis (Kategorien : Sender : Info = 0,8 : 1,5 : 1,1) mit Obergrenzen –
    // auf 1080p-Fernsehern stehen nur 960 dp Breite zur Verfügung.
    // Sendernummer direkt in der Liste eintippen: nach kurzer Pause startet der Sender.
    val numberInput = remember { ChannelNumberInput() }
    LaunchedEffect(numberInput.digits) {
        if (numberInput.digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        numberInput.take()?.let { n -> ChannelNumberInput.indexFor(n, channels.size)?.let { vm.open(channels, it) } }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
        val d = if (ev.type == KeyEventType.KeyDown) digitOf(ev.key) else null
        if (d != null) numberInput.type(d)
        d != null
    }) {
    if (numberInput.digits.isNotEmpty()) {
        Text(
            numberInput.digits + "_", color = Color.White, fontFamily = Mono, fontSize = 44.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 64.dp).zIndex(1f).panel().padding(horizontal = 18.dp, vertical = 4.dp),
        )
    }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 27.dp, end = 48.dp, bottom = 24.dp)) {
        // Spalte 1: Kategorien
        Column(Modifier.weight(0.8f).widthIn(max = 270.dp).fillMaxHeight()) {
            TitleWithIcon("Live TV", Icons.Rounded.LiveTv, 30.sp)
            Text("${vm.section(Section.LIVE).items.size} Sender", color = Muted, fontSize = 15.sp)
            Text("0–9 Sender wählen · OK halten blendet aus", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(allCategories) { _, cat ->
                    CategoryItem(
                        name = cat.name,
                        selected = cat.id == vm.liveCategory,
                        modifier = (if (cat.id == vm.liveCategory) Modifier.focusRequester(categoryFocus) else Modifier).testTag("category:${cat.id}"),
                        onHide = if (cat.id.startsWith("__")) null else ({ vm.toggleHidden(Section.LIVE, cat.id); vm.liveCategory = AppState.LIVE_ALL }),
                        onFocus = { vm.liveCategory = cat.id },
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Spalte 2: Sender
        Column(Modifier.weight(1.5f).fillMaxHeight()) {
            Headline(splitProviderTag(categoryName).name, 22.sp)
            Text("${channels.size} Sender", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
            if (channels.isEmpty()) {
                EmptyView(
                    if (vm.liveCategory == AppState.LIVE_FAV) "Noch keine Favoriten" else "Keine Sender",
                    if (vm.liveCategory == AppState.LIVE_FAV) "Halte OK auf einem Sender gedrückt, um ihn hier zu speichern." else "In dieser Kategorie gibt es keine Sender.",
                    if (vm.liveCategory == AppState.LIVE_FAV) Icons.Rounded.StarBorder else Icons.Rounded.LiveTv,
                )
            } else {
                LazyColumn(state = channelState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                    itemsIndexed(channels) { i, ch ->
                        val key = "live:${vm.liveCategory}:$i"
                        ChannelRow(
                            number = i + 1,
                            item = ch,
                            favorite = ch.favKey in vm.favorites,
                            modifier = (if (key == restoreKey) Modifier.focusRequester(restore) else Modifier).testTag("channel:$i"),
                            onFocus = { focused = ch; vm.focusKey = key },
                            onClick = { vm.open(channels, i) },
                            onLongClick = { vm.toggleFavorite(ch) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // Spalte 3: Info
        InfoPanel(focused, epg, focused?.let { it.favKey in vm.favorites } == true, Modifier.weight(1.1f).widthIn(max = 380.dp).fillMaxHeight())
    }
    }
}

/** Kategorie-Eintrag (auch für Filme/Serien): Fokus wählt die Kategorie aus. */
@Composable
internal fun CategoryItem(
    name: String, selected: Boolean, modifier: Modifier, icon: ImageVector? = null,
    /** Langes Drücken auf OK: Kategorie ausblenden (nur Anbieter-Kategorien, sonst null). */
    onHide: (() -> Unit)? = null,
    onFocus: () -> Unit,
) {
    Surface(
        onClick = onFocus,
        onLongClick = onHide,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Surface1 else Color.Transparent,
            contentColor = if (selected) TextColor else Muted,
            focusedContainerColor = FocusBg,
            focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Auswahl nie nur über die Fläche: roter Strich links.
            Box(Modifier.width(3.dp).height(20.dp).background(if (selected) Signal else Color.Transparent))
            if (icon != null) Icon(icon, Modifier.padding(start = 12.dp), size = 18.dp)
            val tagged = splitProviderTag(name)
            tagged.tag?.let { TagChip(it, Modifier.padding(start = 12.dp)) }
            Text(
                // Anbieter-Kategorien sind oft lang („DE | SPORT ▸ FUSSBALL & LIVE-EVENTS“) → zwei Zeilen.
                tagged.name, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            )
        }
    }
}

@Composable
private fun ChannelRow(
    number: Int,
    item: Item,
    favorite: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Surface1, contentColor = TextColor,
            focusedContainerColor = FocusBg, focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString().padStart(2, '0'), fontSize = 15.sp, fontFamily = Mono, color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.width(32.dp))
            Box(Modifier.width(72.dp).height(44.dp), Alignment.Center) {
                ChannelLogo(item, 40.dp)
            }
            val tagged = splitProviderTag(item.name)
            tagged.tag?.let { TagChip(it, Modifier.padding(start = 12.dp)) }
            Text(
                tagged.name, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (tagged.tag != null) 10.dp else 16.dp),
            )
            if (favorite) Icon(Icons.Rounded.Star, tint = Signal, size = 20.dp)
        }
    }
}

@Composable
private fun InfoPanel(item: Item?, epg: List<EpgEntry>, favorite: Boolean, modifier: Modifier) {
    val ambient = LocalAmbient.current
    LaunchedEffect(item) { ambient.url = item?.logo }
    Column(modifier.panel().padding(24.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Bg.copy(alpha = 0.6f), Corner),
            Alignment.Center,
        ) {
            if (item != null) ChannelLogo(item, 110.dp) else Masthead(20.sp)
        }
        Spacer(Modifier.height(20.dp))
        if (item == null) {
            Headline("Wähle einen Sender", 22.sp, maxLines = 2)
            Text("Mit ▲ ▼ durch die Liste, OK zum Ansehen.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            InfoDetails(item, epg, favorite)
        }
    }
}

@Composable
private fun ColumnScope.InfoDetails(item: Item, epg: List<EpgEntry>, favorite: Boolean) {
        Headline(splitProviderTag(item.name).name, 24.sp, maxLines = 2)
        if (favorite) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.Star, tint = Signal, size = 16.dp)
            Text("Favorit", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        val now = epg.getOrNull(0)
        if (now != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Kicker("Jetzt live", Signal, Modifier.padding(start = 8.dp))
            }
            Text(now.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(now.start, color = Muted, fontSize = 13.sp, fontFamily = Mono)
                ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 10.dp))
                Text(now.end, color = Muted, fontSize = 13.sp, fontFamily = Mono)
            }
            epg.getOrNull(1)?.let { next ->
                Kicker("Danach", modifier = Modifier.padding(top = 18.dp))
                Text("${next.start}  ${next.title}", fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        } else {
            Text("Keine Programminformationen", color = Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("OK  ansehen     ·     OK halten  Favorit", color = Muted, fontSize = 13.sp)
}

private fun minutesOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/** Fortschritt der laufenden Sendung anhand der Uhrzeiten „HH:mm“; [nowMinutes] = Minuten seit Mitternacht. */
internal fun epgProgress(e: EpgEntry, nowMinutes: Int = minutesOfDay()): Float {
    fun minutes(s: String): Int? = s.split(":").takeIf { it.size >= 2 }?.let { p ->
        val h = p[0].toIntOrNull(); val m = p[1].toIntOrNull()
        if (h == null || m == null) null else h * 60 + m
    }
    val start = minutes(e.start) ?: return 0f
    var end = minutes(e.end) ?: return 0f
    var now = nowMinutes
    // Nur Sendungen über Mitternacht (z. B. 23:30–00:30) verschieben Ende und „jetzt“ in den nächsten Tag.
    // Sonst bedeutet „jetzt vor Beginn“ schlicht: noch nicht angefangen (0 %).
    if (end <= start) {
        end += 24 * 60
        if (now < start) now += 24 * 60
    }
    return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

@Composable
private fun LiveSkeleton() {
    Row(Modifier.fillMaxSize().padding(start = 28.dp, top = 32.dp, end = 40.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(0.8f).widthIn(max = 270.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Shimmer(Modifier.width(160.dp).height(40.dp))
            repeat(9) { Shimmer(Modifier.fillMaxWidth().height(42.dp)) }
        }
        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Shimmer(Modifier.width(220.dp).height(40.dp))
            repeat(8) { Shimmer(Modifier.fillMaxWidth().height(64.dp)) }
        }
        Shimmer(Modifier.weight(1.1f).widthIn(max = 380.dp).fillMaxHeight())
    }
}
