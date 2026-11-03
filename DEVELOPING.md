# Developing Socle for IntelliJ IDEA

The plugin is Kotlin, built with Gradle (`./gradlew`) and the IntelliJ Platform Gradle plugin. It
needs Java 21 or newer.

```bash
./gradlew test          # unit and platform tests
./gradlew runIde        # a sandbox IDE with the plugin (and the Scala plugin and LSP4IJ)
./gradlew buildPlugin   # build/distributions/socle-intellij-<version>.zip
./gradlew verifyPlugin  # the JetBrains plugin verifier
```

## The bundled language server

The plugin runs the Socle language server, a jar inside the plugin zip (`server/socle-lsp.jar`).
`gradle.properties` pins the Socle release it comes from:
- `socleVersion` is the version;
- `socleLspSha256` is the sha256 of that release's `socle-lsp-<version>.jar`, as listed in its
  `SHA256SUMS`.

`./gradlew serverJar` downloads the jar from `socless/socle` by HTTPS, and falls back to
`gh release download` (a logged-in `gh`) when that fails. A jar whose sha256 differs fails the
build.

To bump the server:
1. Set `socleVersion` to the new release, and `socleLspSha256` to the sha256 from
   `gh release download v<new> --repo socless/socle --pattern SHA256SUMS`.
2. Run the end-to-end check against that release.
3. Note the new server version in the changelog.

To try a server that is not released yet, point `SOCLE_LSP_JAR` at a local jar (for example
`out/socle-lsp/assembly.dest/out.jar` of a Socle checkout) before `./gradlew runIde` or
`./gradlew buildPlugin`.

The plugin talks to the server through LSP and the `socle/*` methods listed in Socle's
`docs/lsp-protocol.md`.

## CI and releases

- **CI:** runs the tests, the plugin verifier and builds the zip (bundling the pinned server) on
  pull requests and by hand.
- **Releases:** a `v*` tag runs `release.yml`, which checks the version against `pluginVersion` and
  the notes (`tools/release/notes.sh`), builds the zip and publishes it as a GitHub release. There
  is no JetBrains Marketplace publishing for now.
