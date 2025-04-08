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

package org.proninyaroslav.libretorrent.ui.colorpicker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageView;

import androidx.core.widget.ImageViewCompat;

import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.flag.FlagView;

import org.proninyaroslav.libretorrent.R;

public class CustomBubbleFlag extends FlagView {

    private final ImageView bubble;

    public CustomBubbleFlag(Context context) {
        super(context, R.layout.color_picker_bubble_flag);
        this.bubble = findViewById(R.id.bubble);
    }

    @Override
    public void onRefresh(ColorEnvelope colorEnvelope) {
        ImageViewCompat.setImageTintList(bubble, ColorStateList.valueOf(colorEnvelope.getColor()));
    }

    @Override
    public void onFlipped(Boolean isFlipped) {
    }
}
