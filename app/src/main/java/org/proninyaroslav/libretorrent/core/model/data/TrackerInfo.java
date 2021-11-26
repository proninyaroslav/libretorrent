/*
 * Copyright (C) 2016, 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.libtorrent4j.AnnounceEndpoint;
import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.AnnounceInfohash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * The class provides a package model with information
 * about the state of the bittorrent tracker, sent from the service.
 */

public class TrackerInfo extends AbstractInfoParcel {
    public String url;
    public String message;
    public int tier;
    public int status;

    public static class Status {
        public static final int UNKNOWN = -1;
        public static final int WORKING = 0;
        public static final int UPDATING = 1;
        public static final int NOT_CONTACTED = 2;
        public static final int NOT_WORKING = 3;
    }

    private class Result {
        int status;
        String message;

        Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    public TrackerInfo(AnnounceEntry entry) {
        super(entry.url());

        url = entry.url();
        tier = entry.tier();

        List<AnnounceEndpoint> endpoints = entry.endpoints();
        var result = getMessageAndStatus(entry, endpoints);
        message = result.message;
        status = result.status;
    }

    public TrackerInfo(String url, String message, int tier, int status) {
        super(url);

        this.url = url;
        this.message = message;
        this.tier = tier;
        this.status = status;
    }

    private Result getMessageAndStatus(AnnounceEntry entry, List<AnnounceEndpoint> endpoints) {
        if (entry == null || endpoints.isEmpty()) {
            return new Result(Status.UNKNOWN, "");
        }

        var statusMap = new HashMap<Integer, Integer>();
        String firstTrackerMessage = "";
        String firstErrorMessage = "";
        for (var e : endpoints) {
            calcStatusCount(entry, e.infohashV1(), statusMap);
            calcStatusCount(entry, e.infohashV2(), statusMap);

            if (TextUtils.isEmpty(firstTrackerMessage)) {
                var messageV1 = e.infohashV1().message();
                var messageV2 = e.infohashV2().message();
                if (!TextUtils.isEmpty(messageV1)) {
                    firstTrackerMessage = messageV1;
                } else if (!TextUtils.isEmpty(messageV2)) {
                    firstTrackerMessage = messageV2;
                }
            }
            if (TextUtils.isEmpty(firstErrorMessage)) {
                var errorV1 = e.infohashV1().swig().getLast_error();
                var errorV2 = e.infohashV2().swig().getLast_error();
                if (errorV1 != null && !TextUtils.isEmpty(errorV1.message())) {
                    firstErrorMessage = errorV1.message();
                } else if (errorV2 != null && !TextUtils.isEmpty(errorV2.message())) {
                    firstErrorMessage = errorV2.message();
                }
            }
        }
        var numEndpoints = statusMap.values().stream().reduce(0, Integer::sum);
        var numUpdating = statusMap.get(Status.UPDATING);
        var numWorking = statusMap.get(Status.WORKING);
        var numNotWorking = statusMap.get(Status.NOT_WORKING);
        var numNotContacted = statusMap.get(Status.NOT_CONTACTED);

        if (numUpdating != null && numUpdating > 0) {
            return new Result(Status.UPDATING, "");
        } else if (numWorking != null && numWorking > 0) {
            return new Result(Status.WORKING, firstTrackerMessage);
        } else if (numNotWorking != null && numNotWorking.equals(numEndpoints)) {
            return new Result(
                    Status.NOT_WORKING,
                    TextUtils.isEmpty(firstTrackerMessage)
                            ? firstErrorMessage
                            : firstTrackerMessage
            );
        } else if (numNotContacted != null && numNotContacted.equals(numEndpoints)) {
            return new Result(Status.NOT_CONTACTED,
                    TextUtils.isEmpty(firstTrackerMessage)
                            ? firstErrorMessage
                            : firstTrackerMessage
            );
        }

        return new Result(Status.UNKNOWN, "");
    }

    private void calcStatusCount(
            AnnounceEntry entry,
            AnnounceInfohash infoHash,
            Map<Integer, Integer> statusMap
    ) {
        var status = infoHashStatus(entry, infoHash);
        var count = statusMap.getOrDefault(status, 0);
        statusMap.put(status, count + 1);
    }

    private int infoHashStatus(AnnounceEntry entry, AnnounceInfohash infoHash) {
        if (infoHash.updating()) {
            return Status.UPDATING;
        } else if (infoHash.fails() > 0) {
            return Status.NOT_WORKING;
        } else if (entry.isVerified()) {
            return Status.WORKING;
        } else {
            return Status.NOT_CONTACTED;
        }
    }

    public TrackerInfo(Parcel source) {
        super(source);

        url = source.readString();
        message = source.readString();
        tier = source.readInt();
        status = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(url);
        dest.writeString(message);
        dest.writeInt(tier);
        dest.writeInt(status);
    }

    public static final Parcelable.Creator<TrackerInfo> CREATOR =
            new Parcelable.Creator<TrackerInfo>() {
                @Override
                public TrackerInfo createFromParcel(Parcel source) {
                    return new TrackerInfo(source);
                }

                @Override
                public TrackerInfo[] newArray(int size) {
                    return new TrackerInfo[size];
                }
            };


    @Override
    public int compareTo(@NonNull Object another) {
        return url.compareTo(((TrackerInfo) another).url);
    }

    @Override
    public int hashCode() {
        int prime = 31, result = 1;

        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + tier;
        result = prime * result + status;

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackerInfo))
            return false;

        if (o == this)
            return true;

        TrackerInfo state = (TrackerInfo) o;

        return (url == null || url.equals(state.url)) &&
                (message == null || message.equals(state.message)) &&
                tier == state.tier &&
                status == state.status;
    }

    @NonNull
    @Override
    public String toString() {
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

        return "TrackerInfo{" +
                "url='" + url + '\'' +
                ", message='" + message + '\'' +
                ", tier=" + tier +
                ", status=" + status +
                '}';
    }
}
