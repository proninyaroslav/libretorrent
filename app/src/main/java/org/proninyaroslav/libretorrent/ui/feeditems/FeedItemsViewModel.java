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

package org.proninyaroslav.libretorrent.ui.feeditems;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.service.FeedFetcherWorker;

import java.util.Collections;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

public class FeedItemsViewModel extends AndroidViewModel
{
    private FeedRepository repo;
    private long feedId;
    private BehaviorSubject<Boolean> refreshStatus = BehaviorSubject.create();
    private CompositeDisposable disposables = new CompositeDisposable();

    public FeedItemsViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getFeedRepository(application);
        feedId = -1;
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        disposables.clear();
    }

    public void clearData()
    {
        disposables.clear();
        feedId = -1;
    }

    public void setFeedId(long feedId)
    {
        this.feedId = feedId;
    }

    public Flowable<List<FeedItem>> observeItemsByFeedId()
    {
        return repo.observeItemsByFeedId(feedId);
    }

    public Single<List<FeedItem>> getItemsByFeedIdSingle()
    {
        return repo.getItemsByFeedIdSingle(feedId);
    }

    public Observable<Boolean> observeRefreshStatus()
    {
        return refreshStatus;
    }

    public void refreshChannel()
    {
        if (feedId == -1)
            return;

        Data data = new Data.Builder()
                .putString(FeedFetcherWorker.TAG_ACTION, FeedFetcherWorker.ACTION_FETCH_CHANNEL)
                .putLong(FeedFetcherWorker.TAG_CHANNEL_ID, feedId)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FeedFetcherWorker.class)
                .setInputData(data)
                .build();

        runFetchWorker(work);
    }

    public void markAllAsRead()
    {
        if (feedId == -1)
            return;

        disposables.add(Completable.fromRunnable(() -> repo.markAsReadByFeedId(Collections.singletonList(feedId))).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void markAsRead(@NonNull String itemId)
    {
        disposables.add(Completable.fromRunnable(() -> repo.markAsRead(itemId))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void markAsUnread(@NonNull String itemId)
    {
        disposables.add(Completable.fromRunnable(() -> repo.markAsUnread(itemId))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    private void runFetchWorker(WorkRequest work)
    {
        refreshStatus.onNext(true);

        WorkManager.getInstance(getApplication()).enqueue(work);

        WorkManager.getInstance(getApplication()).getWorkInfoByIdLiveData(work.getId())
                .observeForever(this::observeWorkResult);
    }

    private void observeWorkResult(WorkInfo info)
    {
        boolean finished = info.getState().isFinished();
        if (finished)
            WorkManager.getInstance(getApplication()).getWorkInfoByIdLiveData(info.getId())
                    .removeObserver(this::observeWorkResult);

        refreshStatus.onNext(!finished);
    }
}
