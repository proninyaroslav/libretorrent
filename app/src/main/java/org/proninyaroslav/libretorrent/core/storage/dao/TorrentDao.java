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

package org.proninyaroslav.libretorrent.core.storage.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public interface TorrentDao
{
    String QUERY_GET_ALL = "SELECT * FROM Torrent";
    String QUERY_GET_BY_ID = "SELECT * FROM Torrent WHERE id = :id";

    @Insert
    void add(Torrent torrent);

    @Update
    void update(Torrent torrent);

    @Delete
    void delete(Torrent torrent);

    @Query(QUERY_GET_ALL)
    List<Torrent> getAllTorrents();

    @Query(QUERY_GET_BY_ID)
    Torrent getTorrentById(String id);

    @Query(QUERY_GET_BY_ID)
    Single<Torrent> getTorrentByIdSingle(String id);

    @Query(QUERY_GET_BY_ID)
    Flowable<Torrent> observeTorrentById(String id);
}
