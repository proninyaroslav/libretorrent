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

package org.proninyaroslav.libretorrent.core;

import android.os.Parcel;
import android.os.Parcelable;

import com.frostwire.jlibtorrent.TorrentInfo;

import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/*
 * Provides full information about the torrent, taken from bencode.
 */

public class TorrentMetaInfo implements Parcelable
{
    private String torrentName;
    private String sha1Hash;
    private String comment;
    private String createdBy;
    private long torrentSize;
    private long creationDate;
    private int fileCount;
    private int pieceLength;
    private int numPieces;
    private ArrayList<BencodeFileItem> fileList;

    public TorrentMetaInfo(String pathToTorrent) throws IOException, DecodeException
    {
        File torrentFile = new File(pathToTorrent);
        try {
            getMetaInfo(new TorrentInfo(torrentFile));

        } catch (IllegalArgumentException e) {
            throw new DecodeException(e);
        }
    }

    public TorrentMetaInfo(byte[] data) throws DecodeException
    {
        try {
            getMetaInfo(TorrentInfo.bdecode(data));

        } catch (IllegalArgumentException e) {
            throw new DecodeException(e);
        }
    }

    public TorrentMetaInfo(TorrentInfo info) throws DecodeException
    {
        try {
            getMetaInfo(info);

        } catch (IllegalArgumentException e) {
            throw new DecodeException(e);
        }
    }

    private void getMetaInfo(TorrentInfo info)
    {
        torrentName = info.name();
        sha1Hash = info.infoHash().toHex();
        comment = info.comment();
        createdBy = info.creator();
        /* Correct convert UNIX time (time_t) */
        creationDate = info.creationDate() * 1000L;
        torrentSize = info.totalSize();
        fileCount = info.numFiles();
        fileList = Utils.getFileList(info.origFiles());
        pieceLength = info.pieceLength();
        numPieces = info.numPieces();
    }

    public TorrentMetaInfo (Parcel source)
    {
        torrentName = source.readString();
        sha1Hash = source.readString();
        comment = source.readString();
        createdBy = source.readString();
        torrentSize = source.readLong();
        creationDate = source.readLong();
        fileCount = source.readInt();
        fileList = new ArrayList<>();
        source.readTypedList(fileList, BencodeFileItem.CREATOR);
        pieceLength = source.readInt();
        numPieces = source.readInt();
    }

    public String getTorrentName()
    {
        return torrentName;
    }

    public String getSha1Hash()
    {
        return sha1Hash;
    }

    public String getComment()
    {
        return comment;
    }

    public String getCreatedBy()
    {
        return createdBy;
    }

    public long getTorrentSize()
    {
        return torrentSize;
    }

    public long getCreationDate()
    {
        return creationDate;
    }

    public int getFileCount()
    {
        return fileCount;
    }

    public int getPieceLength()
    {
        return pieceLength;
    }

    public int getNumPieces()
    {
        return numPieces;
    }

    /*
     * The order of addition in the returned list corresponds
     * to the order of indexes in jlibtorrent.FileStorage
     */

    public ArrayList<BencodeFileItem> getFiles()
    {
        return fileList;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
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
            new Parcelable.Creator<TorrentMetaInfo>()
            {
                @Override
                public TorrentMetaInfo createFromParcel(Parcel source)
                {
                    return new TorrentMetaInfo(source);
                }

                @Override
                public TorrentMetaInfo[] newArray(int size)
                {
                    return new TorrentMetaInfo[size];
                }
            };

    @Override
    public String toString()
    {
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
