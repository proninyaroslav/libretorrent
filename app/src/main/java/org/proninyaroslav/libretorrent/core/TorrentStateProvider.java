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

package org.proninyaroslav.libretorrent.core;

import android.util.Log;

import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

/*
 * Provides runtime information about torrent, which isn't saved to the database.
 */

public class TorrentStateProvider
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentStateProvider.class.getSimpleName();

    private static final int GET_STATE_SYNC_TIME = 1000; /* ms */

    private static TorrentStateProvider INSTANCE;
    private TorrentEngine engine;

    public static TorrentStateProvider getInstance(@NonNull TorrentEngine engine)
    {
        if (INSTANCE == null) {
            synchronized (TorrentStateProvider.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentStateProvider(engine);
            }
        }
        return INSTANCE;
    }

    private TorrentStateProvider(TorrentEngine engine)
    {
        this.engine = engine;
    }

    public Flowable<BasicStateParcel> observeState(@NonNull String id)
    {
        return makeStateFlowable(id);
    }

    public Flowable<List<BasicStateParcel>> observeStateList()
    {
        return makeStateListFlowable();
    }

    public Flowable<AdvanceStateParcel> observeAdvancedState(@NonNull String id)
    {
        return makeAdvancedStateFlowable(id);
    }

    public Flowable<List<TrackerStateParcel>> observeTrackersState(@NonNull String id)
    {
        return makeTrackersStateFlowable(id);
    }

    public Flowable<List<PeerStateParcel>> observePeersState(@NonNull String id)
    {
        return makePeersStateFlowable(id);
    }

    public Flowable<boolean[]> observePiecesState(@NonNull String id)
    {
        return makePiecesFlowable(id);
    }

    private Flowable<BasicStateParcel> makeStateFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<BasicStateParcel> state = new AtomicReference<>();

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(String torrentId)
                {
                    if (!id.equals(torrentId))
                        return;

                    BasicStateParcel newState = engine.makeBasicStateSync(id);
                    BasicStateParcel oldState = state.get();
                    if (newState != null && !newState.equals(oldState)) {
                        state.set(newState);
                        if (!emitter.isCancelled())
                            emitter.onNext(newState);
                    }
                }
            };

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    BasicStateParcel s = engine.makeBasicStateSync(id);
                    state.set(s);
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

    private Flowable<List<BasicStateParcel>> makeStateListFlowable()
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<BasicStateParcel>> stateList = new AtomicReference<>();

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(String torrentId)
                {
                    List<BasicStateParcel> newStateList = engine.makeBasicStateListSync();
                    List<BasicStateParcel> oldStateList = stateList.get();
                    if (oldStateList == null || !oldStateList.containsAll(newStateList)) {
                        stateList.set(newStateList);
                        if (!emitter.isCancelled())
                            emitter.onNext(newStateList);
                    }
                }
            };

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    stateList.set(engine.makeBasicStateListSync());
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(stateList.get());
                        engine.addListener(listener);
                        emitter.setDisposable(Disposables.fromAction(() ->
                                engine.removeListener(listener)));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<AdvanceStateParcel> makeAdvancedStateFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<AdvanceStateParcel> state = new AtomicReference<>();

            Disposable d = Observable.interval(GET_STATE_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                AdvanceStateParcel newState = engine.makeAdvancedStateSync(id);
                                AdvanceStateParcel oldState = state.get();
                                if (newState != null && !newState.equals(oldState)) {
                                    state.set(newState);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newState);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting advanced state for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    AdvanceStateParcel s = engine.makeAdvancedStateSync(id);
                    state.set(s);
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

    private Flowable<List<TrackerStateParcel>> makeTrackersStateFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<TrackerStateParcel>> stateList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_STATE_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                List<TrackerStateParcel> newStateList = engine.makeTrackerStateParcelList(id);
                                List<TrackerStateParcel> oldStateList = stateList.get();
                                if (oldStateList == null || !oldStateList.containsAll(newStateList)) {
                                    stateList.set(newStateList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newStateList);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting trackers state for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    stateList.set(engine.makeTrackerStateParcelList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(stateList.get());
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }

    private Flowable<List<PeerStateParcel>> makePeersStateFlowable(String id)
    {
        return Flowable.create((emitter) -> {
            final AtomicReference<List<PeerStateParcel>> stateList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_STATE_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                List<PeerStateParcel> newStateList = engine.makePeerStateParcelList(id);
                                List<PeerStateParcel> oldStateList = stateList.get();
                                if (oldStateList == null || !oldStateList.containsAll(newStateList)) {
                                    stateList.set(newStateList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newStateList);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting peers state for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    stateList.set(engine.makePeerStateParcelList(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(stateList.get());
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
            final AtomicReference<boolean[]> stateList = new AtomicReference<>();

            Disposable d = Observable.interval(GET_STATE_SYNC_TIME, TimeUnit.MILLISECONDS)
                    .subscribe((__) -> {
                                boolean[] newStateList = engine.getPieces(id);
                                boolean[] oldStateList = stateList.get();
                                if (!Arrays.equals(oldStateList, newStateList)) {
                                    stateList.set(newStateList);
                                    if (!emitter.isCancelled())
                                        emitter.onNext(newStateList);
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting pieces for torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            });

            if (!emitter.isCancelled()) {
                Thread t = new Thread(() -> {
                    stateList.set(engine.getPieces(id));
                    if (!emitter.isCancelled()) {
                        /* Emit once to avoid missing any data and also easy chaining */
                        emitter.onNext(stateList.get());
                        emitter.setDisposable(Disposables.fromAction(d::dispose));
                    }
                });
                t.start();
            }

        }, BackpressureStrategy.LATEST);
    }
}
