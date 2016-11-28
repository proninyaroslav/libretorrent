/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.settings.customprefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.customviews.LightColorView;

import yuku.ambilwarna.AmbilWarnaDialog;

/*
 * Color picker settings.
 */

public class ColorPreference extends Preference
{
    private int value;

    public ColorPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        setWidgetLayoutResource(R.layout.preference_light_color);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);

        LightColorView color = (LightColorView) holder.findViewById(R.id.lightColor);
        if (color != null) {
            color.setColor(value);
        }
    }

    @Override
    protected void onClick()
    {
        new AmbilWarnaDialog(getContext(), value, false, new AmbilWarnaDialog.OnAmbilWarnaListener()
        {
            @Override public void onOk(AmbilWarnaDialog dialog, int color)
            {
                if (!callChangeListener(color)) {
                    return;
                }

                value = color;
                persistInt(value);
                notifyChanged();
            }

            @Override public void onCancel(AmbilWarnaDialog dialog)
            {
                /* Nothing */
            }
        }).show();
    }

    public void forceSetValue(int value)
    {
        this.value = value;

        persistInt(value);
        notifyChanged();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
    {
        if (restoreValue) {
            value = getPersistedInt(value);
        } else {
            int value = (Integer) defaultValue;
            this.value = value;
            persistInt(value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            return superState;
        }

        SavedState myState = new SavedState(superState);
        myState.value = value;

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
        if (!state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);

            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        this.value = myState.value;
        notifyChanged();
    }

    private static class SavedState extends BaseSavedState
    {
        int value;

        public SavedState(Parcel source)
        {
            super(source);
            value = source.readInt();
        }

        @Override public void writeToParcel(Parcel dest, int flags)
        {
            super.writeToParcel(dest, flags);
            dest.writeInt(value);
        }

        public SavedState(Parcelable superState)
        {
            super(superState);
        }

        @SuppressWarnings("unused")
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>()
        {
            public SavedState createFromParcel(Parcel in)
            {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }
        };
    }
}
