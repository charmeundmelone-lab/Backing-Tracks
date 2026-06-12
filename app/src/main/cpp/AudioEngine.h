#pragma once

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

#include "StemSource.h"

// Geladener Song: bis zu 4 Instrument-Stems (Bus MAIN -> links)
// und 1 Click/Cue-Stem (Bus CUE -> rechts).
struct LoadedSong {
    std::vector<std::unique_ptr<StemSource>> instruments;
    std::unique_ptr<StemSource> click;
    int64_t totalFrames = 0;
};

// Ein einziger Stereo-Stream als Master-Clock. Alle Stems werden im selben
// Callback gemischt: Instrumente mono-summiert auf links, Click/Cue auf rechts.
// Strikte Bus-Trennung — kein Signalweg zwischen den Seiten.
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    static constexpr int32_t kSampleRate = 48000;

    static AudioEngine &instance();

    bool start();
    void shutdown();

    // Lädt einen Song (Pfade leer = Slot unbenutzt). Gibt totalFrames zurück, -1 bei Fehler.
    int64_t loadSong(const std::array<std::string, 4> &instrumentPaths,
                     const std::array<float, 4> &instrumentGains,
                     const std::string &clickPath, float clickGain);
    void unloadSong();

    void play();
    void pause();
    void stop(); // Pause + Position 0
    void seek(int64_t frame);

    int64_t position() const { return mPosition.load(std::memory_order_relaxed); }
    bool isPlaying() const { return mPlaying.load(std::memory_order_relaxed); }
    bool isFinished() const { return mFinished.load(std::memory_order_relaxed); }
    void clearFinished() { mFinished.store(false, std::memory_order_relaxed); }
    bool hadStreamError() const { return mStreamError.load(std::memory_order_relaxed); }

    void setBusGains(float mainGain, float cueGain);
    void setSwapSides(bool swap) { mSwapSides.store(swap, std::memory_order_relaxed); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    AudioEngine() = default;

    bool openStream();

    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mStreamMutex;

    // Song-Swap: Mutex wird nur beim Laden/Entladen gehalten; der Callback
    // benutzt try_lock und liefert Stille, falls gerade getauscht wird.
    std::mutex mSongMutex;
    std::shared_ptr<LoadedSong> mSong;

    std::atomic<int64_t> mPosition{0};
    std::atomic<bool> mPlaying{false};
    std::atomic<bool> mFinished{false};
    std::atomic<bool> mStreamError{false};
    std::atomic<float> mMainGain{1.0f};
    std::atomic<float> mCueGain{1.0f};
    std::atomic<bool> mSwapSides{false};
};
