/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

public class NavBarFragmentViewModel extends AndroidViewModel {
    private final FeedRepository feedRepo;

    public NavBarFragmentViewModel(@NonNull Application application) {
        super(application);

        feedRepo = RepositoryHelper.getFeedRepository(application);
    }

    public Flowable<Integer> observeUnreadFeedsCount() {
        return feedRepo.observeUnreadFeedIdList().map(List::size);
    }
}
