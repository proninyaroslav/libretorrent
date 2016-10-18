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

import android.content.Context;

import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SettingsPack;

import java.util.Collection;

/*
 * The interface for TorrentEngine management and getting status.
 */

public interface TorrentEngineInterface
{
    void start();

    void stop();

    void restart();

    void pause();

    void resume();

    TorrentDownload download(Torrent torrent);

    void asyncDownload(Collection<Torrent> torrents);

    Session getSession();

    long getDownloadRate();

    long getUploadRate();

    long getTotalDownload();

    long getTotalUpload();

    int getDownloadRateLimit();

    int getUploadRateLimit();

    void saveSettings();

    void loadSettings();

    void setSettings(SettingsPack sp);

    SettingsPack getSettings();

    void revertToDefaultConfiguration();

    void setDownloadSpeedLimit(int limit);

    int getDownloadSpeedLimit();

    void setUploadSpeedLimit(int limit);

    int getUploadSpeedLimit();

    int getPort();

    void setPort(int port);

    void enableIpFilter(String path);

    void disableIpFilter();

    void setProxy(Context context, ProxySettingsPack proxy);

    ProxySettingsPack getProxy();

    void disableProxy(Context context);

    boolean isListening();

    boolean isStarted();

    boolean isPaused();

    boolean isDHTEnabled();

    boolean isPeXEnabled();

    boolean isLSDEnabled();
}
