/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.MoveFlags;
import com.frostwire.jlibtorrent.PeerInfo;
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
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;

import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.util.ArrayList;
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
            AlertType.STORAGE_MOVED_FAILED.swig()
    };

    private Context context;
    private TorrentHandle th;
    private Torrent torrent;
    private TorrentEngineCallback callback;
    private InnerListener listener;
    private Set<File> incompleteFilesToRemove;
    private File parts;
    private long lastSaveResumeTime;

    public TorrentDownload(Context context,
                           TorrentHandle handle,
                           Torrent torrent,
                           TorrentEngineCallback callback)
    {
        this.context = context;
        this.th = handle;
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
            }
        }
    }

    private void torrentRemoved()
    {
        if (callback != null) {
            callback.onTorrentRemoved(torrent.getId());
        }

        if (parts != null) {
            parts.delete();
        }

        finalCleanup(incompleteFilesToRemove);
    }

    /*
     * Generate fast-resume data for the torrent, see libtorrent documentation
     */

    private void saveResumeData(boolean force)
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
                th.saveResumeData();
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
        if (!th.isValid()) {
            return;
        }

        th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        th.pause();
        saveResumeData(true);
    }

    public void resume(boolean forced)
    {
        if (!th.isValid()) {
            return;
        }

        if (forced)
            th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        else
            th.setFlags(TorrentFlags.AUTO_MANAGED);
        th.unsetFlags(TorrentFlags.UPLOAD_MODE);
        th.resume();
        saveResumeData(true);
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
        if (th == null || !th.isValid()) {
            return;
        }

        if (priorities != null) {
            /* Priorities for all files, priorities list for some selected files not supported */
            if (th.torrentFile().numFiles() != priorities.length) {
                return;
            }

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
        TorrentInfo info = th.torrentFile();

        return info != null ? info.totalSize() : 0;
    }

    public long getDownloadSpeed()
    {
        return (isFinished() || isPaused() || isSeeding()) ? 0 : th.status().downloadPayloadRate();
    }

    public long getUploadSpeed()
    {
        return ((isFinished() && !isSeeding()) || isPaused()) ? 0 : th.status().uploadPayloadRate();
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
        return th.status().activeDuration() / 1000L;
    }

    public long getSeedingTime()
    {
        return th.status().seedingDuration() / 1000L;
    }

    /*
     * Counts the amount of bytes received this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    public long getReceivedBytes()
    {
        return th.status().totalPayloadDownload();
    }

    public long getTotalReceivedBytes()
    {
        return th.status().allTimeDownload();
    }

    /*
     * Counts the amount of bytes send this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    public long getSentBytes()
    {
        return th.status().totalPayloadUpload();
    }

    public long getTotalSentBytes()
    {
        return th.status().allTimeUpload();
    }

    public int getConnectedPeers()
    {
        return th.status().numPeers();
    }

    public int getConnectedSeeds()
    {
        return th.status().numSeeds();
    }

    public int getTotalPeers()
    {
        return th.status().listPeers();
    }

    public int getTotalSeeds()
    {
        return th.status().listSeeds();
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

    public ArrayList<PeerInfo> getPeers()
    {
        return th.peerInfo();
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
        return th.status().totalWanted();
    }

    public void replaceTrackers(Set<String> trackers)
    {
        List<AnnounceEntry> urls = new ArrayList<>(trackers.size());

        for (String url : trackers) {
            urls.add(new AnnounceEntry(url));
        }

        th.replaceTrackers(urls);
        th.saveResumeData();
    }

    public void addTrackers(Set<String> trackers)
    {
        for (String url : trackers) {
            th.addTracker(new AnnounceEntry(url));
        }

        th.saveResumeData();
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
        if (getStateCode() != TorrentStateCode.DOWNLOADING) {
            return 0;
        }

        TorrentInfo ti = th.torrentFile();
        if (ti == null) {
            return 0;
        }

        TorrentStatus status = th.status();
        long left = ti.totalSize() - status.totalDone();
        long rate = status.downloadPayloadRate();

        if (left <= 0) {
            return 0;
        }

        if (rate <= 0) {
            return -1;
        }

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
        return th.status().numPieces();
    }

    public double getShareRatio()
    {
        long uploaded = getTotalSentBytes();

        long allTimeReceived = getTotalReceivedBytes();
        long totalDone = th.status().totalDone();

        /*
         * Special case for a seeder who lost its stats,
         * also assume nobody will import a 99% done torrent
         */
        long downloaded = (allTimeReceived < totalDone * 0.01 ? totalDone : allTimeReceived);

        if (downloaded == 0) {
            return (uploaded == 0 ? 0.0 : MAX_RATIO);
        }

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
        th.saveResumeData();
    }

    public int getDownloadSpeedLimit()
    {
        return th.getDownloadLimit();
    }

    public void setUploadSpeedLimit(int limit)
    {
        th.setUploadLimit(limit);
        th.saveResumeData();
    }

    public int getUploadSpeedLimit()
    {
        return th.getUploadLimit();
    }

    public String getInfoHash()
    {
        return th.infoHash().toString();
    }

    public TorrentStateCode getStateCode()
    {
        if (!TorrentEngine.getInstance().isStarted()) {
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
                TorrentEngine.getInstance().isPaused() || !TorrentEngine.getInstance().isStarted());
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
        if (!th.isValid()) {
            return false;
        }

        torrent_flags_t flags = th.status().flags();
        return flags.and_(TorrentFlags.SEQUENTIAL_DOWNLOAD).nonZero();
    }
}
