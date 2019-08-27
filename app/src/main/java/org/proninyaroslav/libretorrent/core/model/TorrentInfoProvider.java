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

import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Consumer;

/*
 * Provides runtime information about torrent, which isn't saved to the database.
 */

public class TorrentInfoProvider
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentInfoProvider.class.getSimpleName();

    private static final int GET_INFO_SYNC_TIME = 1000; /* ms */
    private static final int GET_SESSION_STATS_SYNC_TIME = 3000; /* ms */

    private static TorrentInfoProvider INSTANCE;
    private TorrentEngine engine;

    public static TorrentInfoProvider getInstance(@NonNull TorrentEngine engine)
    {
        if (INSTANCE == null) {
            synchronized (TorrentInfoProvider.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentInfoProvider(engine);
            }
        }
        return INSTANCE;
    }

    private TorrentInfoProvider(TorrentEngine engine)
    {
        this.engine = engine;
    }

    public Flowable<TorrentInfo> observeInfo(@NonNull String id)
    {
        return makeInfoFlowable(id);
    }

    public Flowable<List<TorrentInfo>> observeInfoList()
    {
        return makeInfoListFlowable();
    }

    public Single<List<TorrentInfo>> getInfoListSingle()
    {
        return makeInfoListSingle();
    }

    public Flowable<AdvancedTorrentInfo> observeAdvancedInfo(@NonNull String id)
    {
        return makeAdvancedInfoFlowable(id);
    }

    public Flowable<List<TrackerInfo>> observeTrackersInfo(@NonNull String id)
    {
        return makeTrackersInfoFlowable(id);
    }

    public Flowable<List<PeerInfo>> observePeersInfo(@NonNull String id)
    {
        return makePeersInfoFlowable(id);
    }

    public Flowable<boolean[]> observePiecesInfo(@NonNull String id)
    {
        return makePiecesFlowable(id);
    }

    public Flowable<String> observeTorrentsDeleted()
    {
        return makeTorrentsDeletedFlowable();
    }

    public Flowable<SessionStats> observeSessionStats()
    {
        return makeSessionStatsFlowable();
    }

    private Flowable<TorrentInfo> makeInfoFlowable(String id)
    {
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
                public void onTorrentStateChanged(@NonNull String torrentId)
                {
                    try {
                        handleEvent.accept(torrentId);

                    } catch ( Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onTorrentPaused(@NotNull String torrentId)
                {
                    try {
                        handleEvent.accept(torrentId);

                    } catch ( Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onRestoreSessionError(@NonNull String torrentId)
                {
                    try {
                        handleEvent.accept(torrentId);

                    } catch ( Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
                    }
                }

                @Override
                public void onTorrentError(@NonNull String torrentId, String errorMsg)
                {
                    try {
                        handleEvent.accept(torrentId);

                    } catch ( Exception e) {
                        if (!emitter.isCancelled())
                            emitter.onError(e);
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
                        emitter.setDisposable(Disposables.fromAction(() ->
                                engine.removeListener(listener)));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<TorrentInfo>> makeInfoListFlowable()
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<TorrentInfo>> infoList = new AtomicReference<>();

            Runnable handleInfo = () -> {
                List<TorrentInfo> newInfoList = engine.makeInfoListSync();
                List<TorrentInfo> oldInfoList = infoList.get();
                if (oldInfoList == null || oldInfoList.size() != newInfoList.size() ||
                    !oldInfoList.containsAll(newInfoList)) {
                    infoList.set(newInfoList);
                    if (!emitter.isCancelled())
                        emitter.onNext(newInfoList);
                }
            };

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(@NonNull String torrentId)
                {
                    handleInfo.run();
                }

                @Override
                public void onTorrentPaused(@NotNull String torrentId)
                {
                    handleInfo.run();
                }

                @Override
                public void onTorrentRemoved(@NonNull String torrentId)
                {
                    handleInfo.run();
                }

                @Override
                public void onRestoreSessionError(@NonNull String torrentId)
                {
                    handleInfo.run();
                }

                @Override
                public void onTorrentError(@NonNull String torrentId, String errorMsg)
                {
                    handleInfo.run();
                }
            };

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.set(engine.makeInfoListSync());
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.get());
                        engine.addListener(listener);
                        emitter.setDisposable(Disposables.fromAction(() ->
                                engine.removeListener(listener)));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Single<List<TorrentInfo>> makeInfoListSingle()
    {
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

    private Flowable<AdvancedTorrentInfo> makeAdvancedInfoFlowable(String id)
    {
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
                            (Throwable t) -> {
                                Log.e(TAG, "Getting advanced info for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    AdvancedTorrentInfo s = engine.makeAdvancedInfoSync(id);
                    info.set(s);
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        if (s != null)
                            emitter.onNext(s);
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<TrackerInfo>> makeTrackersInfoFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<TrackerInfo>> infoList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                List<TrackerInfo> newInfoList = engine.makeTrackerInfoList(id);
                                List<TrackerInfo> oldInfoList = infoList.get();
                                if (oldInfoList == null || oldInfoList.size() != newInfoList.size() ||
                                    !oldInfoList.containsAll(newInfoList)) {
                                    infoList.set(newInfoList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newInfoList);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting trackers info for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.set(engine.makeTrackerInfoList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.get());
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<PeerInfo>> makePeersInfoFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<PeerInfo>> infoList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_INFO_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                List<PeerInfo> newInfoList = engine.makePeerInfoList(id);
                                List<PeerInfo> oldInfoList = infoList.get();
                                if (oldInfoList == null || oldInfoList.size() != newInfoList.size() ||
                                    !oldInfoList.containsAll(newInfoList)) {
                                    infoList.set(newInfoList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newInfoList);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting peers info for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.set(engine.makePeerInfoList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.get());
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<boolean[]> makePiecesFlowable(String id)
    {
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
                            (Throwable t) -> {
                                Log.e(TAG, "Getting pieces for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    infoList.set(engine.getPieces(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(infoList.get());
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<String> makeTorrentsDeletedFlowable()
    {
        return Flowable.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentRemoved(@NonNull String id)
                {
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

    private Flowable<SessionStats> makeSessionStatsFlowable()
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<SessionStats> stats = new AtomicReference<>();

            Disposable d = Observable.interval(GET_SESSION_STATS_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                SessionStats newStats = engine.makeSessionStats();
                                SessionStats oldStats = stats.get();
                                if (newStats != null && !newStats.equals(oldStats)) {
                                    stats.set(newStats);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newStats);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting session stats error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    SessionStats s = engine.makeSessionStats();
                    stats.set(s);
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        if (s != null)
                            emitter.onNext(s);
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }
}
