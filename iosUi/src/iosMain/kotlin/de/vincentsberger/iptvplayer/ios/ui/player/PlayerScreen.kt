package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.runtime.Composable
import de.vincentsberger.iptvplayer.ios.ui.ComingSoon
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item

@Composable
fun PlayerScreen(app: AppState, startItems: List<Item>, startIndex: Int, startPosition: Long, newPlayer: () -> PlayerEngine, onBack: () -> Unit) =
    ComingSoon("Player")
