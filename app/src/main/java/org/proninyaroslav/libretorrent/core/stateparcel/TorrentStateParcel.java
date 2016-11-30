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

package org.proninyaroslav.libretorrent.core.stateparcel;

import android.os.Parcel;
import android.os.Parcelable;

import org.proninyaroslav.libretorrent.core.TorrentStateCode;

import java.util.Arrays;

/*
 * The class provides a package model with information
 * about state of the torrent, sent from the service.
 */

public class TorrentStateParcel extends AbstractStateParcel<TorrentStateParcel>
{
    public String torrentId = "";
    public String name = "";
    public TorrentStateCode stateCode = TorrentStateCode.UNKNOWN;
    public int progress = 0;
    public int seeds = 0;
    public int totalSeeds = 0;
    public int peers = 0;
    public int totalPeers = 0;
    public int downloadedPieces = 0;
    public long receivedBytes = 0L;
    public long uploadedBytes = 0L;
    public long totalBytes = 0L;
    public long downloadSpeed = 0L;
    public long uploadSpeed = 0L;
    public long ETA = -1L;
    public long[] filesReceivedBytes = new long[0];
    public double shareRatio = 0.;

    boolean equalsById = false;

    public TorrentStateParcel()
    {
        super();
    }

    public TorrentStateParcel(String torrentId, String name)
    {
        super(torrentId);

        this.torrentId = torrentId;
        this.name = name;
        stateCode = TorrentStateCode.STOPPED;
    }

    public TorrentStateParcel(String torrentId, String name,
                              TorrentStateCode stateCode, int progress,
                              long receivedBytes, long uploadedBytes,
                              long totalBytes, long downloadSpeed, long uploadSpeed,
                              long ETA, long[] filesReceivedBytes, int totalSeeds,
                              int seeds, int totalPeers, int peers, int downloadedPieces,
                              double shareRatio)
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
        this.filesReceivedBytes = filesReceivedBytes;
        this.seeds = seeds;
        this.totalSeeds = totalSeeds;
        this.peers = peers;
        this.totalPeers = totalPeers;
        this.downloadedPieces = downloadedPieces;
        this.shareRatio = shareRatio;
    }

    public TorrentStateParcel(Parcel source)
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
        filesReceivedBytes = source.createLongArray();
        seeds = source.readInt();
        totalSeeds = source.readInt();
        peers = source.readInt();
        totalPeers = source.readInt();
        downloadedPieces = source.readInt();
        shareRatio = source.readDouble();
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
        dest.writeLongArray(filesReceivedBytes);
        dest.writeInt(seeds);
        dest.writeInt(totalSeeds);
        dest.writeInt(peers);
        dest.writeInt(totalPeers);
        dest.writeInt(downloadedPieces);
        dest.writeDouble(shareRatio);
    }

    public static final Parcelable.Creator<TorrentStateParcel> CREATOR =
            new Parcelable.Creator<TorrentStateParcel>()
            {
                @Override
                public TorrentStateParcel createFromParcel(Parcel source)
                {
                    return new TorrentStateParcel(source);
                }

                @Override
                public TorrentStateParcel[] newArray(int size)
                {
                    return new TorrentStateParcel[size];
                }
            };

    public boolean isEqualsById()
    {
        return equalsById;
    }

    public void setEqualsById(boolean equalsById)
    {
        this.equalsById = equalsById;
    }

    @Override
    public int compareTo(TorrentStateParcel another)
    {
        return name.compareTo(another.name);
    }

    @Override
    public int hashCode()
    {
        if (equalsById) {
            return torrentId.hashCode();
        }

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
        result += Arrays.hashCode(filesReceivedBytes);
        result = prime * result + seeds;
        result = prime * result + totalSeeds;
        result = prime * result + peers;
        result = prime * result + totalPeers;
        result = prime * result + downloadedPieces;
        long shareRationBits = Double.doubleToLongBits(shareRatio);
        result = prime * result + (int) (shareRationBits ^ (shareRationBits >>> 32));

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (equalsById) {
            return (o instanceof TorrentStateParcel &&
                    (torrentId.equals(((TorrentStateParcel) o).torrentId)));
        }

        if (!(o instanceof TorrentStateParcel)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        TorrentStateParcel state = (TorrentStateParcel) o;

        return (torrentId == null || torrentId.equals(state.torrentId)) &&
                (name == null || name.equals(state.name)) &&
                (stateCode == null || stateCode.equals(state.stateCode)) &&
                progress == state.progress &&
                seeds == state.seeds &&
                totalSeeds == state.totalSeeds &&
                peers == state.peers &&
                totalPeers == state.totalPeers &&
                downloadedPieces == state.downloadedPieces &&
                receivedBytes == state.receivedBytes &&
                uploadedBytes == state.uploadedBytes &&
                totalBytes == state.totalBytes &&
                downloadSpeed == state.downloadSpeed &&
                uploadSpeed == state.uploadSpeed &&
                ETA == state.ETA &&
                Arrays.equals(filesReceivedBytes, state.filesReceivedBytes) &&
                shareRatio == state.shareRatio;
    }

    @Override
    public String toString()
    {
        return "TorrentStateParcel{" +
                "torrentId='" + torrentId + '\'' +
                ", name='" + name + '\'' +
                ", stateCode=" + stateCode +
                ", progress=" + progress +
                ", seeds=" + seeds +
                ", totalSeeds=" + totalSeeds +
                ", peers=" + peers +
                ", totalPeers=" + totalPeers +
                ", downloadedPieces=" + downloadedPieces +
                ", receivedBytes=" + receivedBytes +
                ", uploadedBytes=" + uploadedBytes +
                ", totalBytes=" + totalBytes +
                ", downloadSpeed=" + downloadSpeed +
                ", uploadSpeed=" + uploadSpeed +
                ", ETA=" + ETA +
                ", filesReceivedBytes=" + Arrays.toString(filesReceivedBytes) +
                ", shareRatio=" + shareRatio +
                '}';
    }
}
