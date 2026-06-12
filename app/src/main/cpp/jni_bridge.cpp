#include <jni.h>

#include <array>
#include <string>

#include "AudioEngine.h"

namespace {

std::string jstringToStd(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return result;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeStart(JNIEnv *, jobject) {
    return AudioEngine::instance().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeShutdown(JNIEnv *, jobject) {
    AudioEngine::instance().shutdown();
}

// paths: Array der Länge 5 — Index 0..3 Instrumente, Index 4 Click/Cue.
// gains: lineare Gains, gleiche Indizes.
JNIEXPORT jlong JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeLoadSong(JNIEnv *env, jobject,
                                                        jobjectArray paths,
                                                        jfloatArray gains) {
    std::array<std::string, 4> instrumentPaths;
    std::string clickPath;
    jfloat gainBuf[5] = {1, 1, 1, 1, 1};
    env->GetFloatArrayRegion(gains, 0, 5, gainBuf);
    std::array<float, 4> instrumentGains{};

    for (int i = 0; i < 5; i++) {
        auto js = (jstring) env->GetObjectArrayElement(paths, i);
        std::string path = jstringToStd(env, js);
        if (js) env->DeleteLocalRef(js);
        if (i < 4) {
            instrumentPaths[i] = path;
            instrumentGains[i] = gainBuf[i];
        } else {
            clickPath = path;
        }
    }
    return AudioEngine::instance().loadSong(instrumentPaths, instrumentGains, clickPath,
                                            gainBuf[4]);
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeUnloadSong(JNIEnv *, jobject) {
    AudioEngine::instance().unloadSong();
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativePlay(JNIEnv *, jobject) {
    AudioEngine::instance().play();
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativePause(JNIEnv *, jobject) {
    AudioEngine::instance().pause();
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeStop(JNIEnv *, jobject) {
    AudioEngine::instance().stop();
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeSeek(JNIEnv *, jobject, jlong frame) {
    AudioEngine::instance().seek(frame);
}

JNIEXPORT jlong JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeGetPosition(JNIEnv *, jobject) {
    return AudioEngine::instance().position();
}

JNIEXPORT jboolean JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeIsPlaying(JNIEnv *, jobject) {
    return AudioEngine::instance().isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeIsFinished(JNIEnv *, jobject) {
    return AudioEngine::instance().isFinished() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeClearFinished(JNIEnv *, jobject) {
    AudioEngine::instance().clearFinished();
}

JNIEXPORT jboolean JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeHadStreamError(JNIEnv *, jobject) {
    return AudioEngine::instance().hadStreamError() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeSetBusGains(JNIEnv *, jobject, jfloat main,
                                                           jfloat cue) {
    AudioEngine::instance().setBusGains(main, cue);
}

JNIEXPORT void JNICALL
Java_de_minitraxx_app_audio_NativeEngine_nativeSetSwapSides(JNIEnv *, jobject,
                                                            jboolean swap) {
    AudioEngine::instance().setSwapSides(swap == JNI_TRUE);
}

} // extern "C"
