#include "AudioEngine.h"

#include <android/log.h>

#include <algorithm>
#include <cmath>

#define LOG_TAG "MiniTraxx"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
// Weiches Begrenzen, damit die Mono-Summe von 4 Stems nicht hart clippt.
inline float softClip(float x) {
    if (x > 1.0f) return 1.0f - 1.0f / (1.0f + (x - 1.0f) * 2.0f);
    if (x < -1.0f) return -1.0f + 1.0f / (1.0f - (x + 1.0f) * 2.0f);
    return x;
}
} // namespace

AudioEngine &AudioEngine::instance() {
    static AudioEngine engine;
    return engine;
}

bool AudioEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)
            ->setSampleRate(kSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboe::Usage::Media)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return false;
    }
    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
        stream->close();
        return false;
    }
    mStream = stream;
    mStreamError.store(false, std::memory_order_relaxed);
    LOGI("stream started: sr=%d burst=%d", stream->getSampleRate(),
         stream->getFramesPerBurst());
    return true;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) return true;
    return openStream();
}

void AudioEngine::shutdown() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
    unloadSong();
}

int64_t AudioEngine::loadSong(const std::array<std::string, 4> &instrumentPaths,
                              const std::array<float, 4> &instrumentGains,
                              const std::string &clickPath, float clickGain) {
    auto song = std::make_shared<LoadedSong>();
    int64_t total = 0;
    for (int i = 0; i < 4; i++) {
        if (instrumentPaths[i].empty()) continue;
        auto src = StemSource::open(instrumentPaths[i], instrumentGains[i]);
        if (!src) return -1;
        total = std::max(total, src->frames());
        song->instruments.push_back(std::move(src));
    }
    if (!clickPath.empty()) {
        song->click = StemSource::open(clickPath, clickGain);
        if (!song->click) return -1;
        total = std::max(total, song->click->frames());
    }
    if (total == 0) return -1;
    song->totalFrames = total;

    {
        std::lock_guard<std::mutex> lock(mSongMutex);
        mPlaying.store(false, std::memory_order_relaxed);
        mSong = std::move(song);
        mPosition.store(0, std::memory_order_relaxed);
        mFinished.store(false, std::memory_order_relaxed);
    }
    return total;
}

void AudioEngine::unloadSong() {
    std::lock_guard<std::mutex> lock(mSongMutex);
    mPlaying.store(false, std::memory_order_relaxed);
    mSong.reset();
    mPosition.store(0, std::memory_order_relaxed);
    mFinished.store(false, std::memory_order_relaxed);
}

void AudioEngine::play() {
    start();
    mFinished.store(false, std::memory_order_relaxed);
    mPlaying.store(true, std::memory_order_relaxed);
}

void AudioEngine::pause() {
    mPlaying.store(false, std::memory_order_relaxed);
}

void AudioEngine::stop() {
    mPlaying.store(false, std::memory_order_relaxed);
    mPosition.store(0, std::memory_order_relaxed);
    mFinished.store(false, std::memory_order_relaxed);
}

void AudioEngine::seek(int64_t frame) {
    std::lock_guard<std::mutex> lock(mSongMutex);
    int64_t total = mSong ? mSong->totalFrames : 0;
    mPosition.store(std::clamp<int64_t>(frame, 0, total), std::memory_order_relaxed);
    mFinished.store(false, std::memory_order_relaxed);
}

void AudioEngine::setBusGains(float mainGain, float cueGain) {
    mMainGain.store(mainGain, std::memory_order_relaxed);
    mCueGain.store(cueGain, std::memory_order_relaxed);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream * /*stream*/,
                                                   void *audioData, int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    std::fill(out, out + numFrames * 2, 0.0f);

    if (!mPlaying.load(std::memory_order_relaxed)) {
        return oboe::DataCallbackResult::Continue;
    }

    std::unique_lock<std::mutex> lock(mSongMutex, std::try_to_lock);
    if (!lock.owns_lock() || !mSong) {
        return oboe::DataCallbackResult::Continue;
    }

    const LoadedSong &song = *mSong;
    int64_t pos = mPosition.load(std::memory_order_relaxed);
    const float mainGain = mMainGain.load(std::memory_order_relaxed);
    const float cueGain = mCueGain.load(std::memory_order_relaxed);
    const bool swap = mSwapSides.load(std::memory_order_relaxed);

    for (int32_t i = 0; i < numFrames; i++) {
        const int64_t frame = pos + i;
        float main = 0.0f;
        for (const auto &inst : song.instruments) {
            main += inst->sampleAt(frame);
        }
        float cue = song.click ? song.click->sampleAt(frame) : 0.0f;
        main = softClip(main * mainGain);
        cue = softClip(cue * cueGain);
        out[i * 2] = swap ? cue : main;      // links  = MAIN (Tracks)
        out[i * 2 + 1] = swap ? main : cue;  // rechts = CUE  (Click/Ansagen)
    }

    pos += numFrames;
    if (pos >= song.totalFrames) {
        mPosition.store(song.totalFrames, std::memory_order_relaxed);
        mPlaying.store(false, std::memory_order_relaxed);
        mFinished.store(true, std::memory_order_relaxed);
    } else {
        mPosition.store(pos, std::memory_order_relaxed);
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    LOGE("stream error after close: %s", oboe::convertToText(error));
    mPlaying.store(false, std::memory_order_relaxed);
    mStreamError.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(mStreamMutex);
    mStream.reset();
}
