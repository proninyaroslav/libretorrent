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

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

public class ColorPickerViewModel extends ViewModel {
    private ColorPickerState state;

    @Nullable
    public ColorPickerState getState() {
        return state;
    }

    public void setColor(@ColorInt int color) {
        state = new ColorPickerState(color);
    }
}
