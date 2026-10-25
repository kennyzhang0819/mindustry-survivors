#!/usr/bin/env bash
cd "$(dirname "$0")/.."
./build.sh > /dev/null || { echo "Build failed"; exit 1; }
out="${SURVIVORS_TEST_DIR:-$(mktemp -d)}"
limit="${SURVIVORS_TEST_TIMEOUT:-600}"
names=()
i=0
for spec in "$@"; do
  i=$((i + 1))
  mode="${spec%%:*}"
  only=""
  [ "$spec" != "$mode" ] && only="${spec#*:}"
  name="$i-$mode"
  dir="$out/$name"
  rm -rf "$dir"
  mkdir -p "$dir/Mindustry/mods" "$dir/shots"
  cp build/MindustrySurvivors.jar "$dir/Mindustry/mods/"
  names+=("$name")
  (
    APPDATA="$dir" XDG_DATA_HOME="$dir" SURVIVORS_SHOTS="$dir/shots" SURVIVORS_AUTOTEST="$mode" SURVIVORS_DAMAGE_ONLY="$only" \
    SURVIVORS_DEV=1 SURVIVORS_NOMUSIC=1 \
    java -XX:ErrorFile="$dir/hs_err_%p.log" -Duser.language=en -Duser.country=US -jar libs/Mindustry.jar > "$dir/log.txt" 2>&1 &
    pid=$!
    waited=0
    while kill -0 "$pid" 2> /dev/null; do
      if [ -n "$(ls -A "$dir/Mindustry/crashes" 2>/dev/null)" ] || ls "$dir"/hs_err_*.log > /dev/null 2>&1; then
        sleep 2
        kill "$pid" 2> /dev/null
        echo crashed > "$dir/exit"
        exit
      fi
      if [ "$waited" -ge "$limit" ]; then
        kill "$pid" 2> /dev/null
        echo "timed out" > "$dir/exit"
        exit
      fi
      sleep 2
      waited=$((waited + 2))
    done
    wait "$pid"
    echo $? > "$dir/exit"
  ) &
done
echo "Running ${#names[@]} tests in $out"
wait

failed=0
for name in "${names[@]}"; do
  dir="$out/$name"
  code=$(cat "$dir/exit" 2>/dev/null || echo missing)
  problems=()
  [ "$code" != "0" ] && problems+=("exit code $code")
  grep -q "RESULT.*done" "$dir/log.txt" || problems+=("no RESULT done ($(grep -o 'RESULT.*' "$dir/log.txt" | head -1))")
  errors=$(grep -cE '\[E\]|Exception|CRASH|ERROR DIALOG' "$dir/log.txt")
  [ "$errors" != "0" ] && problems+=("$errors error lines")
  ls "$dir"/hs_err_*.log > /dev/null 2>&1 && problems+=("Java crash dump")
  [ -n "$(ls -A "$dir/Mindustry/crashes" 2>/dev/null)" ] && problems+=("game crash report")
  if [ ${#problems[@]} -eq 0 ]; then
    echo "PASS $name"
  else
    failed=$((failed + 1))
    echo "FAIL $name: $(IFS=,; echo "${problems[*]}")"
    grep -nE -m5 -A4 '\[E\]|Exception|CRASH|ERROR DIALOG' "$dir/log.txt" | cut -c1-220 | sed 's/^/    /'
    for report in "$dir"/Mindustry/crashes/* "$dir"/hs_err_*.log; do
      [ -f "$report" ] && { echo "    $report"; grep -m8 -E 'Exception|Error|at mindustrysurvivors|IllegalState|^[A-Z]' "$report" | cut -c1-200 | sed 's/^/      /'; }
    done
  fi
done
echo "$failed of ${#names[@]} failed. Logs in $out"
[ "$failed" -eq 0 ]
