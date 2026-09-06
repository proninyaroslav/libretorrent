/*
 * Copyright (C) 2026 Andrey Makoldin <19638591+Avlaak@users.noreply.github.com>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.databinding.ItemTorrentListBinding;
import org.proninyaroslav.libretorrent.ui.home.TorrentListAdapter;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Real key events against attached views, including duplicate IDs in recycled rows. */
public class TvNavigationTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class) {
        private SettingsRepository preferences;
        private boolean askStorage;
        private boolean askNotifications;
        private boolean showStorageWarning;

        @Override
        protected Intent getActivityIntent() {
            return new Intent(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }

        @Override
        protected void beforeActivityLaunched() {
            preferences = RepositoryHelper.getSettingsRepository(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            askStorage = preferences.askManageAllFilesPermission();
            askNotifications = preferences.askNotificationPermission();
            showStorageWarning = preferences.showManageAllFilesWarningDialog();
            // These view-only tests need no storage or notification access. Keep
            // first-launch permission screens from intercepting injected keys.
            preferences.askManageAllFilesPermission(false);
            preferences.askNotificationPermission(false);
            preferences.showManageAllFilesWarningDialog(false);
        }

        @Override
        protected void afterActivityFinished() {
            preferences.askManageAllFilesPermission(askStorage);
            preferences.askNotificationPermission(askNotifications);
            preferences.showManageAllFilesWarningDialog(showStorageWarning);
        }
    };

    private Context tvContext() {
        var config = new Configuration(activityRule.getActivity().getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                | Configuration.UI_MODE_TYPE_TELEVISION;
        return new ContextThemeWrapper(activityRule.getActivity().createConfigurationContext(config),
                R.style.AppTheme);
    }

    private void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void key(int keyCode) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void phoneAndTabletKeepTheirExistingLayoutAndInputHandlers() {
        // A television's FEATURE_LEANBACK intentionally overrides UI mode. Run
        // this regression check on a phone/tablet device or emulator.
        Assume.assumeFalse(activityRule.getActivity().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK));
        onMain(() -> {
            for (int smallestWidth : new int[]{360, 800}) {
                var config = new Configuration(activityRule.getActivity().getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                        | Configuration.UI_MODE_TYPE_NORMAL;
                config.smallestScreenWidthDp = smallestWidth;
                var context = new ContextThemeWrapper(
                        activityRule.getActivity().createConfigurationContext(config), R.style.AppTheme);
                assertFalse(TvNavigation.isTelevision(context));

                var root = new LinearLayout(context);
                var row = ItemTorrentListBinding.inflate(
                        android.view.LayoutInflater.from(context), root, true);
                int maxLines = row.name.getMaxLines();
                TextUtils.TruncateAt ellipsize = row.name.getEllipsize();
                var clicks = new AtomicInteger();
                var longClicks = new AtomicInteger();
                var keys = new AtomicInteger();
                row.card.setOnClickListener(v -> clicks.incrementAndGet());
                row.card.setOnLongClickListener(v -> {
                    longClicks.incrementAndGet();
                    return true;
                });
                row.card.setOnKeyListener((v, code, event) -> {
                    keys.incrementAndGet();
                    return true;
                });
                row.pauseButton.setOnKeyListener((v, code, event) -> {
                    keys.incrementAndGet();
                    return true;
                });

                var list = new RecyclerView(context);
                list.setAdapter(new TorrentListAdapter(null));
                list.setFocusable(true);
                list.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                root.addView(list);
                var tracker = new SelectionTracker.Builder<>("touch-regression", list,
                        new ItemKeyProvider<Long>(ItemKeyProvider.SCOPE_MAPPED) {
                            @Override
                            public Long getKey(int position) { return (long) position; }

                            @Override
                            public int getPosition(@NonNull Long key) { return key.intValue(); }
                        }, new ItemDetailsLookup<Long>() {
                            @Override
                            public ItemDetails<Long> getItemDetails(@NonNull MotionEvent event) {
                                return null;
                            }
                        }, StorageStrategy.createLongStorage()).build();

                var pager = new ViewPager2(context);
                pager.setFocusable(true);
                var pages = (RecyclerView) pager.getChildAt(0);
                boolean pagesFocusable = pages.isFocusable();
                int pagesFocusability = pages.getDescendantFocusability();
                root.addView(pager);
                var appBar = new AppBarLayout(context);
                var barParams = new AppBarLayout.LayoutParams(100, 100);
                barParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
                appBar.addView(new View(context), barParams);
                root.addView(appBar);

                TvNavigation.configurePager(pager);
                TvNavigation.configureFocusContainers(root);
                TvNavigation.keepAppBarVisible(appBar);
                TvNavigation.configureFileName(row.name);
                TvNavigation.linkRowAction(row.card, row.pauseButton, true, true);
                TvNavigation.bindSelection(row.card, tracker, 0L,
                        () -> fail("Touch click handler must not be replaced"));

                assertEquals(maxLines, row.name.getMaxLines());
                assertEquals(ellipsize, row.name.getEllipsize());
                assertTrue(list.isFocusable());
                assertEquals(ViewGroup.FOCUS_BEFORE_DESCENDANTS, list.getDescendantFocusability());
                assertTrue(pager.isFocusable());
                assertEquals(pagesFocusable, pages.isFocusable());
                assertEquals(pagesFocusability, pages.getDescendantFocusability());
                assertEquals(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL, barParams.getScrollFlags());
                row.card.performClick();
                row.card.performLongClick();
                // ViewGroup dispatches to its own key listener only when it
                // has bounds and focus; these views are not attached to a window.
                row.card.layout(0, 0, 400, 100);
                row.card.setFocusableInTouchMode(true);
                assertTrue(row.card.requestFocus());
                row.card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                row.pauseButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                assertEquals(1, clicks.get());
                assertEquals(1, longClicks.get());
                assertEquals(2, keys.get());
                assertFalse(tracker.hasSelection());
            }
        });
    }

    @Test
    public void pauseNavigationStaysInTheSameRowAndMediaKeyTogglesOnce() {
        var rows = new ItemTorrentListBinding[2];
        var clicks = new AtomicInteger();
        onMain(() -> {
            var context = tvContext();
            var root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < rows.length; i++) {
                rows[i] = ItemTorrentListBinding.inflate(
                        android.view.LayoutInflater.from(context), root, true);
                rows[i].name.setText("Torrent " + i);
                TvNavigation.linkRowAction(rows[i].card, rows[i].pauseButton, true, true);
            }
            rows[1].pauseButton.setOnClickListener(v -> clicks.incrementAndGet());
            activityRule.getActivity().setContentView(root);
        });
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        onMain(() -> assertTrue(rows[1].card.requestFocus()));
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        onMain(() -> assertTrue(rows[1].pauseButton.hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        onMain(() -> assertTrue(rows[1].card.isFocused()));
        key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertEquals(1, clicks.get());
        onMain(() -> {
            rows[1].card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TvNavigation.linkRowAction(rows[1].card, rows[1].pauseButton, true, true);
        });
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        onMain(() -> assertTrue(rows[1].pauseButton.hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        onMain(() -> assertTrue(rows[1].card.isFocused()));
    }

    @Test
    public void downEntersPagerContentAndUpCanLeaveIt() {
        var header = new Button[1];
        var content = new Button[2];
        var footer = new Button[1];
        var pagerRef = new ViewPager2[1];
        onMain(() -> {
            var context = tvContext();
            var root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            header[0] = new Button(context);
            header[0].setText("Files tab");
            root.addView(header[0], new LinearLayout.LayoutParams(400, 100));
            var pager = new ViewPager2(context);
            pagerRef[0] = pager;
            pager.setOffscreenPageLimit(1);
            pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                    var page = new LinearLayout(context);
                    page.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                    var button = new Button(context);
                    button.setText("Open folder");
                    page.addView(button, new LinearLayout.LayoutParams(400, 100));
                    return new RecyclerView.ViewHolder(page) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    content[position] = (Button) ((ViewGroup) holder.itemView).getChildAt(0);
                }

                @Override
                public int getItemCount() { return 2; }
            });
            TvNavigation.configurePager(pager);
            root.addView(pager, new LinearLayout.LayoutParams(-1, 400));
            footer[0] = new Button(context);
            footer[0].setText("Add");
            root.addView(footer[0], new LinearLayout.LayoutParams(400, 100));
            activityRule.getActivity().setContentView(root);
        });
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        onMain(() -> assertTrue(header[0].requestFocus()));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onMain(() -> assertTrue("The page's button, not the pager container, must receive focus",
                content[0].isFocused()));
        onMain(() -> {
            assertNotNull("The neighboring page should be cached for this regression test", content[1]);
            assertFalse("Hidden page must reject focus", content[1].requestFocus());
        });
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onMain(() -> assertTrue(footer[0].isFocused()));
        key(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> assertTrue(content[0].isFocused()));
        key(KeyEvent.KEYCODE_DPAD_UP);
        onMain(() -> assertTrue(header[0].isFocused()));
        onMain(() -> pagerRef[0].setCurrentItem(1, false));
        onMain(() -> {
            assertTrue(content[1].requestFocus());
            assertFalse("Previous page must reject focus after switching tabs", content[0].requestFocus());
        });
    }
}
