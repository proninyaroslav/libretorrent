/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.service;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FetchLinkException;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.data.MagnetInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/*
 * The worker for downloading torrents from RSS/Atom items.
 */

public class FeedDownloaderWorker extends Worker
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedDownloaderWorker.class.getSimpleName();

    public static final String ACTION_DOWNLOAD_TORRENT_LIST = "org.proninyaroslav.libretorrent.service.FeedDownloaderWorker.ACTION_DOWNLOAD_TORRENT_LIST";
    public static final String TAG_ACTION = "action";
    public static final String TAG_ITEM_ID_LIST = "item_id_list";

    private static final long START_ENGINE_RETRY_TIME = 3000; /* ms */

    private TorrentEngine engine;
    private FeedRepository repo;
    private SettingsRepository pref;

    public FeedDownloaderWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        engine = TorrentEngine.getInstance(getApplicationContext());
        repo = RepositoryHelper.getFeedRepository(getApplicationContext());
        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());

        Data data = getInputData();
        String action = data.getString(TAG_ACTION);

        if (action == null)
            return Result.failure();

        if (ACTION_DOWNLOAD_TORRENT_LIST.equals(action))
            return addTorrents(fetchTorrents(data.getStringArray(TAG_ITEM_ID_LIST)));

        return Result.failure();
    }

    private ArrayList<AddTorrentParams> fetchTorrents(String... ids)
    {
        ArrayList<AddTorrentParams> paramsList = new ArrayList<>();
        if (ids == null)
            return paramsList;

        for (FeedItem item : repo.getItemsById(ids)) {
            AddTorrentParams params = fetchTorrent(item);
            if (params != null)
                paramsList.add(params);
        }

        return paramsList;
    }

    private AddTorrentParams fetchTorrent(FeedItem item)
    {
        if (item == null)
            return null;

        Uri downloadPath = Utils.getTorrentDownloadPath(getApplicationContext());
        if (downloadPath == null)
            return null;
        String name;
        Priority[] priorities = null;
        boolean isMagnet = false;
        String source, sha1hash;

        if (item.downloadUrl.startsWith(Utils.MAGNET_PREFIX)) {
            MagnetInfo info;
            try {
                info = engine.parseMagnet(item.downloadUrl);

            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getMessage());
                return null;
            }
            sha1hash = info.getSha1hash();
            name = info.getName();
            isMagnet = true;
            source = item.downloadUrl;

        } else {
            byte[] response;
            TorrentMetaInfo info;
            try {
                response = Utils.fetchHttpUrl(getApplicationContext(), item.downloadUrl);
                info = new TorrentMetaInfo(response);

            } catch (FetchLinkException e) {
                Log.e(TAG, "URL fetch error: " + Log.getStackTraceString(e));
                return null;
            } catch (DecodeException e) {
                Log.e(TAG, "Invalid torrent: " + Log.getStackTraceString(e));
                return null;
            }
            FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(getApplicationContext());
            if (fs.getDirAvailableBytes(downloadPath) < info.torrentSize) {
                Log.e(TAG, "Not enough free space for " + info.torrentName);
                return null;
            }
            File tmp;
            try {
                tmp = fs.makeTempFile(".torrent");
                org.apache.commons.io.FileUtils.writeByteArrayToFile(tmp, response);

            } catch (Exception e) {
                Log.e(TAG, "Error write torrent file " + info.torrentName + ": " + Log.getStackTraceString(e));
                return null;
            }
            priorities = new Priority[info.fileList.size()];
            Arrays.fill(priorities, Priority.DEFAULT);
            sha1hash = info.sha1Hash;
            name = info.torrentName;
            source = Uri.fromFile(tmp).toString();
        }

        return new AddTorrentParams(source, isMagnet, sha1hash, name,
                priorities, downloadPath, false,
                !pref.feedStartTorrents());
    }

    private Result addTorrents(ArrayList<AddTorrentParams> paramsList)
    {
        if (paramsList == null || paramsList.isEmpty())
            return Result.failure();

        if (!engine.isRunning())
            engine.start();

        /* TODO: maybe refactor */
        while (!engine.isRunning()) {
            try {
                Thread.sleep(START_ENGINE_RETRY_TIME);
                engine.start();

            } catch (InterruptedException e) {
                return Result.failure();
            }
        }

        engine.addTorrents(paramsList, true);

        return Result.success();
    }
}
