/*
 * Copyright (C) 2020-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.ui.customviews.SwitchBar;

/*
 * A preference with SwitchBar like in Android settings.
 */

public class SwitchBarPreference extends SwitchPreferenceCompat
{
    private SwitchBar switchButton;

    public SwitchBarPreference(Context context)
    {
        this(context, null);
    }

    public SwitchBarPreference(Context context, AttributeSet attrs)
    {
        /* Use the preferenceStyle as the default style */
        this(context, attrs, R.attr.preferenceStyle);
    }

    public SwitchBarPreference(Context context, AttributeSet attrs, int defStyleAttr)
    {
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public SwitchBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);

        setLayoutResource(R.layout.preference_switchbar);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);

        switchButton = (SwitchBar)holder.findViewById(R.id.switchButton);

        switchButton.setOnCheckedChangeListener(listener);
        switchButton.setChecked(isChecked());
    }

    private final CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            if (!callChangeListener(isChecked)) {
                /*
                 * Listener didn't like it, change it back.
                 * CompoundButton will make sure we don't recurse.
                 */
                switchButton.setChecked(!isChecked);
                return;
            }

            setChecked(isChecked);
        }
    };
}
