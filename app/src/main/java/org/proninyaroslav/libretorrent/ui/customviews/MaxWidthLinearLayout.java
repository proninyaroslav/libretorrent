/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.customviews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import org.proninyaroslav.libretorrent.R;

public class MaxWidthLinearLayout extends LinearLayout {
    private final int maxWidth;

    public MaxWidthLinearLayout(Context context) {
        super(context);
        maxWidth = 0;
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        try (var a = getContext().obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout)) {
            maxWidth = a.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_maxWidth, Integer.MAX_VALUE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (maxWidth > 0 && maxWidth < measuredWidth) {
            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, measureMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}