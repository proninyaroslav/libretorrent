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

package org.proninyaroslav.libretorrent.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.error_code;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class FeedDownloaderService extends JobIntentService
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedDownloaderService.class.getSimpleName();

    public static final String ACTION_DOWNLOAD_TORRENT = "org.proninyaroslav.libretorrent.services.FeedDownloaderService.ACTION_DOWNLOAD_TORRENT";
    public static final String ACTION_DOWNLOAD_TORRENT_LIST = "org.proninyaroslav.libretorrent.services.FeedDownloaderService.ACTION_DOWNLOAD_TORRENT_LIST";
    public static final String TAG_URL_ARG = "url_arg";
    public static final String TAG_URL_LIST_ARG = "url_list_arg";

    private SharedPreferences pref;

    public static void enqueueWork(Context context, Intent i)
    {
        enqueueWork(context, FeedDownloaderService.class, TAG.hashCode(), i);
    }

    @Override
    protected void onHandleWork(@Nullable Intent intent)
    {
        if (intent == null || intent.getAction() == null)
            return;

        switch (intent.getAction()) {
            case ACTION_DOWNLOAD_TORRENT:
                sendAddTorrentParams(fetchUrl(intent.getStringExtra(TAG_URL_ARG)));
                break;
            case ACTION_DOWNLOAD_TORRENT_LIST:
                sendAddTorrentParams(fetchUrls(intent.getStringArrayListExtra(TAG_URL_LIST_ARG)));
                break;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "Start " + FeedDownloaderService.class.getSimpleName());

        pref = SettingsManager.getPreferences(this);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + FeedDownloaderService.class.getSimpleName());
    }

    private ArrayList<AddTorrentParams> fetchUrls(ArrayList<String> urls)
    {
        ArrayList<AddTorrentParams> paramsList = new ArrayList<>();
        if (urls == null)
            return paramsList;

        for (String url : urls) {
            AddTorrentParams params = fetchUrl(url);
            if (params != null)
                paramsList.add(params);
        }

        return paramsList;
    }

    private AddTorrentParams fetchUrl(String url)
    {
        if (url == null)
            return null;

        String downloadPath = TorrentUtils.getTorrentDownloadPath(this);
        String name;
        ArrayList<Priority> priorities = null;
        boolean isMagnet = false;
        String source, sha1hash;

        if (url.startsWith(Utils.MAGNET_PREFIX)) {
            error_code ec = new error_code();
            add_torrent_params p = add_torrent_params.parse_magnet_uri(url, ec);
            if (ec.value() != 0) {
                Log.e(TAG, "Invalid magnet link: " + url);
                return null;
            }
            sha1hash = p.getInfo_hash().to_hex();
            name = sha1hash;
            isMagnet = true;
            source = url;

        } else {
            byte[] response;
            TorrentMetaInfo info;
            try {
                response = Utils.fetchHttpUrl(getApplicationContext(), url);
                info = new TorrentMetaInfo(response);

            } catch (FetchLinkException e) {
                Log.e(TAG, "URL fetch error: " + Log.getStackTraceString(e));
                return null;
            } catch (DecodeException e) {
                Log.e(TAG, "Invalid torrent: " + Log.getStackTraceString(e));
                return null;
            }
            if (FileIOUtils.getFreeSpace(downloadPath) < info.torrentSize) {
                Log.e(TAG, "Not enough free space for " + info.torrentName);
                return null;
            }
            File tmp;
            try {
                tmp = FileIOUtils.makeTempFile(this, ".torrent");
                FileUtils.writeByteArrayToFile(tmp, response);

            } catch (Exception e) {
                Log.e(TAG, "Error write torrent file " + info.torrentName + ": " +
                        Log.getStackTraceString(e));
                return null;
            }
            priorities = new ArrayList<>(Collections.nCopies(info.fileList.size(), Priority.NORMAL));
            sha1hash = info.sha1Hash;
            name = info.torrentName;
            source = tmp.getAbsolutePath();
        }

        return new AddTorrentParams(source, isMagnet, sha1hash, name,
                                    priorities, downloadPath, false,
                                    !pref.getBoolean(getString(R.string.pref_key_feed_start_torrents),
                                                     SettingsManager.Default.feedStartTorrents));
    }

    private void sendAddTorrentParams(AddTorrentParams params)
    {
        if (params == null)
            return;

        Intent i = new Intent(getApplicationContext(), TorrentTaskService.class);
        i.setAction(TorrentTaskService.ACTION_ADD_TORRENT);
        i.putExtra(TorrentTaskService.TAG_ADD_TORRENT_PARAMS, params);
        startService(i);
    }

    private void sendAddTorrentParams(ArrayList<AddTorrentParams> params)
    {
        if (params == null || params.isEmpty())
            return;

        Intent i = new Intent(getApplicationContext(), TorrentTaskService.class);
        i.setAction(TorrentTaskService.ACTION_ADD_TORRENT_LIST);
        i.putExtra(TorrentTaskService.TAG_ADD_TORRENT_PARAMS_LIST, params);
        startService(i);
    }
}
