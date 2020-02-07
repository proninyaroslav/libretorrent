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

package org.proninyaroslav.libretorrent.core.model.data.metainfo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/*
 * The class encapsulates path, index and file size, extracted from bencode.
 */

public class BencodeFileItem implements Parcelable, Comparable<BencodeFileItem>
{
    private String path;
    private int index;
    private long size;

    public BencodeFileItem(String path, int index, long size)
    {
        this.path = path;
        this.index = index;
        this.size = size;
    }

    public BencodeFileItem(Parcel source)
    {
        path = source.readString();
        index = source.readInt();
        size = source.readLong();
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }

    public int getIndex()
    {
        return index;
    }

    public void setIndex(int index)
    {
        this.index = index;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(path);
        dest.writeInt(index);
        dest.writeLong(size);
    }

    public static final Parcelable.Creator<BencodeFileItem> CREATOR =
            new Parcelable.Creator<BencodeFileItem>()
            {
                @Override
                public BencodeFileItem createFromParcel(Parcel source)
                {
                    return new BencodeFileItem(source);
                }

                @Override
                public BencodeFileItem[] newArray(int size)
                {
                    return new BencodeFileItem[size];
                }
            };

    @Override
    public int compareTo(@NonNull BencodeFileItem anotner)
    {
        return path.compareTo(anotner.path);
    }

    @Override
    public String toString()
    {
        return "BencodeFileItem{" +
                "path='" + path + '\'' +
                ", index=" + index +
                ", size=" + size +
                '}';
    }
}