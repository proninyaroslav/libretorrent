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

import android.os.Bundle;

import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;

/*
 * Provides message with information about the torrent state.
 */

public class TorrentStateMsg
{
    public static final String TYPE = "TYPE";
    public static final String STATE = "STATE";
    public static final String STATES = "STATES";
    public static final String TORRENT = "TORRENT";
    public static final String TORRENT_ID = "TORRENT_ID";
    public static final String META_INFO = "META_INFO";

    public enum Type {
        UPDATE_TORRENT,
        UPDATE_TORRENTS,
        TORRENT_ADDED,
        TORRENT_REMOVED,
        MAGNET_FETCHED
    }

    public static Bundle makeUpdateTorrentBundle(BasicStateParcel state)
    {
        Bundle b = new Bundle();
        b.putSerializable(TYPE, Type.UPDATE_TORRENT);
        b.putParcelable(STATE, state);

        return b;
    }

    public static Bundle makeUpdateTorrentsBundle(Bundle states)
    {
        Bundle b = new Bundle();
        b.putSerializable(TYPE, Type.UPDATE_TORRENTS);
        b.putBundle(STATES, states);

        return b;
    }

    public static Bundle makeTorrentAddedBundle(Torrent torrent)
    {
        Bundle b = new Bundle();
        b.putSerializable(TYPE, Type.TORRENT_ADDED);
        b.putParcelable(TORRENT, torrent);

        return b;
    }

    public static Bundle makeTorrentRemovedBundle(String id)
    {
        Bundle b = new Bundle();
        b.putSerializable(TYPE, Type.TORRENT_REMOVED);
        b.putString(TORRENT_ID, id);

        return b;
    }

    public static Bundle makeMagnetFetchedBundle(TorrentMetaInfo info)
    {
        Bundle b = new Bundle();
        b.putSerializable(TYPE, Type.MAGNET_FETCHED);
        b.putParcelable(META_INFO, info);

        return b;
    }
}
