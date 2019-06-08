/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

public abstract class TorrentEngineListener
{
    public void onTorrentAdded(String id) {};

    public void onTorrentStateChanged(String id) {};

    public void onTorrentFinished(String id) {};

    public void onTorrentRemoved(String id) {};

    public void onTorrentPaused(String id) {};

    public void onTorrentResumed(String id) {};

    public void onSessionStarted() {};

    public void onTorrentMoved(String id, boolean success){ };

    public void onIpFilterParsed(boolean success) {};

    public void onMagnetLoaded(String hash, byte[] bencode) {};

    public void onTorrentMetadataLoaded(String id, Exception err) {};

    public void onRestoreSessionError(String id) {};

    public void onTorrentError(String id, String errorMsg) {};

    public void onSessionError(String errorMsg) {};

    public void onNatError(String errorMsg) {};

    public void onApplyingParams(String id) {};

    public void onParamsApplied(String id, Throwable e) {}
}
