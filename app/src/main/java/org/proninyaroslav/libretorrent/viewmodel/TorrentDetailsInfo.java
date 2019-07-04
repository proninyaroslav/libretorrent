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

package org.proninyaroslav.libretorrent.viewmodel;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import org.proninyaroslav.libretorrent.BR;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;

import java.util.Arrays;
import java.util.List;

public class TorrentDetailsInfo extends BaseObservable
{
    private Torrent torrent;
    private TorrentMetaInfo metaInfo;
    private BasicStateParcel state;
    private AdvanceStateParcel advancedState;
    private String dirName;
    private long storageFreeSpace = -1;
    private int leechers;
    private int totalLeechers;

    @Bindable
    public Torrent getTorrent()
    {
        return torrent;
    }

    public void setTorrent(Torrent torrent)
    {
        this.torrent = torrent;
        notifyPropertyChanged(BR.torrent);
    }

    @Bindable
    public BasicStateParcel getState()
    {
        return state;
    }

    public void setState(BasicStateParcel state)
    {
        this.state = state;
        notifyPropertyChanged(BR.state);
    }

    @Bindable
    public AdvanceStateParcel getAdvancedState()
    {
        return advancedState;
    }

    public void setAdvancedState(AdvanceStateParcel advancedState)
    {
        this.advancedState = advancedState;
        notifyPropertyChanged(BR.advancedState);
    }

    @Bindable
    public String getDirName()
    {
        return dirName;
    }

    public void setDirName(String dirName)
    {
        this.dirName = dirName;
        notifyPropertyChanged(BR.dirName);
    }

    @Bindable
    public long getStorageFreeSpace()
    {
        return storageFreeSpace;
    }

    public void setStorageFreeSpace(long storageFreeSpace)
    {
        this.storageFreeSpace = storageFreeSpace;
        notifyPropertyChanged(BR.storageFreeSpace);
    }

    @Bindable
    public TorrentMetaInfo getMetaInfo()
    {
        return metaInfo;
    }

    public void setMetaInfo(TorrentMetaInfo metaInfo)
    {
        this.metaInfo = metaInfo;
        notifyPropertyChanged(BR.metaInfo);
    }

    @Bindable
    public int getLeechers()
    {
        return leechers;
    }

    public void setLeechers(int leechers)
    {
        this.leechers = leechers;
        notifyPropertyChanged(BR.leechers);
    }

    @Bindable
    public int getTotalLeechers()
    {
        return totalLeechers;
    }

    public void setTotalLeechers(int totalLeechers)
    {
        this.totalLeechers = totalLeechers;
        notifyPropertyChanged(BR.totalLeechers);
    }

//    @Bindable
//    public List<TrackerStateParcel> getTrackers()
//    {
//        return trackers;
//    }
//
//    public void setTrackers(List<TrackerStateParcel> trackers)
//    {
//        this.trackers = trackers;
//        notifyPropertyChanged(BR.trackers);
//    }
//
//    @Bindable
//    public List<PeerStateParcel> getPeers()
//    {
//        return peers;
//    }
//
//    public void setPeers(List<PeerStateParcel> peers)
//    {
//        this.peers = peers;
//        notifyPropertyChanged(BR.peers);
//    }
//
//    @Bindable
//    public boolean[] getPieces()
//    {
//        return pieces;
//    }
//
//    public void setPieces(boolean[] pieces)
//    {
//        this.pieces = pieces;
//        notifyPropertyChanged(BR.pieces);
//    }

    @Override
    public String toString()
    {
        return "TorrentDetailsInfo{" +
                "torrent=" + torrent +
                ", metaInfo=" + metaInfo +
                ", state=" + state +
                ", advancedState=" + advancedState +
                ", dirName='" + dirName + '\'' +
                ", storageFreeSpace=" + storageFreeSpace +
                ", leechers=" + leechers +
                ", totalLeechers=" + totalLeechers +
                '}';
    }
}
