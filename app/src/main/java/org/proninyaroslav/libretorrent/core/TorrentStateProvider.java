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

import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;

import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Function;

/*
 * Provides runtime information about torrent, which isn't saved to the database.
 */

public class TorrentStateProvider
{
    public Flowable<BasicStateParcel> observeBasicState(@NonNull String torrentId)
    {
        Maybe<BasicStateParcel> maybe = Maybe.fromCallable(() ->
                TorrentHelper.makeBasicStateParcel(torrentId));

        return createBasicStateFlowable(torrentId)
                .flatMapMaybe((Function<Object, MaybeSource<BasicStateParcel>>) o -> maybe);
    }

    public Single<TorrentMetaInfo> getFetchedMetadata(@NonNull String hash)
    {
        return createMagnetLoadedFlowable(hash);
    }

    public AdvanceStateParcel getAdvanceState(@NonNull String torrentId)
    {
        return TorrentHelper.makeAdvancedState(torrentId);
    }

    public List<PeerStateParcel> getPeerStateList(@NonNull String torrentId)
    {
        return TorrentHelper.makePeerStateParcelList(torrentId);
    }

    public List<TrackerStateParcel> getTrackerStateList(@NonNull String torrentId)
    {
        return TorrentHelper.makeTrackerStateParcelList(torrentId);
    }

    private static final Object NOP = new Object();

    private Flowable<Object> createBasicStateFlowable(String torrentId)
    {
        return Flowable.create((emitter) -> {
            TorrentSessionListener listener = new TorrentSessionListener() {
                @Override
                void onTorrentStateChanged(String id)
                {
                    if (id == null || !id.equals(torrentId))
                        return;

                    if (!emitter.isCancelled())
                        emitter.onNext(NOP);
                }

                @Override
                void onTorrentPaused(String id)
                {
                    if (id == null || !id.equals(torrentId))
                        return;

                    if (!emitter.isCancelled())
                        emitter.onNext(NOP);
                }
            };
            if (!emitter.isCancelled()) {
                TorrentEngineOld.getInstance().addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        TorrentEngineOld.getInstance().removeListener(listener)));
            }

            /* Emit once to avoid missing any data and also easy chaining */
            if (!emitter.isCancelled())
                emitter.onNext(NOP);

        }, BackpressureStrategy.LATEST);
    }

    private Single<TorrentMetaInfo> createMagnetLoadedFlowable(String hash)
    {
        return Single.create((emitter) -> {
            TorrentSessionListener listener = new TorrentSessionListener() {
                @Override
                void onMagnetLoaded(String h, byte[] bencode)
                {
                    if (h == null || !h.equals(hash))
                        return;

                    if (!emitter.isDisposed()) {
                        TorrentMetaInfo info;
                        try {
                            info = new TorrentMetaInfo(bencode);

                        } catch (DecodeException e) {
                            emitter.onError(e);
                            return;
                        }
                        emitter.onSuccess(info);
                    }
                }
            };
            if (!emitter.isDisposed()) {
                TorrentEngineOld.getInstance().addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        TorrentEngineOld.getInstance().removeListener(listener)));
            }
        });
    }
}
