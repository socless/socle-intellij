# Changelog

Notable changes to Socle for IntelliJ IDEA, in the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format, with
[Semantic Versioning](https://semver.org/). Each release names the Socle
release whose language server it bundles.

## [Unreleased]

### Added

- The Firtool options setting (`firtoolOpts`, default `-preserve-values=named`): the firtool
  options Simulate compiles the design with, so the ports and instances firtool would remove are
  in the trace. Empty turns it off. A server older than Socle 0.26.0 ignores it.

## [0.1.3] - 2026-11-10

Bundles the language server of Socle 0.25.2.

### Changed

- The bundled server, from Socle 0.25.2: View design and Simulate run on the test module's own
  JDK when it pins one, a `test` module that is not a `TestModule` keeps its fork settings,
  FunSuite tests inside a `def` get their lens, and a project opened above or below its
  `build.mill` works. Waveforms of a design whose top `io` bundle is empty follow the
  schematic, and a keyboard click after a pan is no longer swallowed.

## [0.1.2] - 2026-11-10

Bundles the language server of Socle 0.25.1.

### Changed

- The bundled server, from Socle 0.25.1: the design panel fits a narrow editor split, with no
  scrollbars around the page.

## [0.1.1] - 2026-11-10

### Changed

- The plugin's logo fits the Platform's 40 x 40 frame, and the dark theme gets its own: the
  brand's symbol alone. Bundles the language server of Socle 0.25.0.
- The Marketplace page and the README show screenshots and an animation of the plugin, in dark
  and light.
- The Tools | Socle menu shows icons on Show Design Panel, Refresh Tests and Settings.

### Fixed

- `runIde` starts the language server: its sandbox now gets the server jar. `runIdeLocal` runs the
  plugin in an installed IDE, whose JCEF the design panel needs.

## [0.1.0] - 2026-11-06

### Added

- The first version: View design and Simulate on ChiselSim tests, the design panel beside the
  editor, the Socle tool window (Tests, Hierarchy, Details), the embedded Surfer tab and the Socle
  settings, at parity with Socle for VS Code. Bundles the language server of Socle 0.25.0.
  Needs IntelliJ IDEA 2026.2 or newer with the Scala plugin and LSP4IJ.
