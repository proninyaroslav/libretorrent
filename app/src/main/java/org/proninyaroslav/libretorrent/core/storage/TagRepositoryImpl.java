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

package org.proninyaroslav.libretorrent.core.storage;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class TagRepositoryImpl implements TagRepository {
    private final @NonNull AppDatabase db;

    public TagRepositoryImpl(@NonNull AppDatabase db) {
        this.db = db;
    }

    @Override
    public void insert(@NonNull TagInfo info) {
        db.tagInfoDao().insert(info);
    }

    @Override
    public void update(@NonNull TagInfo info) {
        db.tagInfoDao().update(info);
    }

    @Override
    public void delete(@NonNull TagInfo info) {
        db.tagInfoDao().delete(info);
    }

    @Override
    public TagInfo getByName(@NonNull String name) {
        return db.tagInfoDao().getByName(name);
    }

    @Override
    public Flowable<List<TagInfo>> observeAll() {
        return db.tagInfoDao().observeAll();
    }

    @Override
    public Flowable<List<TagInfo>> observeByTorrentId(String torrentId) {
        return db.tagInfoDao().observeByTorrentId(torrentId);
    }

    @Override
    public Single<List<TagInfo>> getByTorrentIdAsync(String torrentId) {
        return db.tagInfoDao().getByTorrentIdAsync(torrentId);
    }

    @Override
    public List<TagInfo> getByTorrentId(String torrentId) {
        return db.tagInfoDao().getByTorrentId(torrentId);
    }
}
