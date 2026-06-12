package de.minitraxx.app.audio

/**
 * Dünner JNI-Wrapper um die native Oboe-Engine.
 * Frame-Basis: 48 kHz (kanonisches Format aller Stems).
 */
object NativeEngine {

    const val SAMPLE_RATE = 48_000

    init {
        System.loadLibrary("minitraxx")
    }

    fun start(): Boolean = nativeStart()
    fun shutdown() = nativeShutdown()

    /**
     * Lädt einen Song. [paths] hat 5 Einträge: Index 0..3 Instrument-Stems,
     * Index 4 Click/Cue. Nicht belegte Slots sind null.
     * @return Gesamtlänge in Frames oder -1 bei Fehler.
     */
    fun loadSong(paths: Array<String?>, gains: FloatArray): Long {
        require(paths.size == 5 && gains.size == 5)
        return nativeLoadSong(paths, gains)
    }

    fun unloadSong() = nativeUnloadSong()
    fun play() = nativePlay()
    fun pause() = nativePause()
    fun stop() = nativeStop()
    fun seek(frame: Long) = nativeSeek(frame)
    fun positionFrames(): Long = nativeGetPosition()
    fun isPlaying(): Boolean = nativeIsPlaying()
    fun isFinished(): Boolean = nativeIsFinished()
    fun clearFinished() = nativeClearFinished()
    fun hadStreamError(): Boolean = nativeHadStreamError()
    fun setBusGains(main: Float, cue: Float) = nativeSetBusGains(main, cue)
    fun setSwapSides(swap: Boolean) = nativeSetSwapSides(swap)

    private external fun nativeStart(): Boolean
    private external fun nativeShutdown()
    private external fun nativeLoadSong(paths: Array<String?>, gains: FloatArray): Long
    private external fun nativeUnloadSong()
    private external fun nativePlay()
    private external fun nativePause()
    private external fun nativeStop()
    private external fun nativeSeek(frame: Long)
    private external fun nativeGetPosition(): Long
    private external fun nativeIsPlaying(): Boolean
    private external fun nativeIsFinished(): Boolean
    private external fun nativeClearFinished()
    private external fun nativeHadStreamError(): Boolean
    private external fun nativeSetBusGains(main: Float, cue: Float)
    private external fun nativeSetSwapSides(swap: Boolean)
}
