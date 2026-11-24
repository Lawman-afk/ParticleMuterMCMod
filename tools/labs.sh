#!/bin/sh
# Runs the Lab (gradlew runLab) in a checkout of any branch, once per run asked for, one after another, each run's log in
# the log folder given (never the mods folder). As Culprit's tools/labs.sh: a watchdog closes the client at once if its
# log shows a loading error or a crash (an error screen would otherwise wait on the desktop of the person at the
# computer), and closes a run that has gone on for 6 minutes (a run takes about 1).
#
#   sh tools/labs.sh <checkout> <log folder> <log prefix> <run>...
#   runs: plain | esm | shaders (Iris and Sodium, or Oculus and Embeddium, with Sodium Extra or Rubidium Extra and the
#         Complementary pack) | iris (the same without the Extra) | extra (Sodium Extra alone: 26.3, where it and
#         Iris do not yet take the same Sodium) | irisstable (1.21.1's released Iris and Sodium pair) | dh |
#         pack (the Lab's test pack)
DIR="$1"; LOGS="$2"; PREFIX="$3"; shift 3
export JAVA_HOME=/c/Users/lawso/curseforge/minecraft/Install/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon
cd "$DIR" || exit 1
NAME=$(basename "$DIR")
close_client() {
  powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"name='java.exe' or name='javaw.exe'\" | Where-Object { \$_.CommandLine -match [regex]::Escape('$NAME') -and \$_.CommandLine -notmatch 'GradleDaemon' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" > /dev/null 2>&1
}
for run in "$@"; do
  case $run in
    plain) args="" ;;
    esm) args="-PwithESM" ;;
    shaders) args="-PwithIris -PwithSodiumExtra -PirisPack=complementary" ;;
    iris) args="-PwithIris -PirisPack=complementary" ;;
    extra) args="-PwithSodiumExtra" ;;
    irisstable) args="-PwithIris -PirisStable -PirisPack=complementary" ;;
    dh) args="-PwithDH" ;;
    pack) args="-PwithPack" ;;
    *) echo "unknown run $run"; continue ;;
  esac
  log="$LOGS/${PREFIX}_${run}.log"
  ./gradlew runLab $args --console=plain > "$log" 2>&1 &
  gradle=$!
  waited=0
  while kill -0 $gradle 2>/dev/null; do
    waited=$((waited + 3))
    if [ $waited -gt 360 ]; then
      echo "lab $run: still running after 6 minutes (a run takes about 1), closing the client"
      close_client
      break
    fi
    if grep -q "is not a valid mod file\|Mod loading has failed\|Error loading mods\|ModLoadingException\|Critical injection failure\|MixinTransformerError\|---- Minecraft Crash Report ----" "$log" 2>/dev/null; then
      echo "lab $run: loading error or crash, closing the client"
      close_client
      break
    fi
    sleep 3
  done
  wait $gradle
  echo "lab $run exit $?: $(grep -a -c 'Lab check: PASS' "$log") passed, $(grep -a -c 'Lab check: FAIL' "$log") failed"
  grep -a "Lab check: FAIL\|Critical injection\|is not a valid mod file\|Crash Report\|Mods loaded with\|could not make the test pack\|test resource pack" "$log" | cut -c1-300 | head -8
done
