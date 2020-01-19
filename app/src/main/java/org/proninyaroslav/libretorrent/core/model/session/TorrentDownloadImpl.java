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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import org.libtorrent4j.AlertListener;
import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.ErrorCode;
import org.libtorrent4j.FileStorage;
import org.libtorrent4j.MoveFlags;
import org.libtorrent4j.PieceIndexBitfield;
import org.libtorrent4j.SessionHandle;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.Vectors;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.AlertType;
import org.libtorrent4j.alerts.FastresumeRejectedAlert;
import org.libtorrent4j.alerts.FileErrorAlert;
import org.libtorrent4j.alerts.MetadataFailedAlert;
import org.libtorrent4j.alerts.MetadataReceivedAlert;
import org.libtorrent4j.alerts.PieceFinishedAlert;
import org.libtorrent4j.alerts.ReadPieceAlert;
import org.libtorrent4j.alerts.SaveResumeDataAlert;
import org.libtorrent4j.alerts.StateChangedAlert;
import org.libtorrent4j.alerts.TorrentAlert;
import org.libtorrent4j.alerts.TorrentErrorAlert;
import org.libtorrent4j.swig.add_torrent_params;
import org.libtorrent4j.swig.byte_vector;
import org.libtorrent4j.swig.peer_info_vector;
import org.libtorrent4j.swig.torrent_handle;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.model.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.ReadPieceInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStream;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;

/*
 * This class encapsulate one stream with running torrent.
 */

class TorrentDownloadImpl implements TorrentDownload
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentDownload.class.getSimpleName();

    private static final long SAVE_RESUME_SYNC_TIME = 10000; /* ms */
    private static final long CRITICAL_WORK_WAIT_TIMEOUT = 30000; /* ms */
    private static final double MAX_RATIO = 9999.;
    /* For streaming */
    private final static int PRELOAD_PIECES_COUNT = 5;
    private static final int DEFAULT_PIECE_DEADLINE = 1000; /* ms */
    private static final int MAX_METADATA_SIZE = 2 * 1024 * 1024;

    private static final int[] INNER_LISTENER_TYPES = new int[] {
            AlertType.STATE_CHANGED.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_REMOVED.swig(),
            AlertType.TORRENT_PAUSED.swig(),
            AlertType.TORRENT_RESUMED.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
            AlertType.STORAGE_MOVED.swig(),
            AlertType.STORAGE_MOVED_FAILED.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.PIECE_FINISHED.swig(),
            AlertType.READ_PIECE.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.METADATA_FAILED.swig(),
            AlertType.FILE_ERROR.swig(),
            AlertType.FASTRESUME_REJECTED.swig(),
    };

    private SessionManager sessionManager;
    private TorrentHandle th;
    private String id;
    private TorrentRepository repo;
    private FileSystemFacade fs;
    private Queue<TorrentEngineListener> listeners;
    private InnerListener listener;
    private Set<Uri> incompleteFilesToRemove;
    private Uri partsFile;
    private long lastSaveResumeTime;
    private AtomicReference<String> name;
    private TorrentCriticalWork criticalWork = new TorrentCriticalWork();
    private boolean autoManaged;
    private boolean stopRequested = false;
    private boolean stopped = false;
    private Completable stopEvent;

    public TorrentDownloadImpl(SessionManager sessionManager,
                               TorrentRepository repo,
                               FileSystemFacade fs,
                               final Queue<TorrentEngineListener> listeners,
                               String id,
                               TorrentHandle handle,
                               boolean autoManaged)
    {
        this.id = id;
        this.repo = repo;
        this.fs = fs;
        this.sessionManager = sessionManager;
        this.autoManaged = autoManaged;
        this.listeners = listeners;
        this.th = handle;
        this.name = new AtomicReference<>(handle.name());
        partsFile = getPartsFile();
        listener = new InnerListener();
        sessionManager.addListener(listener);

        /*
         * Save resume data after first start, if needed
         * (e.g torrent just added)
         */
        if (th.needSaveResumeData())
            saveResumeData(true);
    }

    private interface CallListener
    {
        void apply(TorrentEngineListener listener);
    }

    private void notifyListeners(@NonNull CallListener l)
    {
        for (TorrentEngineListener listener : listeners) {
            if (listener != null)
                l.apply(listener);
        }
    }

    private boolean operationNotAllowed()
    {
        return !th.isValid() || stopped;
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
            if (!(alert instanceof TorrentAlert<?>))
                return;

            if (!((TorrentAlert<?>) alert).handle().swig().op_eq(th.swig()))
                return;

            AlertType type = alert.type();
            switch (type) {
                case STATE_CHANGED:
                    StateChangedAlert a = ((StateChangedAlert)alert);
                    notifyListeners((listener) ->
                            listener.onTorrentStateChanged(id,
                                    stateToStateCode(a.getPrevState()),
                                    stateToStateCode(a.getState())));
                    break;
                case TORRENT_FINISHED:
                    notifyListeners((listener) ->
                            listener.onTorrentFinished(id));
                    saveResumeData(true);
                    break;
                case TORRENT_REMOVED:
                    torrentRemoved();
                    break;
                case TORRENT_PAUSED:
                    notifyListeners((listener) ->
                            listener.onTorrentPaused(id));
                    break;
                case TORRENT_RESUMED:
                    resetTorrentError();

                    notifyListeners((listener) ->
                            listener.onTorrentResumed(id));
                    break;
                case SAVE_RESUME_DATA:
                    serializeResumeData((SaveResumeDataAlert)alert);
                    break;
                case STORAGE_MOVED:
                    onStorageMoved(true);
                    break;
                case STORAGE_MOVED_FAILED:
                    onStorageMoved(false);
                    break;
                case PIECE_FINISHED:
                    saveResumeData(false);
                    int piece = ((PieceFinishedAlert)alert).pieceIndex();
                    notifyListeners((listener) ->
                            listener.onPieceFinished(id, piece));
                    break;
                case METADATA_RECEIVED:
                    handleMetadata((MetadataReceivedAlert)alert);
                    saveResumeData(true);
                    break;
                case READ_PIECE:
                    handleReadPiece((ReadPieceAlert)alert);
                    break;
                default:
                    checkError(alert);
                    break;
            }
        }
    }

    private void onStorageMoved(boolean success)
    {
        criticalWork.setMoving(false);
        notifyListeners((listener) -> listener.onTorrentMoved(id, success));

        saveResumeData(true);
    }

    private void resetTorrentError()
    {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        torrent.error = null;
        repo.updateTorrent(torrent);
    }

    private void checkError(Alert<?> alert)
    {
        String errorMsg = getErrorMsg(alert);

        if (errorMsg != null) {
            Log.e(TAG, "Torrent " + id + ": " + errorMsg);
            Torrent torrent = repo.getTorrentById(id);
            if (torrent != null) {
                torrent.error = errorMsg;
                repo.updateTorrent(torrent);
            }
            pause();
        }

        notifyListeners((listener) ->
                listener.onTorrentError(id, new Exception(errorMsg)));
    }

    private String getErrorMsg(Alert<?> alert)
    {
        String errorMsg = null;
        switch (alert.type()) {
            case TORRENT_ERROR: {
                TorrentErrorAlert errorAlert = (TorrentErrorAlert)alert;
                ErrorCode error = errorAlert.error();
                if (error.isError()) {
                    StringBuilder sb = new StringBuilder();
                    String filename = errorAlert.filename().substring(
                            errorAlert.filename().lastIndexOf("/") + 1);
                    if (errorAlert.filename() != null)
                        sb.append("[").append(filename).append("] ");
                    sb.append(Utils.getErrorMsg(error));
                    errorMsg = sb.toString();
                }
                break;
            } case METADATA_FAILED: {
                MetadataFailedAlert metadataFailedAlert = (MetadataFailedAlert)alert;
                ErrorCode error = metadataFailedAlert.getError();
                if (error.isError())
                    errorMsg = Utils.getErrorMsg(error);
                break;
            } case FILE_ERROR: {
                FileErrorAlert fileErrorAlert = (FileErrorAlert)alert;
                ErrorCode error = fileErrorAlert.error();
                String filename = fileErrorAlert.filename().substring(
                        fileErrorAlert.filename().lastIndexOf("/") + 1);
                if (error.isError())
                    errorMsg = "[" + filename + "] " +
                            Utils.getErrorMsg(error);
                break;
            } case FASTRESUME_REJECTED: {
                FastresumeRejectedAlert resumeRejectedAlert = (FastresumeRejectedAlert)alert;
                ErrorCode error = resumeRejectedAlert.error();
                if (error.isError())
                    errorMsg = Utils.getErrorMsg(error);
                break;
            }
        }

        return errorMsg;
    }

    private void handleMetadata(MetadataReceivedAlert alert)
    {
        Exception[] err = new Exception[1];
        String newName = null;
        Torrent torrent;
        try {
            torrent = repo.getTorrentById(id);
            if (torrent == null)
                throw new NullPointerException(id + " doesn't exists");

            int size = alert.metadataSize();
            if (size < 0 || size > MAX_METADATA_SIZE)
                return;

            TorrentMetaInfo info = new TorrentMetaInfo(alert.torrentData());

            /* Skip if default name is changed */
            String name = this.name.get();
            if (info.sha1Hash.equals(name) || TextUtils.isEmpty(name)) {
                this.name.set(info.torrentName);
                newName = info.torrentName;
            }

            long availableBytes = fs.getDirAvailableBytes(torrent.downloadPath);
            if (availableBytes < info.torrentSize)
                throw new FreeSpaceException("Not enough free space");

        } catch (Exception e) {
            err[0] = e;
            pause();
            notifyListeners((listener) ->
                    listener.onTorrentError(id, e));

        } finally {
            torrent = repo.getTorrentById(id);
            if (torrent != null) {
                torrent.setMagnetUri(null);
                if (newName != null)
                    torrent.name = newName;
                if (err[0] != null)
                    torrent.error = err[0].toString();

                repo.updateTorrent(torrent);
            }
        }

        notifyListeners((listener) ->
                listener.onTorrentMetadataLoaded(id, err[0]));
    }

    private void handleReadPiece(ReadPieceAlert alert)
    {
        Exception err = null;
        if (alert.error().isError())
            err = new Exception(alert.error().message());
        ReadPieceInfo info = new ReadPieceInfo(alert.piece(),
                alert.size(),
                alert.bufferPtr(),
                err);

        notifyListeners((listener) ->
                listener.onReadPiece(id, info));
    }

    private void torrentRemoved()
    {
        notifyListeners((listener) ->
                listener.onTorrentRemoved(id));

        forceStop();

        if (partsFile != null) {
            try {
                fs.deleteFile(partsFile);

            } catch (FileNotFoundException e) {
                /* Ignore */
            }
        }
        finalCleanup(incompleteFilesToRemove);
    }

    /*
     * Generate fast-resume data for the torrent, see libtorrent documentation
     */

    @Override
    public void saveResumeData(boolean force)
    {
        long now = System.currentTimeMillis();

        if (force || (now - lastSaveResumeTime) >= SAVE_RESUME_SYNC_TIME)
            lastSaveResumeTime = now;
        else
            /* Skip, too fast, see SAVE_RESUME_SYNC_TIME */
            return;

        try {
            if (th.isValid()) {
                criticalWork.setSaveResume(true);
                th.saveResumeData(TorrentHandle.SAVE_INFO_DICT);
            }

        } catch (Exception e) {
            Log.w(TAG, "Error triggering resume data of " + id + ":");
            Log.w(TAG, Log.getStackTraceString(e));

            criticalWork.setSaveResume(false);
        }
    }

    private void serializeResumeData(SaveResumeDataAlert alert)
    {
        try {
            byte_vector data = add_torrent_params.write_resume_data(alert.params().swig()).bencode();
            repo.addFastResume(new FastResume(id, Vectors.byte_vector2bytes(data)));

        } catch (Throwable e) {
            Log.e(TAG, Log.getStackTraceString(e));

        } finally {
            criticalWork.setSaveResume(false);
        }
    }

    @Override
    public String getTorrentId()
    {
        return id;
    }

    @Override
    public Completable requestStop()
    {
        if (stopEvent != null && (stopRequested || stopped))
            return stopEvent;

        stopRequested = true;

        /* Wait to complete critical works */
        stopEvent = Completable.create((emitter) -> {
            if (emitter.isDisposed())
                return;

            AtomicLong recentChangeTime = new AtomicLong(-1);
            Disposable d = criticalWork.observeStateChanging()
                    .timeout(CRITICAL_WORK_WAIT_TIMEOUT, TimeUnit.MILLISECONDS) /* Avoid infinite wait */
                    .subscribe(
                            (state) -> {
                                if (state.changeTime < recentChangeTime.get())
                                    return;
                                recentChangeTime.set(state.changeTime);
                                if (!(emitter.isDisposed() || state.isDuringChange())) {
                                    doStop();
                                    emitter.onComplete();
                                }
                            },
                            (err) -> {
                                Log.e(TAG, "Error waiting for critical work: "
                                        + Log.getStackTraceString(err));
                                if (!emitter.isDisposed()) {
                                    doStop();
                                    emitter.onComplete();
                                }
                            });
            emitter.setDisposable(d);
        });

        return stopEvent;
    }

    private void forceStop()
    {
        stopRequested = true;
        doStop();
    }

    private void doStop()
    {
        if (!stopRequested || stopped)
            return;

        sessionManager.removeListener(listener);
        stopRequested = false;
        stopped = true;
        stopEvent = null;
    }

    @Override
    public void pause()
    {
        if (operationNotAllowed())
            return;

        doPause();
    }

    @Override
    public void resume()
    {
        if (operationNotAllowed())
            return;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null || torrent.manuallyPaused)
            return;

        doResume();
    }

    @Override
    public void pauseManually()
    {
        if (operationNotAllowed())
            return;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        torrent.manuallyPaused = true;
        repo.updateTorrent(torrent);

        doPause();
    }

    @Override
    public void resumeManually()
    {
        if (operationNotAllowed())
            return;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        torrent.manuallyPaused = false;
        repo.updateTorrent(torrent);

        doResume();
    }

    private void doPause()
    {
        if (operationNotAllowed())
            return;

        th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        th.pause();
        saveResumeData(true);
    }

    private void doResume()
    {
        if (operationNotAllowed())
            return;

        if (autoManaged)
            th.setFlags(TorrentFlags.AUTO_MANAGED);
        else
            th.unsetFlags(TorrentFlags.AUTO_MANAGED);
        th.resume();
        saveResumeData(true);
    }

    @Override
    public void setAutoManaged(boolean autoManaged)
    {
        if (operationNotAllowed() || isPaused())
            return;

        this.autoManaged = autoManaged;

        if (autoManaged)
            th.setFlags(TorrentFlags.AUTO_MANAGED);
        else
            th.unsetFlags(TorrentFlags.AUTO_MANAGED);
    }

    @Override
    public boolean isAutoManaged()
    {
        return !operationNotAllowed() && th.status().flags().and_(TorrentFlags.AUTO_MANAGED).nonZero();
    }

    @Override
    public int getProgress()
    {
        if (operationNotAllowed())
            return 0;

        TorrentStatus ts = th.status();
        if (ts == null)
            return 0;

        float fp = ts.progress();
        if (Float.compare(fp, 1f) == 0)
            return 100;

        int p = (int)(fp * 100);
        if (p > 0)
            return Math.min(p, 100);

        return 0;
    }

    @Override
    public void prioritizeFiles(@NonNull Priority[] priorities)
    {
        if (operationNotAllowed())
            return;

        TorrentInfo ti = th.torrentFile();
        if (ti == null)
            return;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        org.libtorrent4j.Priority[] p = PriorityConverter.convert(priorities);
        /* Priorities for all files, priorities list for some selected files not supported */
        if (ti.numFiles() != p.length)
            return;

        th.prioritizeFiles(p);
    }

    @Override
    public long getSize()
    {
        if (operationNotAllowed())
            return 0;

        TorrentInfo info = th.torrentFile();

        return info != null ? info.totalSize() : 0;
    }

    @Override
    public long getDownloadSpeed()
    {
        return operationNotAllowed() || isFinished() || isPaused() || isSeeding() ?
                0 :
                th.status().downloadPayloadRate();
    }

    @Override
    public long getUploadSpeed()
    {
        return operationNotAllowed() || isFinished() && !isSeeding() || isPaused() ?
                0 :
                th.status().uploadPayloadRate();
    }

    @Override
    public void remove(boolean withFiles)
    {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent != null) {
            repo.deleteTorrent(torrent);
            incompleteFilesToRemove = getIncompleteFiles(torrent);
        }

        if (!operationNotAllowed()) {
            if (withFiles)
                sessionManager.remove(th, SessionHandle.DELETE_FILES);
            else
                sessionManager.remove(th);
        }
    }

    /*
     * Deletes incomplete files.
     */

    private void finalCleanup(Set<Uri> incompleteFiles)
    {
        if (incompleteFiles == null)
            return;

        for (Uri path : incompleteFiles) {
            try {
                if (!fs.deleteFile(path))
                    Log.w(TAG, "Can't delete file " + path);
            } catch (Exception e) {
                Log.w(TAG, "Can't delete file " + path + ", ex: " + e.getMessage());
            }
        }
    }

    private Set<Uri> getIncompleteFiles(Torrent torrent)
    {
        Set<Uri> s = new HashSet<>();

        if (torrent.isDownloadingMetadata())
            return s;

        try {
            if (operationNotAllowed())
                return s;

            long[] progress = th.fileProgress(TorrentHandle.FileProgressFlags.PIECE_GRANULARITY);
            TorrentInfo ti = th.torrentFile();
            FileStorage fileStorage = ti.files();
            Uri prefix = torrent.downloadPath;

            for (int i = 0; i < progress.length; i++) {
                String filePath = fileStorage.filePath(i);
                long fileSize = fileStorage.fileSize(i);
                if (progress[i] < fileSize) {
                    /* Lets see if indeed the file is incomplete */
                    Uri path = fs.getFileUri(filePath, prefix);
                    if (path == null)
                        continue;

                    if (fs.lastModified(path) >= torrent.dateAdded)
                        /* We have a file modified (supposedly) by this transfer */
                        s.add(path);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error calculating the incomplete files set of " + id);
        }

        return s;
    }

    @Override
    public long getActiveTime()
    {
        return operationNotAllowed() ? 0 : th.status().activeDuration() / 1000L;
    }

    @Override
    public long getSeedingTime()
    {
        return operationNotAllowed() ? 0 : th.status().seedingDuration() / 1000L;
    }

    /*
     * Counts the amount of bytes received this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    @Override
    public long getReceivedBytes()
    {
        return operationNotAllowed() ? 0 : th.status().totalPayloadDownload();
    }

    @Override
    public long getTotalReceivedBytes()
    {
        return operationNotAllowed() ? 0 : th.status().allTimeDownload();
    }

    /*
     * Counts the amount of bytes send this session, but only
     * the actual payload data (i.e the interesting data), these counters
     * ignore any protocol overhead.
     */

    @Override
    public long getSentBytes()
    {
        return operationNotAllowed() ? 0 : th.status().totalPayloadUpload();
    }

    @Override
    public long getTotalSentBytes()
    {
        return operationNotAllowed() ? 0 : th.status().allTimeUpload();
    }

    @Override
    public int getConnectedPeers()
    {
        return operationNotAllowed() ? 0 : th.status().numPeers();
    }

    @Override
    public int getConnectedSeeds()
    {
        return operationNotAllowed() ? 0 : th.status().numSeeds();
    }

    @Override
    public int getConnectedLeechers()
    {
        if (operationNotAllowed())
            return 0;

        TorrentStatus ts = th.status();

        return ts.numPeers() - ts.numSeeds();
    }

    @Override
    public int getTotalPeers()
    {
        if (operationNotAllowed())
            return 0;

        TorrentStatus ts = th.status();
        int peers = ts.numComplete() + ts.numIncomplete();

        return (peers > 0 ? peers : ts.listPeers());
    }

    @Override
    public int getTotalSeeds()
    {
        if (operationNotAllowed())
            return 0;

        TorrentStatus ts = th.status();
        int numComplete = ts.numComplete();

        return (numComplete > 0 ? numComplete : ts.listSeeds());
    }

    @Override
    public int getTotalLeechers()
    {
        if (operationNotAllowed())
            return 0;

        TorrentStatus ts = th.status();
        int numIncomplete = ts.numIncomplete();

        return (numIncomplete > 0 ? numIncomplete : ts.listPeers() - ts.listSeeds());
    }

    @Override
    public void requestTrackerAnnounce()
    {
        if (operationNotAllowed())
            return;

        th.forceReannounce();
    }

    @Override
    public Set<String> getTrackersUrl()
    {
        if (operationNotAllowed())
            return new HashSet<>();

        List<AnnounceEntry> trackers = th.trackers();
        Set<String> urls = new HashSet<>(trackers.size());

        for (AnnounceEntry entry : trackers)
            urls.add(entry.url());

        return urls;
    }

    @Override
    public List<TrackerInfo> getTrackerInfoList()
    {
        if (operationNotAllowed())
            return new ArrayList<>();

        List<AnnounceEntry> trackers = th.trackers();
        ArrayList<TrackerInfo> states = new ArrayList<>();

        for (AnnounceEntry entry : trackers)
            states.add(new TrackerInfo(entry));

        return states;
    }

    @Override
    public List<PeerInfo> getPeerInfoList()
    {
        if (operationNotAllowed())
            return new ArrayList<>();

        ArrayList<PeerInfo> infoList = new ArrayList<>();
        List<AdvancedPeerInfo> peers = advancedPeerInfo();

        TorrentStatus status = th.status();
        if (status == null)
            return infoList;

        for (AdvancedPeerInfo peer : peers) {
            PeerInfo state = new PeerInfo(peer, status);
            infoList.add(state);
        }

        return infoList;
    }

    /*
     * This function is similar as TorrentHandle::peerInfo()
     */

    private List<AdvancedPeerInfo> advancedPeerInfo()
    {
        torrent_handle th_swig = th.swig();
        peer_info_vector v = new peer_info_vector();
        th_swig.get_peer_info(v);

        int size = (int)v.size();
        ArrayList<AdvancedPeerInfo> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            l.add(new AdvancedPeerInfo(v.get(i)));

        return l;
    }

    /*
     * The total number of bytes we want to download. This may be smaller than the total
     * torrent size in case any pieces are prioritized to 0, i.e. not wanted.
     */

    @Override
    public long getTotalWanted()
    {
        return operationNotAllowed() ? 0 : th.status().totalWanted();
    }

    @Override
    public void replaceTrackers(@NonNull Set<String> trackers)
    {
        List<AnnounceEntry> urls = new ArrayList<>(trackers.size());
        for (String url : trackers)
            urls.add(new AnnounceEntry(url));

        if (operationNotAllowed())
            return;
        th.replaceTrackers(urls);
        saveResumeData(true);
    }

    @Override
    public void addTrackers(@NonNull Set<String> trackers)
    {
        if (operationNotAllowed())
            return;

        for (String url : trackers) {
            if (url == null)
                continue;
            th.addTracker(new AnnounceEntry(url));
        }
        saveResumeData(true);
    }

    @Override
    public void addWebSeeds(@NonNull List<String> urls)
    {
        if (operationNotAllowed())
            return;

        for (String url : urls) {
            if (url == null)
                continue;
            th.addUrlSeed(url);
        }
        saveResumeData(true);
    }

    @Override
    public boolean[] pieces()
    {
        if (operationNotAllowed())
            return new boolean[0];

        PieceIndexBitfield bitfield = th.status(TorrentHandle.QUERY_PIECES).pieces();
        boolean[] pieces = new boolean[bitfield.size()];
        for (int i = 0; i < bitfield.size(); i++)
            pieces[i] = bitfield.getBit(i);

        return pieces;
    }

    @Override
    public String makeMagnet(boolean includePriorities)
    {
        if (operationNotAllowed())
            return null;

        String uri = th.makeMagnetUri();

        if (includePriorities) {
            String indices = getFileIndicesBep53(th.filePriorities());
            if (!TextUtils.isEmpty(indices))
                uri += "&so=" + indices;
        }

        return uri;
    }

    @VisibleForTesting
    public static String getFileIndicesBep53(org.libtorrent4j.Priority[] priorities)
    {
        ArrayList<String> buf = new ArrayList<>();
        int startIndex = -1;
        int endIndex = -1;

        String indicesStr;
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i].swig() == org.libtorrent4j.Priority.IGNORE.swig()) {
                if ((indicesStr = indicesToStr(startIndex, endIndex)) != null)
                    buf.add(indicesStr);
                startIndex = -1;

            } else {
                endIndex = i;
                if (startIndex == -1)
                    startIndex = endIndex;
            }
        }
        if ((indicesStr = indicesToStr(startIndex, endIndex)) != null)
            buf.add(indicesStr);

        return TextUtils.join(",", buf);
    }

    /*
     * Returns files indices whose priorities are higher than IGNORE.
     * For more about see BEP53 http://www.bittorrent.org/beps/bep_0053.html
     */

    private static String indicesToStr(int startIndex, int endIndex)
    {
        if (startIndex == -1 || endIndex == -1)
            return null;

        return (startIndex == endIndex ?
                Integer.toString(endIndex) :
                String.format(Locale.ENGLISH, "%d-%d", startIndex, endIndex));
    }

    @Override
    public void setSequentialDownload(boolean sequential)
    {
        if (operationNotAllowed())
            return;

        if (sequential)
            th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
        else
            th.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
    }

    @Override
    public void setTorrentName(@NonNull String name)
    {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        torrent.name = name;
        repo.updateTorrent(torrent);
    }

    @Override
    public long getETA()
    {
        if (operationNotAllowed())
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

    @Override
    public TorrentMetaInfo getTorrentMetaInfo() throws DecodeException
    {
        if (operationNotAllowed())
            return null;

        TorrentInfo ti = th.torrentFile();
        TorrentMetaInfo info;
        if (ti != null) {
            info = new TorrentMetaInfo(ti);
        } else {
            String hash = getInfoHash();
            String name = getTorrentName();
            if (hash == null || name == null)
                return null;
            info = new TorrentMetaInfo(name, hash);
        }

        return info;
    }

    @Override
    public String getTorrentName()
    {
        Torrent torrent = repo.getTorrentById(id);

        return (torrent == null ? null : torrent.name);
    }

    @Override
    public synchronized void setDownloadPath(@NonNull Uri path)
    {
        criticalWork.setMoving(true);

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return;

        torrent.downloadPath = path;
        repo.updateTorrent(torrent);

        notifyListeners((listener) -> listener.onTorrentMoving(id));

        String pathStr = fs.makeFileSystemPath(path);
        try {
            th.moveStorage(pathStr, MoveFlags.ALWAYS_REPLACE_FILES);

        } catch (Exception e) {
            Log.e(TAG, "Error changing save path: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Override
    public long[] getFilesReceivedBytes()
    {
        if (operationNotAllowed())
            return null;

        return th.fileProgress(TorrentHandle.FileProgressFlags.PIECE_GRANULARITY);
    }

    @Override
    public void forceRecheck()
    {
        if (operationNotAllowed())
            return;

        th.forceRecheck();
    }

    @Override
    public int getNumDownloadedPieces()
    {
        return operationNotAllowed() ? 0 : th.status().numPieces();
    }

    @Override
    public double getShareRatio()
    {
        if (operationNotAllowed())
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

    @Override
    public Uri getPartsFile()
    {
        if (operationNotAllowed())
            return null;

        TorrentInfo ti = th.torrentFile();
        if (ti == null)
            return null;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        return fs.getFileUri(torrent.downloadPath, "." + ti.infoHash() + ".parts");
    }

    @Override
    public void setDownloadSpeedLimit(int limit)
    {
        if (operationNotAllowed())
            return;

        th.setDownloadLimit(limit);
        saveResumeData(true);
    }

    @Override
    public int getDownloadSpeedLimit()
    {
        return operationNotAllowed() ? 0 : th.getDownloadLimit();
    }

    @Override
    public void setUploadSpeedLimit(int limit)
    {
        if (operationNotAllowed())
            return;

        th.setUploadLimit(limit);
        saveResumeData(true);
    }

    @Override
    public int getUploadSpeedLimit()
    {
        return operationNotAllowed() ? 0 : th.getUploadLimit();
    }

    @Override
    public String getInfoHash()
    {
        return operationNotAllowed() ? null : th.infoHash().toString();
    }

    @Override
    public TorrentStateCode getStateCode()
    {
        if (!sessionManager.isRunning())
            return TorrentStateCode.STOPPED;

        if (isPaused())
            return TorrentStateCode.PAUSED;

        if (!th.isValid())
            return TorrentStateCode.ERROR;

        TorrentStatus status = th.status();
        boolean isPaused = isPaused(status);

        if (isPaused && status.isFinished())
            return TorrentStateCode.FINISHED;

        if (isPaused && !status.isFinished())
            return TorrentStateCode.PAUSED;

        if (!isPaused && status.isFinished())
            return TorrentStateCode.SEEDING;

       return stateToStateCode(status.state());
    }

    private TorrentStateCode stateToStateCode(TorrentStatus.State state)
    {
        switch (state) {
            case CHECKING_RESUME_DATA:
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
            default:
                return TorrentStateCode.UNKNOWN;
        }
    }

    @Override
    public boolean isPaused()
    {
        return !operationNotAllowed() && (isPaused(th.status(true)) ||
                sessionManager.isPaused() || !sessionManager.isRunning());
    }

    private boolean isPaused(TorrentStatus s)
    {
        return s.flags().and_(TorrentFlags.PAUSED).nonZero();
    }

    @Override
    public boolean isSeeding()
    {
        return !operationNotAllowed() && th.status().isSeeding();
    }

    @Override
    public boolean isFinished()
    {
        return !operationNotAllowed() && th.status().isFinished();
    }

    @Override
    public boolean isDownloading()
    {
        return getDownloadSpeed() > 0;
    }

    @Override
    public boolean isSequentialDownload()
    {
        return !operationNotAllowed() && th.status().flags().and_(TorrentFlags.SEQUENTIAL_DOWNLOAD).nonZero();
    }

    @Override
    public void setMaxConnections(int connections)
    {
        if (operationNotAllowed())
            return;
        th.swig().set_max_connections(connections);
    }

    @Override
    public int getMaxConnections()
    {
        if (operationNotAllowed())
            return -1;

        return th.swig().max_connections();
    }

    @Override
    public void setMaxUploads(int uploads)
    {
        if (operationNotAllowed())
            return;
        th.swig().set_max_uploads(uploads);
    }

    @Override
    public int getMaxUploads()
    {
        if (operationNotAllowed())
            return -1;

        return th.swig().max_uploads();
    }

    @Override
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

    @Override
    public double[] getFilesAvailability(int[] piecesAvailability)
    {
        if (operationNotAllowed())
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
            Pair<Integer, Integer> filePieces = getFilePieces(ti, i);
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

    @Override
    public int[] getPiecesAvailability()
    {
        if (operationNotAllowed())
            return new int[0];

        PieceIndexBitfield pieces = th.status(TorrentHandle.QUERY_PIECES).pieces();
        List<AdvancedPeerInfo> peers = advancedPeerInfo();
        int[] avail = new int[pieces.size()];
        for (int i = 0; i < pieces.size(); i++)
            avail[i] = (pieces.getBit(i) ? 1 : 0);

        for (AdvancedPeerInfo peer : peers) {
            PieceIndexBitfield peerPieces = peer.pieces();
            for (int i = 0; i < pieces.size(); i++)
                if (peerPieces.getBit(i))
                    ++avail[i];
        }

        return avail;
    }

    private Pair<Integer, Integer> getFilePieces(TorrentInfo ti, int fileIndex)
    {
        if (operationNotAllowed())
            return null;

        if (fileIndex < 0 || fileIndex >= ti.numFiles())
            return null;
        FileStorage fs = ti.files();
        long fileSize = fs.fileSize(fileIndex);
        long fileOffset = fs.fileOffset(fileIndex);

        return new Pair<>((int)(fileOffset / ti.pieceLength()),
                          (int)((fileOffset + fileSize - 1) / ti.pieceLength()));
    }

    @Override
    public boolean havePiece(int pieceIndex)
    {
        return !operationNotAllowed() && th.havePiece(pieceIndex);
    }

    @Override
    public void readPiece(int pieceIndex)
    {
        if (operationNotAllowed())
            return;

        th.readPiece(pieceIndex);
    }

    /*
     * Set the bytes of the selected file that you're interested in
     * the piece of that specific offset is selected and that piece plus the 1 preceding and the 3 after it.
     * These pieces will then be prioritised, which results in continuing the sequential download after that piece
     */

    @Override
    public void setInterestedPieces(@NonNull TorrentStream stream, int startPiece, int numPieces)
    {
        if (startPiece < 0 || numPieces < 0)
            return;

        for (int i = 0; i < numPieces; i++) {
            int piece = startPiece + i;
            if (piece > stream.lastFilePiece)
                break;

            if (i + 1 == numPieces) {
                int preloadPieces = PRELOAD_PIECES_COUNT;
                for (int p = piece; p <= stream.lastFilePiece; p++) {
                    /* Set max priority to first found piece that is not confirmed finished */
                    if (!operationNotAllowed() && !th.havePiece(p)) {
                        th.piecePriority(p, org.libtorrent4j.Priority.TOP_PRIORITY);
                        th.setPieceDeadline(p, DEFAULT_PIECE_DEADLINE);
                        preloadPieces--;
                        if (preloadPieces == 0)
                            break;
                    }
                }

            } else {
                if (!operationNotAllowed() && !th.havePiece(piece)) {
                    th.piecePriority(piece, org.libtorrent4j.Priority.TOP_PRIORITY);
                    th.setPieceDeadline(piece, DEFAULT_PIECE_DEADLINE);
                }
            }
        }
    }

    @Override
    public TorrentStream getStream(int fileIndex)
    {
        if (operationNotAllowed())
            return null;

        TorrentInfo ti = th.torrentFile();
        FileStorage fs = ti.files();
        Pair<Integer, Integer> filePieces = getFilePieces(ti, fileIndex);
        if (filePieces == null)
            throw new IllegalArgumentException("Incorrect file index");

        return new TorrentStream(id, fileIndex,
                                 filePieces.first, filePieces.second, ti.pieceLength(),
                                 fs.fileOffset(fileIndex), fs.fileSize(fileIndex),
                                 ti.pieceSize(filePieces.second));

    }

    @Override
    public boolean isValid()
    {
        return th.isValid();
    }

    @Override
    public boolean isStopped()
    {
        return stopped;
    }

    @Override
    public Priority[] getFilePriorities()
    {
        if (operationNotAllowed())
            return new Priority[0];

        return PriorityConverter.convert(th.filePriorities());
    }

    @Override
    public byte[] getBencode()
    {
        if (operationNotAllowed())
            return null;

        TorrentInfo ti = th.torrentFile();

        return (ti == null ? null : ti.bencode());
    }
}
