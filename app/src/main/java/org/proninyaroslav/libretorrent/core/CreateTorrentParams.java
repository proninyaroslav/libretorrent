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

package org.proninyaroslav.libretorrent.core;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class CreateTorrentParams implements Parcelable
{
    public static final String FILTER_SEPARATOR = "\\|";

    /* Path to the file or dir */
    private String path;
    private String pathToSave;
    ArrayList<String> trackerUrls;
    ArrayList<String> webSeedUrls;
    private String comments;
    boolean startSeeding, isPrivate, optimizeAlignment;
    private int pieceSize;
    ArrayList<String> skipFilesList;

    public CreateTorrentParams(String path, ArrayList<String> trackerUrls,
                               ArrayList<String> webSeedUrls, String comments,
                               boolean startSeeding, boolean isPrivate, boolean optimizeAlignment,
                               ArrayList<String> skipFilesList, int pieceSize) {
        this.path = path;
        this.trackerUrls = trackerUrls;
        this.webSeedUrls = webSeedUrls;
        this.comments = comments;
        this.startSeeding = startSeeding;
        this.isPrivate = isPrivate;
        this.optimizeAlignment = optimizeAlignment;
        this.skipFilesList = skipFilesList;
        this.pieceSize = pieceSize;
    }

    public CreateTorrentParams(Parcel source)
    {
        path = source.readString();
        pathToSave = source.readString();
        trackerUrls = source.readArrayList(String.class.getClassLoader());
        webSeedUrls = source.readArrayList(String.class.getClassLoader());
        comments = source.readString();
        startSeeding = source.readByte() != 0;
        isPrivate = source.readByte() != 0;
        optimizeAlignment = source.readByte() != 0;
        skipFilesList = source.readArrayList(String.class.getClassLoader());
        pieceSize = source.readInt();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(path);
        dest.writeString(pathToSave);
        dest.writeList(trackerUrls);
        dest.writeStringList(webSeedUrls);
        dest.writeString(comments);
        dest.writeByte((byte)(startSeeding ? 1 : 0));
        dest.writeByte((byte)(isPrivate ? 1 : 0));
        dest.writeByte((byte)(optimizeAlignment ? 1 : 0));
        dest.writeStringList(skipFilesList);
        dest.writeInt(pieceSize);
    }

    public static final Creator<CreateTorrentParams> CREATOR =
            new Creator<CreateTorrentParams>()
            {
                @Override
                public CreateTorrentParams createFromParcel(Parcel source)
                {
                    return new CreateTorrentParams(source);
                }

                @Override
                public CreateTorrentParams[] newArray(int size)
                {
                    return new CreateTorrentParams[size];
                }
            };

    public String getPath()
    {
        return path;
    }

    public String getPathToSave()
    {
        return pathToSave;
    }

    public void setPathToSave(String pathToSave)
    {
        this.pathToSave = pathToSave;
    }

    public ArrayList<String> getTrackerUrls()
    {
        return trackerUrls;
    }

    public ArrayList<String> getWebSeedUrls()
    {
        return webSeedUrls;
    }

    public String getComments()
    {
        return comments;
    }

    public boolean isStartSeeding()
    {
        return startSeeding;
    }

    public boolean isPrivate()
    {
        return isPrivate;
    }

    public boolean isOptimizeAlignment()
    {
        return optimizeAlignment;
    }

    public ArrayList<String> getSkipFilesList()
    {
        return skipFilesList;
    }

    public int getPieceSize()
    {
        return pieceSize;
    }

    @Override
    public int hashCode()
    {
        return path.hashCode();
    }

    @Override
    public String toString()
    {
        return "CreateTorrentParams{" +
                "path='" + path + '\'' +
                ", pathToSave='" + pathToSave + '\'' +
                ", trackerUrls=" + trackerUrls +
                ", webSeedUrls=" + webSeedUrls +
                ", comments='" + comments + '\'' +
                ", startSeeding=" + startSeeding +
                ", isPrivate=" + isPrivate +
                ", optimizeAlignment=" + optimizeAlignment +
                ", skipFilesList=" + skipFilesList +
                '}';
    }
}
