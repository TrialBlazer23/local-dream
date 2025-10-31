## Quick orientation — what this repo is

- Local Dream is an Android app (Jetpack Compose) that runs Stable Diffusion models locally. UI/UX lives in `app/src/main/java/...` (entry: `MainActivity.kt`).
- Heavy ML work is implemented in native C++ under `app/src/main/cpp/src/` (entry: `main.cpp`) and uses two execution paths: Qualcomm QNN (NPU) and MNN (CPU/GPU).

## High-level architecture (read these files together)

- UI layer: `app/src/main/java/.../MainActivity.kt` and `ui/` screens — Compose navigation between `ModelList`, `ModelRun`, `Upscale`, `History`, `PromptLibrary`.
- Native model runtime: `app/src/main/cpp/src/main.cpp` — command-line style options control which models and runtimes are used. Key flags: `--clip`, `--unet`, `--vae_decoder`, `--tokenizer`, `--backend`, `--system_library`, `--patch`, `--cpu` (use MNN).
- Build wiring: `app/build.gradle.kts` — product flavors `basic`/`filter`, ABI limited to `arm64-v8a`, custom APK name template.
- Model conversion: `convert/README.md` — scripts to convert safetensors → ONNX → QNN. Use this when adding new NPU models.

## Developer workflows (concrete commands)

**For NPU support on Galaxy S23 Ultra and similar devices:**

1. First, prepare QNN runtime assets (creates `app/src/main/assets/qnnlibs/`):

```bash
export QNN_SDK_ROOT=/absolute/path/to/QNN_SDK_2.xx
./scripts/prepare-qnn-assets.sh
```

2. Build native libs (Linux/WSL):

```bash
cd app/src/main/cpp
# Update QNN_SDK_ROOT path in CMakeLists.txt first
bash ./build.sh
```

3. Build APK (Gradle):

```bash
./gradlew assembleBasicDebug   # debug APK
./gradlew assembleBasicRelease # release (requires signing properties)
```

**Alternative:** open project in Android Studio and use Build → Generate APKs for iterative UI/debugging.

## Key project-specific patterns and gotchas

- Dual runtime paths: code supports both QNN (NPU via Qualcomm dynamic loading) and MNN (CPU/GPU). The C++ code branches early on — search `use_mnn` in `main.cpp`.
- Dynamic QNN loading: native code dynamically loads backend libraries at runtime (see `dynamicloadutil::getQnnFunctionPointers`). When testing locally, ensure `--system_library` and `--backend` args are passed to the native runner, or that the packaged assets contain the correct QNN libs (see `scripts/prepare-qnn-assets.sh`).
- CLIP v2 auto-detection: if `clip.mnn` sits next to `pos_emb.bin` and `token_emb.bin`, code uses `clip_v2` behavior (see `processCommandLine` in `main.cpp`).
- High-res model patching: 768/1024 patches are zstd-based patch files applied at runtime (`--patch` option). See `applyZstdPatch` and `processPatchLogic` in `main.cpp` — do not assume a patched file always exists; the code will attempt to produce one from a base unet binary + patch.
- Tiling strategy: VAE encoder/decoder and upscaler use explicit tiling/blending (functions like `blend_vae_encoder_tiles`, `blend_vae_output_tiles`, and upscaler tiling in `main.cpp`). When changing sizes or overlap, update these functions consistently.
- ABI / packaging: project packages native libs and QNN runtime assets into `app/src/main/assets/qnnlibs`; the app extracts and sets LD_LIBRARY_PATH at runtime. ABI is limited to `arm64-v8a` in `app/build.gradle.kts`.

## Critical Android ↔ Native integration points

**JNI/Service call flow:**
- `BackendService.kt` (main orchestrator) → spawns native process via `ProcessBuilder(command)` → `main.cpp` as HTTP server on localhost:8081
- `BackgroundGenerationService.kt` → HTTP POST to `localhost:8081/generate` → native runtime processes via QNN/MNN
- **Key insight:** No direct JNI binding; communication is via HTTP + process spawn. Native binary runs as separate process with LD_LIBRARY_PATH set to extracted QNN libs.

**QNN Asset Loading (crucial for NPU):**
- `BackendService.prepareRuntimeDir()` extracts `assets/qnnlibs/*.so` to `filesDir/runtime_libs/`
- Sets `LD_LIBRARY_PATH` and `DSP_LIBRARY_PATH` environment variables before spawning native process
- **Galaxy S23 Ultra:** requires Hexagon V79 libs (`libQnnHtpV79.so`, `libQnnHtpV79Skel.so`) — these are included in the asset script

## Integration points you'll touch most often

- Native model runner CLI (`app/src/main/cpp/src/main.cpp`) — unit of behavior for generation logic, tiling, patching, and dynamic QNN binding.
- Asset prep script: `scripts/prepare-qnn-assets.sh` — copy QNN libs into assets before packaging.
- Model conversion: `convert/*` — follow `convert/README.md` to generate QNN models for different SOC targets (`--min_soc` options).
- Gradle & signing: `app/build.gradle.kts` and `gradle.properties` hold signing placeholders (`RELEASE_*`); Change only via environment or CI secrets.

## Examples you might need to implement features or reproduce bugs

- To run the native sample app locally (after building native code) you can invoke the binary with full paths to model files (this mirrors the flags the app uses at runtime):

    - Required flags: `--clip`, `--unet`, `--vae_decoder`, `--tokenizer`, `--backend`, `--system_library`
    - Optional for patches: `--patch <path/to/768.patch>`

- To add a new NPU model: follow `convert/README.md` to generate QNNs, put the result in model folder, and ensure app knows how to download or bundle it (UI model list code is under `app/src/main/java/.../data`).

## What to check before editing native runtime logic

- Confirm whether runtime uses QNN or MNN by searching for `use_mnn` and `use_mnn_clip` in `main.cpp`.
- If changing model I/O shapes, update both QNN execution paths and MNN interpreters (they are implemented separately). Look for `executeUnetGraphs`, `executeVaeEncoderGraphs`, and MNN session setup blocks.

## CI guidance for QNN builds

**Current limitation:** `.github/workflows/build-apk.yml` cannot build native NPU libraries without QNN SDK access.

**To enable full QNN builds in CI:**

1. **Add QNN SDK to secrets/environment:** Store QNN SDK as an artifact or in a private repository accessible via GitHub Actions.

2. **Update workflow (add before `assembleBasicDebug`):**
```yaml
- name: Download/Extract QNN SDK  
  env:
    QNN_SDK_URL: ${{ secrets.QNN_SDK_URL }}  # or upload as artifact
  run: |
    # Download and extract QNN SDK
    curl -O $QNN_SDK_URL
    unzip -q *.zip
    export QNN_SDK_ROOT=$(pwd)/QNN_SDK_*
    echo "QNN_SDK_ROOT=$QNN_SDK_ROOT" >> $GITHUB_ENV

- name: Update CMakeLists QNN path
  run: |
    sed -i "s|set(QNN_SDK_ROOT /data/qairt/.*)|set(QNN_SDK_ROOT $QNN_SDK_ROOT)|" app/src/main/cpp/CMakeLists.txt

- name: Prepare QNN assets and build native
  run: |
    ./scripts/prepare-qnn-assets.sh
    cd app/src/main/cpp && bash ./build.sh
```

3. **Add signing credentials as secrets:** `RELEASE_STORE_FILE_BASE64`, `RELEASE_STORE_PASSWORD`, etc.

## Where CI / automated builds may be limited

- GitHub Actions cannot build the full native NPU artifacts without a QNN SDK and signing credentials (see `BUILD_INSTRUCTIONS.md`). CI builds are limited to Android project structure only.

## Quick file map (most-relevant files)

- `app/src/main/cpp/src/main.cpp` — native runtime & generation logic (read first)
- `app/src/main/cpp/CMakeLists.txt` & `CMakePresets.json` — native build configuration and QNN/NDK path points
- `scripts/prepare-qnn-assets.sh` — copy QNN runtime into `app/src/main/assets/qnnlibs`
- `convert/README.md` — model conversion flow for NPU
- `app/build.gradle.kts` — flavors, ABI, packaging quirks
- `app/src/main/java/io/github/xororz/localdream/MainActivity.kt` — app entry & navigation

If anything here is unclear or you want more detail (examples for invoking the native binary, the exact Gradle flavor used in CI, or pointers into the model-list/download code), tell me which area to expand and I’ll iterate.
