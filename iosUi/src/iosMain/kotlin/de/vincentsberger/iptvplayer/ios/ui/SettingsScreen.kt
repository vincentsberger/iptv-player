package de.vincentsberger.iptvplayer.ios.ui

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.flagOf
import de.vincentsberger.iptvplayer.shared.labelOf

/** Einstellungen (Grundstock): Anbieter, Länder für Live TV, Sortierung, Start, Lizenzen, Version. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppState, onBack: () -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    if (licenses) { LicensesScreen(onBack = { licenses = false }); return }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Einstellungen", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Anbieter")
            app.accounts.forEach { saved ->
                val active = saved.id == app.activeAccountId
                Row(
                    Modifier.fillMaxWidth().clickable { app.switchAccount(saved.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (active) Signal else Muted, modifier = Modifier.size(20.dp),
                    )
                    Text(labelOf(saved.account), Modifier.padding(start = 12.dp), maxLines = 2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Anbieter hinzufügen", icon = Icons.Rounded.Add) { app.addAccount() }
                TextButton({ confirmLogout = true }) { Text("Abmelden", color = Danger) }
            }

            HorizontalDivider(color = Line)
            Kicker("Länder in Live TV")
            val countries = app.availableLiveCountries()
            if (countries.isEmpty()) Text("Dein Anbieter kennzeichnet keine Länder.", color = Muted, fontSize = 14.sp)
            else {
                Text("Nur Kategorien dieser Länder zeigen – nichts gewählt heißt: alle.", color = Muted, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    countries.forEach { c ->
                        FilterChip(
                            selected = c.tag in app.liveCountries,
                            onClick = { app.toggleLiveCountry(c.tag) },
                            label = { Text(listOfNotNull(flagOf(c.tag), c.tag, c.count.toString()).joinToString(" ")) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Surface2, selectedLabelColor = TextColor),
                        )
                    }
                }
                if (app.liveCountries.isNotEmpty()) TextButton({ app.clearLiveCountries() }) { Text("Alle Länder zeigen", color = Muted) }
            }

            HorizontalDivider(color = Line)
            Toggle("Listen alphabetisch sortieren", app.sortAz) { app.sortAz = it }
            Toggle("Mit dem letzten Sender starten", app.startWithLastChannel) { app.startWithLastChannel = it }

            HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth().clickable { licenses = true }.padding(vertical = 10.dp)) { Text("Lizenzen") }
            Text("IPTV Player ${appVersion()} · Updates über eine neue Installation (siehe Anleitung).", color = Muted, fontSize = 13.sp)
            Text("Diese App ist ein reiner Player und enthält keine Inhalte. Wiedergabe mit VLC (LGPL).", color = Muted, fontSize = 13.sp)
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Abmelden?") },
        text = { Text("Zugangsdaten, Favoriten und Verlauf dieses Anbieters werden vom iPhone entfernt.") },
        confirmButton = { TextButton({ confirmLogout = false; app.logout() }) { Text("Abmelden", color = Danger) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Abbrechen") } },
        containerColor = Surface1,
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Signal, checkedThumbColor = OnSignal))
    }
}
