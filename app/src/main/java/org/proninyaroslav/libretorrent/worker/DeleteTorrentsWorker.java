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

package org.proninyaroslav.libretorrent.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.proninyaroslav.libretorrent.MainApplication;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;

/*
 * Used only by TorrentEngine.
 */

public class DeleteTorrentsWorker extends Worker
{
    @SuppressWarnings("unused")
    private static final String TAG = DeleteTorrentsWorker.class.getSimpleName();

    public static final String TAG_ID_LIST = "id_list";
    public static final String TAG_WITH_FILES = "with_files";

    public DeleteTorrentsWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        Context context = getApplicationContext();
        TorrentEngine engine = TorrentEngine.getInstance(context);
        TorrentRepository repo = ((MainApplication)context).getTorrentRepository();

        Data data = getInputData();
        String[] idList = data.getStringArray(TAG_ID_LIST);
        boolean withFile = data.getBoolean(TAG_WITH_FILES, false);
        if (idList == null)
            return Result.failure();

        for (String id : idList) {
            if (id == null)
                continue;

            Torrent torrent = repo.getTorrentById(id);
            if (torrent == null)
                continue;
            try {
                engine.doDeleteTorrent(torrent, withFile);

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        return Result.success();
    }
}
