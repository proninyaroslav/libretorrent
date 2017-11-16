/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/*
 * The class encapsulates the torrent file and its meta information.
 */

public class Torrent implements Parcelable, Comparable<Torrent>
{
    /* Usually torrent SHA-1 hash */
    private String id;
    /* Path to the torrent file (or magnet URI if downloadingMetadata = true)*/
    private String torrentFile;
    private String downloadPath;
    /*
     * The index position in array must be
     * equal to the priority position in array
     */
    private List<Priority> filePriorities;
    private String torrentName;
    private boolean sequentialDownload = false;
    private boolean finished = false;
    private boolean paused = false;
    private boolean downloadingMetadata = false;
    private long dateAdded;

    public Torrent(String id, String torrentName,
                   List<Priority> filePriorities,
                   String downloadPath, long dateAdded)
    {
        this(id, null, torrentName, filePriorities, downloadPath, dateAdded);
    }

    public Torrent(String id, String torrentFile,
                   String torrentName,
                   List<Priority> filePriorities,
                   String downloadPath, long dateAdded)
    {
        this.id = id;
        this.torrentFile = torrentFile;
        this.torrentName = torrentName;
        this.filePriorities = filePriorities;
        this.downloadPath = downloadPath;
        this.dateAdded = dateAdded;
    }

    public Torrent(Parcel source)
    {
        id = source.readString();
        torrentFile = source.readString();
        downloadPath = source.readString();
        filePriorities = source.readArrayList(Priority.class.getClassLoader());
        torrentName = source.readString();
        sequentialDownload = source.readByte() != 0;
        finished = source.readByte() != 0;
        paused = source.readByte() != 0;
        downloadingMetadata = source.readByte() != 0;
        dateAdded = source.readLong();
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return torrentName;
    }

    public void setName(String name)
    {
        torrentName = name;
    }

    public String getTorrentFilePath()
    {
        return torrentFile;
    }

    public void setTorrentFilePath(String path)
    {
        torrentFile = path;
    }

    public List<Priority> getFilePriorities()
    {
        return filePriorities;
    }

    public void setFilePriorities(List<Priority> priorities)
    {
        filePriorities = priorities;
    }

    public String getDownloadPath()
    {
        return downloadPath;
    }

    public void setDownloadPath(String path)
    {
        downloadPath = path;
    }

    public void setSequentialDownload(boolean sequential)
    {
        sequentialDownload = sequential;
    }

    public boolean isSequentialDownload()
    {
        return sequentialDownload;
    }

    public boolean isFinished()
    {
        return finished;
    }

    public void setFinished(boolean finished)
    {
        this.finished = finished;
    }

    public boolean isPaused()
    {
        return paused;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    public boolean isDownloadingMetadata()
    {
        return downloadingMetadata;
    }

    public void setDownloadingMetadata(boolean downloadingMetadata)
    {
        this.downloadingMetadata = downloadingMetadata;
    }

    public long getDateAdded()
    {
        return dateAdded;
    }

    public void setDateAdded(long datetime)
    {
        this.dateAdded = datetime;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(id);
        dest.writeString(torrentFile);
        dest.writeString(downloadPath);
        dest.writeList(filePriorities);
        dest.writeString(torrentName);
        dest.writeByte((byte) (sequentialDownload ? 1 : 0));
        dest.writeByte((byte) (finished ? 1 : 0));
        dest.writeByte((byte) (paused ? 1 : 0));
        dest.writeByte((byte) (downloadingMetadata ? 1 : 0));
        dest.writeLong(dateAdded);
    }

    public static final Parcelable.Creator<Torrent> CREATOR =
            new Parcelable.Creator<Torrent>()
            {
                @Override
                public Torrent createFromParcel(Parcel source)
                {
                    return new Torrent(source);
                }

                @Override
                public Torrent[] newArray(int size)
                {
                    return new Torrent[size];
                }
            };

    @Override
    public int compareTo(Torrent another)
    {
        return torrentName.compareTo(another.getName());
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Torrent && (o == this || id.equals(((Torrent) o).id));
    }

    @Override
    public String toString() {
        return "Torrent{" +
                "id='" + id + '\'' +
                ", torrentFile='" + torrentFile + '\'' +
                ", downloadPath='" + downloadPath + '\'' +
                ", filePriorities=" + filePriorities +
                ", torrentName='" + torrentName + '\'' +
                ", sequentialDownload=" + sequentialDownload +
                ", finished=" + finished +
                ", paused=" + paused +
                ", downloadingMetadata=" + downloadingMetadata +
                ", dateAdded=" + SimpleDateFormat.getDateTimeInstance().format(new Date(dateAdded)) +
                '}';
    }
}
