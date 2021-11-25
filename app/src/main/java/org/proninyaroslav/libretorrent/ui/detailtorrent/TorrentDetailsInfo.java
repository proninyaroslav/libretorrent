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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import org.proninyaroslav.libretorrent.BR;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;

public class TorrentDetailsInfo extends BaseObservable
{
    private Torrent torrent;
    private TorrentMetaInfo metaInfo;
    private TorrentInfo torrentInfo;
    private AdvancedTorrentInfo advancedInfo;
    private String dirName;
    private long storageFreeSpace = -1;

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
    public TorrentInfo getTorrentInfo()
    {
        return torrentInfo;
    }

    public void setTorrentInfo(TorrentInfo torrentInfo)
    {
        this.torrentInfo = torrentInfo;
        notifyPropertyChanged(BR.torrentInfo);
    }

    @Bindable
    public AdvancedTorrentInfo getAdvancedInfo()
    {
        return advancedInfo;
    }

    public void setAdvancedInfo(AdvancedTorrentInfo advancedInfo)
    {
        this.advancedInfo = advancedInfo;
        notifyPropertyChanged(BR.advancedInfo);
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

    @NonNull
    @Override
    public String toString()
    {
        return "TorrentDetailsInfo{" +
                "torrent=" + torrent +
                ", metaInfo=" + metaInfo +
                ", torrentInfo=" + torrentInfo +
                ", advancedInfo=" + advancedInfo +
                ", dirName='" + dirName + '\'' +
                ", storageFreeSpace=" + storageFreeSpace +
                '}';
    }
}
