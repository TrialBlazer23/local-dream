set -e
cmake --preset android-release
cmake --build --preset android-release

mkdir -p lib
cp -r ./build/android/qnnlibs ../assets/
mkdir -p ../jniLibs/arm64-v8a/
cp ./build/android/bin/arm64-v8a/libstable_diffusion_core.so ../jniLibs/arm64-v8a/
