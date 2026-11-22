#!/usr/bin/env bash
# Downloads the native dependencies of the Android port at pinned revisions.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEPS="${DEPS_DIR:-$HERE/deps}"
mkdir -p "$DEPS"

fetch() { # name url ref
    local name="$1" url="$2" ref="$3"
    if [ -d "$DEPS/$name/.git" ]; then
        echo "== $name already present"
        return
    fi
    echo "== Fetching $name @ $ref"
    git init -q "$DEPS/$name"
    git -C "$DEPS/$name" remote add origin "$url"
    git -C "$DEPS/$name" fetch -q --depth 1 origin "$ref"
    git -C "$DEPS/$name" checkout -q FETCH_HEAD
}

# OpenMoHAA engine + Android patch (re-applied on every run)
fetch openmohaa   https://github.com/openmoh/openmohaa.git   a2f340195975f4f042e28a60b62561dd9a0b2700
echo "== Applying patches/openmohaa-android.patch"
git -C "$DEPS/openmohaa" reset -q --hard
git -C "$DEPS/openmohaa" clean -q -fdx
git -C "$DEPS/openmohaa" apply --whitespace=nowarn "$HERE/../patches/openmohaa-android.patch"

# Keep SDL in sync with the Java files in app/src/main/java/org/libsdl/app
fetch SDL2        https://github.com/libsdl-org/SDL.git      98d1f3a45aae568ccd6ed5fec179330f47d4d356 # release-2.32.8
fetch openal-soft https://github.com/kcat/openal-soft.git    dc7d7054a5b4f3bec1dc23a42fd616a0847af948 # 1.24.3
fetch gl4es       https://github.com/ptitSeb/gl4es.git       8b1a3da5d2331d0f88b70be25963182d46eed857
