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
import android.net.Uri;
import android.util.Log;

import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentStatus;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

public class TorrentHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentHelper.class.getSimpleName();

    public static BasicStateParcel makeBasicStateParcel(@NonNull String id)
    {
        TorrentDownload task = TorrentEngineOld.getInstance().getTask(id);
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();

        return new BasicStateParcel(
                torrent.id,
                torrent.name,
                task.getStateCode(),
                task.getProgress(),
                task.getTotalReceivedBytes(),
                task.getTotalSentBytes(),
                task.getTotalWanted(),
                task.getDownloadSpeed(),
                task.getUploadSpeed(),
                task.getETA(),
                torrent.dateAdded,
                task.getTotalPeers(),
                task.getConnectedPeers(),
                torrent.error);
    }

    public static AdvanceStateParcel makeAdvancedState(@NonNull String id)
    {
        TorrentDownload task = TorrentEngineOld.getInstance().getTask(id);
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();
        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvanceStateParcel(
                torrent.id,
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

    public static ArrayList<TrackerStateParcel> makeTrackerStateParcelList(@NonNull String id)
    {
        TorrentDownload task = TorrentEngineOld.getInstance().getTask(id);
        if (task == null)
            return null;

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = TorrentEngineOld.getInstance().isDHTEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = TorrentEngineOld.getInstance().isLSDEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = TorrentEngineOld.getInstance().isPeXEnabled() ?
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

    public static ArrayList<PeerStateParcel> makePeerStateParcelList(@NonNull String id)
    {
        TorrentDownload task = TorrentEngineOld.getInstance().getTask(id);
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

    public static MagnetInfo fetchMagnet(String uri) throws Exception
    {
        org.libtorrent4j.AddTorrentParams p = TorrentEngineOld.getInstance().fetchMagnet(uri);
        MagnetInfo info = null;
        List<Priority> priorities = null;
        if (p != null) {
            priorities = Arrays.asList(p.filePriorities());
            info = new MagnetInfo(uri, p.infoHash().toHex(), p.name(), priorities);
        }

        return info;
    }

    public static Torrent addTorrent(@NonNull Context context,
                                     @NonNull TorrentRepository repo,
                                     @NonNull Torrent torrent,
                                     @NonNull String source,
                                     boolean fromMagnet,
                                     boolean removeFile) throws IOException, FileAlreadyExistsException, DecodeException
    {
        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();

        if (fromMagnet) {
            byte[] bencode = TorrentEngineOld.getInstance().getLoadedMagnet(torrent.id);
            TorrentEngineOld.getInstance().removeLoadedMagnet(torrent.id);
            if (bencode == null) {
                torrent.setMagnetUri(source);
                repo.addTorrent(context, torrent);
            } else {
                if (repo.getTorrentById(torrent.id) == null) {
                    repo.addTorrent(context, torrent, bencode);
                } else {
                    TorrentEngineOld.getInstance().mergeTorrent(torrent, bencode);
                    repo.replaceTorrent(context, torrent, bencode);
                    throw new FileAlreadyExistsException();
                }
            }
        } else {
            if (repo.getTorrentById(torrent.id) == null) {
                repo.addTorrent(context, torrent, Uri.parse(source), removeFile);
            } else {
                TorrentEngineOld.getInstance().mergeTorrent(torrent);
                repo.replaceTorrent(context, torrent, Uri.parse(source), removeFile);
                throw new FileAlreadyExistsException();
            }
        }

        torrent = repo.getTorrentById(torrent.id);
        if (torrent == null)
            throw new IOException("torrent is null");

        if (!torrent.isDownloadingMetadata()) {
            if (!TorrentUtils.torrentDataExists(context, torrent.id)) {
                repo.deleteTorrent(context, torrent);
                throw new FileNotFoundException("Torrent doesn't exists: " + torrent.name);
            }
            boolean saveTorrentFile = pref.getBoolean(context.getString(R.string.pref_key_save_torrent_files),
                                                      SettingsManager.Default.saveTorrentFiles);
            if (saveTorrentFile) {
                String savePath = pref.getString(context.getString(R.string.pref_key_save_torrent_files_in),
                                                 torrent.downloadPath.toString());
                saveTorrentFileIn(context, torrent, Uri.parse(savePath));
            }
            /*
             * This is possible if the magnet data came after Torrent object
             * has already been created and nothing is known about the received data
             */
            if (torrent.filePriorities.isEmpty()) {
                TorrentMetaInfo info = new TorrentMetaInfo(torrent.getSource());
                torrent.filePriorities = Collections.nCopies(info.fileCount, Priority.DEFAULT);
                repo.updateTorrent(torrent);
            }
        }

        return torrent;
    }

    public static void saveTorrentFileIn(@NonNull Context context,
                                         @NonNull Torrent torrent,
                                         @NonNull Uri saveDir)
    {
        String torrentFileName = torrent.name + ".torrent";
        try {
            if (!TorrentUtils.copyTorrentFileToDir(context, torrent.id, saveDir, torrentFileName))
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }
}
