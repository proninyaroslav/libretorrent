/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data.entity;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * The class encapsulates the torrent file and its meta information.
 */

@Entity
public class Torrent implements Parcelable
{
    /*
     * This torrent is visible and shows in the notifications after completion
     */
    public static final int VISIBILITY_VISIBLE_NOTIFY_FINISHED = 0;
    /*
     * This torrent doesn't show in the notifications
     */
    public static final int VISIBILITY_HIDDEN = 1;

    /* Usually torrent SHA-1 hash */
    @NonNull
    @PrimaryKey
    public String id;
    @NonNull
    public String name;
    @NonNull
    public Uri downloadPath;
    public long dateAdded;
    public String error;
    public boolean manuallyPaused;
    /*
     * Warning: read-only field, do not change directly,
     *          only trough setMagnetUri
     */
    private String magnet;
    public boolean downloadingMetadata = false;
    public int visibility = VISIBILITY_VISIBLE_NOTIFY_FINISHED;

    @Ignore
    public Torrent(@NonNull String id,
                   @NonNull Uri downloadPath,
                   @NonNull String name,
                   boolean manuallyPaused,
                   long dateAdded)
    {
        this.id = id;
        this.name = name;
        this.downloadPath = downloadPath;
        this.manuallyPaused = manuallyPaused;
        this.dateAdded = dateAdded;
    }

    public Torrent(@NonNull String id,
                   String magnet,
                   @NonNull Uri downloadPath,
                   @NonNull String name,
                   boolean manuallyPaused,
                   long dateAdded)
    {
        this(id, downloadPath, name, manuallyPaused, dateAdded);

        this.magnet = magnet;
    }

    @Ignore
    public Torrent(Parcel source)
    {
        id = source.readString();
        magnet = source.readString();
        downloadPath = source.readParcelable(Uri.class.getClassLoader());
        name = source.readString();
        downloadingMetadata = source.readByte() != 0;
        dateAdded = source.readLong();
        error = source.readString();
        manuallyPaused = source.readByte() != 0;
        visibility = source.readInt();
    }

    public boolean isDownloadingMetadata()
    {
        return downloadingMetadata;
    }

    public String getMagnet()
    {
        return magnet;
    }

    public void setMagnetUri(String magnet)
    {
        this.magnet = magnet;
        downloadingMetadata = magnet != null;
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
        dest.writeString(magnet);
        dest.writeParcelable(downloadPath, flags);
        dest.writeString(name);
        dest.writeByte((byte)(downloadingMetadata ? 1 : 0));
        dest.writeLong(dateAdded);
        dest.writeString(error);
        dest.writeByte((byte)(manuallyPaused ? 1 : 0));
        dest.writeInt(visibility);
    }

    public static final Creator<Torrent> CREATOR =
            new Creator<Torrent>()
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
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Torrent && (o == this || id.equals(((Torrent)o).id));
    }

    @Override
    public String toString()
    {
        return "Torrent{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", downloadPath=" + downloadPath +
                ", dateAdded=" + SimpleDateFormat.getDateTimeInstance().format(new Date(dateAdded)) +
                ", error='" + error + '\'' +
                ", manuallyPaused=" + manuallyPaused +
                ", magnet='" + magnet + '\'' +
                ", downloadingMetadata=" + downloadingMetadata +
                ", visibility=" + visibility +
                '}';
    }
}
