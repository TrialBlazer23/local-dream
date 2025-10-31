# Build Instructions

This document outlines the steps required to successfully build the Local Dream Android APK.

## Recent Fixes Applied

### 1. Fixed Deprecated Dependencies
- ✅ Updated Android Gradle Plugin from 8.13.0 (non-existent) to 8.3.0 (latest stable)
- ✅ Updated Kotlin from 1.9.0 to 1.9.22 (compatible with AGP 8.3)
- ✅ Updated KSP from 1.9.0-1.0.13 to 1.9.22-1.0.17 (matches Kotlin version)
- ✅ Updated Compose compiler extension version to 1.5.10 (compatible with Kotlin 1.9.22)
- ✅ Fixed repository configuration to use `maven.google.com` explicitly
- ✅ Switched to buildscript approach for better plugin resolution

### 2. Fixed Malformed XML Files
- ✅ Fixed `app/src/main/res/values/strings.xml` - removed duplicate `</resources>` tags and spurious backticks
- ✅ Fixed `app/src/main/res/values-zh/strings.xml` - removed spurious backticks after closing tag

## Prerequisites

Before building, ensure you have the following installed:

### Required Tools
1. **Java Development Kit (JDK) 17**
   - Download from: https://adoptium.net/
   - Verify: `java -version`

2. **Android Studio** (or Android SDK Command-line Tools)
   - Download from: https://developer.android.com/studio

3. **Android NDK 26.1.10909125** (or compatible version)
   - Install via Android Studio SDK Manager
   - Or via command line: `sdkmanager "ndk;26.1.10909125"`

4. **Rust Toolchain**
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   rustup default stable
   rustup target add aarch64-linux-android
   ```

5. **Build Tools**
   - **CMake**: `brew install cmake` (macOS) or `sudo apt-get install cmake` (Linux)
   - **Ninja**: `brew install ninja` (macOS) or `sudo apt-get install ninja-build` (Linux)

### Required SDKs

6. **Qualcomm QNN SDK 2.39**
   - Download from: https://apigwx-aws.qualcomm.com/qsc/public/v1/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.39.0.250926/v2.39.0.250926.zip
   - Extract to a known location
   - Update `QNN_SDK_ROOT` in `app/src/main/cpp/CMakeLists.txt` with the path (for native build)
   - Copy QNN runtime assets into `app/src/main/assets/qnnlibs` (see below)

## Build Steps

### Step 1: Clone the Repository
```bash
git clone --recursive https://github.com/TrialBlazer23/local-dream.git
cd local-dream
```

### Step 2: Configure SDK Paths

1. Edit `app/src/main/cpp/CMakeLists.txt`:
   - Update `QNN_SDK_ROOT` with your QNN SDK path

2. Edit `app/src/main/cpp/CMakePresets.json`:
   - Update `ANDROID_NDK_ROOT` with your Android NDK path

### Step 3: Build Native Libraries

**On Linux/macOS:**
```bash
cd app/src/main/cpp/
bash ./build.sh
```

**On Windows:**
```powershell
# Install dependencies if needed:
# winget install Kitware.CMake
# winget install Ninja-build.Ninja
# winget install Rustlang.Rustup
# winget install waterlan.dos2unix  # for dos2unix

cd app\src\main\cpp\

# Convert patch file
dos2unix SampleApp.patch

# Build
.\build.bat
```

### Step 4: Build the APK
### Step 3.5 (Recommended): Prepare QNN runtime assets

To run on Snapdragon NPU, the app packages QNN runtime libraries under `app/src/main/assets/qnnlibs/` and extracts them at runtime.

We’ve provided a helper script to copy the required files from your local QNN SDK:

```bash
export QNN_SDK_ROOT=/absolute/path/to/QNN_SDK
./scripts/prepare-qnn-assets.sh
```

This will place files like `libQnnHtp.so`, `libQnnSystem.so`, and the Hexagon HTP skel libraries into the assets folder. See `app/src/main/assets/qnnlibs/README.md` for the complete list and details.

Notes:
- If you only plan to use CPU models, you can skip this step.
- On-device, the app sets LD_LIBRARY_PATH and DSP_LIBRARY_PATH to point to these libs.


#### Option A: Using Android Studio (Recommended)
1. Open Android Studio
2. Open this project
3. Go to **Build → Generate Signed Bundle / APK**
4. Select **APK**
5. Choose **debug** or **release** build variant
6. For release builds, you'll need to configure signing:
   - Create or use an existing keystore
   - Set the following in `gradle.properties` or as environment variables:
     ```
     RELEASE_STORE_FILE=path/to/your/keystore.jks
     RELEASE_STORE_PASSWORD=your_store_password
     RELEASE_KEY_ALIAS=your_key_alias
     RELEASE_KEY_PASSWORD=your_key_password
     ```

#### Option B: Using Command Line
```bash
# Build debug APK (no signing required)
./gradlew assembleBasicDebug

# Build release APK (requires signing configuration)
./gradlew assembleBasicRelease
```

### Step 5: Locate the APK

After a successful build, the APK will be located at:
- **Debug**: `app/build/outputs/apk/basic/debug/LocalDream_armv8a_2.1.2.apk`
- **Release**: `app/build/outputs/apk/basic/release/LocalDream_armv8a_2.1.2.apk`

## Build Variants

This project has two product flavors:
- **basic**: Standard version
- **filter**: Version with NSFW filtering

And two build types:
- **debug**: For development and testing
- **release**: For distribution (requires signing)

## Troubleshooting

### Issue: "Could not resolve com.android.tools.build:gradle:8.3.0"
**Solution**: Ensure you have internet access and can reach `maven.google.com` or `dl.google.com`. If behind a proxy, configure Gradle proxy settings in `gradle.properties`.

### Issue: Native library build fails
**Solution**: 
- Verify all SDK paths are correctly set in CMakeLists.txt and CMakePresets.json
- Ensure Rust toolchain is installed with aarch64-linux-android target
- Check that CMake and Ninja are in your PATH

### Issue: "Execution failed for task ':app:packageDebug'"
**Solution**: This likely means native libraries weren't built. Complete Step 3 first.

### Issue: Release build fails with signing error
**Solution**: Either:
- Build a debug APK instead (`assembleBasicDebug`)
- Or configure signing credentials as described in Step 4

## GitHub Actions

The project includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`) for automated builds. However, it currently:
- ✅ Can build the Android project structure
- ❌ Cannot build native libraries (requires QNN SDK setup)
- ❌ Cannot create release builds (requires signing credentials)

To enable full automated builds:
1. Add QNN SDK as a downloadable artifact or repository secret
2. Configure GitHub Secrets for release signing
3. Update the workflow to build native libraries before building the APK

## Next Steps

To complete the build process and generate a distributable APK:

1. **Install Prerequisites**: Ensure all required tools and SDKs are installed
2. **Build Native Libraries**: Run the build script in `app/src/main/cpp/`
3. **Configure Signing** (for release builds): Set up keystore and credentials
4. **Build APK**: Use Android Studio or Gradle command line
5. **Test**: Install the APK on a compatible Android device

## Support

For issues or questions:
- Check the [main README](README.md) for general project information
- Visit the [Issues page](https://github.com/TrialBlazer23/local-dream/issues)
- Join the [Telegram group](https://t.me/local_dream)
