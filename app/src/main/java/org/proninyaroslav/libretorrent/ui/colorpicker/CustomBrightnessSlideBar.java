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
import android.util.AttributeSet;

import com.skydoves.colorpickerview.sliders.BrightnessSlideBar;

public class CustomBrightnessSlideBar extends BrightnessSlideBar {
    public CustomBrightnessSlideBar(Context context) {
        super(context);
    }

    public CustomBrightnessSlideBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomBrightnessSlideBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomBrightnessSlideBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onInflateFinished() {
        // In the original version, the selector is set to the default position at the slide bar end,
        // which prevents the correct brightness position of the initial color from being set.
        // Just ignore super.onInflateFinished.
    }
}
