/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*
 * A base item for the file list adapters.
 */

public class FileItem implements Parcelable, Comparable<FileItem>
{
    public int index;
    public String name;
    public boolean isFile;
    public long size;

    public FileItem(int index, String name, boolean isFile, long size)
    {
        this.index = index;
        this.name = name;
        this.isFile = isFile;
        this.size = size;
    }

    public FileItem(Parcel source)
    {
        index = source.readInt();
        name = source.readString();
        isFile = source.readByte() != 0;
        size = source.readLong();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeInt(index);
        dest.writeString(name);
        dest.writeByte((byte)(isFile ? 1 : 0));
        dest.writeLong(size);
    }

    public static final Creator<FileItem> CREATOR =
            new Creator<FileItem>()
            {
                @Override
                public FileItem createFromParcel(Parcel source)
                {
                    return new FileItem(source);
                }

                @Override
                public FileItem[] newArray(int size)
                {
                    return new FileItem[size];
                }
            };

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (!(o instanceof FileItem))
            return false;

        if (o == this)
            return true;

        FileItem item = (FileItem) o;

        return index == item.index &&
                (name == null || name.equals(item.name)) &&
                isFile == item.isFile;
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((name == null) ? 0 : name.hashCode());
        if (isFile)
            result = prime * result + index;

        return result;
    }

    @Override
    public int compareTo(@NonNull FileItem o)
    {
        return name.compareTo(o.name);
    }

    @NonNull
    @Override
    public String toString()
    {
        return "FileItem{" +
                "index=" + index +
                ", name='" + name + '\'' +
                ", isFile=" + isFile +
                ", size=" + size +
                '}';
    }
}
