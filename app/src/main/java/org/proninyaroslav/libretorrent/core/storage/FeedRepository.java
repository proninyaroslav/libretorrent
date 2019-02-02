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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.proninyaroslav.libretorrent.core.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.entity.FeedItem;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.Flowable;

public class FeedRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedRepository.class.getSimpleName();

    public static final String SERIALIZE_FILE_FORMAT = "json";
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

    public void addFeed(@NonNull FeedChannel feed)
    {
        db.feedDao().addFeed(feed);
    }

    public void addFeeds(@NonNull List<FeedChannel> feeds)
    {
        db.feedDao().addFeeds(feeds);
    }

    public void updateFeed(@NonNull FeedChannel feed)
    {
        db.feedDao().updateFeed(feed);
    }

    public void deleteFeed(@NonNull FeedChannel feed)
    {
        db.feedDao().deleteFeed(feed);
    }

    public Flowable<FeedChannel> observeFeedByUrl(@NonNull String url)
    {
        return db.feedDao().observeFeedByUrl(url);
    }

    @NonNull
    public Flowable<List<FeedChannel>> observeAllFeeds()
    {
        return db.feedDao().observeAllFeeds();
    }

    public static String serializeChannels(@NonNull ArrayList<FeedChannel> channels)
    {
        return new Gson().toJson(channels);
    }

    public static ArrayList<FeedChannel> deserializeChannels(@NonNull Reader reader)
    {
        return new Gson().fromJson(reader, new TypeToken<ArrayList<FeedChannel>>(){}.getType());
    }

    public void addItem(@NonNull FeedItem item)
    {
        db.feedDao().addItem(item);
    }

    public void addItems(@NonNull List<FeedItem> items)
    {
        db.feedDao().addItems(items);
    }

    public void deleteItems(@NonNull List<FeedItem> items)
    {
        db.feedDao().deleteItems(items);
    }

    public void deleteFeedItems(@NonNull String feedUrl)
    {
        db.feedDao().deleteFeedItems(feedUrl);
    }

    public void deleteItemsOlderThan(long keepDateBorderTime)
    {
        db.feedDao().deleteItemsOlderThan(keepDateBorderTime);
    }

    public void markAsRead(long itemId)
    {
        db.feedDao().markAsRead(itemId);
    }

    public void markAsUnread(long itemId)
    {
        db.feedDao().markAsUnread(itemId);
    }

    public void markAllAsRead()
    {
        db.feedDao().markAllAsRead();
    }

    public Flowable<List<FeedItem>> observeItemsByFeedUrl(@NonNull String feedUrl)
    {
        return db.feedDao().observeItemsByFeedUrl(feedUrl);
    }
}
