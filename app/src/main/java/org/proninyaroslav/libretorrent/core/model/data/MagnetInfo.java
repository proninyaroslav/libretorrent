/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/*
 * Provides full information about magnet.
 */

public class MagnetInfo implements Parcelable
{
    private String uri;
    private String sha1hash;
    private String name;
    /*
     * BEP53 standard http://www.bittorrent.org/beps/bep_0053.html
     *
     * Note: priorities number may not coincide with
     *       the actual files number in the torrent.
     *       In this case, manually add the missing number
     *       of priorities to the end of the array
     */
    private List<Priority> filePriorities;

    public MagnetInfo(String uri, String sha1hash, String name, List<Priority> filePriorities)
    {
        this.uri = uri;
        this.sha1hash = sha1hash;
        this.name = name;
        this.filePriorities = filePriorities;
    }

    public MagnetInfo(Parcel s)
    {
        uri = s.readString();
        sha1hash = s.readString();
        name = s.readString();
        filePriorities = s.readArrayList(Priority.class.getClassLoader());
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(uri);
        dest.writeString(sha1hash);
        dest.writeString(name);
        dest.writeList(filePriorities);
    }

    public static final Parcelable.Creator<MagnetInfo> CREATOR =
            new Parcelable.Creator<MagnetInfo>()
            {
                @Override
                public MagnetInfo createFromParcel(Parcel source)
                {
                    return new MagnetInfo(source);
                }

                @Override
                public MagnetInfo[] newArray(int size)
                {
                    return new MagnetInfo[size];
                }
            };

    public String getUri()
    {
        return uri;
    }

    public String getSha1hash()
    {
        return sha1hash;
    }

    public String getName()
    {
        return name;
    }

    public List<Priority> getFilePriorities()
    {
        return filePriorities;
    }

    @Override
    public int hashCode()
    {
        return sha1hash.hashCode();
    }

    @Override
    public String toString()
    {
        return "MagnetInfo{" +
                "uri='" + uri + '\'' +
                ", sha1hash='" + sha1hash + '\'' +
                ", name='" + name + '\'' +
                ", filePriorities=" + filePriorities +
                '}';
    }
}
