# Manual check

What the headless check (`run.sh`) cannot see: the panel's content, the tool window and the Surfer
tab. About five minutes, in your own IntelliJ IDEA 2026.2+ with the Scala plugin and LSP4IJ.

Setup: `./gradlew buildPlugin`, then Settings | Plugins | the gear icon | Install Plugin from Disk,
`build/distributions/socle-intellij-<version>.zip`, and restart. Open a Socle checkout at the pinned
tag (Verilator on the PATH for Simulate), and trust the project.

1. Open `example/test/src/gcd/GcdSpec.scala`. Within a minute, a Socle icon shows in the margin of
   each test, and the Socle tool window's Tests tab lists the suites.
2. Click a test's icon, View Design. The panel opens beside the editor and shows the GCD schematic;
   the test's row says "shown".
3. Move the cursor into another class of the file: the schematic highlights that class's boxes.
   Click a box: its code opens.
4. Hierarchy, Classes and Details fill; selecting a Hierarchy row moves the schematic. Classes lists
   the design's classes: open one, its parameterizations are open, and selecting an instance moves
   the schematic. Close the panel: all three empty. Tools | Socle |
   Show Design Panel reopens it on the same design.
5. Switch the IDE theme (light / dark): the panel follows without a reload.
6. Simulate with the default viewer: the Surfer app opens on the trace.
7. Set the wave viewer to `surfer-panel` (Settings | Tools | Socle), Simulate again: the Surfer tab
   opens, and selecting a module in the panel changes its signals. Close it, then Tools | Socle |
   Show Waveforms reopens it.
8. Language Servers view, restart Socle, then View design again: the panel (and the Surfer tab)
   follow the new server.

Note the results (and the IDE version) in the pull request.
