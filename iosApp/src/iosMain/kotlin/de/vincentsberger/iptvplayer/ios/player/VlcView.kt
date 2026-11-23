package de.vincentsberger.iptvplayer.ios.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import vlckit.VLCMedia
import vlckit.VLCMediaPlayer
import vlckit.VLCMediaPlayerDelegateProtocol
import vlckit.VLCMediaPlayerStateBuffering
import vlckit.VLCMediaPlayerStateEnded
import vlckit.VLCMediaPlayerStateError
import vlckit.VLCMediaPlayerStateOpening
import vlckit.VLCMediaPlayerStatePlaying
import vlckit.VLCMediaPlayerStateStopped

enum class PlayerState { OPENING, BUFFERING, PLAYING, ERROR, ENDED }

/** Übersetzt die Zustandsmeldungen von VLCKit (kommen auf dem Main-Thread). */
private class StateDelegate(
    private val player: VLCMediaPlayer,
    private val onState: (PlayerState) -> Unit,
) : NSObject(), VLCMediaPlayerDelegateProtocol {
    override fun mediaPlayerStateChanged(aNotification: NSNotification) {
        val state = when (player.state) {
            VLCMediaPlayerStateOpening -> PlayerState.OPENING
            VLCMediaPlayerStateBuffering -> PlayerState.BUFFERING
            VLCMediaPlayerStatePlaying -> PlayerState.PLAYING
            VLCMediaPlayerStateError -> PlayerState.ERROR
            VLCMediaPlayerStateEnded, VLCMediaPlayerStateStopped -> PlayerState.ENDED
            else -> return
        }
        onState(state)
    }
}

/**
 * Spielt [url] über MobileVLCKit in einer eingebetteten UIView. Ton läuft auch bei Stummschalter (Audio-Session
 * „Playback“), der Bildschirm bleibt an, solange abgespielt wird. Neue [url] = neuer Stream im selben Player.
 */
@Composable
fun VlcView(url: String, modifier: Modifier = Modifier, onState: (PlayerState) -> Unit = {}) {
    val currentOnState by rememberUpdatedState(onState)
    val player = remember { VLCMediaPlayer() }
    // VLCKit hält den Delegate nur schwach – hier stark festhalten.
    val delegate = remember {
        StateDelegate(player) { state ->
            UIApplication.sharedApplication.idleTimerDisabled = state == PlayerState.PLAYING
            currentOnState(state)
        }
    }

    DisposableEffect(player) {
        player.delegate = delegate
        onDispose {
            player.stop()
            player.delegate = null
            player.drawable = null
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }

    LaunchedEffect(url) {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            currentOnState(PlayerState.ERROR)
        } else {
            player.media = VLCMedia(uRL = nsUrl)
            player.play()
        }
    }

    UIKitView(
        factory = { UIView().apply { backgroundColor = UIColor.blackColor }.also { player.drawable = it } },
        modifier = modifier,
    )
}
