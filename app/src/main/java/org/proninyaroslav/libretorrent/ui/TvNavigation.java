/*
 * Copyright (C) 2026 Andrey Makoldin <avlaaak@gmail.com>
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
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;

import org.proninyaroslav.libretorrent.ui.customviews.EmptyListPlaceholder;

/** Remote navigation adjustments; touch devices retain their normal behavior. */
public final class TvNavigation {
    private TvNavigation() {}

    public static boolean isTelevision(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static void configurePager(ViewPager2 pager) {
        if (!isTelevision(pager.getContext())) {
            return;
        }
        // ViewPager2's internal RecyclerView otherwise takes focus before the page,
        // trapping D-pad navigation on a container with no selectable action.
        pager.setFocusable(false);
        var pages = (RecyclerView) pager.getChildAt(0);
        pages.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        pages.setFocusable(false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePagerFocus(pager, pages);
            }
        });
        pages.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                updatePagerFocus(pager, pages);
                pages.post(() -> updatePagerFocus(pager, pages));
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {}
        });
        updatePagerFocus(pager, pages);
    }

    private static void updatePagerFocus(ViewPager2 pager, RecyclerView pages) {
        // Cached pages are attached but off screen. Without this, focus can disappear
        // into the Info page while navigating down the Files page (or vice versa).
        for (int i = 0; i < pages.getChildCount(); i++) {
            var page = (ViewGroup) pages.getChildAt(i);
            boolean current = pages.getChildAdapterPosition(page) == pager.getCurrentItem();
            page.setDescendantFocusability(current ? ViewGroup.FOCUS_AFTER_DESCENDANTS
                    : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            page.setFocusable(false);
        }
    }

    public static void configureFocusContainers(View view) {
        if (!isTelevision(view.getContext())) {
            return;
        }
        configureTvFocusContainers(view);
    }

    private static void configureTvFocusContainers(View view) {
        if (view instanceof EmptyListPlaceholder placeholder) {
            placeholder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            placeholder.setFocusable(false);
            return;
        }
        if (view instanceof RecyclerView list) {
            list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            list.setFocusable(false);
        }
        if (view instanceof AppBarLayout appBar) {
            keepAppBarVisible(appBar);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                configureTvFocusContainers(group.getChildAt(i));
            }
        }
    }

    public static void configureFileName(TextView name) {
        if (isTelevision(name.getContext())) {
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    public static boolean focusCurrentPage(ViewPager2 pager) {
        var pages = (RecyclerView) pager.getChildAt(0);
        var holder = pages.findViewHolderForAdapterPosition(pager.getCurrentItem());
        if (holder == null) {
            return false;
        }
        View files = holder.itemView.findViewById(org.proninyaroslav.libretorrent.R.id.file_list);
        if (files != null && files.isShown() && files.requestFocus(View.FOCUS_DOWN)) {
            return true;
        }
        return holder.itemView.requestFocus(View.FOCUS_DOWN);
    }

    public static <K> void bindSelection(View row, SelectionTracker<K> tracker, K key,
                                         Runnable open) {
        if (!isTelevision(row.getContext()) || tracker == null) {
            return;
        }
        // SelectionTracker's touch gestures don't provide a remote long-press action.
        row.setOnLongClickListener(v -> tracker.select(key));
        row.setOnClickListener(v -> {
            if (tracker.hasSelection()) {
                if (tracker.isSelected(key)) {
                    tracker.deselect(key);
                } else {
                    tracker.select(key);
                }
            } else {
                open.run();
            }
        });
    }

    public static void keepAppBarVisible(AppBarLayout appBar) {
        if (!isTelevision(appBar.getContext())) {
            return;
        }
        appBar.setExpanded(true, false);
        for (int i = 0; i < appBar.getChildCount(); i++) {
            var child = appBar.getChildAt(i);
            var params = (AppBarLayout.LayoutParams) child.getLayoutParams();
            params.setScrollFlags(0);
            child.setLayoutParams(params);
        }
    }

    /** Link a row to its nested button using actual views, not recycled row IDs. */
    public static void linkRowAction(View row, View action, boolean actionAtStart,
                                     boolean mediaPlayPause) {
        if (!isTelevision(row.getContext())) {
            return;
        }
        boolean actionOnLeft = actionAtStart
                != (row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        int toAction = actionOnLeft ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        int toRow = actionOnLeft ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == toAction && action.isShown() && action.isEnabled()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    action.requestFocus();
                }
                return true;
            }
            return handleMediaKey(action, mediaPlayPause, keyCode, event);
        });
        action.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == toRow) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    row.requestFocus();
                }
                return true;
            }
            return handleMediaKey(action, mediaPlayPause, keyCode, event);
        });
    }

    private static boolean handleMediaKey(View action, boolean enabled, int keyCode, KeyEvent event) {
        if (!enabled || keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            action.performClick();
        }
        return true;
    }
}
