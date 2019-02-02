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

package org.proninyaroslav.libretorrent.core.storage.dao;

import org.proninyaroslav.libretorrent.core.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.entity.FeedItem;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import io.reactivex.Flowable;

@Dao
public interface FeedDao
{
    String QUERY_GET_ALL_FEEDS = "SELECT * FROM FeedChannel";
    String QUERY_GET_FEED_BY_ID = "SELECT * FROM FeedChannel WHERE url = :url";
    String QUERY_DELETE_FEED_ITEMS = "DELETE FROM FeedItem WHERE feedUrl = :feedUrl";
    String QUERY_DELETE_ITEMS_OLDER_THAN = "DELETE FROM FeedItem WHERE fetchDate < :keepDateBorderTime";
    String QUERY_MARK_AS_READ = "UPDATE FeedItem SET read = 1 WHERE id = :itemId";
    String QUERY_MARK_AS_UNREAD = "UPDATE FeedItem SET read = 0 WHERE id = :itemId";
    String QUERY_MARK_ALL_AS_READ = "UPDATE FeedItem SET read = 1";
    String QUERY_GET_ITEMS_BY_FEED_URL = "SELECT * FROM FeedItem WHERE feedUrl = :feedUrl";

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addFeed(FeedChannel feed);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addFeeds(List<FeedChannel> feeds);

    @Update
    void updateFeed(FeedChannel feed);

    @Delete
    void deleteFeed(FeedChannel feed);

    @Query(QUERY_GET_FEED_BY_ID)
    Flowable<FeedChannel> observeFeedByUrl(String url);

    @Query(QUERY_GET_ALL_FEEDS)
    Flowable<List<FeedChannel>> observeAllFeeds();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addItem(FeedItem item);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addItems(List<FeedItem> items);

    @Delete
    void deleteItems(List<FeedItem> items);

    @Query(QUERY_DELETE_FEED_ITEMS)
    void deleteFeedItems(String feedUrl);

    @Query(QUERY_DELETE_ITEMS_OLDER_THAN)
    void deleteItemsOlderThan(long keepDateBorderTime);

    @Query(QUERY_MARK_AS_READ)
    void markAsRead(long itemId);

    @Query(QUERY_MARK_AS_UNREAD)
    void markAsUnread(long itemId);

    @Query(QUERY_MARK_ALL_AS_READ)
    void markAllAsRead();

    @Query(QUERY_GET_ITEMS_BY_FEED_URL)
    Flowable<List<FeedItem>> observeItemsByFeedUrl(String feedUrl);
}
