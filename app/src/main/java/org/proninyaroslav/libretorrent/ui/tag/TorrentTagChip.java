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

package org.proninyaroslav.libretorrent.ui.tag;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.chip.Chip;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;

public class TorrentTagChip extends Chip {
    private TagInfo tag;

    public TorrentTagChip(Context context, TagInfo tag) {
        super(context);

        init(tag);
    }

    public TorrentTagChip(Context context, @DrawableRes int icon, @StringRes int label) {
        super(context);

        init(icon, label);
    }

    public TorrentTagChip(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(null);
    }

    public TorrentTagChip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(null);
    }

    @Nullable
    public TagInfo getTag() {
        return tag;
    }

    private void init(@Nullable TagInfo tag) {
        if (tag != null) {
            this.tag = tag;
            setText(tag.name);
            setChipIconTint(new ColorStateList(
                    new int[][]{new int[]{}},
                    new int[]{tag.color})
            );
        }
        setChipIconResource(R.drawable.torrent_tag_color_background);
        setCloseIconVisible(true);
        setCheckable(true);
        setClickable(false);
    }

    private void init(@DrawableRes int icon, @StringRes int label) {
        if (label != -1) {
            setText(label);
        }
        if (icon != -1) {
            setChipIconResource(icon);
        }
        setCloseIconVisible(true);
        setCheckable(true);
        setClickable(false);
    }
}
