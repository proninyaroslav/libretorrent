/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Arrays;

/*
 * Bundle of parameters that need to be changed in the torrent.
 * All parameters nullable.
 */

public class ChangeableParams implements Parcelable
{
    public String name;
    public Uri dirPath;
    public Boolean sequentialDownload;
    public Priority[] priorities;

    public ChangeableParams() {}

    public ChangeableParams(@NonNull Parcel source)
    {
        name = source.readString();
        dirPath = source.readParcelable(Uri.class.getClassLoader());
        byte sequentialDownloadVal= source.readByte();
        if (sequentialDownloadVal != -1)
            sequentialDownload = sequentialDownloadVal > 0;
        priorities = (Priority[])source.readArray(Priority.class.getClassLoader());
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(name);
        dest.writeParcelable(dirPath, flags);
        if (sequentialDownload == null)
            dest.writeByte((byte)-1);
        else
            dest.writeByte((byte)(sequentialDownload ? 1 : 0));
        dest.writeArray(priorities);
    }

    public static final Creator<ChangeableParams> CREATOR =
            new Creator<ChangeableParams>()
            {
                @Override
                public ChangeableParams createFromParcel(Parcel source)
                {
                    return new ChangeableParams(source);
                }

                @Override
                public ChangeableParams[] newArray(int size)
                {
                    return new ChangeableParams[size];
                }
            };

    @Override
    public String toString()
    {
        return "ChangeableParams{" +
                "name='" + name + '\'' +
                ", dirPath=" + dirPath +
                ", sequentialDownload=" + sequentialDownload +
                ", priorities=" + Arrays.toString(priorities) +
                '}';
    }
}
