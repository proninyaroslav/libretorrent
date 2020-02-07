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

import android.net.Uri;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;

import java.io.IOException;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

public interface FeedRepository
{
    String getSerializeFileFormat();

    String getSerializeMimeType();

    String getFilterSeparator();

    long addFeed(@NonNull FeedChannel channel);

    long[] addFeeds(@NonNull List<FeedChannel> feeds);

    int updateFeed(@NonNull FeedChannel channel);

    void deleteFeed(@NonNull FeedChannel channel);

    void deleteFeeds(@NonNull List<FeedChannel> feeds);

    FeedChannel getFeedById(long id);

    Single<FeedChannel> getFeedByIdSingle(long id);

    Flowable<List<FeedChannel>> observeAllFeeds();

    List<FeedChannel> getAllFeeds();

    Single<List<FeedChannel>> getAllFeedsSingle();

    void serializeAllFeeds(@NonNull Uri file) throws IOException;

    List<FeedChannel> deserializeFeeds(@NonNull Uri file) throws IOException;

    void addItems(@NonNull List<FeedItem> items);

    void deleteItemsOlderThan(long keepDateBorderTime);

    void markAsRead(@NonNull String itemId);

    void markAsUnread(@NonNull String itemId);

    void markAsReadByFeedId(List<Long> feedId);

    Flowable<List<FeedItem>> observeItemsByFeedId(long feedId);

    Single<List<FeedItem>> getItemsByFeedIdSingle(long feedId);

    List<String> getItemsIdByFeedId(long feedId);

    List<String> findItemsExistingTitles(@NonNull List<String> titles);

    List<FeedItem> getItemsById(@NonNull String... itemsId);
}
