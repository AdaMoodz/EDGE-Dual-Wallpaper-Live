# Phase 2 test asset mapping

Source folder: `C:\Users\ULTRAPC\Desktop\HU launcher`

Live HU evidence shows that the center display renders at 1440 x 1920. Phase 2 therefore keeps both compatibility profiles and uses the portrait profile first:

| Original file | Measured size | Phase 2 role | Runtime output |
|---|---:|---|---:|
| `CSD wallpaper 1920x720.png` | 1086 x 1448 | Primary center-display static test and launcher source | 1440 x 1920 for CSD; 375 x 500 for launcher |
| `HU launcher image 375x500.png` | 2048 x 768 | Wide Geely compatibility profile | 1920 x 720, not applied by default |

The APK keeps the source images unchanged. The first live test normalizes the portrait image to an exact 1440 x 1920 PNG when writing the Geely day/night staging files. The 1920 x 720 source remains packaged for a separate compatibility test.

The day and night files are distinct:

- Day uses the normalized source without color modification.
- Night uses a darker, cooler color matrix while preserving the same crop and dimensions.
- Both files are written before Geely ThemeService is asked to apply the static wallpaper.

## What is verified off-car

- Both source files decode as RGB PNG.
- Their aspect ratios exactly match the intended output ratios.
- Android debug and release APKs build successfully.
- Android lint passes.
- The primary static source is packaged as `phase2_csd_portrait_source.png`.
- The wide compatibility source is packaged as `phase2_csd_wide_source.png`.
- The portrait source is also retained as `phase2_launcher_source.png` for the next 375 x 500 launcher-layer integration.

## Verified on the CityRay HU (2026-07-02)

- Target identity: `G426_J1`, Android 11, `ecarx` userdebug firmware.
- Geely ThemeService transaction 10 applied the static wallpaper successfully.
- The active CSD surface is exactly 1440 x 1920.
- Day mode displayed the day file pixel-for-pixel; night mode displayed the darker night file pixel-for-pixel.
- Both staged files and the applied wallpaper survived a display sleep/wake cycle unchanged.
- `CityRay.png` restored successfully through the verified rollback path.
- The original source, rollback copy, day staging file, and night staging file all ended with SHA-256 `5b45e8ddfc1d5ab737f5510bf0a776108a6b5b27f7f42e645531e58e39e5400d`.
- The instrument cluster was not modified.

Full evidence is recorded in `PHASE2_LIVE_RESULTS.md`. HU restart persistence remains a separate test; display sleep/wake persistence is proven.
