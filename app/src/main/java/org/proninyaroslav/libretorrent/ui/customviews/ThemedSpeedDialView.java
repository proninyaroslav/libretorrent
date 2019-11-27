/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.ui.customviews;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.leinardi.android.speeddial.FabWithLabelView;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import org.proninyaroslav.libretorrent.R;

public class ThemedSpeedDialView extends SpeedDialView
{
    public ThemedSpeedDialView(@NonNull Context context)
    {
        super(context);
    }

    public ThemedSpeedDialView(@NonNull Context context,
                               @Nullable AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ThemedSpeedDialView(@NonNull Context context,
                               @Nullable AttributeSet attrs,
                               int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Nullable
    @Override
    public FabWithLabelView addActionItem(@NonNull SpeedDialActionItem actionItem,
                                          int position,
                                          boolean animate)
    {
        Context context = getContext();
        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                R.attr.colorOnSecondary,
                R.attr.colorSecondary,
                R.attr.colorOnBackground,
                R.attr.foreground
        });
        int tintColor = a.getColor(0, 0);
        int backgroundColor = a.getColor(1, 0);
        int labelColor = a.getColor(2, 0);
        int labelBackgroundColor = a.getColor(3, 0);
        a.recycle();

        actionItem = new SpeedDialActionItem.Builder(actionItem.getId(),
                actionItem.getFabImageDrawable(context))
                .setFabImageTintColor(tintColor)
                .setFabBackgroundColor(backgroundColor)
                .setLabel(actionItem.getLabel(context))
                .setLabelClickable(actionItem.isLabelClickable())
                .setLabelColor(labelColor)
                .setLabelBackgroundColor(labelBackgroundColor)
                .setTheme(actionItem.getTheme())
                .create();

        return super.addActionItem(actionItem, position, animate);
    }
}
