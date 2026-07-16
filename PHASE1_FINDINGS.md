# EDGE Theme Phase 1 — CityRay G426_J1

Validated live on the Geely CityRay HU at `192.168.100.65` on 2026-07-01.

## Confirmed platform

- Model: `G426_J1`
- Device: `ecarx`
- Android: 11
- Geely theme package: `com.geely.theme`
- Theme package build: `1.0.20241230G(11a)`
- Current wallpaper type returned by the system service: `0` (static)

## Confirmed no-root integration

The exported service below accepts the official Geely theme Binder contract:

- Component: `com.geely.theme/.service.themeservice.ThemeService`
- Interface: `com.geely.lib.oneosapi.theme.IThemeService`
- Transaction 6: `getCurrentWallpaperType()`
- Transaction 10: `dressStaticWallpaper(int screenType)`
- CSD screen type: `0`

Transaction 10 executes inside the Geely system process (UID 1000). This is the correct no-root apply path. Calling Geely Java classes through `createPackageContext()` is not equivalent: that code still runs with the caller app UID and cannot write `/mnt/ivres`.

## Confirmed paths

Staging paths writable with user-approved all-files access:

- Day: `/sdcard/XUI/static_wallpaper/app_saved_csd_static_wallpaper.png`
- Night: `/sdcard/XUI/static_wallpaper/dark_app_saved_csd_static_wallpaper.png`

Protected active paths:

- Day: `/mnt/ivres/XUI/static_wallpaper/current_csd_static_wallpaper.png`
- Night: `/mnt/ivres/XUI/static_wallpaper/dark_current_csd_static_wallpaper.png`

The active files return `EACCES` to both ADB shell and an ordinary application. The Phase-1 app therefore refuses to enable Apply until it has complete day/night backup files.

## Safety result

- No wallpaper was changed.
- No staging wallpaper was created.
- The active Geely wallpaper component remains `com.geely.theme/.wallpaper.GeelyWallpaperService`.
- The pre-test screenshot is in `analysis/edge_theme_phase1/backup_20260701_014818/before.png`.
- The installed debug app can bind to ThemeService and query state, but Apply and Restore remain disabled because a reversible backup is unavailable.

## Firmware defect excluded from the app

ThemeService transaction 7 (`getOwnerStaticWallpaperData`) crashes `com.geely.theme` when the Geely account is not logged in. Its internal code constructs `GoodsDetailActivity` directly and calls `showLoginDialog()` without a valid Activity context. The probe was stopped, the call was removed, the safe APK was reinstalled, and a 10-second stability check produced an empty crash buffer.

## Phase 2 requirement

Before enabling theme application, implement a recoverable rollback source. Acceptable options are:

1. Reapply a known original Geely theme record through `applyWallpaperByLauncher` after obtaining that record without transaction 7.
2. Export the current protected day/night files through an existing privileged Geely service that supports file transfer.
3. Require the user to select and save a known restore theme before custom CSD application.

Do not weaken the backup gate merely to make the demo apply.
