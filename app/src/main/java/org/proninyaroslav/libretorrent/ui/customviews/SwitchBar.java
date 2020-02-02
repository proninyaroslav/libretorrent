/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class SwitchBar extends SwitchCompat
{
    public SwitchBar(@NonNull Context context)
    {
        super(new ContextThemeWrapper(context, R.style.SwitchBar));

        init(context);
    }

    public SwitchBar(@NonNull Context context, @Nullable AttributeSet attrs)
    {
        super(new ContextThemeWrapper(context, R.style.SwitchBar), attrs);

        init(context);
    }

    public SwitchBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(new ContextThemeWrapper(context, R.style.SwitchBar), attrs, defStyleAttr);

        init(context);
    }

    private void init(Context context)
    {
        Drawable background = ContextCompat.getDrawable(context, R.drawable.switchbar_background);
        if (background != null)
            Utils.setBackground(this, background);

        setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        setText(isChecked() ? R.string.switch_on : R.string.switch_off);
    }

    @Override
    public void setChecked(boolean checked)
    {
        super.setChecked(checked);

        setText(isChecked() ? R.string.switch_on : R.string.switch_off);
    }
}
