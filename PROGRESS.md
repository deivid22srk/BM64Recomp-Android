# BM64Recomp — Port para Android (Estado do Trabalho)

Última atualização: 2026-08-21 (sessão 1)

## Objetivo
Portar https://github.com/RevoSucks/BM64Recomp (Bomberman 64: Recompiled) para Android
como app nativo (Gradle + NDK + CMake), com CI no GitHub Actions gerando o APK.

## Diagnóstico concluído

### Assets do jogo (seção 0 do pedido)
- **Runtime**: exige a ROM de varejo **Bomberman 64 (USA) 1.0, .z64**. O app pede a ROM
  no menu principal (`rom_hash = 0xc4c0b74bbb696426` = XXH3 da ROM big-endian).
  A ROM do usuário `/storage/7AEE-2D07/n64/Bomberman 64 (USA).z64` foi verificada:
  hash XXH3 bate EXATAMENTE (`c4c0b74bbb696426`). ✔
- **Build/codegen**: exige `bm64.decomp.us.z64` (ROM descomprimida, ~9.6 MB,
  md5 `d2f9313cccae0af85d23ac37666c0173`). NÃO vem no repositório (upstream usa repo
  secreto). GERADA com sucesso localmente a partir da ROM de varejo via ferramenta
  `bm64decompress` do decomp Bomberhackers/bm64 — md5 de saída confere 100%. ✔
  → Nada precisa ser pedido ao usuário além da ROM já fornecida.
- **CI**: upstream busca a ROM em repositório privado via PAT. Faremos o mesmo:
  repo PRIVADO `deivid22srk/bm64-rom-private` contendo apenas o baserom (ROM de varejo);
  o workflow clona com GITHUB_TOKEN e gera a ROM descomprimida na hora.
  (A ROM não vai para o repo público do port.)

### Pipeline de build (validado localmente no Termux, aarch64)
1. `bm64decompress` (Bomberhackers/bm64/tools) → gera bm64.decomp.us.z64 ✔
2. N64Recomp @ 98bf104b (pinned no validate.yml) → `N64Recomp bm64.us.toml`
   → 74447 funções, RecompiledFuncs/ ✔
3. `RSPRecomp aspMain.toml` → rsp/aspMain.cpp ✔
4. patches MIPS: `make -C patches CC=clang LD=ld.lld` (clang Termux tem target MIPS) ✔
5. `N64Recomp patches.toml` → RecompiledPatches/patches.c + patches.bin ✔

### Problemas encontrados e soluções
| Problema | Solução |
|---|---|
| Submódulo `lib/sf64` (sonicdcer/sf64) sumiu do GitHub | Repontado para fork `inspectredc/sf64`, commit pinned b870a09 preservado |
| RT64 espera `ANativeWindow*` no Android (plume tem branch `__ANDROID__` pronto) | Converter SDL_Window→ANativeWindow via SDL_GetWindowWMinfo em rt64_render_context.cpp |
| `rt64_application_window.cpp` tem `static_assert(false)` no Android (só no caminho de janela própria, não usado) | Patch mínimo no submódulo rt64 via arquivo .patch aplicado no CI/local |
| nfd (nativefiledialog-extended) cai em PLATFORM_LINUX→GTK no Android | Patch no CMake do rt64 p/ pular nfd no Android + stub `nfd_stub_android.c` no port |
| ultramodern WindowHandle = SDL_Window* no __ANDROID__ | Mantido; conversão para ANativeWindow só no limite (rt64_render_context.cpp) |
| PLUME_SDL_VULKAN_ENABLED/RT64_SDL_WINDOW_VULKAN não devem ser definidos no Android (plume usa vkCreateAndroidSurfaceKHR nativo) | Ramos ANDROID no CMake raiz sem esses defines; flag SDL_WINDOW_VULKAN dispensável |
| Freetype: Linux usa system freetype; Windows usa binaries | Android: compilar fonte do freetype (vendored/clonado no CI) |
| main() → precisa virar SDL_main no Android | incluir SDL_main.h (sem SDL_MAIN_HANDLED) sob __ANDROID__ |
| Paths (config/saves/mods/controller db/assets) | get_program_path/get_app_folder_path → SDL_AndroidGetInternalStoragePath(); assets extraídos do APK pelo Java antes do super.onCreate() |
| Diálogo de ROM (NFD) não existe no Android | SAF (ACTION_OPEN_DOCUMENT) via JNI bridge; callback marshalado à thread principal por SDL_USEREVENT (code próprio) |
| Launcher pede ROM via botão | No Android: auto-start se ROM válida; senão dispara SAF picker |

### Decisões de arquitetura Android
- SDL 2.30.x compartilhado (libSDL2.so) built via CMake (clone pinned no CI em lib/SDL);
  Java glue vendored (org/libsdl/app) da mesma tag.
- App: Kotlin-less (Java), MainActivity extends SDLActivity; meta lib_name default.
- arm64-v8a only; minSdk 26; targetSdk 34; sensorLandscape; Vulkan requerido.
- ABI Release; sem LTO inicialmente; RecompiledFuncs é o grosso (~75k funções).
- Touch = mouse sintetizado do SDL (menus ok); gamepad Bluetooth via SDL GameController.

## Estado
- [x] Investigação/diagnóstico completo
- [x] Codegen pipeline validado localmente
- [ ] Estrutura Gradle + mudanças nativas (EM PROGRESSO)
- [ ] Validação local de compilação (parcial — host build)
- [ ] Repos GitHub (privado ROM + público port) e push
- [ ] Workflow build.yml verde
- [ ] APK instalado/testado no dispositivo

## Como retomar
1. `cd ~/2/BM64Recomp` — working tree já tem codegen feito (RecompiledFuncs/, rsp/, RecompiledPatches/).
2. Ferramentas em `~/2/N64RecompSource/cmake-build/`.
3. Este arquivo lista o resto das tarefas.
