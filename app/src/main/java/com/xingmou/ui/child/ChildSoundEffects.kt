package com.xingmou.ui.child

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * 儿童答题音效：AudioTrack 现场合成柔和的双音提示音，不依赖音频素材文件。
 * 答对 = 上行明快双音；答错 = 下行低柔双音（不刺耳、不吓人）。
 */
class ChildSoundEffects {

    companion object {
        private const val TAG = "XingmouSound"
    }

    private var track: AudioTrack? = null

    /** 答对：880Hz → 1174Hz（A5 → D6），明快不吵。 */
    fun correct() = play(chime(first = 880.0, second = 1174.7, noteSeconds = 0.14, gain = 0.50))

    /** 答错：392Hz → 330Hz（G4 → E4），低柔短促。 */
    fun wrong() = play(chime(first = 392.0, second = 329.6, noteSeconds = 0.16, gain = 0.32))

    private fun chime(first: Double, second: Double, noteSeconds: Double, gain: Double): ShortArray {
        val sampleRate = 22_050
        val noteSamples = (sampleRate * noteSeconds).toInt()
        val pcm = ShortArray(noteSamples * 2)
        for (i in pcm.indices) {
            val note = i / noteSamples
            val freq = if (note == 0) first else second
            val local = (i % noteSamples).toDouble() / sampleRate
            // 10ms 起音 + 60ms 收尾包络，听感圆润
            val attack = (local / 0.010).coerceAtMost(1.0)
            val release = ((noteSeconds - local) / 0.060).coerceAtMost(1.0)
            val envelope = attack * release * gain
            pcm[i] = (sin(2 * PI * freq * local) * envelope * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    private fun play(pcm: ShortArray) {
        release()
        runCatching {
            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(22_050)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
            newTrack.write(pcm, 0, pcm.size)
            newTrack.play()
            track = newTrack
        }.onFailure { android.util.Log.e(TAG, "chime play failed", it) }
    }

    fun release() {
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }
}
