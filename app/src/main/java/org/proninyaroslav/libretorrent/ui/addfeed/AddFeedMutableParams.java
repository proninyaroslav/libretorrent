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

package org.proninyaroslav.libretorrent.ui.addfeed;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import org.proninyaroslav.libretorrent.BR;

public class AddFeedMutableParams extends BaseObservable
{
    private long feedId = -1;
    private String url;
    private String name;
    private boolean autoDownload = false;
    private boolean notDownloadImmediately = true;
    private String filter;
    private boolean regexFilter = false;

    public long getFeedId()
    {
        return feedId;
    }

    public void setFeedId(long feedId)
    {
        this.feedId = feedId;
    }

    @Bindable
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
        notifyPropertyChanged(BR.url);
    }

    @Bindable
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
        notifyPropertyChanged(BR.name);
    }

    @Bindable
    public boolean isAutoDownload()
    {
        return autoDownload;
    }

    public void setAutoDownload(boolean autoDownload)
    {
        this.autoDownload = autoDownload;
        notifyPropertyChanged(BR.autoDownload);
    }

    @Bindable
    public boolean isNotDownloadImmediately()
    {
        return notDownloadImmediately;
    }

    public void setNotDownloadImmediately(boolean notDownloadImmediately)
    {
        this.notDownloadImmediately = notDownloadImmediately;
        notifyPropertyChanged(BR.notDownloadImmediately);
    }

    @Bindable
    public String getFilter()
    {
        return filter;
    }

    public void setFilter(String filter)
    {
        this.filter = filter;
        notifyPropertyChanged(BR.filter);
    }

    @Bindable
    public boolean isRegexFilter()
    {
        return regexFilter;
    }

    public void setRegexFilter(boolean regexFilter)
    {
        this.regexFilter = regexFilter;
        notifyPropertyChanged(BR.regexFilter);
    }
}
