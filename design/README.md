# Design source

The colour and layout source for the app's two themes. These files are what the
Compose theme (`Color.kt`, `Type.kt`, `Shapes.kt`) is written against — when a token
changes, it changes here first.

| File | What it holds |
|---|---|
| `Main.dc.html` | The full colour token table, dark beside light |
| `Charts.dc.html` | The seven-slot category chart series, both themes, plus the rules that govern it |
| `HomeDark.dc.html` / `HomeLight.dc.html` | The Home screen at 412 × 892, both themes |
| `canvas.json` | Where each board sits on the canvas |

Artboards are 412 × 892 at 1× density, so **every `px` here is a `dp` in Compose**,
one for one. No conversion.

## The two rules worth knowing before you change a colour

- **The amber `#FFB020` is identical in both themes** — same byte, and buttons use it
  as-is either way. It is what makes the app recognisably itself when the switch is
  flipped, so do not re-step it. Light carries two darker relatives *beside* it rather
  than in place of it: `accentInk #995C00` where amber has to be read as text, and
  `accentGraphic #C98A12` where it has to hold up as a bar. Both collapse back to the
  accent in dark.
- **The chart series was computed and validated, not chosen by eye** — including for
  colour-blindness. Substituting a hex by taste will quietly break a check. Amber,
  mint and coral are deliberately excluded from the series: they already mean
  *button*, *succeeded* and *failed*.

## Regenerating the canvas

The published canvas is one ~2.5 MB HTML file built from the files above. It is
generated, so it is gitignored rather than committed. Rebuild it by re-running the
`design` skill's `seed-canvas.mjs` over this folder, then republishing to the same
artifact URL so the link stays stable:

<https://claude.ai/code/artifact/97924bdd-69ab-4ae0-b63b-d476e2acacd9>
