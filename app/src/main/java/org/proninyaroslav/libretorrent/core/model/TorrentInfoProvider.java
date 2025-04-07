/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.util.Log;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentListState;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Consumer;

/*
 * Provides runtime information about torrent, which isn't saved to the database.
 */

public class TorrentInfoProvider {
    private static final String TAG = TorrentInfoProvider.class.getSimpleName();

    private static final int GET_INFO_SYNC_TIME = 1000; /* ms */

    private static volatile TorrentInfoProvider INSTANCE;
    private final TorrentEngine engine;
    private final TagRepository tagRepo;

    public static TorrentInfoProvider getInstance(
            @NonNull TorrentEngine engine,
            @NonNull TagRepository tagRepo
    ) {
        if (INSTANCE == null) {
            synchronized (TorrentInfoProvider.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentInfoProvider(engine, tagRepo);
            }
        }
        return INSTANCE;
    }

    public static TorrentInfoProvider getInstance(@NonNull Context appContext) {
        if (INSTANCE == null) {
            synchronized (TorrentInfoProvider.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentInfoProvider(
                            TorrentEngine.getInstance(appContext),
                            RepositoryHelper.getTagRepository(appContext)
                    );
            }
        }
        return INSTANCE;
    }

    private TorrentInfoProvider(TorrentEngine engine, TagRepository tagRepo) {
        this.engine = engine;
        this.tagRepo = tagRepo;
    }

    public Flowable<TorrentInfo> observeInfo(@NonNull String id) {
        return makeInfoFlowable(id);
    }

    public Flowable<TorrentListState> observeInfoList() {
        return makeInfoListFlowable();
    }

    public Single<List<TorrentInfo>> getInfoListSingle() {
        return makeInfoListSingle();
    }

    public Flowable<AdvancedTorrentInfo> observeAdvancedInfo(@NonNull String id) {
        return makeAdvancedInfoFlowable(id);
    }

    public Flowable<List<TrackerInfo>> observeTrackersInfo(@NonNull String id) {
        return makeTrackersInfoFlowable(id);
    }

    public Flowable<List<PeerInfo>> observePeersInfo(@NonNull String id) {
        return makePeersInfoFlowable(id);
    }

    public Flowable<boolean[]> observePiecesInfo(@NonNull String id) {
        return makePiecesFlowable(id);
    }

    public Flowable<String> observeTorrentsDeleted() {
        return makeTorrentsDeletedFlowable();
    }

    public Flowable<SessionStats> observeSessionStats() {
        return makeSessionStatsFlowable();
    }

    public Single<SessionStats> getSessionStatsSingle() {
        return makeSessionStatsSingle();
    }

    private Flowable<TorrentInfo> makeInfoFlowable(String id) {
        return Flowable.create((emitter) -> {
            final AtomicReference<TorrentInfo> info = new AtomicReference<>();

            Consumer<String> handleEvent = (torrentId) -> {
                if (!id.equals(torrentId))
                    return;

                TorrentInfo newInfo = engine.makeInfoSync(id);
                TorrentInfo oldInfo = info.get();
                if (newInfo != null && !newInfo.equals(oldInfo)) {
                    info.set(newInfo);
                    if (!emitter.isCancelled())
                        emitter.onNext(newInfo);
                }
            };

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(@NonNull String torrentId,
                                                  @NonNull TorrentStateCode prevState,
                                                  @NonNull TorrentStateCode curState) {
                    try {
                        handleEvent.accept(torrentId);

                    } catch (Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onTorrentPaused(@NonNull String torrentId) {
                    try {
                        handleEvent.accept(torrentId);

                    } catch (Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onRestoreSessionError(@NonNull String torrentId) {
                    try {
                        handleEvent.accept(torrentId);

                    } catch (Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onTorrentError(@NonNull String torrentId, Exception e) {
                    try {
                        handleEvent.accept(torrentId);

                    } catch (Exception ex) {
                        if (!emitter.isCancelled())
                            emitter.onError(ex);
                    }
                }

                @Override
                public void onSessionStats(@NonNull SessionStats stats) {
                    try {
                        handleEvent.accept(id);

                    } catch (Exception ex) {
                        if (!emitter.isCancelled())
                            emitter.onError(ex);
                    }
                }
            };

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    TorrentInfo s = engine.makeInfoSync(id);
                    info.set(s);
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        if (s != null)
                            emitter.onNext(s);
                        engine.addListener(listener);
                        CompositeDisposable disposables = new CompositeDisposable();
                        disposables.add(Disposables.fromAction(() ->
                                engine.removeListener(listener))
                        );
                        disposables.add(tagRepo.observeByTorrentId(id)
                                .subscribe((__) -> {
                                    try {
                                        handleEvent.accept(id);
                                    } catch (Exception ex) {
                                        if (!emitter.isCancelled())
                                            emitter.onError(ex);
                                    }
                                })
                        );
                        emitter.setDisposable(disposables);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<TorrentListState> makeInfoListFlowable() {
        return Flowable.create((emitter) -> {
            final ConcurrentHashMap.KeySetView<TorrentInfo, Boolean> infoList
                    = ConcurrentHashMap.newKeySet();

            Runnable handleInfo = () -> {
                var newInfoList = engine.makeInfoListSync();
                if (infoList.size() != newInfoList.size() || !infoList.containsAll(newInfoList)) {
                    infoList.clear();
                    infoList.addAll(newInfoList);
                    if (!emitter.isCancelled()) {
                        emitter.onNext(new TorrentListState.Loaded(newInfoList));
                    }
                }
            };

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(@NonNull String torrentId,
                                                  @NonNull TorrentStateCode prevState,
                                                  @NonNull TorrentStateCode curState) {
                    handleInfo.run();
                }

                @Override
                public void onTorrentPaused(@NonNull String torrentId) {
                    handleInfo.run();
                }

                @Override
                public void onTorrentRemoved(@NonNull String torrentId) {
                    handleInfo.run();
                }

                @Override
                public void onRestoreSessionError(@NonNull String torrentId) {
                    handleInfo.run();
                }

                @Override
                public void onTorrentError(@NonNull String torrentId, Exception e) {
                    handleInfo.run();
                }

                @Override
                public void onSessionStats(@NonNull SessionStats stats) {
                    handleInfo.run();
                }
            };

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    emitter.onNext(new TorrentListState.Initial());
                    infoList.clear();
                    infoList.addAll(engine.makeInfoListSync());
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(new TorrentListState.Loaded(infoList.stream().toList()));
                        engine.addListener(listener);
                        CompositeDisposable disposables = new CompositeDisposable();
                        disposables.add(Disposables.fromAction(() ->
                                engine.removeListener(listener))
                        );
                        disposables.add(tagRepo.observeAll()
                                .subscribe((__) -> handleInfo.run())
                        );
                        emitter.setDisposable(disposables);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Single<List<TorrentInfo>> makeInfoListSingle() {
        return Single.create((emitter) -> {
            if (!emitter.isDisposed()) {
                Thread t = new Thread(() -> {
                    List<TorrentInfo> infoList = engine.makeInfoListSync();
                    if (!emitter.isDisposed())
                        emitter.onSuccess(infoList);
                });
                t.start();
            }
        });
    }

    private Flowable<AdvancedTorrentInfo> makeAdvancedInfoFlowable(String id) {
        return Flowable.create((emitter) -> {
            final AtomicReference<AdvancedTorrentInfo> info = new AtomicReference<>();

            Disposable d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                AdvancedTorrentInfo newInfo = engine.makeAdvancedInfoSync(id);
                                AdvancedTorrentInfo oldInfo = info.get();
                                if (newInfo != null && !newInfo.equals(oldInfo)) {
                                    info.set(newInfo);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newInfo);
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting advanced info for torrent " + id + " error: " +
                                    Log.getStackTraceString(t)));

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    AdvancedTorrentInfo s = engine.makeAdvancedInfoSync(id);
                    info.set(s);
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        if (s != null)
                            emitter.onNext(s);
                        emitter.setDisposable(d);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<TrackerInfo>> makeTrackersInfoFlowable(String id) {
        return Flowable.create((emitter) -> {
            var infoList = new HashSet<TrackerInfo>();

            Disposable d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                var newInfoList = engine.makeTrackerInfoList(id);
                                if (infoList.size() != newInfoList.size() || !infoList.containsAll(newInfoList)) {
                                    infoList.clear();
                                    infoList.addAll(newInfoList);
                                    if (!emitter.isCancelled()) {
                                        emitter.onNext(newInfoList);
                                    }
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting trackers info for torrent " + id + " error: " +
                                    Log.getStackTraceString(t)));

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.clear();
                    infoList.addAll(engine.makeTrackerInfoList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.stream().toList());
                        emitter.setDisposable(d);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<PeerInfo>> makePeersInfoFlowable(String id) {
        return Flowable.create((emitter) -> {
            var infoList = new HashSet<PeerInfo>();

            var d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                var newInfoList = engine.makePeerInfoList(id);
                                if (infoList.size() != newInfoList.size() || !infoList.containsAll(newInfoList)) {
                                    infoList.clear();
                                    infoList.addAll(newInfoList);
                                    if (!emitter.isCancelled()) {
                                        emitter.onNext(newInfoList);
                                    }
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting peers info for torrent " + id + " error: " +
                                    Log.getStackTraceString(t)));

            if (!emitter.isCancelled()) {
                var t = new Thread(() -> {
                    infoList.clear();
                    infoList.addAll(engine.makePeerInfoList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.stream().toList());
                        emitter.setDisposable(d);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<boolean[]> makePiecesFlowable(String id) {
        return Flowable.create((emitter) -> {
            final AtomicReference<boolean[]> infoList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                boolean[] newInfoList = engine.getPieces(id);
                                boolean[] oldInfoList = infoList.get();
                                if (!Arrays.equals(oldInfoList, newInfoList)) {
                                    infoList.set(newInfoList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newInfoList);
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting pieces for torrent " + id + " error: " +
                                    Log.getStackTraceString(t)));

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.set(engine.getPieces(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.get());
                        emitter.setDisposable(d);
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<String> makeTorrentsDeletedFlowable() {
        return Flowable.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentRemoved(@NonNull String id) {
                    if (!emitter.isCancelled())
                        emitter.onNext(id);
                }
            };

            if (!emitter.isCancelled()) {
                engine.addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        engine.removeListener(listener)));
            }

        }, BackpressureStrategy.DROP);
    }

    private Flowable<SessionStats> makeSessionStatsFlowable() {
        return Flowable.create((emitter) -> {
            final AtomicReference<SessionStats> stats = new AtomicReference<>();

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onSessionStats(@NonNull SessionStats newStats) {
                    SessionStats oldStats = stats.get();
                    if (!newStats.equals(oldStats)) {
                        stats.set(newStats);
                        if (!emitter.isCancelled())
                            emitter.onNext(newStats);
                    }
                }
            };

            if (!emitter.isCancelled()) {
                engine.addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        engine.removeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }

    private Single<SessionStats> makeSessionStatsSingle() {
        return Single.create((emitter) -> {
            if (!emitter.isDisposed()) {
                TorrentEngineListener listener = new TorrentEngineListener() {
                    @Override
                    public void onSessionStats(@NonNull SessionStats newStats) {
                        if (!emitter.isDisposed()) {
                            emitter.onSuccess(newStats);
                        }
                    }
                };

                if (!emitter.isDisposed()) {
                    engine.addListener(listener);
                    emitter.setDisposable(Disposables.fromAction(() ->
                            engine.removeListener(listener)));
                }
            }
        });
    }
}
