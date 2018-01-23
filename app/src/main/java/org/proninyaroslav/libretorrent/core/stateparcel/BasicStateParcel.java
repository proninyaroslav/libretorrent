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

package org.proninyaroslav.libretorrent.core.stateparcel;

import android.os.Parcel;
import android.os.Parcelable;

import org.proninyaroslav.libretorrent.core.TorrentStateCode;

/*
 * The class provides a package model with dynamically changing information
 * about state of the torrent, sent from the service.
 */

public class BasicStateParcel extends AbstractStateParcel<BasicStateParcel>
{
    public String torrentId = "";
    public String name = "";
    public TorrentStateCode stateCode = TorrentStateCode.UNKNOWN;
    public int progress = 0;
    public long receivedBytes = 0L;
    public long uploadedBytes = 0L;
    public long totalBytes = 0L;
    public long downloadSpeed = 0L;
    public long uploadSpeed = 0L;
    public long ETA = -1L;
    public long dateAdded = 0L;
    public int totalPeers = 0;
    public int peers = 0;

    public BasicStateParcel()
    {
        super();
    }

    public BasicStateParcel(String torrentId, String name, long dateAdded)
    {
        super(torrentId);

        this.torrentId = torrentId;
        this.name = name;
        this.stateCode = TorrentStateCode.STOPPED;
        this.dateAdded = dateAdded;
    }

    public BasicStateParcel(String torrentId, String name,
                            TorrentStateCode stateCode, int progress,
                            long receivedBytes, long uploadedBytes,
                            long totalBytes, long downloadSpeed,
                            long uploadSpeed, long ETA, long dateAdded,
                            int totalPeers, int peers)
    {
        super(torrentId);

        this.torrentId = torrentId;
        this.name = name;
        this.stateCode = stateCode;
        this.progress = progress;
        this.receivedBytes = receivedBytes;
        this.uploadedBytes = uploadedBytes;
        this.totalBytes = totalBytes;
        this.downloadSpeed = downloadSpeed;
        this.uploadSpeed = uploadSpeed;
        this.ETA = ETA;
        this.dateAdded = dateAdded;
        this.totalPeers = totalPeers;
        this.peers = peers;
    }

    public BasicStateParcel(Parcel source)
    {
        super(source);

        torrentId = source.readString();
        name = source.readString();
        stateCode = (TorrentStateCode) source.readSerializable();
        progress = source.readInt();
        receivedBytes = source.readLong();
        uploadedBytes = source.readLong();
        totalBytes = source.readLong();
        downloadSpeed = source.readLong();
        uploadSpeed = source.readLong();
        ETA = source.readLong();
        dateAdded = source.readLong();
        totalPeers = source.readInt();
        peers = source.readInt();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        super.writeToParcel(dest, flags);

        dest.writeString(torrentId);
        dest.writeString(name);
        dest.writeSerializable(stateCode);
        dest.writeInt(progress);
        dest.writeLong(receivedBytes);
        dest.writeLong(uploadedBytes);
        dest.writeLong(totalBytes);
        dest.writeLong(downloadSpeed);
        dest.writeLong(uploadSpeed);
        dest.writeLong(ETA);
        dest.writeLong(dateAdded);
        dest.writeInt(totalPeers);
        dest.writeInt(peers);
    }

    public static final Parcelable.Creator<BasicStateParcel> CREATOR =
            new Parcelable.Creator<BasicStateParcel>()
            {
                @Override
                public BasicStateParcel createFromParcel(Parcel source)
                {
                    return new BasicStateParcel(source);
                }

                @Override
                public BasicStateParcel[] newArray(int size)
                {
                    return new BasicStateParcel[size];
                }
            };

    @Override
    public int compareTo(BasicStateParcel another)
    {
        return name.compareTo(another.name);
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((torrentId == null) ? 0 : torrentId.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((stateCode == null) ? 0 : stateCode.hashCode());
        result = prime * result + progress;
        result = prime * result + (int) (receivedBytes ^ (receivedBytes >>> 32));
        result = prime * result + (int) (uploadedBytes ^ (uploadedBytes >>> 32));
        result = prime * result + (int) (totalBytes ^ (totalBytes >>> 32));
        result = prime * result + (int) (downloadSpeed ^ (downloadSpeed >>> 32));
        result = prime * result + (int) (uploadSpeed ^ (uploadSpeed >>> 32));
        result = prime * result + (int) (ETA ^ (ETA >>> 32));
        result = prime * result + (int) (dateAdded ^ (dateAdded >>> 32));
        result = prime * result + totalPeers;
        result = prime * result + peers;

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof BasicStateParcel))
            return false;

        if (o == this)
            return true;

        BasicStateParcel state = (BasicStateParcel) o;

        return (torrentId == null || torrentId.equals(state.torrentId)) &&
                (name == null || name.equals(state.name)) &&
                (stateCode == null || stateCode.equals(state.stateCode)) &&
                progress == state.progress &&
                receivedBytes == state.receivedBytes &&
                uploadedBytes == state.uploadedBytes &&
                totalBytes == state.totalBytes &&
                downloadSpeed == state.downloadSpeed &&
                uploadSpeed == state.uploadSpeed &&
                ETA == state.ETA &&
                dateAdded == state.dateAdded &&
                totalPeers == state.totalPeers &&
                peers == state.peers;
    }

    @Override
    public String toString()
    {
        return "BasicStateParcel{" +
                "torrentId='" + torrentId + '\'' +
                ", name='" + name + '\'' +
                ", stateCode=" + stateCode +
                ", progress=" + progress +
                ", receivedBytes=" + receivedBytes +
                ", uploadedBytes=" + uploadedBytes +
                ", totalBytes=" + totalBytes +
                ", downloadSpeed=" + downloadSpeed +
                ", uploadSpeed=" + uploadSpeed +
                ", ETA=" + ETA +
                ", dateAdded=" + dateAdded +
                ", totalPeers=" + totalPeers +
                ", peers=" + peers +
                '}';
    }
}
