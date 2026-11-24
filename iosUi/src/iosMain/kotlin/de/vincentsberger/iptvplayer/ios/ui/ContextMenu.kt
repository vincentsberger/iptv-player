package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.design.ChannelLogo
import de.vincentsberger.iptvplayer.ios.ui.design.PosterCard
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.DownloadRules
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.downloadStartMessage

/**
 * Menü bei langem Drücken (Entwurf „18“, P13): statt sofort umzuschalten hebt sich der Titel an, der Hintergrund wird
 * unscharf, darunter Favorit, Herunterladen, Details und – abgesetzt – die Kategorie ausblenden. Gezeigt von
 * [ContextMenuHost] (in IosApp über allem); der Inhalt darunter wird über [isOpen] unscharf.
 */
object ContextMenu {
    var item by mutableStateOf<Item?>(null); private set
    val isOpen: Boolean get() = item != null

    fun show(item: Item) {
        this.item = item
        platform.UIKit.UIImpactFeedbackGenerator(platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
    }

    fun close() { item = null }
}

@Composable
fun ContextMenuHost(app: AppState) {
    // Inhalt merken, damit das Ausblenden nicht leer läuft.
    var shown by remember { mutableStateOf<Item?>(null) }
    ContextMenu.item?.let { shown = it }
    AnimatedVisibility(ContextMenu.isOpen, enter = fadeIn(), exit = fadeOut()) {
        val item = shown ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().background(Bg.copy(alpha = 0.6f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { ContextMenu.close() }
                .semantics { contentDescription = "Menü schließen" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AnimatedVisibility(true, enter = scaleIn(initialScale = 0.9f)) {
                    if (item.section == Section.LIVE) ChannelLogo(item.logo, item.name, size = 96.dp)
                    else PosterCard(item, item.favKey in app.favorites, { ContextMenu.close(); app.open(listOf(item), 0) }, width = 168.dp)
                }
                Menu(app, item)
            }
        }
    }
}

@Composable
private fun Menu(app: AppState, item: Item) {
    val fav = item.favKey in app.favorites
    val category = app.sections[item.section]?.categories?.firstOrNull { it.id == item.categoryId }
    val entries = buildList<Triple<String, ImageVector, () -> Unit>> {
        add(Triple(if (fav) "Aus Favoriten" else "Zu Favoriten", if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star) { toggleFavoriteWithFeedback(app, item) })
        if (DownloadRules.canDownload(item) && IosDownloads.infoFor(item.url) == null) add(Triple("Herunterladen", Icons.Rounded.DownloadForOffline) {
            Feedback.show(downloadStartMessage(IosDownloads.start(item), IosDownloads.freeBytes()) ?: "Download gestartet", Icons.Rounded.DownloadForOffline)
        })
        if (item.section != Section.LIVE) add(Triple("Details", Icons.Rounded.Info) { app.open(listOf(item), 0) })
    }
    Column(Modifier.width(250.dp).clip(Corner).background(Surface1).border(1.dp, Line, Corner)) {
        entries.forEachIndexed { i, (label, icon, action) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            MenuRow(label, icon, TextColor) { ContextMenu.close(); action() }
        }
        if (category != null) {
            // Abgesetzt: eine 6-dp-Fuge in der Fläche, weil Ausblenden die ganze Kategorie betrifft.
            Box(Modifier.fillMaxWidth().height(6.dp).background(Bg))
            val name = splitCategoryName(category.name).name
            MenuRow("„$name“ ausblenden", Icons.Rounded.VisibilityOff, Danger) {
                ContextMenu.close()
                app.setHidden(item.section, category.id, true)
                Feedback.show("Kategorie ausgeblendet", Icons.Rounded.VisibilityOff, undo = { app.setHidden(item.section, category.id, false) })
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(role = Role.Button, onClick = onClick).height(48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}
