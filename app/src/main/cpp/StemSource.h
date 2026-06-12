#pragma once

#include <cstdint>
#include <memory>
#include <string>

// Eine Stem-Datei im kanonischen Format (WAV, PCM16, mono, 48 kHz),
// per mmap eingeblendet. Der Audio-Callback liest direkt aus dem Mapping —
// keine Allocations, keine Reads im RT-Pfad.
class StemSource {
public:
    static std::unique_ptr<StemSource> open(const std::string &path, float gain);
    ~StemSource();

    StemSource(const StemSource &) = delete;
    StemSource &operator=(const StemSource &) = delete;

    inline float sampleAt(int64_t frame) const {
        if (frame < 0 || frame >= mFrames) return 0.0f;
        return static_cast<float>(mSamples[frame]) * (1.0f / 32768.0f) * mGain;
    }

    int64_t frames() const { return mFrames; }

private:
    StemSource() = default;

    int mFd = -1;
    void *mMap = nullptr;
    size_t mMapSize = 0;
    const int16_t *mSamples = nullptr;
    int64_t mFrames = 0;
    float mGain = 1.0f;
};
