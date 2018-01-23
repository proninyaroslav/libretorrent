package org.proninyaroslav.libretorrent.core.stateparcel;

import android.content.Intent;
import android.os.Bundle;

import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;

/*
 * Provides intent with information about the torrent state.
 */

public class TorrentStateMsg
{
    public static final String ACTION = TorrentStateMsg.class.getSimpleName();
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

    public static Intent makeUpdateTorrentIntent(BasicStateParcel state)
    {
        Intent i = new Intent(ACTION);
        i.putExtra(TYPE, Type.UPDATE_TORRENT);
        i.putExtra(STATE, state);

        return i;
    }

    public static Intent makeUpdateTorrentsIntent(Bundle states)
    {
        Intent i = new Intent(ACTION);
        i.putExtra(TYPE, Type.UPDATE_TORRENTS);
        i.putExtra(STATES, states);

        return i;
    }

    public static Intent makeTorrentAddedIntent(Torrent torrent)
    {
        Intent i = new Intent(ACTION);
        i.putExtra(TYPE, Type.TORRENT_ADDED);
        i.putExtra(TORRENT, torrent);

        return i;
    }

    public static Intent makeTorrentRemovedIntent(String id)
    {
        Intent i = new Intent(ACTION);
        i.putExtra(TYPE, Type.TORRENT_REMOVED);
        i.putExtra(TORRENT_ID, id);

        return i;
    }

    public static Intent makeMagnetFetchedIntent(TorrentMetaInfo info)
    {
        Intent i = new Intent(ACTION);
        i.putExtra(TYPE, Type.MAGNET_FETCHED);
        i.putExtra(META_INFO, info);

        return i;
    }
}
