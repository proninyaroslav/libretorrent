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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.FileStorage;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.receivers.TorrentTaskServiceReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TorrentHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentHelper.class.getSimpleName();

    public static synchronized void addTorrent(Context context, AddTorrentParams params,
                                               boolean removeFile) throws Throwable
    {
        if (context == null || params == null)
            return;

        TorrentStorage storage = new TorrentStorage(context);
        SharedPreferences pref = SettingsManager.getPreferences(context);

        Torrent torrent = new Torrent(params.getSha1hash(), params.getName(), params.getFilePriorities(),
                params.getPathToDownload(), System.currentTimeMillis());
        torrent.setSource(params.getSource());
        torrent.setSequentialDownload(params.isSequentialDownload());
        torrent.setPaused(params.addPaused());

        if (params.fromMagnet()) {
            byte[] bencode = TorrentEngine.getInstance().getLoadedMagnet(torrent.getId());
            TorrentEngine.getInstance().removeLoadedMagnet(torrent.getId());
            if (bencode == null) {
                torrent.setDownloadingMetadata(true);
                if (!storage.exists(torrent))
                    storage.add(torrent);
            } else {
                torrent.setDownloadingMetadata(false);
                if (storage.exists(torrent)) {
                    storage.replace(torrent, bencode);
                    TorrentEngine.getInstance().mergeTorrent(torrent, bencode);
                    throw new FileAlreadyExistsException();
                } else {
                    storage.add(torrent, bencode);
                }
            }
        } else if (new File(torrent.getSource()).exists()) {
            if (storage.exists(torrent)) {
                TorrentEngine.getInstance().mergeTorrent(torrent);
                storage.replace(torrent, removeFile);
                throw new FileAlreadyExistsException();
            } else {
                storage.add(torrent, torrent.getSource(), removeFile);
            }
        } else {
            throw new FileNotFoundException(torrent.getSource());
        }
        torrent = storage.getTorrentByID(torrent.getId());
        if (torrent == null)
            throw new IOException("torrent is null");
        if (!torrent.isDownloadingMetadata()) {
            if (pref.getBoolean(context.getString(R.string.pref_key_save_torrent_files),
                    SettingsManager.Default.saveTorrentFiles))
                saveTorrentFileIn(
                        context,
                        torrent,
                        pref.getString(context.getString(R.string.pref_key_save_torrent_files_in),
                        torrent.getDownloadPath()));
        }
        if (!torrent.isDownloadingMetadata() && !TorrentUtils.torrentDataExists(context, torrent.getId())) {
            storage.delete(torrent);
            throw new FileNotFoundException("Torrent doesn't exists: " + torrent.getName());
        }
        /*
         * This is possible if the magnet data came after AddTorrentParams object
         * has already been created and nothing is known about the received data
         */
        List<Priority> priorities = torrent.getFilePriorities();
        if (!torrent.isDownloadingMetadata() && (priorities == null || priorities.isEmpty())) {
            TorrentMetaInfo info = new TorrentMetaInfo(torrent.getSource());
            torrent.setFilePriorities(Collections.nCopies(info.fileCount, Priority.DEFAULT));
            storage.update(torrent);
        }

        TorrentEngine.getInstance().download(torrent);
        TorrentTaskServiceReceiver.getInstance().post(TorrentStateMsg.makeTorrentAddedBundle(torrent));
    }

    public static void saveTorrentFileIn(Context context, Torrent torrent, String saveDirPath)
    {
        String torrentFileName = torrent.getName() + ".torrent";
        try {
            if (!TorrentUtils.copyTorrentFile(context,
                    torrent.getId(),
                    saveDirPath,
                    torrentFileName))
            {
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);
            }

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    public static synchronized void pauseResumeTorrents(List<String> ids)
    {
        for (String id : ids) {
            if (id == null)
                continue;

            pauseResumeTorrent(id);
        }
    }

    public static synchronized void pauseResumeTorrent(String id)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        try {
            if (task.isPaused())
                task.resume();
            else
                task.pause();

        } catch (Exception e) {
            /* Ignore */
        }
    }

    public static synchronized void forceRecheckTorrents(List<String> ids)
    {
        if (ids == null)
            return;

        for (String id : ids) {
            if (id == null)
                continue;

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.forceRecheck();
        }
    }

    public static synchronized void forceAnnounceTorrents(List<String> ids)
    {
        if (ids == null)
            return;

        for (String id : ids) {
            if (id == null)
                continue;

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.requestTrackerAnnounce();
        }
    }

    public static synchronized void deleteTorrents(Context context, List<String> ids, boolean withFiles)
    {
        if (context == null || ids == null)
            return;

        TorrentStorage storage = new TorrentStorage(context);
        for (String id : ids) {
            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.remove(withFiles);
            storage.delete(id);
        }
    }

    public static synchronized void setTorrentName(Context context, String id, String name)
    {
        if (context == null || id == null || name == null)
            return;

        TorrentStorage storage = new TorrentStorage(context);
        Torrent torrent = storage.getTorrentByID(id);

        if (torrent == null)
            return;

        torrent.setName(name);
        storage.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);
            TorrentTaskServiceReceiver.getInstance()
                    .post(TorrentStateMsg.makeUpdateTorrentsBundle(makeBasicStatesList()));
        }
    }

    public static Bundle makeBasicStatesList()
    {
        Bundle states = new Bundle();
        for (TorrentDownload task : TorrentEngine.getInstance().getTasks()) {
            if (task == null)
                continue;
            BasicStateParcel state = makeBasicStateParcel(task);
            states.putParcelable(state.torrentId, state);
        }

        return states;
    }

    public static Bundle makeOfflineStatesList(Context context)
    {
        TorrentStorage storage = new TorrentStorage(context);
        Bundle states = new Bundle();
        for (Torrent torrent : storage.getAll()) {
            if (torrent == null)
                continue;
            BasicStateParcel state = new BasicStateParcel(
                    torrent.getId(),
                    torrent.getName(),
                    torrent.getDateAdded());
            states.putParcelable(state.torrentId, state);
        }

        return states;
    }

    public static BasicStateParcel makeBasicStateParcel(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);

        return makeBasicStateParcel(task);
    }

    public static BasicStateParcel makeBasicStateParcel(TorrentDownload task)
    {
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();

        return new BasicStateParcel(
                torrent.getId(),
                torrent.getName(),
                task.getStateCode(),
                task.getProgress(),
                task.getTotalReceivedBytes(),
                task.getTotalSentBytes(),
                task.getTotalWanted(),
                task.getDownloadSpeed(),
                task.getUploadSpeed(),
                task.getETA(),
                torrent.getDateAdded(),
                task.getTotalPeers(),
                task.getConnectedPeers(),
                torrent.getError());
    }

    public static synchronized void setSequentialDownload(Context context, String id, boolean sequential)
    {
        if (context == null || id == null)
            return;

        TorrentStorage storage = new TorrentStorage(context);
        Torrent torrent = storage.getTorrentByID(id);

        if (torrent == null)
            return;

        torrent.setSequentialDownload(sequential);
        storage.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);
            task.setSequentialDownload(sequential);
        }
    }

    public static synchronized void changeFilesPriority(Context context, String id,
                                                 Priority[] priorities) throws FreeSpaceException
    {
        if (context == null || id == null || (priorities == null || priorities.length == 0))
            return;

        TorrentStorage storage = new TorrentStorage(context);
        Torrent torrent = storage.getTorrentByID(id);
        if (torrent == null)
            return;
        torrent.setFilePriorities(Arrays.asList(priorities));
        TorrentInfo ti = new TorrentInfo(new File(torrent.getSource()));
        if (isSelectedFilesTooBig(torrent, ti))
            throw new FreeSpaceException();
        storage.update(torrent);
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);
            task.prioritizeFiles(priorities);
        }
    }

    private static boolean isSelectedFilesTooBig(Torrent torrent, TorrentInfo ti)
    {
        long freeSpace = FileIOUtils.getFreeSpace(torrent.getDownloadPath());
        long filesSize = 0;
        List<Priority> priorities = torrent.getFilePriorities();
        if (priorities != null) {
            FileStorage files = ti.files();
            for (int i = 0; i < priorities.size(); i++)
                if (priorities.get(i) != Priority.IGNORE)
                    filesSize += files.fileSize(i);
        }

        return freeSpace < filesSize;
    }

    public static synchronized void replaceTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.replaceTrackers(new HashSet<>(urls));
    }

    public static synchronized void addTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }

    public static String getMagnet(String id, boolean includePriorities)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet(includePriorities);
    }

    public static void setUploadSpeedLimit(String id, int limit)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.setUploadSpeedLimit(limit);
    }

    public static void setDownloadSpeedLimit(String id, int limit)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.setDownloadSpeedLimit(limit);
    }

    public static AdvanceStateParcel makeAdvancedState(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();
        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvanceStateParcel(
                torrent.getId(),
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail));
    }

    public static TorrentMetaInfo getTorrentMetaInfo(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        TorrentInfo ti = task.getTorrentInfo();
        TorrentMetaInfo info = null;
        try {
            if (ti != null)
                info = new TorrentMetaInfo(ti);
            else
                info = new TorrentMetaInfo(task.getTorrent().getName(), task.getInfoHash());

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public static long getActiveTime(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getActiveTime();
    }

    public static long getSeedingTime(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getSeedingTime();
    }

    public static ArrayList<TrackerStateParcel> getTrackerStatesList(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return makeTrackerStateParcelList(task);
    }

    public static ArrayList<PeerStateParcel> getPeerStatesList(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return makePeerStateParcelList(task);
    }

    private static ArrayList<TrackerStateParcel> makeTrackerStateParcelList(TorrentDownload task)
    {
        if (task == null)
            return null;

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = TorrentEngine.getInstance().isDHTEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = TorrentEngine.getInstance().isLSDEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = TorrentEngine.getInstance().isPeXEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;

        states.add(new TrackerStateParcel(TrackerStateParcel.DHT_ENTRY_NAME, "", -1, statusDHT));
        states.add(new TrackerStateParcel(TrackerStateParcel.LSD_ENTRY_NAME, "", -1, statusLSD));
        states.add(new TrackerStateParcel(TrackerStateParcel.PEX_ENTRY_NAME, "", -1, statusPeX));

        for (AnnounceEntry entry : trackers) {
            String url = entry.url();
            /* Prevent duplicate */
            if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
                continue;
            }

            states.add(new TrackerStateParcel(entry));
        }

        return states;
    }

    private static ArrayList<PeerStateParcel> makePeerStateParcelList(TorrentDownload task)
    {
        if (task == null)
            return null;

        ArrayList<PeerStateParcel> states = new ArrayList<>();
        List<AdvancedPeerInfo> peers = task.advancedPeerInfo();

        TorrentStatus status = task.getTorrentStatus();
        if (status == null)
            return null;

        for (AdvancedPeerInfo peer : peers) {
            PeerStateParcel state = new PeerStateParcel(peer, status);
            states.add(state);
        }

        return states;
    }

    public static boolean[] getPieces(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return task.pieces();
    }

    public static int getUploadSpeedLimit(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getUploadSpeedLimit();
    }

    public static int getDownloadSpeedLimit(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getDownloadSpeedLimit();
    }

    public static MagnetInfo fetchMagnet(String uri) throws Exception
    {
        org.libtorrent4j.AddTorrentParams p = TorrentEngine.getInstance().fetchMagnet(uri);
        MagnetInfo info = null;
        List<Priority> priorities = null;
        if (p != null) {
            if (p.filePriorities() != null)
                priorities = Arrays.asList(p.filePriorities());
            info = new MagnetInfo(uri, p.infoHash().toHex(), p.name(), priorities);
        }

        return info;
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public static synchronized void cancelFetchMagnet(String infoHash)
    {
        if (infoHash == null)
            return;

        TorrentEngine.getInstance().cancelFetchMagnet(infoHash);
    }

    public static synchronized void setTorrentDownloadPath(Context context, ArrayList<String> ids, String path)
    {
        if (context == null || ids == null || path == null || TextUtils.isEmpty(path))
            return;

        TorrentStorage storage = new TorrentStorage(context);
        for (String id : ids) {
            Torrent torrent = storage.getTorrentByID(id);
            if (torrent == null)
                continue;

            torrent.setDownloadPath(path);
            storage.update(torrent);

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task == null)
                continue;
            task.setTorrent(torrent);
            task.setDownloadPath(path);
        }
    }
}
