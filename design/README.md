# Design source

Everything the app is built to look like. These files are what the Compose theme
(`Color.kt`, `Type.kt`, `Shape.kt`) and every screen are written against — when
something changes, it changes here first.

There are **two sets**, made at different times, and the difference matters:

**The theme set** — the palette, type and shape the whole app wears.

| File | What it holds |
|---|---|
| `Main.dc.html` | The full colour token table, dark beside light |
| `Charts.dc.html` | The seven-slot category chart series, both themes, plus the rules that govern it |
| `HomeDark.dc.html` / `HomeLight.dc.html` | The Home screen at 412 × 892, both themes |
| `canvas.json` | Where each board sits on the canvas |

**The dashboard set** — the navigation shell and the seven-dashboard system.

| File | What it holds |
|---|---|
| `ReimaginedApp.dc.html` | The tappable prototype: every screen and state. **The source of truth for layout** |
| `DashboardSpec.dc.html` | The written contract — widget-by-widget behaviour, data needs, states, copy |
| `CurrentApp.dc.html` | A recreation of the app as it was, for comparison |

Artboards are 412 × 892 at 1× density, so **every `px` here is a `dp` in Compose**,
one for one. No conversion.

## Where the two sets disagree

They do disagree, and the conflict is already settled — do not re-open it:

- **On colour, the theme set wins.** `DashboardSpec.dc.html` §1 offers three palette
  variants (Pulse blue, Ledger teal, Feed warm). All three are **superseded**. The
  amber-on-green palette in `Main.dc.html` is what ships, and the dashboards are to be
  re-skinned into it. Section 1 of the spec is kept only for the roles it names
  (`pos`, `neg`, `warn`, `card2`, `navbg`), not for its hex values.
- **On the navigation shell and everything structural, the dashboard set wins.** It is
  later. The bar is Variant A's — Home · Statements · centre `+` · Payees · You — with
  tab chips rather than dots for moving between dashboards. The five-tab bar drawn in
  `HomeLight.dc.html` is out of date.
- **On typography, Sora + JetBrains Mono win**, not the Roboto named in the spec's
  variant table — same supersession as the palette.

The decisions behind all of this are recorded in the two design-review comments on
[issue #16](https://github.com/SandeepChorge/transactions-platform/issues/16).

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
