/*
 * Copyright (C) 2018-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;

import java.util.Arrays;

/*
 * The class provides a package model with dynamically
 * changing information about state of the torrent.
 */

public class AdvancedTorrentInfo extends AbstractInfoParcel<AdvancedTorrentInfo> {
    public String torrentId;
    public int totalSeeds;
    public int seeds;
    public int downloadedPieces;
    public long[] filesReceivedBytes;
    public double shareRatio;
    public long activeTime;
    public long seedingTime;
    public double availability;
    public double[] filesAvailability;
    public int leechers;
    public int totalLeechers;

    public AdvancedTorrentInfo(String torrentId, long[] filesReceivedBytes,
                               int totalSeeds, int seeds, int downloadedPieces,
                               double shareRatio, long activeTime, long seedingTime,
                               double availability, double[] filesAvailability,
                               int leechers, int totalLeechers) {
        super(torrentId);

        this.torrentId = torrentId;
        this.filesReceivedBytes = filesReceivedBytes;
        this.totalSeeds = totalSeeds;
        this.seeds = seeds;
        this.downloadedPieces = downloadedPieces;
        this.shareRatio = shareRatio;
        this.activeTime = activeTime;
        this.seedingTime = seedingTime;
        this.availability = availability;
        this.filesAvailability = filesAvailability;
        this.leechers = leechers;
        this.totalLeechers = totalLeechers;
    }

    public AdvancedTorrentInfo(Parcel source) {
        super(source);

        torrentId = source.readString();
        filesReceivedBytes = source.createLongArray();
        totalSeeds = source.readInt();
        seeds = source.readInt();
        downloadedPieces = source.readInt();
        shareRatio = source.readDouble();
        activeTime = source.readLong();
        seedingTime = source.readLong();
        availability = source.readDouble();
        filesAvailability = source.createDoubleArray();
        leechers = source.readInt();
        totalLeechers = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(torrentId);
        dest.writeLongArray(filesReceivedBytes);
        dest.writeInt(totalSeeds);
        dest.writeInt(seeds);
        dest.writeInt(downloadedPieces);
        dest.writeDouble(shareRatio);
        dest.writeLong(activeTime);
        dest.writeLong(seedingTime);
        dest.writeDouble(availability);
        dest.writeDoubleArray(filesAvailability);
        dest.writeInt(leechers);
        dest.writeInt(totalLeechers);
    }

    public static final Parcelable.Creator<AdvancedTorrentInfo> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public AdvancedTorrentInfo createFromParcel(Parcel source) {
                    return new AdvancedTorrentInfo(source);
                }

                @Override
                public AdvancedTorrentInfo[] newArray(int size) {
                    return new AdvancedTorrentInfo[size];
                }
            };

    @Override
    public int compareTo(@NonNull AdvancedTorrentInfo another) {
        return torrentId.compareTo(another.torrentId);
    }

    @Override
    public int hashCode() {
        int prime = 31, result = 1;

        result = prime * result + ((torrentId == null) ? 0 : torrentId.hashCode());
        result += Arrays.hashCode(filesReceivedBytes);
        result = prime * result + totalSeeds;
        result = prime * result + seeds;
        result = prime * result + downloadedPieces;
        result = prime * result + Double.hashCode(shareRatio);
        result = prime * result + Long.hashCode(activeTime);
        result = prime * result + Long.hashCode(seedingTime);
        result = prime * result + Double.hashCode(availability);
        result += Arrays.hashCode(filesAvailability);
        result = prime * result + leechers;
        result = prime * result + totalLeechers;

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AdvancedTorrentInfo state)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        return (torrentId == null || torrentId.equals(state.torrentId)) &&
                totalSeeds == state.totalSeeds &&
                seeds == state.seeds &&
                downloadedPieces == state.downloadedPieces &&
                Arrays.equals(filesReceivedBytes, state.filesReceivedBytes) &&
                shareRatio == state.shareRatio &&
                activeTime == state.activeTime &&
                seedingTime == state.seedingTime &&
                availability == state.availability &&
                Arrays.equals(filesAvailability, state.filesAvailability) &&
                leechers == state.leechers &&
                totalLeechers == state.totalLeechers;
    }

    @NonNull
    @Override
    public String toString() {
        return "AdvancedTorrentInfo{" +
                "torrentId='" + torrentId + '\'' +
                ", totalSeeds=" + totalSeeds +
                ", seeds=" + seeds +
                ", downloadedPieces=" + downloadedPieces +
                ", filesReceivedBytes=" + Arrays.toString(filesReceivedBytes) +
                ", shareRatio=" + shareRatio +
                ", activeTime=" + activeTime +
                ", seedingTime=" + seedingTime +
                ", availability=" + availability +
                ", filesAvailability=" + Arrays.toString(filesAvailability) +
                ", leechers=" + leechers +
                ", totalLeechers=" + totalLeechers +
                '}';
    }
}
