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

package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.proninyaroslav.libretorrent.R;

/*
 * A stub preference only for summary only.
 */

public class SummaryPreference extends Preference
{
    private TextView summaryView;

    public SummaryPreference(Context context)
    {
        this(context, null);
    }

    public SummaryPreference(Context context, AttributeSet attrs)
    {
        /* Use the preferenceStyle as the default style */
        this(context, attrs, R.attr.preferenceStyle);
    }

    public SummaryPreference(Context context, AttributeSet attrs, int defStyleAttr)
    {
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public SummaryPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);

        setLayoutResource(R.layout.preference_summary);
        /* Icon stub */
        setIcon(android.R.color.transparent);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);

        summaryView = (TextView)holder.findViewById(R.id.summary);

        /* Disable click */
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        summaryView.setText(getSummary());
    }

    @Override
    public void setSummary(CharSequence summary)
    {
        super.setSummary(summary);

        summaryView.setText(summary);
    }

    @Override
    public void setSummary(int summaryResId)
    {
        super.setSummary(summaryResId);

        summaryView.setText(summaryResId);
    }
}
