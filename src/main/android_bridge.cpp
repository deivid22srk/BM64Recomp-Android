#include "android_bridge.h"

#include <SDL.h>
#include <jni.h>

#include <mutex>
#include <queue>
#include <utility>

namespace android_bridge {
    // Custom SDL user event code used to marshal callbacks onto the main
    // (SDL) thread. Chosen to avoid the axis codes used elsewhere.
    constexpr Sint32 EVENT_CODE = 0x414E;

    namespace {
        std::mutex g_mutex;
        std::function<void(bool, const std::filesystem::path&)> g_pick_callback;
        std::queue<std::function<void()>> g_main_thread_queue;
    }

    static void dispatch_to_main(std::function<void()> fn) {
        {
            std::lock_guard lock{g_mutex};
            g_main_thread_queue.push(std::move(fn));
        }

        SDL_Event event{};
        event.type = SDL_USEREVENT;
        event.user.code = EVENT_CODE;
        event.user.data1 = nullptr;
        event.user.data2 = nullptr;
        SDL_PushEvent(&event);
    }

    void request_file_pick(std::function<void(bool, const std::filesystem::path& path)> callback) {
        {
            std::lock_guard lock{g_mutex};
            g_pick_callback = std::move(callback);
        }

        JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
        if (env == nullptr) {
            return;
        }

        jclass cls = env->FindClass("com/deivid22srk/bm64recomp/MainActivity");
        if (cls == nullptr) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            return;
        }

        jmethodID mid = env->GetStaticMethodID(cls, "requestFilePick", "(Landroid/app/Activity;)V");
        if (mid == nullptr) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(cls);
            return;
        }

        jobject activity = (jobject)SDL_AndroidGetActivity();
        if (activity == nullptr) {
            env->DeleteLocalRef(cls);
            return;
        }

        env->CallStaticVoidMethod(cls, mid, activity);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(activity);
        env->DeleteLocalRef(cls);
    }

    void on_file_picked(const std::u8string& imported_path) {
        std::function<void(bool, const std::filesystem::path&)> cb;
        {
            std::lock_guard lock{g_mutex};
            cb = std::move(g_pick_callback);
            g_pick_callback = nullptr;
        }

        if (!cb) {
            return;
        }

        bool success = !imported_path.empty();
        std::filesystem::path path{};
        if (success) {
            path = std::filesystem::path{imported_path};
        }

        dispatch_to_main([cb, success, path]() {
            cb(success, path);
        });
    }

    void process_events() {
        std::queue<std::function<void()>> queue;
        {
            std::lock_guard lock{g_mutex};
            std::swap(queue, g_main_thread_queue);
        }

        while (!queue.empty()) {
            auto fn = std::move(queue.front());
            queue.pop();
            fn();
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_deivid22srk_bm64recomp_MainActivity_nativeOnFilePicked(JNIEnv* env, jclass clazz, jstring path) {
    (void)clazz;

    if (env == nullptr || path == nullptr) {
        android_bridge::on_file_picked({});
        return;
    }

    const char* path_chars = env->GetStringUTFChars(path, nullptr);
    if (path_chars == nullptr) {
        android_bridge::on_file_picked({});
        return;
    }

    std::u8string imported{};
    imported.assign(reinterpret_cast<const char8_t*>(path_chars));
    env->ReleaseStringUTFChars(path, path_chars);

    android_bridge::on_file_picked(imported);
}
