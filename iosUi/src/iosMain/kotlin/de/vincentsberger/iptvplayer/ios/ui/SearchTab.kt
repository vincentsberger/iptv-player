package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.stableKeys

/**
 * Suche über Live TV, Filme und Serien (ab zwei Buchstaben, je Bereich höchstens 60 Treffer – siehe
 * [AppState.searchResults]). Sender erscheinen als Liste mit Programm, Filme und Serien als Posterreihe.
 */
@Composable
fun SearchTab(app: AppState) {
    val results = app.searchResults
    val loading = Section.entries.any { app.section(it).loading }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // Leeres Feld: gleich tippen können. Mit altem Suchbegriff nicht – sonst verdeckt die Tastatur die Treffer.
    LaunchedEffect(Unit) { if (app.query.isEmpty()) focus.requestFocus() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Headline("Suche", 26.sp, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        OutlinedTextField(
            app.query, { app.query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            placeholder = { Text("Sender, Filme oder Serien", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
            trailingIcon = if (app.query.isEmpty()) null else {
                { IconButton({ app.query = ""; focus.requestFocus() }) { Icon(Icons.Rounded.Close, "Leeren", tint = Muted) } }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); app.rememberSearch() }),
            shape = Corner,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal, unfocusedBorderColor = Line, cursorColor = Signal),
        )
        when {
            app.query.trim().length < 2 -> Column {
                RecentSearches(app)
                EmptyState(
                    Icons.Rounded.Search, "Was möchtest du sehen?",
                    "Mindestens zwei Buchstaben – gesucht wird in Live TV, Filmen und Serien.",
                )
            }
            results.isEmpty() && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Signal, trackColor = Line) }
            results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "Keine Treffer", "Versuche einen anderen Suchbegriff.")
            else -> SectionedItems(app, results.toList(), countInTitle = true, onOpen = { keyboard?.hide(); app.rememberSearch() })
        }
    }
}

/**
 * Treffer bzw. Favoriten nach Bereich: Sender als Zeilen (mit „Jetzt“), Filme und Serien als waagerechte Posterreihe.
 * Tippen öffnet wie überall (Sender spielen, Film → Detailseite, Serie → Staffeln); langes Drücken = Favorit.
 */
@Composable
fun SectionedItems(
    app: AppState,
    rows: List<Pair<Section, List<Item>>>,
    countInTitle: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        rows.forEach { (section, list) ->
            item(key = "title:${section.name}") {
                Kicker(
                    if (countInTitle) "${section.title} (${list.size})" else section.title,
                    Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp),
                )
            }
            val keys = stableKeys(list)
            if (section == Section.LIVE) {
                itemsIndexed(list, key = { i, _ -> "live:${keys[i]}" }) { i, item ->
                    ChannelRow(app, item, number = item.number, selected = false, onClick = { onOpen(); app.open(list, i) })
                }
            } else {
                item(key = "row:${section.name}") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(list, key = { i, _ -> keys[i] }) { i, item ->
                            PosterTile(
                                item, app.progressOf(item), item.favKey in app.favorites,
                                onClick = { onOpen(); app.open(list, i) },
                                onLongClick = { toggleFavoriteWithFeedback(app, item) },
                                modifier = Modifier.width(108.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Zuletzt gesuchte Begriffe als Chips – Tippen sucht wieder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(app: AppState) {
    if (app.recentSearches.isEmpty()) return
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Zuletzt gesucht", Modifier.weight(1f))
            TextButton({ app.clearRecentSearches() }) { Text("Löschen", color = Muted, fontSize = 13.sp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            app.recentSearches.forEach { term ->
                Text(
                    term, color = TextColor, fontSize = 14.sp,
                    modifier = Modifier.clip(Corner).background(Surface1).clickable { app.query = term }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
