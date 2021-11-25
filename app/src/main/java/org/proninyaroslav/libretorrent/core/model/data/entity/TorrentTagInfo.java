/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        foreignKeys = {
                @ForeignKey(
                        entity = TagInfo.class,
                        parentColumns = "id",
                        childColumns = "tagId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Torrent.class,
                        parentColumns = "id",
                        childColumns = "torrentId",
                        onDelete = ForeignKey.CASCADE
                ),
        },
        indices = {
                @Index("tagId"),
                @Index("torrentId"),
        },
        primaryKeys = {"tagId", "torrentId"}
)
public class TorrentTagInfo {
    public final long tagId;

    @NonNull
    public final String torrentId;

    public TorrentTagInfo(
            long tagId,
            @NonNull String torrentId
    ) {
        this.tagId = tagId;
        this.torrentId = torrentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TorrentTagInfo that = (TorrentTagInfo) o;

        if (tagId != that.tagId) return false;
        return torrentId.equals(that.torrentId);
    }

    @Override
    public int hashCode() {
        int result = (int) (tagId ^ (tagId >>> 32));
        result = 31 * result + torrentId.hashCode();
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "TorrentTagInfo{" +
                "tagId='" + tagId + '\'' +
                ", torrentId='" + torrentId + '\'' +
                '}';
    }
}
