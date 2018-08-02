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

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.MoveFlags;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionHandle;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.peer_info_vector;
import com.frostwire.jlibtorrent.swig.torrent_handle;

import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * This class encapsulate one stream with running torrent.
 */

public class TorrentDownload
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentDownload.class.getSimpleName();

    private static final long SAVE_RESUME_SYNC_TIME = 10000; /* ms */

    public static final double MAX_RATIO = 9999.;

    private static final int[] INNER_LISTENER_TYPES = new int[] {
            AlertType.BLOCK_FINISHED.swig(),
            AlertType.STATE_CHANGED.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_REMOVED.swig(),
            AlertType.TORRENT_PAUSED.swig(),
            AlertType.TORRENT_RESUMED.swig(),
            AlertType.STATS.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
            AlertType.STORAGE_MOVED.swig(),
            AlertType.STORAGE_MOVED_FAILED.swig(),
            AlertType.METADATA_RECEIVED.swig(),
    };

    private Context context;
    private TorrentHandle th;
    private Torrent torrent;
    private TorrentEngineCallback callback;
    private InnerListener listener;
    private Set<File> incompleteFilesToRemove;
    private File parts;
    private long lastSaveResumeTime;
    private String name;

    public TorrentDownload(Context context,
                           TorrentHandle handle,
                           Torrent torrent,
                           TorrentEngineCallback callback)
    {
        this.context = context;
        this.th = handle;
        this.name = handle.name();
        this.torrent = torrent;
        this.callback = callback;
        TorrentInfo ti = th.torrentFile();
        this.parts = (ti != null ? new File(torrent.getDownloadPath(), "." + ti.infoHash() + ".parts") : null);

        listener = new InnerListener();
        TorrentEngine.getInstance().addListener(listener);
    }

    private final class InnerListener implements AlertListener
    {
        @Override
        public int[] types()
        {
            return INNER_LISTENER_TYPES;
        }

        @Override
        public void alert(Alert<?> alert)
        {
            if (!(alert instanceof TorrentAlert<?>)) {
                return;
            }

            if (!((TorrentAlert<?>) alert).handle().swig().op_eq(th.swig())) {
                return;
            }

            AlertType type = alert.type();

            if (callback == null) {
                return;
            }

            switch (type) {
                case BLOCK_FINISHED:
                case STATE_CHANGED:
                    callback.onTorrentStateChanged(torrent.getId());
                    break;
                case TORRENT_FINISHED:
                    callback.onTorrentFinished(torrent.getId());
                    saveResumeData(true);
                    break;
                case TORRENT_REMOVED:
                    torrentRemoved();
                    break;
                case TORRENT_PAUSED:
                    callback.onTorrentPaused(torrent.getId());
                    break;
                case TORRENT_RESUMED:
                    callback.onTorrentResumed(torrent.getId());
                    break;
                case STATS:
                    callback.onTorrentStateChanged(torrent.getId());
                    break;
                case SAVE_RESUME_DATA:
                    serializeResumeData((SaveResumeDataAlert) alert);
                    break;
                case STORAGE_MOVED:
                    callback.onTorrentMoved(torrent.getId(), true);
                    saveResumeData(true);
                    break;
                case STORAGE_MOVED_FAILED:
                    callback.onTorrentMoved(torrent.getId(), false);
                    saveResumeData(true);
                    break;
                case PIECE_FINISHED:
                    saveResumeData(false);
                    break;
                case METADATA_RECEIVED:
                    MetadataReceivedAlert metadataAlert = ((MetadataReceivedAlert) alert);
                    String hash = metadataAlert.handle().infoHash().toHex();
                    int size = metadataAlert.metadataSize();
                    int maxSize = 2 * 1024 * 1024;
                    byte[] bencode = null;
                    if (0 < size && size <= maxSize)
                        bencode = metadataAlert.torrentData(true);
                    handleMetadata(bencode, hash, metadataAlert.torrentName());
                    break;
            }
        }
    }

    private void handleMetadata(byte[] bencode, String hash, String newName)
    {
        Exception err = null;
        try {
            String pathToDir = TorrentUtils.findTorrentDataDir(context, hash);
            if (pathToDir == null)
                throw new FileNotFoundException("Data dir not found");

            File torrentFile = TorrentUtils.createTorrentFile(TorrentStorage.Model.DATA_TORRENT_FILE_NAME, bencode, new File(pathToDir));
            String pathToTorrent;
            if (torrentFile != null && torrentFile.exists())
                pathToTorrent = torrentFile.getAbsolutePath();
            else
                throw new FileNotFoundException("Torrent file not found");

            TorrentMetaInfo info = new TorrentMetaInfo(pathToTorrent);
            long freeSpace = FileIOUtils.getFreeSpace(torrent.getDownloadPath());
            if (freeSpace < info.torrentSize)
                throw new FreeSpaceException("Not enough free space: "
                        + freeSpace + " free, but torrent size is " + info.torrentSize);

            /* Skip if default name is changed */
            if (torrent.getName().equals(name)) {
                name = newName;
                torrent.setName(newName);
            }
            torrent.setTorrentFilePath(pathToTorrent);
            torrent.setFilePriorities(Collections.nCopies(info.fileList.size(), Priority.NORMAL));
            torrent.setDownloadingMetadata(false);
            setSequentialDownload(torrent.isSequentialDownload());
            if (torrent.isPaused())
                pause();
            else
                resume();
            setDownloadPath(torrent.getDownloadPath());
        } catch (Exception e) {
            err = e;
            remove(true);
        }
        if (callback != null)
            callback.onTorrentMetadataLoaded(hash, err);
    }

    private void torrentRemoved()
    {
        if (callback != null)
            callback.onTorrentRemoved(torrent.getId());

        TorrentEngine.getInstance().removeListener(listener);
        if (parts != null)
            parts.delete();
        finalCleanup(incompleteFilesToRemove);
    }

    /*
     * Generate fast-resume data for the torrent, see libtorrent documentation
     */

    void saveResumeData(boolean force)
    {
        long now = System.currentTimeMillis();

        if (force || (now - lastSaveResumeTime) >= SAVE_RESUME_SYNC_TIME) {
            lastSaveResumeTime = now;
        } else {
            /* Skip, too fast, see SAVE_RESUME_SYNC_TIME */
            return;
        }

        try {
            if (th != null && th.isValid()) {
                th.saveResumeData(TorrentHandle.SAVE_INFO_DICT);
            }

        } catch (Exception e) {
            Log.w(TAG, "Error triggering resume data of " + torrent + ":");
            Log.w(TAG, Log.getStackTraceString(e));
        }
    }

    private void serializeResumeData(SaveResumeDataAlert alert)
    {
        try {
            if (th.isValid()) {
                byte_vector data = add_torrent_params.write_resume_data(alert.params().swig()).bencode();
                TorrentUtils.saveResumeData(context, torrent.getId(), Vectors.byte_vector2bytes(data));
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error saving resume data of " + torrent + ":");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void pause()
    {
        if (!th.isValid())
            return;

        th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        th.pause();
        saveResumeData(true);
    }

    public void resume()
    {
        if (!th.isValid())
            return;

        if (TorrentEngine.getInstance().getSettings().autoManaged)
            th.setFlags(TorrentFlags.AUTO_MANAGED);
        else
            th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        th.resume();
        saveResumeData(true);
    }

    public void setAutoManaged(boolean autoManaged)
    {
        if (isPaused())
            return;

        if (autoManaged)
            th.setFlags(TorrentFlags.AUTO_MANAGED);
        else
            th.unsetFlags(TorrentFlags.AUTO_MANAGED);
    }

    public boolean isAutoManaged()
    {
        return th.isValid() && th.status().flags().and_(TorrentFlags.AUTO_MANAGED).nonZero();
    }

    public void setTorrent(Torrent torrent)
    {
        this.torrent = torrent;
    }

    public Torrent getTorrent()
    {
        return torrent;
    }

    public int getProgress()
    {
        if (th == null || !th.isValid()) {
            return 0;
        }

        if (th.torrentFile() == null) {
            return 0;
        }

        if (th.status() == null) {
            return 0;
        }

        float fp = th.status().progress();
        TorrentStatus.State state = th.status().state();

        if (Float.compare(fp, 1f) == 0 && state != TorrentStatus.State.CHECKING_FILES) {
            return 100;
        }

        int p = (int) (th.status().progress() * 100);
        if (p > 0 && state != TorrentStatus.State.CHECKING_FILES) {
            return Math.min(p, 100);
        }
        final long received = getTotalReceivedBytes();
        final long size = getSize();
        if (size == received) {
            return 100;
        }
        if (size > 0) {
            p = (int) ((received * 100) / size);
            return Math.min(p, 100);
        }

        return 0;
    }

    public void prioritizeFiles(Priority[] priorities)
    {
        if (th == null || !th.isValid())
            return;

        if (priorities != null) {
            /* Priorities for all files, priorities list for some selected files not supported */
            TorrentInfo ti = th.torrentFile();
            if (ti == null)
                return;
            if (th.torrentFile().numFiles() != priorities.length)
                return;

            th.prioritizeFiles(priorities);

        } else {
            /* Did they just add the entire torrent (therefore not selecting any priorities) */
            final Priority[] wholeTorrentPriorities =
                    Priority.array(Priority.NORMAL, th.torrentFile().numFiles());

            th.prioritizeFiles(wholeTorrentPriorities);
        }
    }

    public long getSize()
    {
        if (!th.isValid())
            return 0;

        TorrentInfo info = th.torrentFile();

        return info != null ? info.totalSize() : 0;
    }

    public long getDownloadSpeed()
    {
        return (!th.isValid() || isFinished() || isPaused() || isSeeding()) ? 0 : th.status().downloadPayloadRate();
    }

    public long getUploadSpeed()
    {
        return (!th.isValid() || (isFinished() && !isSeeding()) || isPaused()) ? 0 : th.status().uploadPayloadRate();
    }

    public void remove(boolean withFiles)
    {
        incompleteFilesToRemove = getIncompleteFiles();

        if (th.isValid()) {
            if (withFiles) {
                TorrentEngine.getInstance().remove(th, SessionHandle.DELETE_FILES);
            } else {
                TorrentEngine.getInstance().remove(th);
            }
        }
    }

    /*
     * Deletes incomplete files.
     */

    private void finalCleanup(Set<File> incompleteFiles)
    {
        if (incompleteFiles != null) {
            for (File f : incompleteFiles) {
                try {
                    if (f.exists() && !f.delete()) {
                        Log.w(TAG, "Can't delete file " + f);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Can't delete file " + f + ", ex: " + e.getMessage());
                }
            }
        }
    }

    public Set<File> getIncompleteFiles()
    {
        Set<File> s = new HashSet<>();

        if (torrent.isDownloadingMetadata()) {
            return s;
        }

        try {
            if (!th.isValid()) {
                return s;
            }

            long[] progress = th.fileProgress(TorrentHandle.FileProgressFlags.PIECE_GRANULARITY);

            TorrentInfo ti = th.torrentFile();
            FileStorage fs = ti.files();

            String prefix = torrent.getDownloadPath();

            File torrentFile = new File(torrent.getTorrentFilePath());

            if (!torrentFile.exists()) {
                return s;
            }

            long createdTime = torrentFile.lastModified();

            for (int i = 0; i < progress.length; i++) {
                String filePath = fs.filePath(i);
                long fileSize = fs.fileSize(i);

                if (progress[i] < fileSize) {
                    /* Lets see if indeed the file is incomplete */
                    File f = new File(prefix, filePath);

                    if (!f.exists()) {
                        /* Nothing to do here */
                        continue;
                    }

                    if (f.lastModified() >= createdTime) {
                        /* We have a file modified (supposedly) by this transfer */
                        s.add(f);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating the incomplete files set of " + torrent.getId());
        }

        return s;
    }

    public long getActiveTime()
    {
        return th.isValid() ? th.status().activeDuration() / 1000L : 0;
    }

    public long getSeedingTime()
    {
        return th.isValid() ? th.status().seedingDuration() / 1000L : 0;
    }

    /*
     * Counts the amount of bytes received this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    public long getReceivedBytes()
    {
        return th.isValid() ? th.status().totalPayloadDownload() : 0;
    }

    public long getTotalReceivedBytes()
    {
        return th.isValid() ? th.status().allTimeDownload() : 0;
    }

    /*
     * Counts the amount of bytes send this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    public long getSentBytes()
    {
        return th.isValid() ? th.status().totalPayloadUpload() : 0;
    }

    public long getTotalSentBytes()
    {
        return th.isValid() ? th.status().allTimeUpload() : 0;
    }

    public int getConnectedPeers()
    {
        return th.isValid() ? th.status().numPeers() : 0;
    }

    public int getConnectedSeeds()
    {
        return th.isValid() ? th.status().numSeeds() : 0;
    }

    public int getTotalPeers()
    {
        if (!th.isValid())
            return 0;

        TorrentStatus ts = th.status();
        int peers = ts.numComplete() + ts.numIncomplete();

        return (peers > 0 ? peers : th.status().listPeers());
    }

    public int getTotalSeeds()
    {
        return th.isValid() ? th.status().listSeeds() : 0;
    }

    public void requestTrackerAnnounce()
    {
        th.forceReannounce();
    }

    public void requestTrackerScrape()
    {
        th.scrapeTracker();
    }

    public Set<String> getTrackersUrl()
    {
        if (!th.isValid())
            return new HashSet<>();

        List<AnnounceEntry> trackers = th.trackers();
        Set<String> urls = new HashSet<>(trackers.size());

        for (AnnounceEntry entry : trackers) {
            urls.add(entry.url());
        }

        return urls;
    }

    public List<AnnounceEntry> getTrackers()
    {
        if (!th.isValid()) {
            return new ArrayList<>();
        }

        return th.trackers();
    }

    /*
     * This function is similar as TorrentHandle::peerInfo()
     */

    public List<AdvancedPeerInfo> advancedPeerInfo()
    {
        if (!th.isValid())
            return new ArrayList<>();

        torrent_handle th_swig = th.swig();
        peer_info_vector v = new peer_info_vector();
        th_swig.get_peer_info(v);

        int size = (int)v.size();
        ArrayList<AdvancedPeerInfo> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            l.add(new AdvancedPeerInfo(v.get(i)));

        return l;
    }

    public TorrentStatus getTorrentStatus()
    {
        return th.status();
    }

    /*
     * The total number of bytes we want to download. This may be smaller than the total
     * torrent size in case any pieces are prioritized to 0, i.e. not wanted.
     */

    public long getTotalWanted()
    {
        return th.isValid() ? th.status().totalWanted() : 0;
    }

    public void replaceTrackers(Set<String> trackers)
    {
        List<AnnounceEntry> urls = new ArrayList<>(trackers.size());
        for (String url : trackers)
            urls.add(new AnnounceEntry(url));
        th.replaceTrackers(urls);
        saveResumeData(true);
    }

    public void addTrackers(Set<String> trackers)
    {
        for (String url : trackers)
            th.addTracker(new AnnounceEntry(url));
        saveResumeData(true);
    }

    public boolean[] pieces()
    {
        PieceIndexBitfield bitfield = th.status(TorrentHandle.QUERY_PIECES).pieces();
        boolean[] pieces = new boolean[bitfield.size()];

        for (int i = 0; i < bitfield.size(); i++) {
            pieces[i] = bitfield.getBit(i);
        }

        return pieces;
    }

    public String makeMagnet()
    {
        return th.makeMagnetUri();
    }

    public void setSequentialDownload(boolean sequential)
    {
        if (sequential)
            th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
        else
            th.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
    }

    public long getETA() {
        if (!th.isValid())
            return 0;
        if (getStateCode() != TorrentStateCode.DOWNLOADING)
            return 0;

        TorrentInfo ti = th.torrentFile();
        if (ti == null)
            return 0;
        TorrentStatus status = th.status();
        long left = ti.totalSize() - status.totalDone();
        long rate = status.downloadPayloadRate();
        if (left <= 0)
            return 0;
        if (rate <= 0)
            return -1;

        return left / rate;
    }

    public TorrentInfo getTorrentInfo()
    {
        return th.torrentFile();
    }

    public void setDownloadPath(String path)
    {
        try {
            th.moveStorage(path, MoveFlags.ALWAYS_REPLACE_FILES);

        } catch (Exception e) {
            Log.e(TAG, "Error changing save path: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public long[] getFilesReceivedBytes()
    {
        if (!th.isValid()) {
            return null;
        }

        return th.fileProgress(TorrentHandle.FileProgressFlags.PIECE_GRANULARITY);
    }

    public void forceRecheck()
    {
        th.forceRecheck();
    }

    public int getNumDownloadedPieces()
    {
        return th.isValid() ? th.status().numPieces() : 0;
    }

    public double getShareRatio()
    {
        if (!th.isValid())
            return 0;

        long uploaded = getTotalSentBytes();
        long allTimeReceived = getTotalReceivedBytes();
        long totalDone = th.status().totalDone();
        /*
         * Special case for a seeder who lost its stats,
         * also assume nobody will import a 99% done torrent
         */
        long downloaded = (allTimeReceived < totalDone * 0.01 ? totalDone : allTimeReceived);
        if (downloaded == 0)
            return (uploaded == 0 ? 0.0 : MAX_RATIO);
        double ratio = (double) uploaded / (double) downloaded;

        return (ratio > MAX_RATIO ? MAX_RATIO : ratio);
    }

    public File getPartsFile()
    {
        return parts;
    }

    public void setDownloadSpeedLimit(int limit)
    {
        th.setDownloadLimit(limit);
        saveResumeData(true);
    }

    public int getDownloadSpeedLimit()
    {
        return th.isValid() ? th.getDownloadLimit() : 0;
    }

    public void setUploadSpeedLimit(int limit)
    {
        th.setUploadLimit(limit);
        saveResumeData(true);
    }

    public int getUploadSpeedLimit()
    {
        return th.isValid() ? th.getUploadLimit() : 0;
    }

    public String getInfoHash()
    {
        return th.infoHash().toString();
    }

    public TorrentStateCode getStateCode()
    {
        if (!TorrentEngine.getInstance().isRunning()) {
            return TorrentStateCode.STOPPED;
        }

        if (isPaused()) {
            return TorrentStateCode.PAUSED;
        }

        if (!th.isValid()) {
            return TorrentStateCode.ERROR;
        }

        TorrentStatus status = th.status();
        boolean isPaused = isPaused(status);

        if (isPaused && status.isFinished()) {
            return TorrentStateCode.FINISHED;
        }

        if (isPaused && !status.isFinished()) {
            return TorrentStateCode.PAUSED;
        }

        if (!isPaused && status.isFinished()) {
            return TorrentStateCode.SEEDING;
        }

        TorrentStatus.State stateCode = status.state();

        switch (stateCode) {
            case CHECKING_FILES:
                return TorrentStateCode.CHECKING;
            case DOWNLOADING_METADATA:
                return TorrentStateCode.DOWNLOADING_METADATA;
            case DOWNLOADING:
                return TorrentStateCode.DOWNLOADING;
            case FINISHED:
                return TorrentStateCode.FINISHED;
            case SEEDING:
                return TorrentStateCode.SEEDING;
            case ALLOCATING:
                return TorrentStateCode.ALLOCATING;
            case CHECKING_RESUME_DATA:
                return TorrentStateCode.CHECKING;
            case UNKNOWN:
                return TorrentStateCode.UNKNOWN;
            default:
                return TorrentStateCode.UNKNOWN;
        }
    }

    public boolean isPaused()
    {
        return th.isValid() && (isPaused(th.status(true)) ||
                TorrentEngine.getInstance().isPaused() || !TorrentEngine.getInstance().isRunning());
    }

    private static boolean isPaused(TorrentStatus s)
    {
        return s.flags().and_(TorrentFlags.PAUSED).nonZero();
    }

    public boolean isSeeding()
    {
        return th.isValid() && th.status().isSeeding();
    }

    public boolean isFinished()
    {
        return th.isValid() && th.status().isFinished();
    }

    public boolean isDownloading()
    {
        return getDownloadSpeed() > 0;
    }

    public boolean isSequentialDownload()
    {
        return th.isValid() && th.status().flags().and_(TorrentFlags.SEQUENTIAL_DOWNLOAD).nonZero();
    }

    public void setMaxConnections(int connections)
    {
        if (!th.isValid())
            return;
        th.swig().set_max_connections(connections);
    }

    public int getMaxConnections()
    {
        if (!th.isValid())
            return -1;

        return th.swig().max_connections();
    }

    public void setMaxUploads(int uploads)
    {
        if (!th.isValid())
            return;
        th.swig().set_max_uploads(uploads);
    }

    public int getMaxUploads()
    {
        if (!th.isValid())
            return -1;

        return th.swig().max_uploads();
    }

    public double getAvailability(int[] piecesAvailability)
    {
        if (piecesAvailability == null || piecesAvailability.length == 0)
            return 0;

        int min = Integer.MAX_VALUE;
        for (int avail : piecesAvailability)
            if (avail < min)
                min = avail;

        int total = 0;
        for (int avail : piecesAvailability)
            if (avail > 0 && avail > min)
                ++total;

        return (total / (double)piecesAvailability.length) + min;
    }

    public double[] getFilesAvailability(int[] piecesAvailability)
    {
        if (!th.isValid())
            return new double[0];

        TorrentInfo ti = th.torrentFile();
        if (ti == null)
            return new double[0];
        int numFiles = ti.numFiles();
        if (numFiles < 0)
            return new double[0];

        double[] filesAvail = new double[numFiles];
        if (piecesAvailability == null || piecesAvailability.length == 0) {
            Arrays.fill(filesAvail, -1);

            return filesAvail;
        }
        for (int i = 0; i < numFiles; i++) {
            Pair<Integer, Integer> filePieces = getFilePieces(i);
            if (filePieces == null) {
                filesAvail[i] = -1;
                continue;
            }
            int availablePieces = 0;
            for (int p = filePieces.first; p <= filePieces.second; p++)
                availablePieces += (piecesAvailability[p] > 0 ? 1 : 0);
            filesAvail[i] = (double)availablePieces / (filePieces.second - filePieces.first + 1);
        }

        return filesAvail;
    }

    public int[] getPiecesAvailability()
    {
        if (!th.isValid())
            return new int[0];

        boolean[] pieces = pieces();
        List<AdvancedPeerInfo> peers = advancedPeerInfo();
        int[] avail = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++)
            avail[i] = (pieces[i] ? 1 : 0);

        for (AdvancedPeerInfo peer : peers) {
            PieceIndexBitfield peerPieces = peer.pieces();
            for (int i = 0; i < pieces.length; i++)
                if (peerPieces.getBit(i))
                    ++avail[i];
        }

        return avail;
    }

    public Pair<Integer, Integer> getFilePieces(int fileIndex)
    {
        if (!th.isValid())
            return null;

        TorrentInfo ti = th.torrentFile();
        if (ti == null)
            return null;
        if (fileIndex < 0 || fileIndex >= ti.numFiles())
            return null;
        FileStorage fs = ti.files();
        long fileSize = fs.fileSize(fileIndex);
        long firstOffset = fs.fileOffset(fileIndex);

        return new Pair<>((int)(firstOffset / ti.pieceLength()),
                          (int)((firstOffset + fileSize - 1) / ti.pieceLength()));
    }
}
