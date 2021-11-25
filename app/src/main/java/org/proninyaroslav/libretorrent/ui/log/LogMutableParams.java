/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

public class LogMutableParams extends BaseObservable
{
    private boolean logging;
    private boolean logSessionFilter;
    private boolean logDhtFilter;
    private boolean logPeerFilter;
    private boolean logPortmapFilter;
    private boolean logTorrentFilter;

    @Bindable
    public boolean isLogging()
    {
        return logging;
    }

    public void setLogging(boolean logging)
    {
        this.logging = logging;
        notifyPropertyChanged(BR.logging);
    }

    @Bindable
    public boolean isLogSessionFilter()
    {
        return logSessionFilter;
    }

    public void setLogSessionFilter(boolean logSessionFilter)
    {
        this.logSessionFilter = logSessionFilter;
        notifyPropertyChanged(BR.logSessionFilter);
    }

    @Bindable
    public boolean isLogDhtFilter()
    {
        return logDhtFilter;
    }

    public void setLogDhtFilter(boolean logDhtFilter)
    {
        this.logDhtFilter = logDhtFilter;
        notifyPropertyChanged(BR.logDhtFilter);
    }

    @Bindable
    public boolean isLogPeerFilter()
    {
        return logPeerFilter;
    }

    public void setLogPeerFilter(boolean logPeerFilter)
    {
        this.logPeerFilter = logPeerFilter;
        notifyPropertyChanged(BR.logPeerFilter);
    }

    @Bindable
    public boolean isLogPortmapFilter()
    {
        return logPortmapFilter;
    }

    public void setLogPortmapFilter(boolean logPortmapFilter)
    {
        this.logPortmapFilter = logPortmapFilter;
        notifyPropertyChanged(BR.logPeerFilter);
    }

    @Bindable
    public boolean isLogTorrentFilter()
    {
        return logTorrentFilter;
    }

    public void setLogTorrentFilter(boolean logTorrentFilter)
    {
        this.logTorrentFilter = logTorrentFilter;
        notifyPropertyChanged(BR.logTorrentFilter);
    }

    @NonNull
    @Override
    public String toString()
    {
        return "LogMutableParams{" +
                "logging=" + logging +
                ", logSessionFilter=" + logSessionFilter +
                ", logDhtFilter=" + logDhtFilter +
                ", logPeerFilter=" + logPeerFilter +
                ", logPortmapFilter=" + logPortmapFilter +
                ", logTorrentFilter=" + logTorrentFilter +
                '}';
    }
}
