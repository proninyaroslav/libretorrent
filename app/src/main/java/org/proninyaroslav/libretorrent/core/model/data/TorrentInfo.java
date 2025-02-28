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

import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/*
 * The class provides a package model with dynamically changing information
 * about state of the torrent, sent from the service.
 */

public class TorrentInfo extends AbstractInfoParcel<TorrentInfo> {
    public static final long MAX_ETA = 8640000;

    @NonNull
    public String torrentId;
    public String name;
    public TorrentStateCode stateCode;
    public int progress = 0;
    public long receivedBytes = 0L;
    public long uploadedBytes = 0L;
    public long totalBytes = 0L;
    public long downloadSpeed = 0L;
    public long uploadSpeed = 0L;
    public long ETA = MAX_ETA;
    public long dateAdded;
    public int totalPeers = 0;
    public int peers = 0;
    public String error;
    public boolean sequentialDownload = false;
    public Priority[] filePriorities = new Priority[0];
    public List<TagInfo> tags;
    public boolean firstLastPiecePriority = false;

    public TorrentInfo(@NonNull String torrentId) {
        super(torrentId);
        this.torrentId = torrentId;
    }

    public TorrentInfo(
            @NonNull String torrentId,
            String name,
            long dateAdded,
            String error,
            @NonNull List<TagInfo> tags
    ) {
        super(torrentId);

        this.torrentId = torrentId;
        this.name = name;
        this.stateCode = TorrentStateCode.STOPPED;
        this.dateAdded = dateAdded;
        this.error = error;
        this.tags = tags;
    }

    public TorrentInfo(
            @NonNull String torrentId,
            String name,
            TorrentStateCode stateCode,
            int progress,
            long receivedBytes,
            long uploadedBytes,
            long totalBytes,
            long downloadSpeed,
            long uploadSpeed,
            long ETA,
            long dateAdded,
            int totalPeers,
            int peers,
            String error,
            boolean sequentialDownload,
            Priority[] filePriorities,
            @NonNull List<TagInfo> tags,
            boolean firstLastPiecePriority
    ) {
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
        this.error = error;
        this.sequentialDownload = sequentialDownload;
        this.filePriorities = filePriorities;
        this.tags = tags;
        this.firstLastPiecePriority = firstLastPiecePriority;
    }

    public TorrentInfo(Parcel source) {
        super(source);

        torrentId = Objects.requireNonNull(source.readString());
        name = source.readString();
        stateCode = TorrentStateCode.fromValue(source.readInt());
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
        error = source.readString();
        sequentialDownload = source.readByte() != 0;
        filePriorities = (Priority[]) source.readArray(Priority.class.getClassLoader());
        tags = new ArrayList<>();
        source.readTypedList(tags, TagInfo.CREATOR);
        firstLastPiecePriority = source.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(torrentId);
        dest.writeString(name);
        dest.writeInt(stateCode.value());
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
        dest.writeString(error);
        dest.writeByte((byte) (sequentialDownload ? 1 : 0));
        dest.writeArray(filePriorities);
        dest.writeTypedList(tags);
        dest.writeByte((byte) (firstLastPiecePriority ? 1 : 0));
    }

    public static final Parcelable.Creator<TorrentInfo> CREATOR = new Parcelable.Creator<>() {
        @Override
        public TorrentInfo createFromParcel(Parcel source) {
            return new TorrentInfo(source);
        }

        @Override
        public TorrentInfo[] newArray(int size) {
            return new TorrentInfo[size];
        }
    };

    @Override
    public int compareTo(@NonNull TorrentInfo another) {
        return name.compareTo((another).name);
    }

    @Override
    public int hashCode() {
        int prime = 31, result = 1;

        result = prime * result + torrentId.hashCode();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((stateCode == null) ? 0 : stateCode.hashCode());
        result = prime * result + progress;
        result = prime * result + Long.hashCode(receivedBytes);
        result = prime * result + Long.hashCode(uploadedBytes);
        result = prime * result + Long.hashCode(totalBytes);
        result = prime * result + Long.hashCode(downloadSpeed);
        result = prime * result + Long.hashCode(uploadSpeed);
        result = prime * result + Long.hashCode(ETA);
        result = prime * result + Long.hashCode(dateAdded);
        result = prime * result + totalPeers;
        result = prime * result + peers;
        result = prime * result + ((error == null) ? 0 : error.hashCode());
        result = prime * result + (sequentialDownload ? 1 : 0);
        result = prime * result + Arrays.hashCode(filePriorities);
        result = prime * result + tags.hashCode();
        result = prime * result + (firstLastPiecePriority ? 1 : 0);

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TorrentInfo info)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        return (torrentId.equals(info.torrentId) &&
                (name == null || name.equals(info.name)) &&
                (stateCode == null || stateCode.equals(info.stateCode)) &&
                progress == info.progress &&
                receivedBytes == info.receivedBytes &&
                uploadedBytes == info.uploadedBytes &&
                totalBytes == info.totalBytes &&
                downloadSpeed == info.downloadSpeed &&
                uploadSpeed == info.uploadSpeed &&
                ETA == info.ETA &&
                dateAdded == info.dateAdded &&
                totalPeers == info.totalPeers &&
                peers == info.peers &&
                (error == null || error.equals(info.error)) &&
                sequentialDownload == info.sequentialDownload &&
                Arrays.equals(filePriorities, info.filePriorities) &&
                firstLastPiecePriority == info.firstLastPiecePriority &&
                tags.equals(info.tags));
    }

    @NonNull
    @Override
    public String toString() {
        return "TorrentInfo{" +
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
                ", error='" + error + '\'' +
                ", sequentialDownload=" + sequentialDownload +
                ", filePriorities=" + Arrays.toString(filePriorities) +
                ", tags=" + tags +
                ", firstLastPiecePriority=" + firstLastPiecePriority +
                '}';
    }
}
