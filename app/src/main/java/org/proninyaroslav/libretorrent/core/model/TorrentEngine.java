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

package org.proninyaroslav.libretorrent.core.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.TorrentFileObserver;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.logger.Logger;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.MagnetInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.session.SessionInitParams;
import org.proninyaroslav.libretorrent.core.model.session.TorrentDownload;
import org.proninyaroslav.libretorrent.core.model.session.TorrentSession;
import org.proninyaroslav.libretorrent.core.model.session.TorrentSessionImpl;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentInputStream;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStream;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileDescriptorWrapper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.ConnectionReceiver;
import org.proninyaroslav.libretorrent.receiver.PowerReceiver;
import org.proninyaroslav.libretorrent.service.TorrentService;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

public class TorrentEngine
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentEngine.class.getSimpleName();

    private Context appContext;
    private TorrentSession session;
    private TorrentStreamServer torrentStreamServer;
    private TorrentRepository repo;
    private SettingsRepository pref;
    private TorrentNotifier notifier;
    private CompositeDisposable disposables = new CompositeDisposable();
    private TorrentFileObserver fileObserver;
    private PowerReceiver powerReceiver = new PowerReceiver();
    private ConnectionReceiver connectionReceiver = new ConnectionReceiver();
    private FileSystemFacade fs;
    private DownloadsCompletedListener downloadsCompleted;
    private ExecutorService exec = Executors.newSingleThreadExecutor();

    private static volatile TorrentEngine INSTANCE;

    public static TorrentEngine getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (TorrentEngine.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentEngine(appContext);
            }
        }

        return INSTANCE;
    }

    private TorrentEngine(@NonNull Context appContext)
    {
        this.appContext = appContext;
        repo = RepositoryHelper.getTorrentRepository(appContext);
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);
        pref = RepositoryHelper.getSettingsRepository(appContext);
        notifier = TorrentNotifier.getInstance(appContext);
        downloadsCompleted = new DownloadsCompletedListener(this);
        session = new TorrentSessionImpl(repo,
                fs,
                SystemFacadeHelper.getSystemFacade(appContext));
        session.setSettings(pref.readSessionSettings());
        session.addListener(engineListener);
    }

    private void handleAutoStop()
    {
        if (pref.shutdownDownloadsComplete())
            forceStop();
    }

    public void start()
    {
        if (isRunning())
            return;

        Utils.startServiceBackground(appContext, new Intent(appContext, TorrentService.class));
    }

    public Flowable<Boolean> observeNeedStartEngine()
    {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            Runnable emitLoop = () -> {
                while (!Thread.interrupted()) {
                    try {
                        Thread.sleep(1000);

                    } catch (InterruptedException e) {
                        return;
                    }

                    if (emitter.isCancelled() || isRunning())
                        return;

                    emitter.onNext(true);
                }
            };

            Disposable d = observeEngineRunning()
                    .subscribeOn(Schedulers.io())
                    .subscribe((isRunning) -> {
                        if (emitter.isCancelled())
                            return;

                        if (!isRunning) {
                            emitter.onNext(true);
                            exec.submit(emitLoop);
                        }
                    });

            if (!emitter.isCancelled()) {
                emitter.onNext(!isRunning());
                emitter.setDisposable(d);
            }

        }, BackpressureStrategy.LATEST);
    }

    public Flowable<Boolean> observeEngineRunning()
    {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onSessionStarted()
                {
                    if (!emitter.isCancelled())
                        emitter.onNext(true);
                }

                @Override
                public void onSessionStopped()
                {
                    if (!emitter.isCancelled())
                        emitter.onNext(false);
                }
            };

            if (!emitter.isCancelled()) {
                emitter.onNext(isRunning());
                addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() -> removeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }

    /*
     * Only calls from TorrentService
     */

    public void doStart()
    {
        if (isRunning())
            return;

        switchConnectionReceiver();
        switchPowerReceiver();
        disposables.add(pref.observeSettingsChanged()
                .subscribe(this::handleSettingsChanged));

        disposables.add(downloadsCompleted.listen()
                .subscribe(
                        this::handleAutoStop,
                        (err) -> {
                            Log.e(TAG, "Auto stop error: " +
                                    Log.getStackTraceString(err));
                            handleAutoStop();
                        }
                ));

        disposables.add(session.getLogger().observeDataSetChanged()
                .subscribe((change) -> {
                    if (change.reason == Logger.DataSetChange.Reason.NEW_ENTRIES && change.entries != null)
                        printSessionLog(change.entries);
                }));

        session.startWithParams(makeSessionInitParams());
    }

    private void printSessionLog(List<LogEntry> entries)
    {
        for (LogEntry entry : entries) {
            if (entry == null)
                continue;

            Log.i(TAG, entry.toString());
        }
    }
    
    private SessionInitParams makeSessionInitParams()
    {
        SessionInitParams initParams = new SessionInitParams();

        if (pref.useRandomPort()) {
            Pair<Integer, Integer> range = SessionSettings.getRandomRangePort();
            initParams.portRangeFirst = range.first;
            initParams.portRangeSecond = range.second;
        } else {
            initParams.portRangeFirst = pref.portRangeFirst();
            initParams.portRangeSecond = pref.portRangeSecond();
        }

        if (pref.proxyChanged()) {
            pref.proxyChanged(false);
            pref.applyProxy(false);

            initParams.proxyType = SessionSettings.ProxyType.fromValue(pref.proxyType());
            initParams.proxyAddress = pref.proxyAddress();
            initParams.proxyPort = pref.proxyPort();
            initParams.proxyPeersToo = pref.proxyPeersToo();
            initParams.proxyRequiresAuth = pref.proxyRequiresAuth();
            initParams.proxyLogin = pref.proxyLogin();
            initParams.proxyPassword = pref.proxyPassword();
        }
        
        return initParams;
    }

    /*
     * Leaves the right to the engine to decide whether to shutdown or not
     */

    public void requestStop()
    {
        if (pref.keepAlive())
            return;

        forceStop();
    }

    public void forceStop()
    {
        Intent i = new Intent(appContext, TorrentService.class);
        i.setAction(TorrentService.ACTION_SHUTDOWN);
        Utils.startServiceBackground(appContext, i);
    }

    /*
     * Only calls from TorrentService
     */

    public void doStop()
    {
        if (!isRunning())
            return;

        disposables.clear();
        stopWatchDir();
        stopStreamingServer();
        session.requestStop();
        cleanTemp();
    }

    public boolean isRunning()
    {
        return session.isRunning();
    }

    public void addListener(TorrentEngineListener listener)
    {
        session.addListener(listener);
    }

    public void removeListener(TorrentEngineListener listener)
    {
        session.removeListener(listener);
    }

    public void rescheduleTorrents()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            if (checkPauseTorrents())
                session.pauseAll();
            else
                session.resumeAll();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void addTorrent(@NonNull AddTorrentParams params,
                           boolean removeFile)
    {
        disposables.add(Completable.fromRunnable(() -> {
            try {
                addTorrentSync(params, removeFile);

            } catch (Exception e) {
                handleAddTorrentError(params.name, e);
            }
        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void addTorrents(@NonNull List<AddTorrentParams> paramsList,
                            boolean removeFile)
    {
        if (!isRunning())
            return;

        disposables.add(Observable.fromIterable(paramsList)
                .subscribeOn(Schedulers.io())
                .subscribe((params) -> {
                    try {
                        session.addTorrent(params, removeFile);

                    } catch (Exception e) {
                        handleAddTorrentError(params.name, e);
                    }
                }));
    }

    public void addTorrent(@NonNull Uri file)
    {
        addTorrent(file, null);
    }

    public void addTorrent(@NonNull Uri file, @Nullable Uri savePath)
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentMetaInfo info = null;
            try (FileDescriptorWrapper w = fs.getFD(file)) {
                FileDescriptor outFd = w.open("r");

                try(FileInputStream is = new FileInputStream(outFd)) {
                    info = new TorrentMetaInfo(is);

                } catch (Exception e) {
                    throw new DecodeException(e);
                }
                addTorrentSync(file, info, savePath);

            } catch (Exception e) {
                handleAddTorrentError((info == null ? file.getPath() : info.torrentName), e);
            }

        }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    /*
     * Do not run in the UI thread
     */

    public Torrent addTorrentSync(@NonNull AddTorrentParams params,
                                  boolean removeFile) throws IOException, TorrentAlreadyExistsException, DecodeException
    {
        if (!isRunning())
            return null;

        return session.addTorrent(params, removeFile);
    }

    public Pair<MagnetInfo, Single<TorrentMetaInfo>> fetchMagnet(@NonNull String uri) throws Exception
    {
        if (!isRunning())
            return null;

        MagnetInfo info = session.fetchMagnet(uri);
        if (info == null)
            return null;
        Single<TorrentMetaInfo> res = createFetchMagnetSingle(info.getSha1hash());

        return Pair.create(info, res);
    }

    public MagnetInfo parseMagnet(@NonNull String uri)
    {
        return session.parseMagnet(uri);
    }

    private Single<TorrentMetaInfo> createFetchMagnetSingle(String targetHash)
    {
        return Single.create((emitter) -> {
                TorrentEngineListener listener = new TorrentEngineListener() {
                    @Override
                    public void onMagnetLoaded(@NonNull String hash, byte[] bencode)
                    {
                        if (!targetHash.equals(hash))
                            return;

                        if (!emitter.isDisposed()) {
                            if (bencode == null)
                                emitter.onError(new IOException(new NullPointerException("bencode is null")));
                            else
                                sendInfoToEmitter(emitter, bencode);
                        }
                    }
                };
                if (!emitter.isDisposed()) {
                    /* Check if metadata is already loaded */
                    byte[] bencode = session.getLoadedMagnet(targetHash);
                    if (bencode == null) {
                        session.addListener(listener);
                        emitter.setDisposable(Disposables.fromAction(() ->
                                session.removeListener(listener)));
                    } else {
                        sendInfoToEmitter(emitter, bencode);
                    }
                }
        });
    }

    private void sendInfoToEmitter(SingleEmitter<TorrentMetaInfo> emitter, byte[] bencode)
    {
        TorrentMetaInfo info;
        try {
            info = new TorrentMetaInfo(bencode);

        } catch (DecodeException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            if (!emitter.isDisposed())
                emitter.onError(e);
            return;
        }

        if (!emitter.isDisposed())
            emitter.onSuccess(info);
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public void cancelFetchMagnet(@NonNull String infoHash)
    {
        if (!isRunning())
            return;

        session.cancelFetchMagnet(infoHash);
    }

    public void pauseResumeTorrent(@NonNull String id)
    {
        disposables.add(Completable.fromRunnable(() -> {
            TorrentDownload task = session.getTask(id);
            if (task == null)
                return;
            try {
                if (task.isPaused())
                    task.resumeManually();
                else
                    task.pauseManually();

            } catch (Exception e) {
                /* Ignore */
            }

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void forceRecheckTorrents(@NonNull List<String> ids)
    {
        disposables.add(Observable.fromIterable(ids)
                .filter((id) -> id != null)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.forceRecheck();
                }));
    }

    public void forceAnnounceTorrents(@NonNull List<String> ids)
    {
        disposables.add(Observable.fromIterable(ids)
                .filter((id) -> id != null)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.requestTrackerAnnounce();
                }));
    }

    public void deleteTorrents(@NonNull List<String> ids, boolean withFiles)
    {
        disposables.add(Observable.fromIterable(ids)
                .observeOn(Schedulers.io())
                .subscribe((id) -> {
                    if (!isRunning())
                        return;
                    session.deleteTorrent(id, withFiles);
                }));
    }

    public void deleteTrackers(@NonNull String id, @NonNull List<String> urls)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        Set<String> trackers = task.getTrackersUrl();
        trackers.removeAll(urls);

        task.replaceTrackers(trackers);
    }

    public void replaceTrackers(@NonNull String id, @NonNull List<String> urls)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.replaceTrackers(new HashSet<>(urls));
    }

    public void addTrackers(@NonNull String id, @NonNull List<String> urls)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }

    public String makeMagnet(@NonNull String id, boolean includePriorities)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet(includePriorities);
    }

    public Flowable<TorrentMetaInfo> observeTorrentMetaInfo(@NonNull String id)
    {
        return Flowable.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentMetadataLoaded(@NonNull String torrentId, Exception err)
                {
                    if (!id.equals(torrentId) || emitter.isCancelled())
                        return;

                    if (err == null) {
                        TorrentMetaInfo info = getTorrentMetaInfo(id);
                        if (info == null)
                            emitter.onError(new NullPointerException());
                        else
                            emitter.onNext(info);
                    } else {
                        emitter.onError(err);
                    }
                }
            };
            if (!emitter.isCancelled()) {
                TorrentMetaInfo info = getTorrentMetaInfo(id);
                if (info == null)
                    emitter.onError(new NullPointerException());
                else
                    emitter.onNext(info);

                session.addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        session.removeListener(listener)));
            }
        }, BackpressureStrategy.LATEST);
    }

    public TorrentMetaInfo getTorrentMetaInfo(@NonNull String id)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        TorrentMetaInfo info = null;
        try {
            info = task.getTorrentMetaInfo();

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public boolean[] getPieces(@NonNull String id)
    {
        if (!isRunning())
            return new boolean[0];

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new boolean[0];

        return task.pieces();
    }

    public void pauseAll()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.pauseAllManually();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void resumeAll()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.resumeAllManually();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void setTorrentName(@NonNull String id, @NonNull String name)
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.setTorrentName(name);

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void setDownloadPath(@NonNull String id, @NonNull Uri path)
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.setDownloadPath(path);

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void setSequentialDownload(@NonNull String id, boolean sequential)
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.setSequentialDownload(sequential);

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void prioritizeFiles(@NonNull String id, @NonNull Priority[] priorities)
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.prioritizeFiles(priorities);

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public TorrentStream getStream(@NonNull String id, int fileIndex)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getStream(fileIndex);
    }

    public TorrentInputStream getTorrentInputStream(@NonNull TorrentStream stream)
    {
        return new TorrentInputStream(session, stream);
    }

    /*
     * Do not run in the UI thread
     */

    public TorrentInfo makeInfoSync(@NonNull String id)
    {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        return makeInfo(torrent);
    }

    private TorrentInfo makeInfo(Torrent torrent)
    {
        TorrentDownload task = session.getTask(torrent.id);
        if (task == null || !task.isValid() || task.isStopped())
            return new TorrentInfo(torrent.id,
                    torrent.name,
                    torrent.dateAdded,
                    torrent.error);
        else
            return new TorrentInfo(
                    torrent.id,
                    torrent.name,
                    task.getStateCode(),
                    task.getProgress(),
                    task.getReceivedBytes(),
                    task.getTotalSentBytes(),
                    task.getTotalWanted(),
                    task.getDownloadSpeed(),
                    task.getUploadSpeed(),
                    task.getETA(),
                    torrent.dateAdded,
                    task.getTotalPeers(),
                    task.getConnectedPeers(),
                    torrent.error,
                    task.isSequentialDownload(),
                    task.getFilePriorities());
    }

    /*
     * Do not run in the UI thread
     */

    public List<TorrentInfo> makeInfoListSync()
    {
        ArrayList<TorrentInfo> stateList = new ArrayList<>();

        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null)
                continue;
            stateList.add(makeInfo(torrent));
        }

        return stateList;
    }

    /*
     * Do not run in the UI thread
     */

    public AdvancedTorrentInfo makeAdvancedInfoSync(@NonNull String id)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvancedTorrentInfo(
                torrent.id,
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail),
                task.getConnectedLeechers(),
                task.getTotalLeechers());
    }

    public List<TrackerInfo> makeTrackerInfoList(@NonNull String id)
    {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

       return task.getTrackerInfoList();
    }

    public List<PeerInfo> makePeerInfoList(@NonNull String id)
    {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        return task.getPeerInfoList();
    }

    public int getUploadSpeedLimit(@NonNull String id)
    {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getUploadSpeedLimit();
    }

    public int getDownloadSpeedLimit(@NonNull String id)
    {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getDownloadSpeedLimit();
    }

    public void setDownloadSpeedLimit(@NonNull String id, int limit)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setDownloadSpeedLimit(limit);
    }

    public void setUploadSpeedLimit(@NonNull String id, int limit)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setUploadSpeedLimit(limit);
    }

    public byte[] getBencode(@NonNull String id)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getBencode();
    }

    public boolean isSequentialDownload(@NonNull String id)
    {
        if (!isRunning())
            return false;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return false;

        return task.isSequentialDownload();
    }

    public int[] getPieceSizeList()
    {
        return session.getPieceSizeList();
    }

    public Logger getSessionLogger()
    {
        return session.getLogger();
    }

    private void saveTorrentFileIn(@NonNull Torrent torrent,
                                   @NonNull Uri saveDir)
    {
        String torrentFileName = torrent.name + ".torrent";
        try {
            if (!saveTorrentFile(torrent.id, saveDir, torrentFileName))
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    private boolean saveTorrentFile(String id, Uri destDir, String fileName) throws IOException
    {
        byte[] bencode = getBencode(id);
        if (bencode == null)
            return false;

        String name = (fileName != null ? fileName : id);

        Uri path = fs.createFile(destDir, name, true);
        if (path == null)
            return false;

        fs.write(bencode, path);

        return true;
    }

    private void switchPowerReceiver()
    {
        boolean batteryControl = pref.batteryControl();
        boolean customBatteryControl = pref.customBatteryControl();
        boolean onlyCharging = pref.onlyCharging();

        try {
            appContext.unregisterReceiver(powerReceiver);

        } catch (IllegalArgumentException e)
        {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
            /* Custom receiver doesn't send sticky intent, reschedule manually */
            rescheduleTorrents();
        } else if (batteryControl || onlyCharging) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getFilter());
        }
    }

    private void switchConnectionReceiver()
    {
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();
        boolean roaming = pref.enableRoaming();

        try {
            appContext.unregisterReceiver(connectionReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (unmeteredOnly || roaming)
            appContext.registerReceiver(connectionReceiver, ConnectionReceiver.getFilter());
    }

    private boolean checkPauseTorrents()
    {
        boolean batteryControl = pref.batteryControl();
        boolean customBatteryControl = pref.customBatteryControl();
        int customBatteryControlValue = pref.customBatteryControlValue();
        boolean onlyCharging = pref.onlyCharging();
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();
        boolean roaming = pref.enableRoaming();

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }

    private void handleOnSessionStarted()
    {
        if (pref.enableIpFiltering()) {
            String path = pref.ipFilteringFile();
            if (path != null)
                session.enableIpFilter(Uri.parse(path));
        }

        if (pref.watchDir())
            startWatchDir();

        boolean enableStreaming = pref.enableStreaming();
        if (enableStreaming)
            startStreamingServer();

        loadTorrents();
    }

    private void startStreamingServer()
    {
        stopStreamingServer();

        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        torrentStreamServer = new TorrentStreamServer(hostname, port);
        try {
            torrentStreamServer.start(appContext);

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            notifier.makeErrorNotify(appContext.getString(R.string.pref_streaming_error));
        }
    }

    private void stopStreamingServer()
    {
        if (torrentStreamServer != null)
            torrentStreamServer.stop();
        torrentStreamServer = null;
    }

    private void loadTorrents()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.restoreTorrents();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    private void setProxy()
    {
        SessionSettings s = session.getSettings();

        s.proxyType = SessionSettings.ProxyType.fromValue(pref.proxyType());
        s.proxyAddress = pref.proxyAddress();
        s.proxyPort = pref.proxyPort();
        s.proxyPeersToo = pref.proxyPeersToo();
        s.proxyRequiresAuth = pref.proxyRequiresAuth();
        s.proxyLogin = pref.proxyLogin();
        s.proxyPassword = pref.proxyPassword();

        session.setSettings(s);
    }

    private SessionSettings.EncryptMode getEncryptMode()
    {
        return SessionSettings.EncryptMode.fromValue(pref.encryptMode());
    }

    private void startWatchDir()
    {
        String dir = pref.dirToWatch();
        Uri uri = Uri.parse(dir);
        /* TODO: SAF support */
        if (Utils.isSafPath(appContext, uri))
            throw new IllegalArgumentException("SAF is not supported:" + uri);
        dir = uri.getPath();

        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir()
    {
        if (fileObserver == null)
            return;

        fileObserver.stopWatching();
        fileObserver = null;
    }

    private TorrentFileObserver makeTorrentFileObserver(String pathToDir)
    {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name)
            {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;

                addTorrent(Uri.fromFile(f));
            }
        };
    }

    private void scanTorrentsInDir(String pathToDir)
    {
        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : org.apache.commons.io.FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(Uri.fromFile(file));
        }
    }

    private Torrent addTorrentSync(Uri file, TorrentMetaInfo info, Uri savePath)
            throws IOException, FreeSpaceException, TorrentAlreadyExistsException, DecodeException
    {
        Priority[] priorities = new Priority[info.fileCount];
        Arrays.fill(priorities, Priority.DEFAULT);
        Uri downloadPath = (savePath == null ? Uri.parse(pref.saveTorrentsIn()) : savePath);

        AddTorrentParams params = new AddTorrentParams(file.toString(),
                false,
                info.sha1Hash,
                info.torrentName,
                priorities,
                downloadPath,
                false,
                false);

        if (fs.getDirAvailableBytes(downloadPath) < info.torrentSize)
            throw new FreeSpaceException();

        return addTorrentSync(params, false);
    }

    private void handleAddTorrentError(String name, Throwable e)
    {
        if (e instanceof TorrentAlreadyExistsException) {
            notifier.makeTorrentInfoNotify(name, appContext.getString(R.string.torrent_exist));
            return;
        }
        Log.e(TAG, Log.getStackTraceString(e));
        String message;
        if (e instanceof FileNotFoundException)
            message = appContext.getString(R.string.error_file_not_found_add_torrent);
        else if (e instanceof IOException)
            message = appContext.getString(R.string.error_io_add_torrent);
        else
            message = appContext.getString(R.string.error_add_torrent);
        notifier.makeTorrentErrorNotify(name, message);
    }

    private void cleanTemp()
    {
        try {
            fs.cleanTempDir();

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void setRandomPortRange()
    {
        Pair<Integer, Integer> range = SessionSettings.getRandomRangePort();
        setPortRange(range.first, range.second);
    }

    private void setPortRange(int first, int second)
    {
        if (first == -1 || second == -1)
            return;

        SessionSettings settings = session.getSettings();
        settings.portRangeFirst = first;
        settings.portRangeSecond = second;
        session.setSettings(settings);
    }

    /*
     * Disable notifications for torrent
     */

    private void markAsHiddenSync(Torrent torrent)
    {
        torrent.visibility = Torrent.VISIBILITY_HIDDEN;
        repo.updateTorrent(torrent);
    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStarted()
        {
            handleOnSessionStarted();
        }

        @Override
        public void onTorrentAdded(@NonNull String id)
        {
            if (pref.saveTorrentFiles())
                saveTorrentFileIn(repo.getTorrentById(id),
                        Uri.parse(pref.saveTorrentFilesIn()));

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;
                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.pause();

                }).subscribeOn(Schedulers.io())
                  .subscribe());
            }
        }

        @Override
        public void onTorrentLoaded(@NonNull String id)
        {
            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;
                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.pause();

                }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }

        @Override
        public void onTorrentFinished(@NonNull String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter((torrent) -> torrent != null)
                    .subscribe((torrent) -> {
                                notifier.makeTorrentFinishedNotify(torrent);
                                if (torrent.visibility != Torrent.VISIBILITY_HIDDEN)
                                    markAsHiddenSync(torrent);

                                if (pref.moveAfterDownload()) {
                                    String curPath = torrent.downloadPath.toString();
                                    String newPath = pref.moveAfterDownloadIn();

                                    if (!curPath.equals(newPath))
                                        setDownloadPath(id, Uri.parse(newPath));
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMoving(@NonNull String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeMovingTorrentNotify(name);
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMoved(@NonNull String id, boolean success)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                if (success)
                                    notifier.makeTorrentInfoNotify(name,
                                            appContext.getString(R.string.torrent_move_success));
                                else
                                    notifier.makeTorrentErrorNotify(name,
                                            appContext.getString(R.string.torrent_move_fail));
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onIpFilterParsed(int ruleCount)
        {
            disposables.add(Completable.fromRunnable(() -> {
                        Toast.makeText(appContext,
                                (ruleCount > 0 ?
                                        appContext.getString(R.string.ip_filter_add_success) :
                                        appContext.getString(R.string.ip_filter_add_error, ruleCount)),
                                Toast.LENGTH_LONG)
                                .show();
                    })
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe()
            );
        }

        @Override
        public void onSessionError(@NonNull String errorMsg)
        {
           notifier.makeSessionErrorNotify(errorMsg);
        }

        @Override
        public void onNatError(@NonNull String errorMsg)
        {
            Log.e(TAG, "NAT error: " + errorMsg);
            if (pref.showNatErrors())
                notifier.makeNatErrorNotify(errorMsg);
        }

        @Override
        public void onRestoreSessionError(@NonNull String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeTorrentErrorNotify(name,
                                        appContext.getString(R.string.restore_torrent_error));
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMetadataLoaded(@NonNull String id, Exception err)
        {
            if (err != null) {
                Log.e(TAG, "Load metadata error: ");
                Log.e(TAG, Log.getStackTraceString(err));
            }

            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter((torrent) -> torrent != null)
                    .subscribe((torrent) -> {
                                if (err == null) {
                                    if (pref.saveTorrentFiles())
                                        saveTorrentFileIn(torrent, Uri.parse(pref.saveTorrentFilesIn()));

                                } else if (err instanceof FreeSpaceException) {
                                    notifier.makeTorrentErrorNotify(torrent.name, appContext.getString(R.string.error_free_space));
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;
                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.pause();

                }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }
    };

    private void handleSettingsChanged(String key)
    {
        boolean reschedule = false;

        if (key.equals(appContext.getString(R.string.pref_key_unmetered_connections_only)) ||
            key.equals(appContext.getString(R.string.pref_key_enable_roaming))) {
            reschedule = true;
            switchConnectionReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                   key.equals(appContext.getString(R.string.pref_key_battery_control))) {
            reschedule = true;
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_custom_battery_control)) ||
                   key.equals(appContext.getString(R.string.pref_key_custom_battery_control_value))) {
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_max_download_speed))) {
            SessionSettings s = session.getSettings();
            s.downloadRateLimit = pref.maxDownloadSpeedLimit();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_upload_speed))) {
            SessionSettings s = session.getSettings();
            s.uploadRateLimit = pref.maxUploadSpeedLimit();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections))) {
            SessionSettings s = session.getSettings();
            s.connectionsLimit = pref.maxConnections();
            s.maxPeerListSize = s.connectionsLimit;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections_per_torrent))) {
            session.setMaxConnectionsPerTorrent(pref.maxConnectionsPerTorrent());

        } else if (key.equals(appContext.getString(R.string.pref_key_max_uploads_per_torrent))) {
            session.setMaxUploadsPerTorrent(pref.maxUploadsPerTorrent());

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_downloads))) {
            SessionSettings s = session.getSettings();
            s.activeDownloads = pref.maxActiveDownloads();
            session.setSettings(s);
            
        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_uploads))) {
            SessionSettings s = session.getSettings();
            s.activeSeeds = pref.maxActiveUploads();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_torrents))) {
            SessionSettings s = session.getSettings();
            s.activeLimit = pref.maxActiveTorrents();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_dht))) {
            SessionSettings s = session.getSettings();
            s.dhtEnabled = pref.enableDht();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_lsd))) {
            SessionSettings s = session.getSettings();
            s.lsdEnabled = pref.enableLsd();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_utp))) {
            SessionSettings s = session.getSettings();
            s.utpEnabled = pref.enableUtp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_upnp))) {
            SessionSettings s = session.getSettings();
            s.upnpEnabled = pref.enableUpnp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_natpmp))) {
            SessionSettings s = session.getSettings();
            s.natPmpEnabled = pref.enableNatPmp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_mode))) {
            SessionSettings s = session.getSettings();
            s.encryptMode = getEncryptMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_in_connections))) {
            SessionSettings s = session.getSettings();
            SessionSettings.EncryptMode state = SessionSettings.EncryptMode.DISABLED;
            s.encryptInConnections = pref.encryptInConnections();
            if (s.encryptInConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_out_connections))) {
            SessionSettings s = session.getSettings();
            SessionSettings.EncryptMode state = SessionSettings.EncryptMode.DISABLED;
            s.encryptOutConnections = pref.encryptOutConnections();
            if (s.encryptOutConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_use_random_port))) {
            if (pref.useRandomPort()) {
                setRandomPortRange();

            } else {
                int portFirst = pref.portRangeFirst();
                int portSecond = pref.portRangeSecond();
                setPortRange(portFirst, portSecond);
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_port_range_first)) ||
                   key.equals(appContext.getString(R.string.pref_key_port_range_second))) {
            int portFirst = pref.portRangeFirst();
            int portSecond = pref.portRangeSecond();
            setPortRange(portFirst, portSecond);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_ip_filtering))) {
            if (pref.enableIpFiltering()) {
                String path = pref.ipFilteringFile();
                if (path != null)
                    session.enableIpFilter(Uri.parse(path));
            } else {
                session.disableIpFilter();
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_ip_filtering_file))) {
            String path = pref.ipFilteringFile();
            if (path != null)
                session.enableIpFilter(Uri.parse(path));

        } else if (key.equals(appContext.getString(R.string.pref_key_apply_proxy))) {
            if (pref.applyProxy()) {
                pref.applyProxy(false);
                pref.proxyChanged(false);
                setProxy();
                Toast.makeText(appContext,
                        R.string.proxy_settings_applied,
                        Toast.LENGTH_SHORT)
                        .show();
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_auto_manage))) {
            session.setAutoManaged(pref.autoManage());

        } else if (key.equals(appContext.getString(R.string.pref_key_watch_dir))) {
            if (pref.watchDir())
                startWatchDir();
            else
                stopWatchDir();

        } else if (key.equals(appContext.getString(R.string.pref_key_dir_to_watch))) {
            stopWatchDir();
            startWatchDir();

        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_enable))) {
            if (pref.enableStreaming())
                startStreamingServer();
            else
                stopStreamingServer();

        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_port)) ||
                key.equals(appContext.getString(R.string.pref_key_streaming_hostname))) {
            startStreamingServer();

        } else if (key.equals(appContext.getString(R.string.pref_key_anonymous_mode))) {
            SessionSettings s = session.getSettings();
            s.anonymousMode = pref.anonymousMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_seeding_outgoing_connections))) {
            SessionSettings s = session.getSettings();
            s.seedingOutgoingConnections = pref.seedingOutgoingConnections();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_logging))) {
            SessionSettings s = session.getSettings();
            s.logging = pref.logging();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_session_filter))) {
            SessionSettings s = session.getSettings();
            s.logSessionFilter = pref.logSessionFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_dht_filter))) {
            SessionSettings s = session.getSettings();
            s.logDhtFilter = pref.logDhtFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_peer_filter))) {
            SessionSettings s = session.getSettings();
            s.logPeerFilter = pref.logPeerFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_portmap_filter))) {
            SessionSettings s = session.getSettings();
            s.logPortmapFilter = pref.logPortmapFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_torrent_filter))) {
            SessionSettings s = session.getSettings();
            s.logTorrentFilter = pref.logTorrentFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_log_size))) {
            SessionSettings s = session.getSettings();
            s.maxLogSize = pref.maxLogSize();
            session.setSettings(s);
        }

        if (reschedule)
            rescheduleTorrents();
    }
}