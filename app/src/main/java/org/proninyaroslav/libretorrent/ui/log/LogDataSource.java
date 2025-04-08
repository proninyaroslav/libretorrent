/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava2.RxPagingSource;

import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.logger.Logger;

import java.util.Collections;

import io.reactivex.Single;

class LogDataSource extends RxPagingSource<Integer, LogEntry> {
    private final Logger logger;

    LogDataSource(@NonNull Logger logger) {
        this.logger = logger;
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, LogEntry> pagingState) {
        return Math.max((pagingState.getAnchorPosition() == null ? 0 : pagingState.getAnchorPosition()) -
                pagingState.getConfig().initialLoadSize / 2, 0);
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, LogEntry>> loadSingle(@NonNull LoadParams<Integer> loadParams) {
        return Single.fromCallable(() -> {
            boolean paused = false;
            if (!logger.isPaused()) {
                logger.pause();
                paused = true;
            }

            int endOfPaginationOffset = logger.getNumEntries();
            int offset = 0;
            if (loadParams.getKey() != null && loadParams.getKey() >= 0) {
                offset = loadParams.getKey() > endOfPaginationOffset
                        ? endOfPaginationOffset
                        : loadParams.getKey();
            }
            var limit = loadParams.getLoadSize();

            if (endOfPaginationOffset == 0) {
                return new LoadResult.Page<>(Collections.emptyList(), null, null);
            }

            try {
                var entries = logger.getEntries(offset, limit);
                var endOfPaginationReached = offset + limit >= endOfPaginationOffset || entries.isEmpty();
                return new LoadResult.Page<>(
                        entries,
                        offset == 0 ? null : offset - limit,
                        endOfPaginationReached ? null : offset + limit
                );
            } finally {
                if (paused) {
                    logger.resume();
                }
            }
        });
    }
}
