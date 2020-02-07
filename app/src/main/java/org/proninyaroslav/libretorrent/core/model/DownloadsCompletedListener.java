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

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;

import java.util.ArrayList;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposables;

/*
 * Emits an event if all torrents have switched from the download state to
 * the finish state (excluding already seeding torrents).
 */

class DownloadsCompletedListener
{
    private TorrentEngine engine;

    DownloadsCompletedListener(@NonNull TorrentEngine engine)
    {
        this.engine = engine;
    }

    public Completable listen()
    {
        return Completable.create((emitter) -> {
            ArrayList<String> torrentsInProgress = new ArrayList<>();

            Runnable eventHandler = () -> {
                if (!emitter.isDisposed() && torrentsInProgress.isEmpty())
                    emitter.onComplete();
            };

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentStateChanged(@NonNull String id,
                                                  @NonNull TorrentStateCode prevState,
                                                  @NonNull TorrentStateCode curState)
                {
                    if (curState == TorrentStateCode.DOWNLOADING)
                        torrentsInProgress.add(id);
                }

                @Override
                public void onTorrentFinished(@NonNull String id)
                {
                    if (torrentsInProgress.remove(id))
                        eventHandler.run();
                }

                @Override
                public void onTorrentRemoved(@NonNull String id)
                {
                    if (torrentsInProgress.remove(id))
                        eventHandler.run();
                }
            };

            if (!emitter.isDisposed()) {
                engine.addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        engine.removeListener(listener)));
            }
        });
    }
}
