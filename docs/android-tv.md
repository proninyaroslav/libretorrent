# Android TV remote navigation

The TV adjustments are enabled for television UI mode or devices advertising
`FEATURE_LEANBACK`. Phones and tablets keep the existing touch behavior.

## Controls

- In the torrent list, select a card with OK to open its details. Left moves from
  the card to its pause/resume button; Right returns to that same card.
- Play/Pause on remotes that have it toggles the focused torrent.
- In details, Left/Right moves between tabs and OK selects the tab. Down enters
  the current page (the file list on Files); Up from a tab reaches pause/resume.
- In the file list, OK opens a folder or a downloaded file. Right reaches the
  download-priority checkbox, Left returns to the row. These directions are
  mirrored in RTL layouts.
- Long-press OK on a torrent or file row to enter selection mode. OK then toggles
  selected rows; the existing contextual toolbar supplies the actions.
- Torrent/file names use two lines on TV, preserving episode numbers at the start
  of long names instead of ellipsizing the middle.
- A primary-color outline identifies the focused control separately from an
  opened torrent, active tab, or checked file.

## Implementation

`TvNavigation` prevents the internal ViewPager2 RecyclerView from taking focus
before page content, excludes cached offscreen pages from focus, links nested
actions using view references (row IDs repeat
in RecyclerView), and exposes remote selection. The details app bar stays visible
on TV so scrolling cannot hide its controls. `TvFocusHighlight` renders one
outline above the activity's focused view and follows scrolling/clipping without
changing the views' backgrounds or selected states.

The same pager and nested-checkbox fixes apply to the Add Torrent screen.
Fragment lifecycle callbacks also configure nested lists, including newly created
pages, so an empty list cannot take focus away from usable controls.

## Validation

`TvNavigationTest` sends D-pad events to attached Android views. It covers:

- Navigation between a row and its action with duplicate RecyclerView row IDs,
  including RTL layouts and one Play/Pause toggle per key press.
- Entering and leaving ViewPager2 content, switching pages, and preventing focus
  from moving into cached offscreen pages.
- Phone and tablet configurations retaining their original text layout,
  RecyclerView/ViewPager2 focus behavior, app-bar scrolling, and click,
  long-click, and key handlers. Run this test on a non-TV device or emulator;
  devices advertising `FEATURE_LEANBACK` intentionally remain in TV mode.

Build and run the local checks:

```sh
./gradlew :app:assembleBaseDebug :app:assembleBaseDebugAndroidTest \
  :app:testBaseDebugUnitTest :app:lintBaseDebug
```

After installing the debug app and instrumentation APK, run navigation tests on
both a TV and a non-TV device:

```sh
adb -s DEVICE shell am instrument -w \
  -e class org.proninyaroslav.libretorrent.ui.TvNavigationTest \
  org.proninyaroslav.libretorrent.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Manual D-pad checks on a JMGO K931CN projector (Android 14/API 34) covered
pause/resume, opening details, entering the Files tab, nested folders, scrolling,
file-priority checkboxes, long-press selection, and opening a downloaded file's
player chooser. The Add Torrent screen was checked for file selection and
reaching the Add button after the last row, using a small trackerless fixture.

Reference: [Android TV navigation guidance](https://developer.android.com/training/tv/get-started/navigation).
