# Socle for IntelliJ IDEA

Chisel IDE support for Scala files: a Socle icon in the margin of each ChiselSim test offers
**View design**, which opens the elaborated design in a panel beside the editor, and **Simulate**.
The schematic follows your cursor, and a click on a box opens its code. The Socle tool window lists
the project's tests (View design and Simulate on each), the design's instance hierarchy and the
details of the selection.

It is the IntelliJ counterpart of [Socle for VS Code](https://github.com/socless/socle-vscode): the
same features, through the same Socle language server.

## Requirements

- IntelliJ IDEA 2025.3 or newer (the open-source edition is enough), with the JetBrains
  [Scala plugin](https://plugins.jetbrains.com/plugin/1347-scala) and Red Hat's
  [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij).
- Java 21 or newer (set the Java path in Settings | Tools | Socle if `java` on your PATH is older).
- A Mill project that contains the Chisel design, with ChiselSim tests.
- A trusted project: Socle runs your build (Mill) and tests.

## Install

Download `socle-intellij-<version>.zip` from the
[releases page](https://github.com/socless/socle-intellij/releases), then in IntelliJ:
Settings | Plugins | the gear icon | Install Plugin from Disk. Each release bundles the language
server of one Socle release (named in its notes). To build it yourself, see
[DEVELOPING.md](DEVELOPING.md).

## Third-party software

The bundled language server ships [Surfer](https://surfer-project.org/) v0.7.0, built to
WebAssembly, for the panel's Waveform tab. Surfer is a separate work licensed under the EUPL-1.2;
its licence text and source pointer ship beside it in the server jar
(`surfer/LICENSE-EUPL-1.2.txt`, `surfer/SOURCE.md`).

## License

Copyright (C) 2026 Iban Guinebert. Licensed under the GNU AGPL-3.0-only, like Socle: see the
LICENSE file and Socle's [LICENSING.md](https://github.com/socless/socle/blob/main/LICENSING.md),
which also covers the commercial licence. The bundled language server carries its own notices
(`socle-legal/` in the jar).
