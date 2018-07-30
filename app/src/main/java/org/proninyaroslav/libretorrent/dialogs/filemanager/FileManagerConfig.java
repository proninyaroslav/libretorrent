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

package org.proninyaroslav.libretorrent.dialogs.filemanager;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/*
 * Specifies the start folder and choose mode (folder or file choose). Part of FileManagerDialog.
 */

public class FileManagerConfig implements Parcelable
{
    public static final int FILE_CHOOSER_MODE = 0;
    public static final int DIR_CHOOSER_MODE = 1;
    public static final int SAVE_FILE_MODE = 2;

    public String path;
    public String title;
    /* File extension, e.g 'torrent' */
    public List<String> highlightFileTypes;
    public String fileName;
    public int showMode;

    public FileManagerConfig(String path, String title, List<String> highlightFileType, int mode)
    {
        this.path = path;
        this.title = title;
        this.highlightFileTypes = highlightFileType;
        showMode = mode;
    }

    public FileManagerConfig (Parcel source)
    {
        path = source.readString();
        title = source.readString();
        highlightFileTypes = new ArrayList<>();
        source.readStringList(highlightFileTypes);
        showMode = source.readInt();
        fileName = source.readString();
    }

    public FileManagerConfig setFileName(String name)
    {
        fileName = name;

        return this;
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
        dest.writeString(title);
        dest.writeStringList(highlightFileTypes);
        dest.writeInt(showMode);
        dest.writeString(fileName);
    }

    public static final Parcelable.Creator<FileManagerConfig> CREATOR =
            new Parcelable.Creator<FileManagerConfig>()
            {
                @Override
                public FileManagerConfig createFromParcel(Parcel source)
                {
                    return new FileManagerConfig(source);
                }

                @Override
                public FileManagerConfig[] newArray(int size)
                {
                    return new FileManagerConfig[size];
                }
            };
}
