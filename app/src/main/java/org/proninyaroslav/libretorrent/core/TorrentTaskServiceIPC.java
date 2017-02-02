/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;

import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;

import java.util.ArrayList;
import java.util.Collection;

/*
 * The interface for interprocess communication with TorrentTaskService.
 */

public class TorrentTaskServiceIPC
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentTaskServiceIPC.class.getSimpleName();

    public static final String TAG_TORRENTS_LIST = "torrents_list";
    public static final String TAG_TORRENT_ID = "torrent_id";
    public static final String TAG_TORRENT_IDS_LIST = "torrent_id_list";
    public static final String TAG_STATE = "state";
    public static final String TAG_STATES_LIST = "states_list";
    public static final String TAG_TORRENT_INFO = "torrent_info";
    public static final String TAG_EXCEPTIONS_LIST = "exception";
    public static final String TAG_TORRENT_NAME = "name";
    public static final String TAG_DOWNLOAD_PATH = "download_path";
    public static final String TAG_SEQUENTIAL = "sequential";
    public static final String TAG_FILE_PRIORITIES = "file_indexes";
    public static final String TAG_TRACKERS_STATES_LIST = "trackers_states_list";
    public static final String TAG_TRACKERS_URL_LIST = "trackers_url_list";
    public static final String TAG_PEERS_STATES_LIST = "peers_states_list";
    public static final String TAG_PIECE_LIST = "piece_list";
    public static final String TAG_MAGNET = "magnet";
    public static final String TAG_ACTIVE_TIME = "active_time";
    public static final String TAG_SEEDING_TIME = "seeding_time";

    /*
     * Send client: callback
     */
    public static final int CLIENT_CONNECT = 1;
    /*
     * Send client: callback
     * Send service: TorrentStateParcel list
     */
    public static final int UPDATE_STATES_ONESHOT = 2;
    /*
     * Send client: Torrent class objects list
     */
    public static final int ADD_TORRENTS = 3;
    /*
     * Send service: TorrentStateParcel list and/or exception (if any)
     */
    public static final int TORRENTS_ADDED = 4;
    /*
     * Send client: callback
     */
    public static final int CLIENT_DISCONNECT = 5;
    /*
     * Send client: torrent id
     * Send service: TorrentStateParcel object
     */
    public static final int UPDATE_STATE = 6;
    /*
     * Send service: only signal
     */
    public static final int TERMINATE_ALL_CLIENTS = 7;
    /*
     * Send client: torrent ids list
     */
    public static final int PAUSE_RESUME_TORRENTS = 8;
    /*
     * Send client: torrent ids list
     */
    public static final int DELETE_TORRENTS = 9;
    /*
     * Send client: torrent ids list
     */
    public static final int DELETE_TORRENTS_WITH_FILES = 10;
    /*
     * Send client: torrent ids list
     */
    public static final int FORCE_RECHECK_TORRENTS = 11;
    /*
     * Send client: torrent ids list
     */
    public static final int FORCE_ANNOUNCE_TORRENTS = 12;
    /*
     * Send client: callback and torrent id
     * Send service: TorrentMetaInfo object
     */
    public static final int GET_TORRENT_INFO = 13;
    /*
     * Send client: torrent id and string value
     */
    public static final int SET_TORRENT_NAME = 14;
    /*
     * Send client: torrent ids list and string value
     */
    public static final int SET_DOWNLOAD_PATH = 15;
    /*
     * Send client: torrent id and boolean value
     */
    public static final int SET_SEQUENTIAL_DOWNLOAD = 16;
    /*
     * Send client: torrent id and priorities list
     * (priorities for all files, priorities list for some selected files not supported)
     */
    public static final int CHANGE_FILES_PRIORITY = 17;
    /*
     * Send client: callback and torrent id
     * Send service: active and seeding time
     */
    public static final int GET_ACTIVE_AND_SEEDING_TIME = 18;
    /*
     * Send client: callback and torrent id
     * Send service: TrackerStateParcel list
     */
    public static final int GET_TRACKERS_STATES = 19;
    /*
     * Send client: torrent id and trackers url
     */
    public static final int REPLACE_TRACKERS = 20;
    /*
     * Send client: torrent id and trackers url
     */
    public static final int ADD_TRACKERS = 21;
    /*
     * Send client: callback and torrent id
     * Send service: PeerStateParcel list
     */
    public static final int GET_PEERS_STATES = 22;
    /*
     * Send client: callback and torrent id
     * Send service: pieces bit array
     */
    public static final int GET_PIECES = 23;
    /*
     * Send client: callback and torrent id
     * Send service: magnet link
     */
    public static final int GET_MAGNET = 24;
    /*
     * Send client: callback, torrent id (if null set global limit)
     * and upload limit in bytes/s
     */
    public static final int SET_UPLOAD_SPEED_LIMIT = 25;
    /*
     * Send client: callback, torrent id (if null set global limit)
     * and download limit in bytes/s
     */
    public static final int SET_DOWNLOAD_SPEED_LIMIT = 26;
    /*
     * Send client: callback and torrent id (if null get global limit)
     * Send service: upload and download limits in bytes/s
     */
    public static final int GET_SPEED_LIMIT = 27;

    public void sendClientConnect(Messenger serviceCallback,
                                  Messenger clientCallback) throws RemoteException
    {
        if (clientCallback == null || serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, CLIENT_CONNECT);
        msg.replyTo = clientCallback;

        serviceCallback.send(msg);
    }

    public void sendClientDisconnect(Messenger serviceCallback,
                                     Messenger clientCallback) throws RemoteException
    {
        if (clientCallback == null || serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, CLIENT_DISCONNECT);
        msg.replyTo = clientCallback;

        serviceCallback.send(msg);
    }

    public void sendTorrentStateOneShot(Messenger clientCallback,
                                        Bundle states) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, UPDATE_STATES_ONESHOT, null);
        msg.getData().putParcelable(TAG_STATES_LIST, states);

        clientCallback.send(msg);
    }

    public void sendTorrentStateOneShot(Messenger serviceCallback,
                                        Messenger clientCallback) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, UPDATE_STATES_ONESHOT, null);
        msg.replyTo = clientCallback;

        serviceCallback.send(msg);
    }

    public void sendAddTorrents(Messenger serviceCallback,
                                ArrayList<Torrent> torrents) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, ADD_TORRENTS, null);
        msg.getData().putParcelableArrayList(TAG_TORRENTS_LIST, new ArrayList<>(torrents));

        serviceCallback.send(msg);
    }

    public void sendTorrentsAdded(Messenger serviceCallback,
                                  ArrayList<TorrentStateParcel> states,
                                  ArrayList<Throwable> exceptions) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, TORRENTS_ADDED, null);
        msg.getData().putSerializable(TAG_EXCEPTIONS_LIST, new ArrayList<>(exceptions));
        msg.getData().putParcelableArrayList(TAG_STATES_LIST, new ArrayList<>(states));

        serviceCallback.send(msg);
    }

    public void sendUpdateState(Messenger clientCallback,
                                TorrentStateParcel state) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, UPDATE_STATE, null);
        msg.getData().putParcelable(TAG_STATE, state);

        clientCallback.send(msg);
    }

    public void sendUpdateState(Messenger serviceCallback,
                                String torrentId) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, UPDATE_STATE, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendTerminateAllClients(Collection<Messenger> clientCallbacks)
    {
        if (clientCallbacks == null) {
            return;
        }

        for (Messenger callback : clientCallbacks) {
            try {
                Message msg = Message.obtain(null, TERMINATE_ALL_CLIENTS, null);
                callback.send(msg);

            } catch (RemoteException e) {
                /* Ignore */
            }
        }
    }

    public void sendPauseResumeTorrents(Messenger serviceCallback,
                                        ArrayList<String> torrentIds) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, PAUSE_RESUME_TORRENTS, null);
        msg.getData().putStringArrayList(TAG_TORRENT_IDS_LIST, new ArrayList<>(torrentIds));

        serviceCallback.send(msg);
    }

    public void sendDeleteTorrents(Messenger serviceCallback,
                                   ArrayList<String> torrentIds,
                                   boolean withFiles) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        int signal = (withFiles ? DELETE_TORRENTS_WITH_FILES : DELETE_TORRENTS);

        Message msg = Message.obtain(null, signal, null);
        msg.getData().putStringArrayList(TAG_TORRENT_IDS_LIST, new ArrayList<>(torrentIds));

        serviceCallback.send(msg);
    }

    public void sendForceRecheck(Messenger serviceCallback,
                                 ArrayList<String> torrentIds) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, FORCE_RECHECK_TORRENTS, null);
        msg.getData().putStringArrayList(TAG_TORRENT_IDS_LIST, new ArrayList<>(torrentIds));

        serviceCallback.send(msg);
    }

    public void sendForceAnnounce(Messenger serviceCallback,
                                  ArrayList<String> torrentIds) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, FORCE_ANNOUNCE_TORRENTS, null);
        msg.getData().putStringArrayList(TAG_TORRENT_IDS_LIST, new ArrayList<>(torrentIds));

        serviceCallback.send(msg);
    }

    public void sendGetTorrentInfo(Messenger serviceCallback,
                                   Messenger clientCallback,
                                   String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_TORRENT_INFO, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetTorrentInfo(Messenger clientCallback,
                                   TorrentMetaInfo info) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_TORRENT_INFO, null);
        msg.getData().putParcelable(TAG_TORRENT_INFO, info);

        clientCallback.send(msg);
    }

    public void sendSetTorrentName(Messenger serviceCallback,
                                   String torrentId, String torrentName) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_TORRENT_NAME, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.getData().putString(TAG_TORRENT_NAME, torrentName);

        serviceCallback.send(msg);
    }

    public void sendSetDownloadPath(Messenger serviceCallback,
                                    ArrayList<String> torrentIds, String path) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_DOWNLOAD_PATH, null);
        msg.getData().putStringArrayList(TAG_TORRENT_IDS_LIST, new ArrayList<>(torrentIds));
        msg.getData().putString(TAG_DOWNLOAD_PATH, path);

        serviceCallback.send(msg);
    }

    public void sendSetSequentialDownload(Messenger serviceCallback,
                                          String torrentId, boolean sequential) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_SEQUENTIAL_DOWNLOAD, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.getData().putBoolean(TAG_SEQUENTIAL, sequential);

        serviceCallback.send(msg);
    }

    public void sendChangeFilesPriority(Messenger serviceCallback,
                                        String torrentId,
                                        ArrayList<Integer> priorities) throws RemoteException
    {
        if (serviceCallback == null || priorities == null) {
            return;
        }

        Message msg = Message.obtain(null, CHANGE_FILES_PRIORITY, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.getData().putIntegerArrayList(TAG_FILE_PRIORITIES, new ArrayList<>(priorities));

        serviceCallback.send(msg);
    }

    public void sendGetActiveAndSeedingTime(Messenger serviceCallback,
                                            Messenger clientCallback,
                                            String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_ACTIVE_AND_SEEDING_TIME, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetActiveAndSeedingTime(Messenger clientCallback,
                                            long activeTime,
                                            long seedingTime) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_ACTIVE_AND_SEEDING_TIME, null);
        msg.getData().putLong(TAG_ACTIVE_TIME, activeTime);
        msg.getData().putLong(TAG_SEEDING_TIME, seedingTime);

        clientCallback.send(msg);
    }

    public void sendGetTrackersStates(Messenger serviceCallback,
                                      Messenger clientCallback,
                                      String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_TRACKERS_STATES, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetTrackersStates(Messenger clientCallback,
                                      ArrayList<TrackerStateParcel> states) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_TRACKERS_STATES, null);
        msg.getData().putParcelableArrayList(TAG_TRACKERS_STATES_LIST, states);

        clientCallback.send(msg);
    }

    public void sendReplaceTrackers(Messenger serviceCallback,
                                    String torrentId,
                                    ArrayList<String> urls) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, REPLACE_TRACKERS, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.getData().putStringArrayList(TAG_TRACKERS_URL_LIST, new ArrayList<>(urls));

        serviceCallback.send(msg);
    }

    public void sendAddTrackers(Messenger serviceCallback,
                                String torrentId,
                                ArrayList<String> urls) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, ADD_TRACKERS, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.getData().putStringArrayList(TAG_TRACKERS_URL_LIST, new ArrayList<>(urls));

        serviceCallback.send(msg);
    }

    public void sendGetPeersStates(Messenger serviceCallback,
                                   Messenger clientCallback,
                                   String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_PEERS_STATES, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetPeersStates(Messenger clientCallback,
                                   ArrayList<PeerStateParcel> states) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_PEERS_STATES, null);
        msg.getData().putParcelableArrayList(TAG_PEERS_STATES_LIST, states);

        clientCallback.send(msg);
    }

    public void sendGetPieces(Messenger serviceCallback,
                              Messenger clientCallback,
                              String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_PIECES, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetPieces(Messenger clientCallback,
                              boolean[] pieces) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_PIECES, null);
        msg.getData().putBooleanArray(TAG_PIECE_LIST, pieces);

        clientCallback.send(msg);
    }

    public void sendGetMagnet(Messenger serviceCallback,
                              Messenger clientCallback,
                              String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_MAGNET, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetMagnet(Messenger clientCallback,
                              String magnet) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_MAGNET, null);
        msg.getData().putString(TAG_MAGNET, magnet);

        clientCallback.send(msg);
    }

    public void sendSetUploadSpeedLimit(Messenger serviceCallback,
                                        String torrentId,
                                        int limit) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_UPLOAD_SPEED_LIMIT, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.arg1 = limit;

        serviceCallback.send(msg);
    }

    public void sendSetUploadSpeedLimit(Messenger serviceCallback,
                                        int limit) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_UPLOAD_SPEED_LIMIT, null);
        msg.arg1 = limit;

        serviceCallback.send(msg);
    }

    public void sendSetDownloadSpeedLimit(Messenger serviceCallback,
                                          String torrentId,
                                          int limit) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_DOWNLOAD_SPEED_LIMIT, null);
        msg.getData().putString(TAG_TORRENT_ID, torrentId);
        msg.arg1 = limit;

        serviceCallback.send(msg);
    }

    public void sendSetDownloadSpeedLimit(Messenger serviceCallback,
                                          int limit) throws RemoteException
    {
        if (serviceCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, SET_DOWNLOAD_SPEED_LIMIT, null);
        msg.arg1 = limit;

        serviceCallback.send(msg);
    }

    public void sendGetSpeedLimit(Messenger serviceCallback,
                                  Messenger clientCallback,
                                  String torrentId) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_SPEED_LIMIT, null);
        msg.replyTo = clientCallback;
        msg.getData().putString(TAG_TORRENT_ID, torrentId);

        serviceCallback.send(msg);
    }

    public void sendGetSpeedLimit(Messenger serviceCallback,
                                  Messenger clientCallback) throws RemoteException
    {
        if (serviceCallback == null || clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_SPEED_LIMIT, null);
        msg.replyTo = clientCallback;

        serviceCallback.send(msg);
    }

    public void sendGetSpeedLimit(Messenger clientCallback,
                                  int uploadSpeedLimit,
                                  int downloadSpeedLimit) throws RemoteException
    {
        if (clientCallback == null) {
            return;
        }

        Message msg = Message.obtain(null, GET_SPEED_LIMIT, null);
        msg.arg1 = uploadSpeedLimit;
        msg.arg2 = downloadSpeedLimit;

        clientCallback.send(msg);
    }
}
