#!/bin/bash
# FlowPlay 1.1.36 Phone/TV manual APK build (Kotlin + Jetpack Compose).
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH=$JAVA_HOME/bin:$PATH

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT=$SDK/build-tools/34.0.0
AAPT2=$BT/aapt2
D8=$BT/d8
ZIPALIGN=$BT/zipalign
APKSIGNER=$BT/apksigner
ANDROID_JAR=$SDK/platforms/android-34/android.jar
KEYSTORE="${FLOWPLAY_KEYSTORE:-$PROJ/.local/debug.keystore}"
export FLOWPLAY_KEYSTORE_PASSWORD="${FLOWPLAY_KEYSTORE_PASSWORD:-android}"
if [ ! -f "$KEYSTORE" ]; then
  if [ -n "${FLOWPLAY_KEYSTORE:-}" ]; then
    echo "FATAL: supplied signing keystore does not exist"; exit 1
  fi
  mkdir -p "$PROJ/.local"
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -keystore "$KEYSTORE" \
    -storepass android -keypass android -alias androiddebugkey -keyalg RSA \
    -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
  [ -x "$tool" ] || { echo "FATAL: missing SDK tool: $tool"; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "FATAL: install Android platform 34"; exit 1; }

DEPS=$PROJ/deps
VARIANT="${FLOWPLAY_VARIANT:-phone}"
case "$VARIANT" in
  phone) VARIANT_LABEL=Phone ;;
  tv) VARIANT_LABEL=TV ;;
  *) echo "FATAL: FLOWPLAY_VARIANT must be phone or tv"; exit 1 ;;
esac
OUT="$PROJ/build/$VARIANT"
ARTIFACTS="$PROJ/artifacts"
python3 "$PROJ/fetch_dependencies.py"
chmod -R u+w $OUT 2>/dev/null || true
rm -rf $OUT && mkdir -p $OUT/compiled $OUT/gen $OUT/classes $OUT/dex \
			  $OUT/aar $OUT/lib-classes $OUT/gen-lib
M=$OUT/src/main
python3 "$PROJ/prepare_variant.py" --project "$PROJ" --destination "$M" --variant "$VARIANT"

VER_CODE=39
VER_NAME="1.1.36"
APK_NAME="FlowPlay-1.1.36-$VARIANT_LABEL.apk"

# Compose compiler plugin (Kotlin 1.9.24 <-> compose compiler 1.5.14)
PLUGIN=$(ls $DEPS/compiler-1.5.14.jar)
[ -f "$PLUGIN" ] || { echo "FATAL: compose compiler plugin missing"; exit 1; }

# KMP-successor duplicates: the old collection / collection-ktx jars contain the
# same androidx.collection.* classes as collection-jvm (their KMP successor).
# d8 aborts on the duplicates, so drop the obsolete ones (newest wins).
DROP_JARS="collection-1.0.0.jar collection-ktx-1.2.0.jar kotlin-stdlib-1.8.0.jar listenablefuture-1.0.jar kotlin-stdlib-1.8.22.jar annotations-13.0.jar"
echo "=== [1/8] Extracting AARs/JARs ==="
CP="$ANDROID_JAR"
AAR_RES_ZIPS=""
> $OUT/aar_pkgs.txt
# Vendored Media3 AARs + locked Compose dependencies
for aar in $PROJ/libs/*.aar $DEPS/*.aar; do
  [ -e "$aar" ] || continue
  n=$(basename "$aar" .aar)
  d=$OUT/aar/$n
  mkdir -p "$d"
  unzip -q -o "$aar" -d "$d"
  if [ -f "$d/classes.jar" ]; then
    CP="$CP:$d/classes.jar"
    mkdir -p "$OUT/lib-classes/$n"
    (cd "$OUT/lib-classes/$n" && unzip -q -n "$d/classes.jar")
  fi
  for j in "$d"/libs/*.jar; do
    [ -f "$j" ] || continue
    CP="$CP:$j"
  done
  if [ -d "$d/res" ]; then
    # overlay-merge library resources into the app package (avoids the
    # proto-manifest that aapt2 emits when --static-lib is used on the app link)
    $AAPT2 compile --dir "$d/res" -o "$OUT/compiled/aar-$n.zip"
    AAR_RES_ZIPS="$AAR_RES_ZIPS -R $OUT/compiled/aar-$n.zip"
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" | head -1 | cut -d'"' -f2)
    echo "$n|$pkg" >> $OUT/aar_pkgs.txt
    echo "aar res: $n ($pkg)"
  fi
done
# plain jars (kotlin-stdlib et al); keep one stdlib version only.
# NOTE: the compose plugin jar and the embeddable compiler must NOT be
# packaged into the APK.
for j in $PROJ/libs/*.jar $DEPS/*.jar; do
  case "$j" in *compiler-1.5.14.jar|*kotlin-compiler-embeddable*|*kotlin-reflect*|*kotlin-script-runtime*|*trove4j*) continue;; esac
  bn0=$(basename "$j")
  case " $DROP_JARS " in *" $bn0 "*) echo "drop (dup): $bn0"; continue;; esac
  CP="$CP:$j"
  bn=$(basename "$j" .jar)
  mkdir -p "$OUT/lib-classes/plain-$bn"
  (cd "$OUT/lib-classes/plain-$bn" && unzip -q -n "$j")
done
echo "classpath entries: $(echo "$CP" | tr ':' '\n' | wc -l)"
# d8 cannot handle module-info.class / multi-release META-INF entries
find $OUT/lib-classes -path "*/META-INF/*" -delete 2>/dev/null || true

echo "=== [2/8] Compiling resources ==="
$AAPT2 compile --dir $M/res -o $OUT/compiled/res.zip

echo "=== [3/8] Linking ==="
# shellcheck disable=SC2086
$AAPT2 link -o $OUT/base.apk \
  -I "$ANDROID_JAR" \
  --manifest $M/AndroidManifest.xml \
  -R $OUT/compiled/res.zip \
  $AAR_RES_ZIPS \
  --auto-add-overlay \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code $VER_CODE \
  --version-name "$VER_NAME" \
  --java $OUT/gen \
  --output-text-symbols $OUT/R.txt
echo "R.java files: $(find $OUT/gen -name 'R.java' | wc -l)"

echo "=== [3b/8] Generating library R.java ==="
ZYNE_OUT=$OUT ZYNE_PROJ=$PROJ python3 $PROJ/gen_lib_r.py
echo "lib R.java files: $(find $OUT/gen-lib-r -name 'R.java' | wc -l)"

echo "=== [4/8] Compiling Kotlin + Java (Compose plugin) ==="
# The Compose plugin is built against kotlin-compiler-embeddable (relocated
# intellij: org.jetbrains.kotlin.com.intellij.*), so it cannot load under the
# regular kotlinc distribution. Run K2JVMCompiler from the embeddable jar.
# Kotlin compiles the .java sources directly (better than pre-compiled classes
# for inner-class resolution).
$JAVA_HOME/bin/java -cp "$DEPS/kotlin-compiler-embeddable-1.9.24.jar:$DEPS/kotlin-stdlib-1.9.24.jar:$DEPS/trove4j-1.0.20200330.jar:$DEPS/annotations-13.0.jar:$DEPS/kotlin-script-runtime-1.9.24.jar:$DEPS/kotlin-reflect-1.6.10.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 1.8 \
  -Xplugin="$PLUGIN" \
  -cp "$CP" \
  -d $OUT/classes \
  $(find $M/java $OUT/gen -name '*.kt' -o -name '*.java')
echo "compiled classes: $(find $OUT/classes -name '*.class' | wc -l)"

echo "=== [5/8] Compiling R.java ==="
$JAVA_HOME/bin/javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -cp "$CP" \
  -d $OUT/classes \
  $(find $OUT/gen $OUT/gen-lib-r -name 'R.java')
[ -f $OUT/classes/com/zynelabs/flowplay/R.class ] || { echo "FATAL: R.class missing"; exit 1; }

echo "=== [5b/8] Compiling app Java sources (kotlinc SKIPS .java files) ==="
$JAVA_HOME/bin/javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -cp "$CP:$OUT/classes" \
  -d $OUT/classes \
  $(find $M/java -name '*.java')
echo "total classes: $(find $OUT/classes -name '*.class' | wc -l)"
[ -f $OUT/classes/com/zynelabs/flowplay/data/Store.class ] || { echo "FATAL: Store.class missing"; exit 1; }

echo "=== [6/8] Dexing ==="
# Package class inputs into one jar to avoid the OS ARG_MAX limit.
mkdir -p "$OUT/program-classes"
cp -a "$OUT/classes/." "$OUT/program-classes/"
for d in "$OUT"/lib-classes/*; do
  [ -d "$d" ] || continue
  cp -a "$d/." "$OUT/program-classes/"
done
$JAVA_HOME/bin/jar cf "$OUT/program.jar" -C "$OUT/program-classes" .
$D8 --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/program.jar"
ls $OUT/dex/

echo "=== [7/8] Packaging, aligning, signing ==="
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q -j $OUT/unsigned.apk classes*.dex)
$ZIPALIGN -p 4 $OUT/unsigned.apk $OUT/aligned.apk
$APKSIGNER sign --ks $KEYSTORE --ks-pass env:FLOWPLAY_KEYSTORE_PASSWORD --out $OUT/$APK_NAME $OUT/aligned.apk
$APKSIGNER verify --print-certs $OUT/$APK_NAME | head -3
$ZIPALIGN -c -p 4 $OUT/$APK_NAME && echo "zipalign OK"

echo "=== [8/8] Verifying ==="
mkdir -p "$ARTIFACTS"
cp $OUT/$APK_NAME "$ARTIFACTS/"$APK_NAME
ls -la "$ARTIFACTS/"$APK_NAME
unzip -l "$ARTIFACTS/"$APK_NAME | grep "classes.*\.dex"
echo "BUILD DONE"
