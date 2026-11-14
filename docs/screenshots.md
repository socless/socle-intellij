# Screenshots for the Marketplace page and the README

A shot list for the plugin's images: four screenshots and one GIF, each in a dark and a light
version. The images go in `docs/images/` under the names below; the README already points at them.

## Setup (once)

1. **The design.** A Socle checkout at the tag the plugin bundles (`socleVersion` in
   `gradle.properties`, `v0.28.0` today), with Verilator on the PATH for Simulate:
   `git -C ~/dev/socless worktree add /tmp/socle-shots v0.28.0`. Open that folder in IntelliJ,
   trust it, and let Mill import it. Run View design once on each test used below, so the shots are
   not taken during the first compile.
2. **The plugin.** `./gradlew runIdeLocal -PlocalIde=/usr/share/idea` from this repository (the
   folder of an installed IntelliJ of the same build): a throwaway IDE (config, caches and plugins
   under `.intellijPlatform/sandbox/`, your own IDE untouched) with the plugin, the Scala plugin and
   LSP4IJ. Not plain `runIde`: its IDE has no JCEF, so the panel stays empty. The first start asks
   the usual first-run questions; answer them as a new user would.
3. **The IDE.**
   - Theme: the default **Dark** for the dark series, **Light** for the light one (Settings |
     Appearance & Behavior | Appearance). The panel follows the theme by itself; change nothing
     else between the two series.
   - Editor font: 15 pt (Settings | Editor | Font), so the code reads at 1280 px.
   - Close every tool window except the one a shot needs (Project, Run, Terminal, Problems...), turn
     off the Problems widget and notification popups (Do Not Disturb in the notifications), and close
     every editor tab except those named in the shot.
   - No personal paths in sight: the title bar shows the project folder, `socle-shots` is fine.
   - Less noise in the editor: Settings | Editor | Inlay Hints, Code vision off (it shows the Git
     author of each class); Show Sticky Lines off (Find Action); highlighting level Syntax for each
     file shown (Ctrl+Alt+Shift+H), so no warning stripe or wavy underline; View | Appearance, Tool
     Window Bars off, which gives the panel the width its header needs.
4. **The window.** Shots are 1280 x 800 (16:10), the Marketplace's recommended size. Make the
   IntelliJ window exactly 1280 x 800 *logical* pixels, so the text and the layout have the size
   the shot will show: on a 2x HiDPI screen it captures at 2560 x 1600 and is halved afterwards
   (crisp); on a 1x screen it captures at 1280 x 800 and needs no resize. Do not capture a larger
   window and shrink it: the 15 pt text would end up around 10 pt. Capture the window only (no
   desktop, no shadow). On KDE, a window rule (Window Rules, Size) holds the size; elsewhere, a
   1280 x 800 virtual display or the IDE in a nested compositor (`cage`, `gamescope -w 1280 -h 800`).

## The shots

Line numbers are those of the `v0.28.0` sources.

### 01 - The schematic follows the cursor (the hero)

- Files: `example/test/src/rvpipeline/RVPipelineSpec.scala`, then
  `example/src/rvpipeline/RVPipeline.scala`.
- Steps:
  1. In `RVPipelineSpec.scala`, the margin icon on line 8, **View design**. The panel opens to the
     right and shows the `RVPipeline` top: fetch, decode, exec, wb and rf.
  2. Open `RVPipeline.scala` in the left split (close `RVPipelineSpec.scala`). Put the cursor on
     line 104 (`io.out.bits.result := io.in.bits.rs1 + io.in.bits.rs2`), inside `class Exec`.
  3. The schematic highlights the `exec` box. Scroll the editor so lines 96 to 107 (the whole
     `Exec` class) sit in the middle.
- In frame: the code on the left (about 45% of the width), the panel on the right with the whole
  pipeline visible (Fit if needed) and `exec` highlighted. No tool window.
- Caption: *The schematic follows your cursor: the class you are editing is highlighted in the
  elaborated design.*

### 01 (GIF) - the same scene, moving

- Same layout as shot 01. Start with the cursor at the end of line 65 (`val pc = RegInit...`, in
  `Fetch`), the editor scrolled so `class Fetch` is near the top.
- Steps: hold the cursor about 2 seconds on each class, moving it down one line at a time with the
  arrow key (about 9 lines a second); the editor scrolls along and the panel follows within half a
  second:
  1. Line 65, `Fetch`: `fetch` highlighted.
  2. Down to line 89, `Decode`: `decode` highlighted.
  3. Down to line 104, `Exec`: `exec` highlighted.
  4. Down to line 110, `WB`: `wb` highlighted. Hold 2 s.
- Length: about 14 s, looped, 12 fps. (A click on a box selects it and lights its wires; it does not
  move the editor, so the GIF shows the code-to-schematic direction only.)

### 02 - Waveforms that follow the selection

- Setting: Settings | Tools | Socle, wave viewer `surfer-panel`, "Waves follow the selection" on.
- File: `example/test/src/gcd/GcdSpec.scala`.
- Steps:
  1. The margin icon on the test (line 9, `it("computes gcd(48, 18)")`), **View design**, then
     the same icon, **Simulate**. The Surfer tab opens in a split to the right.
  2. Close `GcdSpec.scala`, so the window has two columns: the panel on the left and the Surfer tab
     on the right (drag the tabs if IntelliJ put them elsewhere). About 50/50.
  3. With "Waves follow the selection" on, Surfer already lists the GCD module's signals (`io`,
     and `x`, `y`, `busy` under Registers). If not: in the panel, click the `x` register. Surfer shows its signals; if it shows only `x`, select the
     GCD module itself (its row in the Socle tool window's Hierarchy tab) so `x`, `y`, `busy` and `io` are listed,
     then hide the tool window again.
  4. In Surfer, View | Toggle side panel (the waves get the width), then View | Zoom to fit: `x` and `y` step down from 48 and 18 to 6, then
     `io.out.valid` rises. Put the cursor on the cycle where `io.out.valid` rises.
- In frame: the schematic, and the waves with the 48/18 -> 6 descent readable (values shown in
  decimal: right-click each signal, Format, Unsigned). The cursor's value column then reads
  a = 48, b = 18, x = y = result = 6. For the light series, Surfer keeps its own dark theme: set
  View | Theme | light+.
- Caption: *Simulate runs the ChiselSim test with waveforms; Surfer opens beside the schematic and
  shows the signals of what you select.*

### 03 - Where it starts: the margin icon

- File: `example/test/src/gcd/GcdSpec.scala`, alone in the editor, no panel open (close it).
- Steps: click the Socle icon in the margin of line 9; its menu (**View design**, **Simulate**)
  opens. Take the shot with the menu open, the mouse pointer on View design.
- In frame: the editor only, the test body (lines 7 to 30) visible. Since the editor alone
  looks empty at 1280 px, open the Socle tool window on the left on its Tests tab: it shows the
  same test listed. Collapse the `socle-probe` fixture suites there (BrokenSpec, ConfigSpec,
  NoSimulateSpec: their tests are named "broken module" and the like); closing the panel refreshes
  the list and expands them again, so collapse them last.
- Caption: *Each ChiselSim test gets a Socle icon in the margin: View design or Simulate in one
  click.*

### 04 - The Socle tool window

- Files: `example/test/src/deephier/DeepSocSpec.scala`: the deepest hierarchy among the examples,
  so the Hierarchy tab has something to show.
- Steps:
  1. The margin icon on line 8 (`test("DeepSoc elaborates and steps")`), **View design**.
  2. Open the Socle tool window (left), **Hierarchy** tab, expand it two or three levels, and select
     a core (`cluster0` > `cores_0`, a `Core`). The schematic goes there.
  3. Close the spec, so the window is: the tool window on the left (about 25%), the panel on the
     rest.
  4. Take a second frame of the same scene on the **Details** tab (the selection's class,
     parameters and ports), and keep the better one. Hierarchy is the default.
- Caption: *The Socle tool window lists the project's tests, the design's hierarchy and the
  details of the selection.*

## Post-processing

Screenshots: crop to 16:10 (a no-op on an exact capture), then halve a HiDPI capture
(ImageMagick; `-resize` keeps the aspect ratio, so without the crop a capture slightly off 16:10
comes out under 1280 x 800):

```sh
magick capture.png -gravity center -crop 16:10 +repage -resize 1280x800 -strip docs/images/01-cursor-follow-dark.png
```

GIF: the committed ones were recorded as a frame sequence (`grim` every 1/12 s on a headless
1280 x 800 output) and encoded with `ffmpeg` (`palettegen` / `paletteuse`, 128 colours, about
1.7 MB each). Or record the window to an MP4 with your desktop's recorder (Spectacle on KDE, Kooha on GNOME,
or `wf-recorder -g "$(slurp)" -f in.mp4` on wlroots), then:

```sh
mkdir frames && ffmpeg -i in.mp4 -vf "fps=12,scale=1280:-1:flags=lanczos" frames/%04d.png
gifski --fps 12 --width 1280 --quality 80 -o docs/images/01-cursor-follow-dark.gif frames/*.png
```

Aim for under 5 MB (GitHub shows larger GIFs, but slowly); lower `--quality` or the fps if needed.

## Files

| File | Shot |
|---|---|
| `docs/images/01-cursor-follow-dark.gif`, `-light.gif` | 01, moving (README top, Marketplace second) |
| `docs/images/01-cursor-follow-dark.png`, `-light.png` | 01 |
| `docs/images/02-waveforms-dark.png`, `-light.png` | 02 |
| `docs/images/03-margin-icon-dark.png`, `-light.png` | 03 |
| `docs/images/04-tool-window-dark.png`, `-light.png` | 04 |

## Marketplace upload

The Marketplace page has no light or dark mode, so it gets one series: the **dark** one, IntelliJ's
default theme. The light series serves the README for readers in a light GitHub theme.

The IDE's own Marketplace view shows a GIF as a still image (its first frame), so the page leads
with the PNG and the GIF comes second; start the GIF on a frame that reads on its own (the cursor in
`Fetch`, `fetch` highlighted).

In the plugin's admin page, Media, in this order, each with its caption from above:

1. `01-cursor-follow-dark.png`
2. `01-cursor-follow-dark.gif`
3. `02-waveforms-dark.png`
4. `03-margin-icon-dark.png`
5. `04-tool-window-dark.png`

Check before uploading: 1280 x 800 each, no personal path, no notification, no error in sight, text
readable at 100% zoom.
