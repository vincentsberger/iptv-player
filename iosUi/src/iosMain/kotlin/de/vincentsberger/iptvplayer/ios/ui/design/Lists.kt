package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor

/** Gruppe von Zeilen auf surface1 mit Rand (phone.css .grp); zwischen den Zeilen eine Haarlinie. */
@Composable
fun ListGroup(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(Corner).background(Surface1).border(1.dp, Line, Corner)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = Line, thickness = 1.dp)
            row()
        }
    }
}

/** Symbol links in Zeilen einer [ListGroup]: 20 dp, gedämpft. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = Muted) = Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))

/** Zeile einer [ListGroup] (phone.css .li): min. 50 dp, links [leading], rechts optional [value] (gedämpft). */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = TextColor,
    valueColor: Color = Muted,
    valueMono: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp).let { if (onClick != null) it.pressable(onClick = onClick) else it }
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 16.sp, lineHeight = 21.sp)
            if (subtitle != null) SmallText(subtitle)
        }
        if (value != null) {
            if (valueMono) MonoText(value, color = valueColor, size = 14.sp)
            else Text(value, color = valueColor, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
