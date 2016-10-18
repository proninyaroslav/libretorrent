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

package org.proninyaroslav.libretorrent.settings;

import android.os.Parcel;
import android.os.Parcelable;

/*
 * Specifies the toolbar title and fragment (by class name). Part of BasePreferenceActivity.
 */

public class PreferenceActivityConfig implements Parcelable
{
    private String fragment;
    private String title;

    public PreferenceActivityConfig(String fragment, String title)
    {
        this.fragment = fragment;
        this.title = title;
    }

    public PreferenceActivityConfig(Parcel source)
    {
        fragment = source.readString();
        title = source.readString();
    }

    public void setFragment(String fragment)
    {
        this.fragment = fragment;
    }

    public String getFragment()
    {
        return fragment;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(fragment);
        dest.writeString(title);
    }

    public static final Parcelable.Creator<PreferenceActivityConfig> CREATOR =
            new Parcelable.Creator<PreferenceActivityConfig>()
            {
                @Override
                public PreferenceActivityConfig createFromParcel(Parcel source)
                {
                    return new PreferenceActivityConfig(source);
                }

                @Override
                public PreferenceActivityConfig[] newArray(int size)
                {
                    return new PreferenceActivityConfig[size];
                }
            };
}
