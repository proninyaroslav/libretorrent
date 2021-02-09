/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.storage.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public interface TagInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TagInfo info);

    @Update
    void update(TagInfo info);

    @Delete
    void delete(TagInfo info);

    @Query("SELECT * FROM TagInfo WHERE name = :name")
    TagInfo getByName(String name);

    @Query("SELECT * FROM TagInfo")
    Flowable<List<TagInfo>> observeAll();

    @Query("SELECT * FROM TagInfo WHERE id IN " +
            "(SELECT tagId FROM TorrentTagInfo WHERE torrentId = :torrentId)")
    Flowable<List<TagInfo>> observeByTorrentId(String torrentId);

    @Query("SELECT * FROM TagInfo WHERE id IN " +
            "(SELECT tagId FROM TorrentTagInfo WHERE torrentId = :torrentId)")
    Single<List<TagInfo>> getByTorrentIdAsync(String torrentId);

    @Query("SELECT * FROM TagInfo WHERE id IN " +
            "(SELECT tagId FROM TorrentTagInfo WHERE torrentId = :torrentId)")
    List<TagInfo> getByTorrentId(String torrentId);
}
