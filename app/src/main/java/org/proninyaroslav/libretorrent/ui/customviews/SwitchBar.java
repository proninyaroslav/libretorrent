/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.proninyaroslav.libretorrent.R;

public class SwitchBar extends MaterialSwitch {
    public SwitchBar(@NonNull Context context) {
        super(new ContextThemeWrapper(context, R.style.App_Components_SwitchBar), null, R.style.App_Components_SwitchBar);

        init(context, null, 0);
    }

    public SwitchBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(new ContextThemeWrapper(context, R.style.App_Components_SwitchBar), attrs, R.style.App_Components_SwitchBar);

        init(context, attrs, 0);
    }

    public SwitchBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(new ContextThemeWrapper(context, R.style.App_Components_SwitchBar), attrs, defStyleAttr);

        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        setTextAppearance(R.style.TextAppearance_App_Components_SwitchBar);

        var background = new MaterialShapeDrawable();
        background.setTint(MaterialColors.getColor(this, R.attr.colorPrimaryContainer));
        var shape = ShapeAppearanceModel.builder()
                .setAllCornerSizes(80f)
                .build();
        background.setShapeAppearanceModel(shape);
        setBackground(background);

        var foregroundShapeDrawable = new MaterialShapeDrawable();
        foregroundShapeDrawable.setShapeAppearanceModel(background.getShapeAppearanceModel());
        var rippleColor = ColorStateList.valueOf(
                MaterialColors.getColor(this, R.attr.colorControlHighlight));
        var foreground = new RippleDrawable(rippleColor, null, foregroundShapeDrawable);
        setForeground(foreground);
    }
}
