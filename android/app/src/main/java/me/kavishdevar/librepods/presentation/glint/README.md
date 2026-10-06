# presentation/glint/

pro's visual system: the glass material, how it catches the light, motion, haptics, and the pictures. If a screen looks like pro, it uses these. Why: `DECISIONS.md` sections 5, 10, 13, 15, 22 and 33.

| File | What it is |
|---|---|
| `GlassSurface.kt` | `Modifier.glintGlass(...)`, Glint's one glass material for controls that float in the app: backdrop blur and vibrancy, GPU edge refraction with a slight colour fringe, a tint that keeps text legible, the rim highlight, a two-layer shadow. `GlassTier` (Inline, Card, Floating) sets how high it floats; a small floating button gets the strongest shadow for its size, wide anchored bars stay soft. `GlintLight.rim()` is the one rim highlight to use. Built on the Kyant backdrop library. |
| `Glass.kt` | The glass the overlays draw (they can't use the backdrop library): rim swing with the tilt (`rimAxis`), the thickness band, `GlassPress` (swell and finger glow), the solid look for Reduce transparency. |
| `GlassTilt.kt` | `GlassLight`: one shared, ref-counted tilt source for all glass. The rim swings up to 14 degrees as you tilt the phone; level means straight overhead. The motion sensor runs only while glass that wants it is on screen. |
| `GlassBudget.kt` | Glass that goes easy on Battery Saver or a hot phone (lighter blur, no rainbow edge, more tint) and returns by itself. |
| `SystemBlur.kt` | Real blur of whatever is behind an overlay window, shaped to the glass. Needs the phone to allow window blur; otherwise a clean near-solid frosted fill. |
| `GlintEnvironment.kt` | Reduce motion, Reduce transparency and similar comfort settings (Glint's own switches plus Android's). Every animated or glassy thing must respect them. |
| `GlintMotion.kt` | Cards rise in one after another when a screen opens; spring helpers; `pressable`, the press feedback used on rows and buttons. |
| `FrameRate.kt` | Asks for the screen's fastest refresh rate while pro is open or a pop-up is up (not on Battery Saver or a hot phone; not for the pill, which is on screen for hours). |
| `GlintSymbols.kt`, `RowIcons.kt`, `IconAction.kt`, `HeartGlyph.kt` | Pictures, drawn as vectors in one rounded stroke weight: the symbols that replaced Apple's SF Symbols, small tile pictures for list rows (picked from the row's English name by `RowIcons.forName`, first match wins; rows with no match show none), round picture buttons that show their name on press-and-hold, the heart shape. |
| `GlintParts.kt` | Battery gauge, listening-mode glyphs, headphones glyph. |
| `PodsVideo.kt` | LibrePods' own 3D AirPods clips on a texture view. Jake chose these over drawn artwork: do not replace them. |
| `ConnectionStatusPanel.kt` | What the main page shows while the AirPods controls aren't connected. |

## Liquid glass, as pro applies it

The five layers of a convincing glass surface and where each lives: backdrop blur (`SystemBlur`, Kyant backdrop), edge refraction (Kyant backdrop, in-app only; Android never hands an app other apps' pixels, so overlays can't refract the home screen), a moving specular highlight (`GlassTilt`), a bright rim (`GlintLight.rim()`), and an elevation shadow (`GlassTier`). Concentric corners: an inner shape's radius is the outer radius minus the gap, floored at zero (a capsule when the gap is large), as in the pill's icon circle inside the pill. Accessibility: Reduce transparency makes glass solid with a visible border, Reduce motion removes springs and scaling, high contrast adds borders.

## Rules

- Never `remember` theme colours: rows went white on white.
- Glass can't sample other glass cleanly: group nearby controls on one surface instead of stacking panels.
- Don't put glass on repeated list rows (row tiles are cheap drawn tiles on purpose).
- New rows: add a rule to `RowIcons.rules` if the name has a clear picture, and keep related rows consistent (all or none in one list).
