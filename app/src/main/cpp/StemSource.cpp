#include "StemSource.h"

#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>

#define LOG_TAG "MiniTraxx"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

uint32_t readU32(const uint8_t *p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

uint16_t readU16(const uint8_t *p) {
    return static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8);
}

} // namespace

std::unique_ptr<StemSource> StemSource::open(const std::string &path, float gain) {
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        LOGE("StemSource: cannot open %s", path.c_str());
        return nullptr;
    }

    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size < 44) {
        LOGE("StemSource: stat failed or file too small: %s", path.c_str());
        ::close(fd);
        return nullptr;
    }
    auto mapSize = static_cast<size_t>(st.st_size);

    void *map = mmap(nullptr, mapSize, PROT_READ, MAP_PRIVATE, fd, 0);
    if (map == MAP_FAILED) {
        LOGE("StemSource: mmap failed: %s", path.c_str());
        ::close(fd);
        return nullptr;
    }
    madvise(map, mapSize, MADV_WILLNEED);

    const auto *bytes = static_cast<const uint8_t *>(map);
    if (memcmp(bytes, "RIFF", 4) != 0 || memcmp(bytes + 8, "WAVE", 4) != 0) {
        LOGE("StemSource: not a RIFF/WAVE file: %s", path.c_str());
        munmap(map, mapSize);
        ::close(fd);
        return nullptr;
    }

    // Chunks durchlaufen: fmt validieren (PCM16 mono 48k), data lokalisieren.
    size_t pos = 12;
    bool fmtOk = false;
    const int16_t *samples = nullptr;
    int64_t frames = 0;
    while (pos + 8 <= mapSize) {
        const uint8_t *chunk = bytes + pos;
        uint32_t chunkSize = readU32(chunk + 4);
        if (memcmp(chunk, "fmt ", 4) == 0 && chunkSize >= 16 && pos + 8 + 16 <= mapSize) {
            uint16_t format = readU16(chunk + 8);
            uint16_t channels = readU16(chunk + 10);
            uint32_t sampleRate = readU32(chunk + 12);
            uint16_t bits = readU16(chunk + 22);
            fmtOk = (format == 1 && channels == 1 && sampleRate == 48000 && bits == 16);
            if (!fmtOk) {
                LOGE("StemSource: unexpected format (fmt=%u ch=%u sr=%u bits=%u): %s",
                     format, channels, sampleRate, bits, path.c_str());
            }
        } else if (memcmp(chunk, "data", 4) == 0) {
            size_t dataStart = pos + 8;
            size_t dataLen = chunkSize;
            if (dataStart + dataLen > mapSize) dataLen = mapSize - dataStart;
            samples = reinterpret_cast<const int16_t *>(bytes + dataStart);
            frames = static_cast<int64_t>(dataLen / 2);
        }
        pos += 8 + chunkSize + (chunkSize & 1);
    }

    if (!fmtOk || samples == nullptr || frames <= 0) {
        LOGE("StemSource: invalid canonical WAV: %s", path.c_str());
        munmap(map, mapSize);
        ::close(fd);
        return nullptr;
    }

    auto src = std::unique_ptr<StemSource>(new StemSource());
    src->mFd = fd;
    src->mMap = map;
    src->mMapSize = mapSize;
    src->mSamples = samples;
    src->mFrames = frames;
    src->mGain = gain;
    return src;
}

StemSource::~StemSource() {
    if (mMap != nullptr) munmap(mMap, mMapSize);
    if (mFd >= 0) ::close(mFd);
}
