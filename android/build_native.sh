#!/usr/bin/env bash
# Builds all native libraries of the Android port and copies them to
# app/src/main/jniLibs/<abi>/
#
# Requirements: Android NDK (ANDROID_NDK_HOME), cmake >= 3.25, ninja, flex, bison
#
# Usage: ./build_native.sh [abi]      (default abi: arm64-v8a)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
ABI="${1:-arm64-v8a}"
API="${ANDROID_API:-26}"
BUILD_TYPE="${BUILD_TYPE:-Release}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
    echo "ANDROID_NDK_HOME is not set to a valid NDK" >&2
    exit 1
fi

DEPS="${DEPS_DIR:-$HERE/deps}"
OUT="$HERE/build/$ABI"
PREFIX="$OUT/prefix"
JNILIBS="$HERE/app/src/main/jniLibs/$ABI"
JOBS="$(nproc 2>/dev/null || echo 4)"

"$HERE/fetch_deps.sh"

COMMON=(
    -G Ninja
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$ABI"
    -DANDROID_PLATFORM="android-$API"
    -DANDROID_STL=c++_shared
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE"
    -DCMAKE_INSTALL_PREFIX="$PREFIX"
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON
)

echo "=== SDL2"
cmake -S "$DEPS/SDL2" -B "$OUT/sdl2" "${COMMON[@]}" \
    -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST=OFF -DSDL2_DISABLE_INSTALL_DOCS=ON
cmake --build "$OUT/sdl2" -j "$JOBS"
cmake --install "$OUT/sdl2"

echo "=== OpenAL Soft"
cmake -S "$DEPS/openal-soft" -B "$OUT/openal" "${COMMON[@]}" \
    -DLIBTYPE=SHARED -DALSOFT_UTILS=OFF -DALSOFT_EXAMPLES=OFF -DALSOFT_TESTS=OFF \
    -DALSOFT_INSTALL_CONFIG=OFF -DALSOFT_INSTALL_HRTF_DATA=OFF -DALSOFT_INSTALL_AMBDEC_PRESETS=OFF \
    -DALSOFT_BACKEND_OPENSL=ON -DALSOFT_REQUIRE_OPENSL=ON
cmake --build "$OUT/openal" -j "$JOBS"
cmake --install "$OUT/openal"

echo "=== gl4es"
# static library, OpenGL ES 2 backend, context created by SDL
cmake -S "$DEPS/gl4es" -B "$OUT/gl4es" "${COMMON[@]}" \
    -DNOX11=ON -DNOEGL=ON -DSTATICLIB=ON -DNO_INIT_CONSTRUCTOR=ON -DDEFAULT_ES=2 \
    -DUSE_ANDROID_LOG=ON
cmake --build "$OUT/gl4es" -j "$JOBS" --target GL
GL4ES_LIB="$(find "$DEPS/gl4es/lib" "$OUT/gl4es" -name 'libGL.a' | head -n 1)"
if [ -z "$GL4ES_LIB" ]; then
    echo "libGL.a (gl4es) not found" >&2
    exit 1
fi

echo "=== OpenMoHAA"
cmake -S "$DEPS/openmohaa" -B "$OUT/openmohaa" "${COMMON[@]}" \
    -DSDL2_DIR="$PREFIX/lib/cmake/SDL2" \
    -DOPENAL_INCLUDE_DIR="$PREFIX/include" \
    -DOPENAL_LIBRARY="$PREFIX/lib/libopenal.so" \
    -DGL4ES_INCLUDE_DIR="$DEPS/gl4es/include" \
    -DGL4ES_LIBRARY="$GL4ES_LIB" \
    -DBUILD_TESTING=OFF
cmake --build "$OUT/openmohaa" -j "$JOBS"

echo "=== Collecting libraries into $JNILIBS"
rm -rf "$JNILIBS"
mkdir -p "$JNILIBS"

cp "$PREFIX/lib/libSDL2.so" "$JNILIBS/"
cp "$PREFIX/lib/libopenal.so" "$JNILIBS/"
for lib in libmain.so librenderer_opengl1.so librenderer_opengl2.so libgame.so libcgame.so; do
    found="$(find "$OUT/openmohaa" -name "$lib" -type f | head -n 1)"
    if [ -z "$found" ]; then
        echo "$lib was not built" >&2
        exit 1
    fi
    cp "$found" "$JNILIBS/"
done

case "$ABI" in
    arm64-v8a)   TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) TRIPLE=arm-linux-androideabi ;;
    x86_64)      TRIPLE=x86_64-linux-android ;;
    x86)         TRIPLE=i686-linux-android ;;
esac
CXXSHARED="$(find "$NDK/toolchains/llvm/prebuilt" -path "*/sysroot/usr/lib/$TRIPLE/libc++_shared.so" | head -n 1)"
cp "$CXXSHARED" "$JNILIBS/"

ls -la "$JNILIBS"
