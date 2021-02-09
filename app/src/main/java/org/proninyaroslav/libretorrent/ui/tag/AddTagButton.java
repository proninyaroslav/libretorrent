/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.tag;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;

import org.proninyaroslav.libretorrent.R;

public class AddTagButton extends Chip {
    public AddTagButton(@NonNull Context context) {
        super(context);

        init(context);
    }

    public AddTagButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    public AddTagButton(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);

        init(context);
    }

    private void init(@NonNull Context context) {
        setText(R.string.add_tag);
        setChipIconResource(R.drawable.ic_add_18);

        TypedArray a = context.obtainStyledAttributes(
                new TypedValue().data,
                new int[]{
                        R.attr.colorPrimary,
                        R.attr.colorOnPrimary,
                });
        setChipBackgroundColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}},
                new int[]{a.getColor(0, Color.WHITE)})
        );
        setChipIconTint(new ColorStateList(
                new int[][]{new int[]{}},
                new int[]{a.getColor(1, Color.WHITE)})
        );
        setTextColor(a.getColor(1, Color.WHITE));
        a.recycle();
    }
}
