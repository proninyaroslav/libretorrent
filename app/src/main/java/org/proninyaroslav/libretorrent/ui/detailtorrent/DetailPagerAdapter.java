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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.DetailTorrentInfoFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.DetailTorrentStateFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.files.DetailTorrentFilesFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.peers.DetailTorrentPeersFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.pieces.DetailTorrentPiecesFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.pages.trackers.DetailTorrentTrackersFragment;

public class DetailPagerAdapter extends FragmentStatePagerAdapter
{
    public static final int NUM_FRAGMENTS = 6;
    public static final int INFO_FRAG_POS = 0;
    public static final int STATE_FRAG_POS = 1;
    public static final int FILES_FRAG_POS = 2;
    public static final int TRACKERS_FRAG_POS = 3;
    public static final int PEERS_FRAG_POS = 4;
    public static final int PIECES_FRAG_POS = 5;

    private Context context;

    public DetailPagerAdapter(Context context, FragmentManager fm)
    {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.context = context;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position)
    {
        switch (position) {
            case INFO_FRAG_POS:
                return context.getString(R.string.torrent_info);
            case STATE_FRAG_POS:
                return context.getString(R.string.torrent_state);
            case FILES_FRAG_POS:
                return context.getString(R.string.torrent_files);
            case TRACKERS_FRAG_POS:
                return context.getString(R.string.torrent_trackers);
            case PEERS_FRAG_POS:
                return context.getString(R.string.torrent_peers);
            case PIECES_FRAG_POS:
                return context.getString(R.string.torrent_pieces);
            default:
                return null;
        }
    }

    @NonNull
    @Override
    public Fragment getItem(int position)
    {
        switch (position) {
            case INFO_FRAG_POS:
                return DetailTorrentInfoFragment.newInstance();
            case STATE_FRAG_POS:
                return DetailTorrentStateFragment.newInstance();
            case FILES_FRAG_POS:
                return DetailTorrentFilesFragment.newInstance();
            case TRACKERS_FRAG_POS:
                return DetailTorrentTrackersFragment.newInstance();
            case PEERS_FRAG_POS:
                return DetailTorrentPeersFragment.newInstance();
            case PIECES_FRAG_POS:
                return DetailTorrentPiecesFragment.newInstance();
            default:
                return new Fragment();
        }
    }

    @Override
    public int getCount()
    {
        return NUM_FRAGMENTS;
    }
}
