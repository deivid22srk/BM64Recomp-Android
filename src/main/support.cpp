#include "zelda_support.h"
#include <SDL.h>
#include "nfd.h"
#include "RmlUi/Core.h"

#ifdef __ANDROID__
#include "android_bridge.h"
#endif

namespace zelda64 {
    // MARK: - Internal Helpers
    void perform_file_dialog_operation(const std::function<void(bool, const std::filesystem::path&)>& callback) {
#ifdef __ANDROID__
        // Route through the Android SAF file picker. The callback is invoked on
        // the main thread once the user has picked a file (or cancelled).
        android_bridge::request_file_pick([callback](bool success, const std::filesystem::path& path) {
            callback(success, path);
        });
#else
        nfdnchar_t* native_path = nullptr;
        nfdresult_t result = NFD_OpenDialogN(&native_path, nullptr, 0, nullptr);

        bool success = (result == NFD_OKAY);
        std::filesystem::path path;

        if (success) {
            path = std::filesystem::path{native_path};
            NFD_FreePathN(native_path);
        }

        callback(success, path);
#endif
    }

    void perform_file_dialog_operation_multiple(const std::function<void(bool, const std::list<std::filesystem::path>&)>& callback) {
#ifdef __ANDROID__
        // Multiple-selection dialogs are not supported on Android yet.
        callback(false, {});
#else
        const nfdpathset_t* native_paths = nullptr;
        nfdresult_t result = NFD_OpenDialogMultipleN(&native_paths, nullptr, 0, nullptr);

        bool success = (result == NFD_OKAY);
        std::list<std::filesystem::path> paths;
        nfdpathsetsize_t count = 0;

        if (success) {
            NFD_PathSet_GetCount(native_paths, &count);
            for (nfdpathsetsize_t i = 0; i < count; i++) {
                nfdnchar_t* cur_path = nullptr;
                nfdresult_t cur_result = NFD_PathSet_GetPathN(native_paths, i, &cur_path);
                if (cur_result == NFD_OKAY) {
                    paths.emplace_back(std::filesystem::path{cur_path});
                }
            }
            NFD_PathSet_Free(native_paths);
        }

        callback(success, paths);
#endif
    }

    // MARK: - Public API

    std::filesystem::path get_program_path() {
#if defined(__APPLE__)
        return get_bundle_resource_directory();
#elif defined(__ANDROID__)
        return SDL_AndroidGetInternalStoragePath();
#elif defined(__linux__) && defined(RECOMP_FLATPAK)
        return "/app/bin";
#else
        return "";
#endif
    }

    std::filesystem::path get_asset_path(const char* asset) {
        return get_program_path() / "assets" / asset;
    }

    void open_file_dialog(std::function<void(bool success, const std::filesystem::path& path)> callback) {
#ifdef __APPLE__
        dispatch_on_ui_thread([callback]() {
            perform_file_dialog_operation(callback);
        });
#else
        perform_file_dialog_operation(callback);
#endif
    }

    void open_file_dialog_multiple(std::function<void(bool success, const std::list<std::filesystem::path>& paths)> callback) {
#ifdef __APPLE__
        dispatch_on_ui_thread([callback]() {
            perform_file_dialog_operation_multiple(callback);
        });
#else
        perform_file_dialog_operation_multiple(callback);
#endif
    }

    void show_error_message_box(const char *title, const char *message) {
#ifdef __APPLE__
    std::string title_copy(title);
    std::string message_copy(message);

    dispatch_on_ui_thread([title_copy, message_copy] {
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, title_copy.c_str(), message_copy.c_str(), nullptr);
    });
#else
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, title, message, nullptr);
#endif
    }
}
