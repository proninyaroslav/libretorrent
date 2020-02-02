/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core.model;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.ReadPieceInfo;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;

public abstract class TorrentEngineListener
{
    public void onTorrentAdded(@NonNull String id) {}

    public void onTorrentLoaded(@NonNull String id) {}

    public void onTorrentStateChanged(@NonNull String id,
                                      @NonNull TorrentStateCode prevState,
                                      @NonNull TorrentStateCode curState) {}

    public void onTorrentFinished(@NonNull String id) {}

    public void onTorrentRemoved(@NonNull String id) {}

    public void onTorrentPaused(@NonNull String id) {}

    public void onTorrentResumed(@NonNull String id) {}

    public void onSessionStarted() {}

    public void onSessionStopped() {}

    public void onTorrentMoving(@NonNull String id) {}

    public void onTorrentMoved(@NonNull String id, boolean success) {}

    public void onIpFilterParsed(int ruleCount) {}

    public void onMagnetLoaded(@NonNull String hash, byte[] bencode) {}

    public void onTorrentMetadataLoaded(@NonNull String id, Exception err) {}

    public void onRestoreSessionError(@NonNull String id) {}

    public void onTorrentError(@NonNull String id, Exception e) {}

    public void onSessionError(@NonNull String errorMsg) {}

    public void onNatError(@NonNull String errorMsg) {}

    public void onReadPiece(@NonNull String id, ReadPieceInfo info) {}

    public void onPieceFinished(@NonNull String id, int piece) {}

    public void onSessionStats(@NonNull SessionStats stats) {}
}
