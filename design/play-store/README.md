# Play Store listing assets

Rendered from `design/play-store-icon.svg` and the HTML source below, so the store artwork and the
launcher icon cannot drift apart — both draw the same paths in the same 108-unit coordinate space.

| File | Play requirement | Status |
|---|---|---|
| `play-store-icon-512.png` | 512 × 512 PNG, no transparency | ✅ ready |
| `feature-graphic-1024x500.png` | 1024 × 500 PNG/JPG, no transparency | ✅ ready |
| Phone screenshots | 2–8, 16:9 or 9:16 | ❌ **not produced — see below** |
| Tablet screenshots | optional | ❌ not produced |

Both PNGs are 8-bit RGB with no alpha channel, which is what Play requires; a PNG with alpha is
rejected at upload.

## Screenshots are deliberately missing

They have to come from a real running app, and the development emulator holds the owner's **real
PhonePe/Google Pay statements** — actual payee names and amounts. Those cannot go into this
repository, which is public, and should not go onto a public store listing.

Capture them yourself against synthetic data. The emulator is a tablet, so give it phone metrics
first and reset afterwards:

```bash
adb -s emulator-5556 shell wm size 1080x2400 && adb -s emulator-5556 shell wm density 420
```

```bash
adb -s emulator-5556 shell screencap -p /sdcard/shot.png && adb -s emulator-5556 pull /sdcard/shot.png design/play-store/screenshot-01.png
```

```bash
adb -s emulator-5556 shell wm size reset && adb -s emulator-5556 shell wm density reset
```

Worth capturing, in listing order: Home on *Pulse* with the anomaly callout, the *Categories*
dashboard, a session mid-mapping, the payee directory, and search.

## Regenerating the PNGs

There is no `rsvg-convert` or ImageMagick on this machine; headless Chrome does the job and needs an
**absolute** output path or it fails silently:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=512,512 --screenshot="$PWD/design/play-store/play-store-icon-512.png" "file://$PWD/design/play-store/icon.html"
```

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1024,500 --screenshot="$PWD/design/play-store/feature-graphic-1024x500.png" "file://$PWD/design/play-store/feature-graphic.html"
```

Colours come from the design system's dark palette (`core/designsystem/.../theme/Color.kt`) — amber
`#FFB020` on the near-black greens `#0E1512`/`#141C18` — not from values invented for the store.
