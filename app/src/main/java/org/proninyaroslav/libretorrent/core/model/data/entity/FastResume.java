/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

/*
 * The class encapsulates the fast resume data,
 * more about it see https://www.libtorrent.org/manual-ref.html#fast-resume.
 */

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(indices = {@Index(value = "torrentId")},
        foreignKeys = @ForeignKey(
                entity = Torrent.class,
                parentColumns = "id",
                childColumns = "torrentId",
                onDelete = CASCADE))

public class FastResume
{
    @PrimaryKey
    @NonNull
    public String torrentId;
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    @NonNull
    public byte[] data;

    public FastResume(@NonNull String torrentId, @NonNull byte[] data)
    {
        this.torrentId = torrentId;
        this.data = data;
    }
}
