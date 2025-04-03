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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.libtorrent4j.Pair;
import org.libtorrent4j.swig.create_flags_t;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.BehaviorSubject;

public class TorrentBuilder {
    private final Context context;
    private final org.libtorrent4j.TorrentBuilder builder;
    private Predicate<String> fileNameFilter;
    private final BehaviorSubject<Progress> progress = BehaviorSubject.create();

    public record Tracker(@NonNull String url, int tier) {
    }

    public record Progress(int piece, int numPieces) {
    }

    public enum TorrentVersion {
        V1_ONLY(org.libtorrent4j.TorrentBuilder.V1_ONLY),
        V2_ONLY(org.libtorrent4j.TorrentBuilder.V2_ONLY),
        HYBRID(create_flags_t.from_int(0));

        private final create_flags_t flag;

        final create_flags_t flag() {
            return this.flag;
        }

        TorrentVersion(create_flags_t flag) {
            this.flag = flag;
        }
    }

    public TorrentBuilder(@NonNull Context context) {
        this.context = context;
        builder = new org.libtorrent4j.TorrentBuilder();
    }

    public TorrentBuilder setSeedPath(Uri path) throws UnknownUriException {
        String seedPathStr = SystemFacadeHelper.getFileSystemFacade(context)
                .makeFileSystemPath(path);
        builder.path(new File(seedPathStr));

        return this;
    }

    /*
     * The size of each piece in bytes. It must
     * be a multiple of 16 kiB. If a piece size of 0 is specified, a
     * piece size will be calculated such that the torrent file is roughly 40 kB
     */

    public TorrentBuilder setPieceSize(int size) {
        builder.pieceSize(size);

        return this;
    }

    public TorrentBuilder addTrackers(@NonNull List<Tracker> trackers) {
        ArrayList<Pair<String, Integer>> list = new ArrayList<>();
        for (Tracker tracker : trackers)
            list.add(new Pair<>(tracker.url, tracker.tier));

        builder.addTrackers(list);

        return this;
    }

    public TorrentBuilder addUrlSeeds(@NonNull List<String> urls) {
        builder.addUrlSeeds(urls);

        return this;
    }

    public TorrentBuilder setAsPrivate(boolean isPrivate) {
        builder.setPrivate(isPrivate);

        return this;
    }

    public TorrentBuilder setCreator(String creator) {
        builder.creator(creator);

        return this;
    }

    public TorrentBuilder setComment(String comment) {
        builder.comment(comment);

        return this;
    }

    public TorrentBuilder setFileNameFilter(Predicate<String> fileNameFilter) {
        this.fileNameFilter = fileNameFilter;

        return this;
    }

    public TorrentBuilder setTorrentVersion(TorrentVersion version) {
        builder.flags(builder.flags().or_(version.flag()));

        return this;
    }

    public Observable<Progress> observeProgress() {
        return progress;
    }

    public Single<byte[]> build() {
        subscribeProgress();

        return Single.fromCallable(() -> builder.generate().entry().bencode());
    }

    private void subscribeProgress() {
        builder.listener(new org.libtorrent4j.TorrentBuilder.Listener() {
            @Override
            public boolean accept(String filename) {
                try {
                    return fileNameFilter == null || fileNameFilter.test(filename);

                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void progress(int pieceIndex, int numPieces) {
                progress.onNext(new Progress(pieceIndex, numPieces));
            }
        });
    }
}
