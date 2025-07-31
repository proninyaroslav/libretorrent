/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data.metainfo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.apache.commons.io.IOUtils;
import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.swig.add_torrent_params;
import org.libtorrent4j.swig.libtorrent_jni;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Objects;

/*
 * Provides full information about the torrent, taken from bencode.
 */

public class TorrentMetaInfo implements Parcelable {
    @NonNull
    public String torrentName = "";
    @NonNull
    public String sha1Hash = "";
    @NonNull
    public String comment = "";
    @NonNull
    public String createdBy = "";
    public long torrentSize = 0L;
    public long creationDate = 0L;
    public int fileCount = 0;
    public int pieceLength = 0;
    public int numPieces = 0;
    public ArrayList<BencodeFileItem> fileList = new ArrayList<>();

    public TorrentMetaInfo(@NonNull String torrentName, @NonNull String sha1hash) {
        this.torrentName = torrentName;
        this.sha1Hash = sha1hash;
    }

    public TorrentMetaInfo(@NonNull byte[] data) throws DecodeException {
        try {
            var buffer = ByteBuffer.allocateDirect(data.length);
            long ptr = libtorrent_jni.directBufferAddress(buffer);
            long size = libtorrent_jni.directBufferCapacity(buffer);
            add_torrent_params params = add_torrent_params.load_torrent_buffer(ptr, (int) size);

            getMetaInfo(TorrentInfo.bdecode(data), params);

        } catch (Exception e) {
            throw new DecodeException(e);
        }
    }

    public TorrentMetaInfo(TorrentInfo info, add_torrent_params params) throws DecodeException {
        try {
            getMetaInfo(info, params);

        } catch (Exception e) {
            throw new DecodeException(e);
        }
    }

    public TorrentMetaInfo(FileInputStream is) throws DecodeException {
        FileChannel chan = null;
        try {
            chan = is.getChannel();
            var buffer = chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size());
            long ptr = libtorrent_jni.directBufferAddress(buffer);
            long size = libtorrent_jni.directBufferCapacity(buffer);
            add_torrent_params params = add_torrent_params.load_torrent_buffer(ptr, (int) size);
            getMetaInfo(new TorrentInfo(buffer), params);

        } catch (Exception e) {
            throw new DecodeException(e);
        } finally {
            IOUtils.closeQuietly(chan);
        }
    }

    private void getMetaInfo(TorrentInfo info, add_torrent_params params) {
        torrentName = info.name();
        sha1Hash = info.infoHash().toHex();
        comment = params.getComment();
        createdBy = params.getCreated_by();
        /* Correct convert UNIX time (time_t) */
        creationDate = params.getCreation_date() * 1000L;
        torrentSize = info.totalSize();
        fileCount = info.numFiles();
        fileList = Utils.getFileList(info.files());
        pieceLength = info.pieceLength();
        numPieces = info.numPieces();
    }

    public TorrentMetaInfo(Parcel source) {
        torrentName = Objects.requireNonNull(source.readString());
        sha1Hash = Objects.requireNonNull(source.readString());
        comment = Objects.requireNonNull(source.readString());
        createdBy = Objects.requireNonNull(source.readString());
        torrentSize = source.readLong();
        creationDate = source.readLong();
        fileCount = source.readInt();
        fileList = new ArrayList<>();
        source.readTypedList(fileList, BencodeFileItem.CREATOR);
        pieceLength = source.readInt();
        numPieces = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(torrentName);
        dest.writeString(sha1Hash);
        dest.writeString(comment);
        dest.writeString(createdBy);
        dest.writeLong(torrentSize);
        dest.writeLong(creationDate);
        dest.writeInt(fileCount);
        dest.writeTypedList(fileList);
        dest.writeInt(pieceLength);
        dest.writeInt(numPieces);
    }

    public static final Parcelable.Creator<TorrentMetaInfo> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public TorrentMetaInfo createFromParcel(Parcel source) {
                    return new TorrentMetaInfo(source);
                }

                @Override
                public TorrentMetaInfo[] newArray(int size) {
                    return new TorrentMetaInfo[size];
                }
            };

    @Override
    public int hashCode() {
        return sha1Hash.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TorrentMetaInfo info))
            return false;

        if (o == this)
            return true;

        return torrentName.equals(info.torrentName) &&
                sha1Hash.equals(info.sha1Hash) &&
                comment.equals(info.comment) &&
                createdBy.equals(info.createdBy) &&
                torrentSize == info.torrentSize &&
                creationDate == info.creationDate &&
                fileCount == info.fileCount &&
                pieceLength == info.pieceLength &&
                numPieces == info.numPieces;
    }

    @NonNull
    @Override
    public String toString() {
        return "TorrentMetaInfo{" +
                "torrentName='" + torrentName + '\'' +
                ", sha1Hash='" + sha1Hash + '\'' +
                ", comment='" + comment + '\'' +
                ", createdBy='" + createdBy + '\'' +
                ", torrentSize=" + torrentSize +
                ", creationDate=" + creationDate +
                ", fileCount=" + fileCount +
                ", pieceLength=" + pieceLength +
                ", numPieces=" + numPieces +
                ", fileList=" + fileList +
                '}';
    }
}
