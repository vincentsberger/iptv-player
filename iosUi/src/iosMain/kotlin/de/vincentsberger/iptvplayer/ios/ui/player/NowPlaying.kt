package de.vincentsberger.iptvplayer.ios.ui.player

import platform.Foundation.NSNumber
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * Sperrbildschirm und Kontrollzentrum: zeigt, was läuft, und nimmt Abspielen/Pause sowie Weiter/Zurück entgegen
 * (Live: Senderwechsel; Film/Folge: 30 s vor / 10 s zurück wie der Doppeltipp). Ein Player-Bildschirm bindet die
 * Befehle beim Öffnen und gibt sie beim Schließen wieder frei.
 */
internal object NowPlaying {
    private val bound = mutableListOf<Pair<MPRemoteCommand, Any>>()

    fun bind(onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
        unbind()
        val c = MPRemoteCommandCenter.sharedCommandCenter()
        add(c.togglePlayPauseCommand, onToggle)
        add(c.playCommand, onToggle)
        add(c.pauseCommand, onToggle)
        add(c.nextTrackCommand, onNext)
        add(c.previousTrackCommand, onPrevious)
    }

    private fun add(command: MPRemoteCommand, action: () -> Unit) {
        command.enabled = true
        val target = command.addTargetWithHandler { _ ->
            try { action(); MPRemoteCommandHandlerStatusSuccess } catch (e: Exception) { MPRemoteCommandHandlerStatusCommandFailed }
        }
        bound += command to target
    }

    /** Anzeige aktualisieren; [positionMs]/[durationMs] nur bei Filmen und Folgen. */
    fun update(title: String, subtitle: String?, isLive: Boolean, playing: Boolean, positionMs: Long, durationMs: Long) {
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to title,
            MPNowPlayingInfoPropertyIsLiveStream to NSNumber(bool = isLive),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (playing) 1.0 else 0.0),
        )
        subtitle?.let { info[MPMediaItemPropertyArtist] = it }
        if (!isLive && durationMs > 0) {
            info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = durationMs / 1000.0)
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = NSNumber(double = positionMs / 1000.0)
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    fun unbind() {
        bound.forEach { (command, target) -> command.removeTarget(target) }
        bound.clear()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }
}
