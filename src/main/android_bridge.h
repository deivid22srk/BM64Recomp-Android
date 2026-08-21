#ifndef ANDROID_BRIDGE_H
#define ANDROID_BRIDGE_H

#include <functional>
#include <filesystem>
#include <string>

// Bridges native code with the Java activity on Android. File picking is done
// via the Storage Access Framework; results are marshalled back to the main
// thread (the thread running the SDL event loop) via custom SDL events.

namespace android_bridge {
    // Requests a single file from the user. The file is copied into the app's
    // internal storage and the callback receives the copied file's path.
    void request_file_pick(std::function<void(bool success, const std::filesystem::path& path)> callback);

    // Called by the JNI layer when the Java side has finished handling a pick.
    // `imported_path` is empty if the operation was cancelled or failed.
    void on_file_picked(const std::u8string& imported_path);

    // Dispatches pending main-thread callbacks. Called from the SDL event loop.
    void process_events();
}

#endif
