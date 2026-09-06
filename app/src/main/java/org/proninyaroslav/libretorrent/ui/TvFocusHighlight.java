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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import org.proninyaroslav.libretorrent.R;

/** A single focus outline, independent of each widget's checked/selected styling. */
public final class TvFocusHighlight extends Drawable implements ViewTreeObserver.OnPreDrawListener {
    private final View root;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visibleRect = new Rect();
    private final RectF outline = new RectF();
    private final RectF next = new RectF();
    private final int[] rootLocation = new int[2];
    private final float radius;

    public TvFocusHighlight(View root) {
        this.root = root;
        float density = root.getResources().getDisplayMetrics().density;
        radius = 10 * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * density);
        paint.setColor(MaterialColors.getColor(root, R.attr.colorPrimary));
        root.getOverlay().add(this);
        root.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    public boolean onPreDraw() {
        next.setEmpty();
        View focused = root.findFocus();
        if (root.hasWindowFocus() && !root.isInTouchMode() && focused != null
                && focused.isShown() && focused.getGlobalVisibleRect(visibleRect)) {
            root.getLocationOnScreen(rootLocation);
            next.set(visibleRect);
            next.offset(-rootLocation[0], -rootLocation[1]);
            float inset = paint.getStrokeWidth();
            next.inset(inset, inset);
        }
        if (!outline.equals(next)) {
            outline.set(next);
            setBounds(0, 0, root.getWidth(), root.getHeight());
            invalidateSelf();
        }
        return true;
    }

    public void dispose() {
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnPreDrawListener(this);
        }
        root.getOverlay().remove(this);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!outline.isEmpty()) {
            canvas.drawRoundRect(outline, radius, radius, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
