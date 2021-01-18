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

package org.proninyaroslav.libretorrent.ui.addtag;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import org.proninyaroslav.libretorrent.BR;

public class AddTagState extends BaseObservable {
    private Long existsTagId;
    private String name;
    private int color = -1;

    @Bindable
    public Long getExistsTagId() {
        return existsTagId;
    }

    public void setExistsTagId(Long existsTagId) {
        this.existsTagId = existsTagId;
        notifyPropertyChanged(BR.existsTagId);
    }

    @Bindable
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        notifyPropertyChanged(BR.name);
    }

    @Bindable
    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        notifyPropertyChanged(BR.color);
    }
}
