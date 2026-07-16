# Phase 2 live CityRay HU results

Date: 2026-07-02

## Target

- Model: `G426_J1`
- Product/device: `ecarx`
- Android: 11
- Fingerprint: `qti/ecarx/ecarx:11/RQ3A.211001.001/255:userdebug/test-keys`
- App under test: `com.edge.cityray.themephase1.debug`, version `0.2-phase2-debug`

## Proven behavior

1. The app connected to `com.geely.theme/.service.themeservice.ThemeService` using `com.geely.lib.oneosapi.theme.IThemeService`.
2. A verified rollback copy was saved before Apply became available.
3. The app generated exact 1440 x 1920 day and night PNG files.
4. Geely transaction 10 (`dressStaticWallpaper(0)`) copied both files into the protected active-wallpaper locations. Geely logs reported copy success and MD5 success for both.
5. Day and night rendering were each validated on the HU. Captured wallpaper pixels matched their corresponding staged file.
6. After display sleep and wake, the custom night wallpaper returned and both staged SHA-256 values were unchanged.
7. Restore Original copied the verified `CityRay.png` backup into both day/night staging locations and reapplied it successfully.

## Test hashes

- Custom day: `aee2969a7885d52a1aec6a8b57ac65d6d23ea7340f6207f35ea1bbec419fb496`
- Custom night: `6d58d1afe89f3e30b0e4462c9bee9156a8ec6d50f5e17f0c47ffd15eb0a2ec6d`
- Original CityRay and final restored day/night: `5b45e8ddfc1d5ab737f5510bf0a776108a6b5b27f7f42e645531e58e39e5400d`

## Final HU state

- Original CityRay wallpaper restored and visually confirmed.
- Original/rollback/day/night files all match the original SHA-256.
- `csd_wall_type=1` retained.
- Instrument cluster untouched.

## Scope and caveats

- Phase 2 proves the static CSD wallpaper path. Dynamic/animated wallpaper is a different Geely pipeline and was deliberately not modified.
- The 1920 x 720 compatibility asset was not applied because this HU's active center-display surface is 1440 x 1920.
- Forcing Android UI day/night during diagnosis exposed an existing crash in `com.aleksan.button/.AdbHelperService`: it starts a foreground service without promptly calling `startForeground()`. This is unrelated to the EDGE Theme apply/restore path. Production behavior must follow the car's normal day/night transition and must not force UI mode.
- Display sleep/wake persistence is proven. A full vehicle/HU cold restart remains an optional final durability check.

## Evidence folder

`C:\Users\ULTRAPC\Documents\New project\analysis\edge_theme_phase2\live_20260702`

Important captures include `day_mode.png`, `after_apply.png`, `after_sleep_wake.png`, `restored.png`, and `final_restored.png`.
