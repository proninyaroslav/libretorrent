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

package org.proninyaroslav.libretorrent.core;

import android.os.Parcel;
import android.os.Parcelable;

import com.frostwire.jlibtorrent.Priority;

import java.util.List;

public class AddTorrentParams implements Parcelable
{
    /* File path or magnet link */
    private String source;
    private boolean fromMagnet;
    private String sha1hash;
    private String name;
    /* Optional field (e.g. if file information is available) */
    private List<Priority> filePriorities;
    private String pathToDownload;
    private boolean sequentialDownload;
    private boolean addPaused;

    public AddTorrentParams(String source, boolean fromMagnet, String sha1hash,
                            String name, List<Priority> filePriorities, String pathToDownload,
                            boolean sequentialDownload, boolean addPaused)
    {
        this.source = source;
        this.fromMagnet = fromMagnet;
        this.sha1hash = sha1hash;
        this.name = name;
        this.filePriorities = filePriorities;
        this.pathToDownload = pathToDownload;
        this.sequentialDownload = sequentialDownload;
        this.addPaused = addPaused;
    }

    public AddTorrentParams(Parcel s)
    {
        source = s.readString();
        fromMagnet = s.readByte() != 0;
        sha1hash = s.readString();
        name = s.readString();
        filePriorities = s.readArrayList(Priority.class.getClassLoader());
        pathToDownload = s.readString();
        sequentialDownload = s.readByte() != 0;
        addPaused = s.readByte() != 0;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(source);
        dest.writeByte((byte)(fromMagnet ? 1 : 0));
        dest.writeString(sha1hash);
        dest.writeString(name);
        dest.writeList(filePriorities);
        dest.writeString(pathToDownload);
        dest.writeByte((byte) (sequentialDownload ? 1 : 0));
        dest.writeByte((byte) (addPaused ? 1 : 0));
    }

    public static final Parcelable.Creator<AddTorrentParams> CREATOR =
            new Parcelable.Creator<AddTorrentParams>()
            {
                @Override
                public AddTorrentParams createFromParcel(Parcel source)
                {
                    return new AddTorrentParams(source);
                }

                @Override
                public AddTorrentParams[] newArray(int size)
                {
                    return new AddTorrentParams[size];
                }
            };

    public String getSource()
    {
        return source;
    }

    public boolean fromMagnet()
    {
        return fromMagnet;
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

    public String getPathToDownload()
    {
        return pathToDownload;
    }

    public boolean isSequentialDownload()
    {
        return sequentialDownload;
    }

    public boolean addPaused()
    {
        return addPaused;
    }

    @Override
    public int hashCode()
    {
        return sha1hash.hashCode();
    }

    @Override
    public String toString()
    {
        return "AddTorrentParams{" +
                "source='" + source + '\'' +
                ", fromMagnet=" + fromMagnet +
                ", sha1hash='" + sha1hash + '\'' +
                ", name='" + name + '\'' +
                ", filePriorities=" + filePriorities +
                ", pathToDownload='" + pathToDownload + '\'' +
                ", sequentialDownload=" + sequentialDownload +
                ", addPaused=" + addPaused +
                '}';
    }
}
