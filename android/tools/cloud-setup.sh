#!/usr/bin/env bash
# One-command setup so a cloud session can build and test pro offline.
#
# Cloud sessions can't reach dl.google.com or maven.google.com (where Android's libraries and SDK
# live), and have no Docker daemon. So this takes the three things the build needs from places
# that ARE reachable:
#   1. the Gradle library cache   - the "ci-deps-snapshot" release of this repository
#   2. the Android SDK + NDK      - layers of the cimg/android image, fetched from Docker Hub directly
#   3. Robolectric's Android image - one jar from Maven Central (the unit tests need it)
#
# Usage:   android/tools/cloud-setup.sh [WORKDIR]        (default: $HOME/pro-build)
# Then:    source WORKDIR/env.sh
#          cd android && ./gradlew $GRADLE_FLAGS :app:testFossDebugUnitTest
#          ./gradlew $GRADLE_FLAGS lintFossRelease assembleFossRelease     (what CI runs)
#
# Safe to run again: anything already in WORKDIR is skipped. Needs: curl, tar, python3.
# Untrusted downloads are unpacked only into WORKDIR subfolders, never into the repository.
set -euo pipefail

WORK="${1:-$HOME/pro-build}"
REPO="${PRO_REPO:-Jbyjre/pro3}"
IMAGE_TAG="${CIMG_TAG:-2026.08.1-ndk}"
# The layers of that image (counting from 0) that hold the SDK, build tools, platforms, CMake and
# NDK 29. Found by listing the image's layers; the tag is fixed, so these don't move.
LAYERS="${CIMG_LAYERS:-12 13 14 17 19}"
ROBO_JAR="${ROBO_JAR:-android-all-instrumented-16-robolectric-13921718-i7.jar}"
# The folder on Maven Central is the jar name without "android-all-instrumented-" and ".jar".
ROBO_VERSION="${ROBO_JAR#android-all-instrumented-}"; ROBO_VERSION="${ROBO_VERSION%.jar}"
ROBO_URL="https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/${ROBO_VERSION}/${ROBO_JAR}"

mkdir -p "$WORK"/{dl,gradle-home,sdkroot,robo}
cd "$WORK"

echo "== 1/3 Gradle libraries (release ci-deps-snapshot)"
if [ ! -d gradle-home/caches/modules-2 ]; then
  curl -sS -L -C - -o dl/gradle-deps.tgz "https://github.com/$REPO/releases/download/ci-deps-snapshot/gradle-deps.tgz"
  tar xzf dl/gradle-deps.tgz -C gradle-home
  rm -f dl/gradle-deps.tgz
else echo "   already there"; fi

echo "== 2/3 Android SDK and NDK (cimg/android:$IMAGE_TAG layers: $LAYERS)"
SDK="$WORK/sdkroot/home/circleci/android-sdk"
if [ ! -d "$SDK/platforms" ]; then
  TOK=$(curl -sS "https://auth.docker.io/token?service=registry.docker.io&scope=repository:cimg/android:pull" \
        | python3 -I -c "import sys,json;print(json.load(sys.stdin)['token'])")
  AUTH=(-H "Authorization: Bearer $TOK")
  INDEX_ACCEPT="application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json"
  MANIFEST_ACCEPT="application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json"
  # The tag points at a list of platforms; take the linux/amd64 one, then its layers.
  AMD64=$(curl -sS "${AUTH[@]}" -H "Accept: $INDEX_ACCEPT" "https://registry-1.docker.io/v2/cimg/android/manifests/$IMAGE_TAG" \
          | python3 -I -c "import sys,json;m=json.load(sys.stdin)['manifests'];print([x for x in m if x.get('platform',{}).get('architecture')=='amd64'][0]['digest'])")
  curl -sS "${AUTH[@]}" -H "Accept: $MANIFEST_ACCEPT" "https://registry-1.docker.io/v2/cimg/android/manifests/$AMD64" > dl/manifest.json
  for i in $LAYERS; do
    DIGEST=$(python3 -I -c "import sys,json;print(json.load(open('dl/manifest.json'))['layers'][int(sys.argv[1])]['digest'])" "$i")
    echo "   layer $i ($DIGEST)"
    curl -sS -L "${AUTH[@]}" -o "dl/layer$i.tgz" "https://registry-1.docker.io/v2/cimg/android/blobs/$DIGEST"
    tar xzf "dl/layer$i.tgz" -C sdkroot --exclude='home/circleci/.android'
    rm -f "dl/layer$i.tgz"
  done
else echo "   already there"; fi

echo "== 3/3 Robolectric Android image ($ROBO_JAR)"
# A real jar is a zip of about 200 MB. Maven Central sometimes answers with a short "rate
# limited" text instead (HTTP 429); a file like that makes every drawing test crash with
# "Unable to find static field mNativePtr". So: fail on HTTP errors, check the file, retry.
robo_ok() { [ -f "robo/$ROBO_JAR" ] && [ "$(head -c 2 "robo/$ROBO_JAR")" = "PK" ] && [ "$(stat -c %s "robo/$ROBO_JAR")" -gt 10000000 ]; }
if ! robo_ok; then
  rm -f "robo/$ROBO_JAR"
  for wait in 2 4 8 16 32; do
    curl -sS -L -f -o "robo/$ROBO_JAR" "$ROBO_URL" && robo_ok && break
    echo "   download failed or rate-limited; trying again in ${wait}s"
    rm -f "robo/$ROBO_JAR"; sleep "$wait"
  done
  robo_ok || { echo "   could not download $ROBO_JAR (Maven Central rate limit?). Run this script again later."; exit 1; }
else echo "   already there"; fi

cat > env.sh <<ENV
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export GRADLE_USER_HOME="$WORK/gradle-home"
export ROBOLECTRIC_DEPS_DIR="$WORK/robo"
# NDK 30 isn't in the image: use the NDK and CMake that are.
export GRADLE_FLAGS="--offline -PndkVersion=29.0.14206865 -PcmakeVersion=4.1.2"
ENV

echo
echo "Done. Next:  source $WORK/env.sh"
echo "Then:        cd android && ./gradlew \$GRADLE_FLAGS :app:testFossDebugUnitTest"
