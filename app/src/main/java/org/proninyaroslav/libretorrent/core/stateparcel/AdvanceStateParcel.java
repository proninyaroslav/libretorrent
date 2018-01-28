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

import java.util.Arrays;

/*
 * The class provides a package model with dynamically
 * changing information about state of the torrent.
 */

public class AdvanceStateParcel extends AbstractStateParcel<AdvanceStateParcel>
{
    public String torrentId = "";
    public int totalSeeds = 0;
    public int seeds = 0;
    public int downloadedPieces = 0;
    public long[] filesReceivedBytes = new long[0];
    public double shareRatio = 0.0;
    public long activeTime = 0L;
    public long seedingTime = 0L;
    public double availability = 0.0;
    public double[] filesAvailability = new double[0];

    public AdvanceStateParcel()
    {
        super();
    }

    public AdvanceStateParcel(String torrentId, long[] filesReceivedBytes,
                              int totalSeeds, int seeds, int downloadedPieces,
                              double shareRatio, long activeTime, long seedingTime,
                              double availability, double[] filesAvailability)
    {
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
    }

    public AdvanceStateParcel(Parcel source)
    {
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
        dest.writeLongArray(filesReceivedBytes);
        dest.writeInt(totalSeeds);
        dest.writeInt(seeds);
        dest.writeInt(downloadedPieces);
        dest.writeDouble(shareRatio);
        dest.writeLong(activeTime);
        dest.writeLong(seedingTime);
        dest.writeDouble(availability);
        dest.writeDoubleArray(filesAvailability);
    }

    public static final Parcelable.Creator<AdvanceStateParcel> CREATOR =
            new Parcelable.Creator<AdvanceStateParcel>()
            {
                @Override
                public AdvanceStateParcel createFromParcel(Parcel source)
                {
                    return new AdvanceStateParcel(source);
                }

                @Override
                public AdvanceStateParcel[] newArray(int size)
                {
                    return new AdvanceStateParcel[size];
                }
            };

    @Override
    public int compareTo(AdvanceStateParcel another)
    {
        return torrentId.compareTo(another.torrentId);
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((torrentId == null) ? 0 : torrentId.hashCode());
        result += Arrays.hashCode(filesReceivedBytes);
        result = prime * result + totalSeeds;
        result = prime * result + seeds;
        result = prime * result + downloadedPieces;
        long shareRationBits = Double.doubleToLongBits(shareRatio);
        result = prime * result + (int) (shareRationBits ^ (shareRationBits >>> 32));
        result = prime * result + (int) (activeTime ^ (activeTime >>> 32));
        result = prime * result + (int) (seedingTime ^ (seedingTime >>> 32));
        long availabilityBits = Double.doubleToLongBits(availability);
        result = prime * result + (int) (availabilityBits ^ (availabilityBits >>> 32));
        result += Arrays.hashCode(filesAvailability);

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof AdvanceStateParcel))
            return false;

        if (o == this)
            return true;

        AdvanceStateParcel state = (AdvanceStateParcel) o;

        return (torrentId == null || torrentId.equals(state.torrentId)) &&
                totalSeeds == state.totalSeeds &&
                seeds == state.seeds &&
                downloadedPieces == state.downloadedPieces &&
                Arrays.equals(filesReceivedBytes, state.filesReceivedBytes) &&
                shareRatio == state.shareRatio &&
                activeTime == state.activeTime &&
                seedingTime == state.seedingTime &&
                availability == state.availability &&
                Arrays.equals(filesAvailability, state.filesAvailability);
    }

    @Override
    public String toString()
    {
        return "AdvanceStateParcel{" +
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
                '}';
    }
}
