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

package org.proninyaroslav.libretorrent.core.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.proninyaroslav.libretorrent.core.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.entity.FeedItem;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.Flowable;
import io.reactivex.Single;

public class FeedRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedRepository.class.getSimpleName();

    public static final String SERIALIZE_FILE_FORMAT = "json";
    public static final String SERIALIZE_MIME_TYPE = "application/json";
    public static final String FILTER_SEPARATOR = "\\|";

    private static FeedRepository INSTANCE;
    private AppDatabase db;

    public static FeedRepository getInstance(AppDatabase db)
    {
        if (INSTANCE == null) {
            synchronized (FeedRepository.class) {
                if (INSTANCE == null)
                    INSTANCE = new FeedRepository(db);
            }
        }
        return INSTANCE;
    }

    private FeedRepository(AppDatabase db)
    {
        this.db = db;
    }

    public long addFeed(@NonNull FeedChannel channel)
    {
        return db.feedDao().addFeed(channel);
    }

    public long[] addFeeds(@NonNull List<FeedChannel> feeds)
    {
        return db.feedDao().addFeeds(feeds);
    }

    public int updateFeed(@NonNull FeedChannel channel)
    {
        return db.feedDao().updateFeed(channel);
    }

    public void deleteFeed(@NonNull FeedChannel channel)
    {
        db.feedDao().deleteFeed(channel);
    }

    public void deleteFeeds(@NonNull List<FeedChannel> feeds)
    {
        db.feedDao().deleteFeeds(feeds);
    }

    public FeedChannel getFeedById(long id)
    {
        return db.feedDao().getFeedById(id);
    }

    public Single<FeedChannel> getFeedByIdSingle(long id)
    {
        return db.feedDao().getFeedByIdSingle(id);
    }

    public Flowable<List<FeedChannel>> observeAllFeeds()
    {
        return db.feedDao().observeAllFeeds();
    }

    public List<FeedChannel> getAllFeeds()
    {
        return db.feedDao().getAllFeeds();
    }

    public Single<List<FeedChannel>> getAllFeedsSingle()
    {
        return db.feedDao().getAllFeedsSingle();
    }

    public void serializeAllFeeds(@NonNull Context context, @NonNull Uri file) throws IOException
    {
        FileUtils.write(context, new Gson().toJson(getAllFeeds()), Charset.forName("UTF-8"), file);
    }

    public List<FeedChannel> deserializeFeeds(@NonNull Context context, @NonNull Uri file) throws IOException
    {
        List<FeedChannel> feeds;
        ContentResolver resolver = context.getContentResolver();
        ParcelFileDescriptor fd = resolver.openFileDescriptor(file, "rw");

        try (FileInputStream fin = new FileInputStream(fd.getFileDescriptor());
             InputStreamReader reader = new InputStreamReader(fin, Charset.forName("UTF-8"))) {
            feeds = new Gson().fromJson(reader, new TypeToken<ArrayList<FeedChannel>>(){}.getType());
        }

        return feeds;
    }

    public void addItems(@NonNull List<FeedItem> items)
    {
        db.feedDao().addItems(items);
    }

    public void deleteItemsOlderThan(long keepDateBorderTime)
    {
        db.feedDao().deleteItemsOlderThan(keepDateBorderTime);
    }

    public void markAsRead(@NonNull String itemId)
    {
        db.feedDao().markAsRead(itemId);
    }

    public void markAsUnread(@NonNull String itemId)
    {
        db.feedDao().markAsUnread(itemId);
    }

    public void markAsReadByFeedId(List<Long> feedId)
    {
        db.feedDao().markAsReadByFeedId(feedId);
    }

    public Flowable<List<FeedItem>> observeItemsByFeedId(long feedId)
    {
        return db.feedDao().observeItemsByFeedId(feedId);
    }

    public Single<List<FeedItem>> getItemsByFeedIdSingle(long feedId)
    {
        return db.feedDao().getItemsByFeedIdSingle(feedId);
    }

    public List<String> getItemsIdByFeedId(long feedId)
    {
        return db.feedDao().getItemsIdByFeedId(feedId);
    }

    public List<String> findItemsExistingTitles(@NonNull List<String> titles)
    {
        return db.feedDao().findItemsExistingTitles(titles);
    }

    public List<FeedItem> getItemsById(@NonNull String... itemsId)
    {
        return db.feedDao().getItemsById(itemsId);
    }
}
