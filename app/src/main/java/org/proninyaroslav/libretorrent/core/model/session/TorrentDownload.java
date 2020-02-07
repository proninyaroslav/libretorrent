/*
 * Copyright (C) 2016-2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStream;

import java.util.List;
import java.util.Set;

import io.reactivex.Completable;

/*
 * An interface that represents one stream with running torrent.
 */

public interface TorrentDownload
{
    String getTorrentId();

    Completable requestStop();

    void pause();

    void resume();

    void pauseManually();

    void resumeManually();

    void setAutoManaged(boolean autoManaged);

    boolean isAutoManaged();

    /*
     * A value in the range [0, 100], that represents the progress of the torrent's
     * current task. It may be checking files or downloading
     */

    int getProgress();

    void prioritizeFiles(@NonNull Priority[] priorities);

    long getSize();

    long getDownloadSpeed();

    long getUploadSpeed();

    void remove(boolean withFiles);

    long getActiveTime();

    long getSeedingTime();

    long getReceivedBytes();

    long getTotalReceivedBytes();

    long getSentBytes();

    long getTotalSentBytes();

    int getConnectedPeers();

    int getConnectedSeeds();

    int getConnectedLeechers();

    int getTotalPeers();

    int getTotalSeeds();

    int getTotalLeechers();

    void requestTrackerAnnounce();

    Set<String> getTrackersUrl();

    List<TrackerInfo> getTrackerInfoList();

    List<PeerInfo> getPeerInfoList();

    long getTotalWanted();

    void replaceTrackers(@NonNull Set<String> trackers);

    void addTrackers(@NonNull Set<String> trackers);

    void addWebSeeds(@NonNull List<String> urls);

    boolean[] pieces();

    String makeMagnet(boolean includePriorities);

    void setSequentialDownload(boolean sequential);

    void setTorrentName(@NonNull String name);

    long getETA();

    TorrentMetaInfo getTorrentMetaInfo() throws DecodeException;

    String getTorrentName();

    void setDownloadPath(@NonNull Uri path);

    long[] getFilesReceivedBytes();

    void forceRecheck();

    int getNumDownloadedPieces();

    double getShareRatio();

    Uri getPartsFile();

    void setDownloadSpeedLimit(int limit);

    int getDownloadSpeedLimit();

    void setUploadSpeedLimit(int limit);

    int getUploadSpeedLimit();

    String getInfoHash();

    TorrentStateCode getStateCode();

    boolean isPaused();

    boolean isSeeding();

    boolean isFinished();

    boolean isDownloading();

    boolean isSequentialDownload();

    void setMaxConnections(int connections);

    int getMaxConnections();

    void setMaxUploads(int uploads);

    int getMaxUploads();

    double getAvailability(int[] piecesAvailability);

    double[] getFilesAvailability(int[] piecesAvailability);

    int[] getPiecesAvailability();

    boolean havePiece(int pieceIndex);

    void readPiece(int pieceIndex);

    void setInterestedPieces(@NonNull TorrentStream stream, int startPiece, int numPieces);

    TorrentStream getStream(int fileIndex);

    boolean isValid();

    boolean isStopped();

    Priority[] getFilePriorities();

    byte[] getBencode();

    void saveResumeData(boolean force);

    boolean hasMissingFiles();
}
