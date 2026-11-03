#!/usr/bin/env bash
# End-to-end check in a real IDE under Xvfb: an isolated IntelliJ (config, system, plugins) with
# Socle, its companion driver (e2e/), LSP4IJ and the Scala plugin opens a Socle checkout, views a
# ChiselSim test and navigates in the panel. Needs Verilator-free ChiselSim view mode only.
#
#   SOCLE_ROOT=/tmp/socle e2e/run.sh            # a Socle clone at the pinned tag (the server runs Mill there)
#
# Env: IDEA_HOME (default /usr/share/idea, an IntelliJ IDEA 2026.2+ with its JBR and JCEF),
#      SOCLE_E2E_SPEC (default example/test/src/gcd/GcdSpec.scala), GRADLE_CACHE (default ~/.gradle/caches).
# Exits 0 when the driver wrote PASS. Build first: ./gradlew buildPlugin :e2e:buildPlugin
set -euo pipefail

: "${SOCLE_ROOT:?set SOCLE_ROOT to a Socle checkout}"
IDEA_HOME=${IDEA_HOME:-/usr/share/idea}
SPEC=${SOCLE_E2E_SPEC:-example/test/src/gcd/GcdSpec.scala}
CACHE=${GRADLE_CACHE:-$HOME/.gradle/caches}
here=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/socle-e2e.XXXXXX")
mkdir -p "$work/config" "$work/system" "$work/plugins" "$work/log"

find_plugin() { find "$CACHE" -maxdepth 5 -type d -path "*transformed/$1-*" | sort | tail -1; }
lsp4ij=$(find_plugin com.redhat.devtools.lsp4ij)
scala=$(find_plugin org.intellij.scala)
[ -n "$lsp4ij" ] && [ -n "$scala" ] || { echo "LSP4IJ or the Scala plugin is not in $CACHE (run ./gradlew buildPlugin)" >&2; exit 2; }
cp -r "$lsp4ij/"* "$work/plugins/" 2>/dev/null || cp -r "$lsp4ij" "$work/plugins/"
cp -r "$scala/"* "$work/plugins/" 2>/dev/null || cp -r "$scala" "$work/plugins/"
unzip -q "$here"/build/distributions/socle-intellij-*.zip -d "$work/plugins"
unzip -q "$here"/e2e/build/distributions/e2e.zip -d "$work/plugins"

cat > "$work/idea.properties" <<PROPS
idea.config.path=$work/config
idea.system.path=$work/system
idea.plugins.path=$work/plugins
idea.log.path=$work/log
PROPS
{ cat "$IDEA_HOME/bin/idea64.vmoptions"
  echo "-Didea.trust.all.projects=true"
  echo "-Djb.consents.confirmation.enabled=false"
  echo "-Djb.privacy.policy.text=<!--999.999-->"
  echo "-Didea.initially.ask.config=never"
  echo "-Dide.show.tips.on.startup.default.value=false"
  echo "-Didea.suppress.statistics.report=true"
  echo "-Dawt.toolkit.name=XToolkit"
  echo "-Didea.log.debug.categories=#socle.intellij"
  echo "-Dide.browser.jcef.log.level=info"
  echo "-Dide.browser.jcef.gpu.disable=true"
} > "$work/idea.vmoptions"

out="$work/e2e.out"
echo "e2e: work dir $work"
env -u WAYLAND_DISPLAY SOCLE_E2E_OUT="$out" SOCLE_E2E_SPEC="$SPEC" IDEA_PROPERTIES="$work/idea.properties" IDEA_VM_OPTIONS="$work/idea.vmoptions" \
  timeout 660 xvfb-run -a -s "-screen 0 1600x1000x24 +extension GLX +extension RANDR +render -nolisten tcp" "$IDEA_HOME/bin/idea.sh" "$SOCLE_ROOT" > "$work/ide.out" 2>&1 || true
cat "$out" 2>/dev/null || echo "(no driver output)"
tail -n 1 "$out" 2>/dev/null | grep -q '^PASS' || { echo "e2e FAILED: see $work/log/idea.log"; exit 1; }
echo "e2e passed"
