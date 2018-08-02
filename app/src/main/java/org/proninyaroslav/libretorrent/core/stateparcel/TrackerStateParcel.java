/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import com.frostwire.jlibtorrent.AnnounceEndpoint;
import com.frostwire.jlibtorrent.AnnounceEntry;

import java.util.List;

/*
 * The class provides a package model with information
 * about the state of the bittorrent tracker, sent from the service.
 */

public class TrackerStateParcel extends AbstractStateParcel<TrackerStateParcel>
{
    public static final String DHT_ENTRY_NAME = "**DHT**";
    public static final String LSD_ENTRY_NAME = "**LSD**";
    public static final String PEX_ENTRY_NAME = "**PeX**";

    public String url;
    public String message;
    public int tier;
    public int status;

    public class Status
    {
        public static final int UNKNOWN = -1;
        public static final int WORKING = 0;
        public static final int UPDATING = 1;
        public static final int NOT_CONTACTED = 2;
        public static final int NOT_WORKING = 3;
    }

    public TrackerStateParcel(AnnounceEntry entry)
    {
        super(entry.url());

        url = entry.url();
        tier = entry.tier();

        if (entry.endpoints().size() == 0) {
            status = Status.NOT_WORKING;
            message = "";

        } else {
            AnnounceEndpoint bestEndpoint = getBestEndpoint(entry.endpoints());

            message = bestEndpoint.message();
            status = makeStatus(entry, bestEndpoint);
        }
    }

    public TrackerStateParcel(String url, String message, int tier, int status)
    {
        super(url);

        this.url = url;
        this.message = message;
        this.tier = tier;
        this.status = status;
    }

    private int makeStatus(AnnounceEntry entry, AnnounceEndpoint endpoint)
    {
        if (entry == null)
            return Status.UNKNOWN;

        if (entry.isVerified() && endpoint.isWorking())
            return Status.WORKING;
        else if ((endpoint.fails() == 0) && endpoint.updating())
            return Status.UPDATING;
        else if (endpoint.fails() == 0)
            return Status.NOT_CONTACTED;
        else
            return Status.NOT_WORKING;
    }

    private AnnounceEndpoint getBestEndpoint(List<AnnounceEndpoint> endpoints)
    {
        if (endpoints.size() == 1)
            return endpoints.get(0);

        AnnounceEndpoint bestEndpoint = endpoints.get(0);
        for (int i = 0; i < endpoints.size(); i++)
            if (endpoints.get(i).fails() < bestEndpoint.fails())
                bestEndpoint = endpoints.get(i);

        return bestEndpoint;
    }

    public TrackerStateParcel(Parcel source)
    {
        super(source);

        url = source.readString();
        message = source.readString();
        tier = source.readInt();
        status = source.readInt();
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

        dest.writeString(url);
        dest.writeString(message);
        dest.writeInt(tier);
        dest.writeInt(status);
    }

    public static final Parcelable.Creator<TrackerStateParcel> CREATOR =
            new Parcelable.Creator<TrackerStateParcel>()
            {
                @Override
                public TrackerStateParcel createFromParcel(Parcel source)
                {
                    return new TrackerStateParcel(source);
                }

                @Override
                public TrackerStateParcel[] newArray(int size)
                {
                    return new TrackerStateParcel[size];
                }
            };


    @Override
    public int compareTo(TrackerStateParcel another)
    {
        return url.compareTo(another.url);
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + tier;
        result = prime * result + status;

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof TrackerStateParcel)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        TrackerStateParcel state = (TrackerStateParcel) o;

        return (url == null || url.equals(state.url)) &&
                (message == null || message.equals(state.message)) &&
                tier == state.tier &&
                status == state.status;
    }

    @Override
    public String toString()
    {
        String status;

        switch (this.status) {
            case Status.NOT_CONTACTED:
                status = "NOT_CONTACTED";
                break;
            case Status.WORKING:
                status = "WORKING";
                break;
            case Status.UPDATING:
                status = "UPDATING";
                break;
            case Status.NOT_WORKING:
                status = "NOT_WORKING";
                break;
            default:
                status = "UNKNOWN";
        }

        return "TrackerStateParcel{" +
                "url='" + url + '\'' +
                ", message='" + message + '\'' +
                ", tier=" + tier +
                ", status=" + status +
                '}';
    }
}
