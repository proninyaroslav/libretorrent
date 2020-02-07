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

package org.proninyaroslav.libretorrent.ui.addfeed;

import android.app.Application;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.service.FeedFetcherWorker;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AddFeedViewModel extends AndroidViewModel
{
    public AddFeedMutableParams mutableParams = new AddFeedMutableParams();
    public ObservableBoolean showClipboardButton = new ObservableBoolean();
    private Mode mode;
    private FeedRepository repo;
    private CompositeDisposable disposables = new CompositeDisposable();

    public enum Mode
    {
        ADD, EDIT
    }

    public AddFeedViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getFeedRepository(application);
        mode = Mode.ADD;
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        disposables.clear();
    }

    public Mode getMode()
    {
        return mode;
    }

    public void initAddMode(@NonNull Uri uri)
    {
        mode = Mode.ADD;
        /* TODO: files support */
        mutableParams.setUrl(uri.toString());
    }

    public void initAddModeFromClipboard()
    {
        List<CharSequence> clipboard = Utils.getClipboardText(getApplication());
        if (clipboard.isEmpty())
            return;

        String firstItem = clipboard.get(0).toString();
        String c = firstItem.toLowerCase();
        if (c.startsWith(Utils.HTTP_PREFIX))
            initAddMode(Uri.parse(firstItem));
    }

    public void initEditMode(long feedId)
    {
        mode = Mode.EDIT;
        mutableParams.setFeedId(feedId);
        fetchParams();
    }

    public boolean addChannel()
    {
        long id = applyParams(true);
        refreshChannel(id);

        return id != -1;
    }

    public boolean updateChannel()
    {
        long id = applyParams(false);
        refreshChannel(id);

        return id != -1;
    }

    public boolean deleteChannel()
    {
        long feedId = mutableParams.getFeedId();
        if (feedId == -1)
            return false;

        /* Sync wait deleting */
        try {
            Thread t = new Thread(() -> {
                FeedChannel channel = repo.getFeedById(feedId);
                if (channel != null)
                    repo.deleteFeed(channel);
            });
            t.start();
            t.join();

        } catch (InterruptedException e) {
            return false;
        }

        return true;
    }

    private void fetchParams()
    {
        long feedId = mutableParams.getFeedId();
        if (feedId == -1)
            return;

        disposables.add(repo.getFeedByIdSingle(feedId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter((channel) -> channel != null)
                .subscribe((channel) -> {
                    mutableParams.setUrl(channel.url);
                    mutableParams.setName(channel.name);
                    mutableParams.setAutoDownload(channel.autoDownload);
                    mutableParams.setFilter(channel.filter);
                    mutableParams.setRegexFilter(channel.isRegexFilter);
                }));
    }

    private long applyParams(boolean newChannel)
    {
        long feedId = mutableParams.getFeedId();
        if (!newChannel && feedId == -1)
            return -1;
        String url = mutableParams.getUrl();
        if (TextUtils.isEmpty(url))
            return -1;

        FeedChannel channel = new FeedChannel(url,
                mutableParams.getName(),
                0,
                mutableParams.isAutoDownload(),
                mutableParams.getFilter(),
                mutableParams.isRegexFilter(),
                null);
        if (!newChannel)
            channel.id = feedId;

        /* TODO: maybe rewrite to WorkManager */
        /* Sync wait inserting/updating */
        long[] retId = new long[]{-1};
        try {
            Thread t = new Thread(() -> {
                if (newChannel)
                    retId[0] = repo.addFeed(channel);
                else if (repo.updateFeed(channel) == 1)
                    retId[0] = feedId;
            });
            t.start();
            t.join();

        } catch (InterruptedException e) {
            return retId[0];
        }

        return retId[0];
    }

    private void refreshChannel(long feedId)
    {
        if (feedId == -1)
            return;

        Data data = new Data.Builder()
                .putString(FeedFetcherWorker.TAG_ACTION, FeedFetcherWorker.ACTION_FETCH_CHANNEL)
                .putLong(FeedFetcherWorker.TAG_CHANNEL_ID, feedId)
                .putBoolean(FeedFetcherWorker.TAG_NO_AUTO_DOWNLOAD, mutableParams.isNotDownloadImmediately())
                .build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FeedFetcherWorker.class)
                .setInputData(data)
                .build();

        WorkManager.getInstance(getApplication()).enqueue(work);
    }
}
