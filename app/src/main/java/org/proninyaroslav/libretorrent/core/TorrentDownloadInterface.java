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

import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TorrentDownloadInterface
{
    void pause();

    void resume();

    void setTorrent(Torrent torrent);

    Torrent getTorrent();

    TorrentStateCode getStateCode();

    void prioritizeFiles(Priority[] priorities);

    int getProgress();

    long getDownloadSpeed();

    long getUploadSpeed();

    long getSize();

    void remove(boolean withFiles);

    Set<File> getIncompleteFiles();

    long getActiveTime();

    long getSeedingTime();
    /*
     * Counts the amount of bytes received this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */
    long getReceivedBytes();

    long getTotalReceivedBytes();
    /*
     * Counts the amount of bytes send this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */
    long getSentBytes();

    long getTotalSentBytes();

    int getConnectedPeers();

    int getTotalPeers();

    long getETA();

    int getConnectedSeeds();

    int getTotalSeeds();
    /*
     * The total number of bytes we want to download. This may be smaller than the total
     * torrent size in case any pieces are prioritized to 0, i.e. not wanted.
     */
    long getTotalWanted();

    void requestTrackerAnnounce();

    void requestTrackerScrape();

    Set<String> getTrackersUrl();

    List<AnnounceEntry> getTrackers();

    ArrayList<PeerInfo> getPeers();

    TorrentStatus getTorrentStatus();

    void replaceTrackers(Set<String> trackers);

    void addTrackers(Set<String> trackers);

    boolean[] pieces();

    String makeMagnet();

    void setSequentialDownload(boolean sequential);

    TorrentInfo getTorrentInfo();

    void setDownloadPath(String path);

    void forceRecheck();

    long[] getFilesReceivedBytes();

    int getNumDownloadedPieces();

    double getShareRatio();

    File getPartsFile();

    void setDownloadSpeedLimit(int limit);

    int getDownloadSpeedLimit();

    void setUploadSpeedLimit(int limit);

    int getUploadSpeedLimit();

    String getInfoHash();

    boolean isSequentialDownload();

    boolean isPaused();

    boolean isSeeding();

    boolean isFinished();

    boolean isDownloading();
}
