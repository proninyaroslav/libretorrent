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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.service.FeedFetcherWorker;

import java.io.IOException;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

public class FeedViewModel extends AndroidViewModel
{
    private FeedRepository repo;
    private BehaviorSubject<Boolean> refreshStatus = BehaviorSubject.create();
    private CompositeDisposable disposables = new CompositeDisposable();
    public Throwable errorReport;

    public FeedViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getFeedRepository(application);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        disposables.clear();
    }

    public Single<List<FeedChannel>> getAllFeedsSingle()
    {
        return repo.getAllFeedsSingle();
    }

    public Flowable<List<FeedChannel>> observerAllFeeds()
    {
        return repo.observeAllFeeds();
    }

    public void deleteFeeds(@NonNull List<FeedChannel> feeds)
    {
        disposables.add(Completable.fromRunnable(() -> repo.deleteFeeds(feeds))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    public boolean copyFeedUrlToClipboard(@NonNull FeedChannel channel)
    {
        ClipboardManager clipboard = (ClipboardManager)getApplication().getSystemService(Activity.CLIPBOARD_SERVICE);
        if (clipboard == null)
            return false;

        ClipData clip;
        clip = ClipData.newPlainText("URL", channel.url);
        clipboard.setPrimaryClip(clip);

        return true;
    }

    public void refreshFeeds(@NonNull long[] feedIdList)
    {
        Data data = new Data.Builder()
                .putString(FeedFetcherWorker.TAG_ACTION,FeedFetcherWorker.ACTION_FETCH_CHANNEL_LIST)
                .putLongArray(FeedFetcherWorker.TAG_CHANNEL_ID_LIST, feedIdList)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FeedFetcherWorker.class)
                .setInputData(data)
                .build();

        runFetchWorker(work);
    }

    public void refreshAllFeeds()
    {
        Data data = new Data.Builder()
                .putString(FeedFetcherWorker.TAG_ACTION,FeedFetcherWorker.ACTION_FETCH_ALL_CHANNELS)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FeedFetcherWorker.class)
                .setInputData(data)
                .build();

        runFetchWorker(work);
    }

    public Observable<Boolean> observeRefreshStatus()
    {
        return refreshStatus;
    }

    public void saveFeedsSync(@NonNull Uri file) throws IOException, UnknownUriException
    {
        repo.serializeAllFeeds(file);
    }

    public long[] restoreFeedsSync(@NonNull Uri file) throws IOException, UnknownUriException
    {
        List<FeedChannel> feeds = repo.deserializeFeeds(file);

        return repo.addFeeds(feeds);
    }

    public void markAsReadFeeds(@NonNull List<Long> feedIdList)
    {
        disposables.add(Completable.fromRunnable(() -> repo.markAsReadByFeedId(feedIdList)).subscribeOn(Schedulers.io())
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
