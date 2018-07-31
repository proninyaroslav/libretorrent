/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.proninyaroslav.libretorrent.core.FeedChannel;
import org.proninyaroslav.libretorrent.core.FeedItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * The class provides a single access to the feed repository.
 */

public class FeedStorage
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedStorage.class.getSimpleName();

    private String[] allFeedsColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_FEED_NAME,
            DatabaseHelper.COLUMN_FEED_URL,
            DatabaseHelper.COLUMN_FEED_LAST_UPDATE,
            DatabaseHelper.COLUMN_FEED_AUTO_DOWNLOAD,
            DatabaseHelper.COLUMN_FEED_FILTER,
            DatabaseHelper.COLUMN_FEED_IS_REGEX_FILTER,
            DatabaseHelper.COLUMN_FETCH_ERROR
    };

    private String[] allFeedItemsColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL,
            DatabaseHelper.COLUMN_FEED_ITEM_TITLE,
            DatabaseHelper.COLUMN_FEED_ITEM_DOWNLOAD_URL,
            DatabaseHelper.COLUMN_FEED_ITEM_PUB_DATE,
            DatabaseHelper.COLUMN_FEED_ITEM_FETCH_DATE,
            DatabaseHelper.COLUMN_FEED_ITEM_READ,
            DatabaseHelper.COLUMN_FEED_ITEM_ARTICLE_URL
    };

    public static final String SERIALIZE_FILE_FORMAT = "json";
    public static final String FILTER_SEPARATOR = "\\|";

    private Context context;

    public FeedStorage(Context context)
    {
        this.context = context;
    }

    public boolean addChannel(FeedChannel channel)
    {
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_FEED_URL, channel.getUrl());
        values.put(DatabaseHelper.COLUMN_FEED_NAME, channel.getName());
        values.put(DatabaseHelper.COLUMN_FEED_LAST_UPDATE, channel.getLastUpdate());
        values.put(DatabaseHelper.COLUMN_FEED_AUTO_DOWNLOAD, (channel.isAutoDownload() ? 1 : 0));
        values.put(DatabaseHelper.COLUMN_FEED_FILTER, channel.getFilter());
        values.put(DatabaseHelper.COLUMN_FEED_IS_REGEX_FILTER, (channel.isRegexFilter() ? 1 : 0));
        values.put(DatabaseHelper.COLUMN_FETCH_ERROR, channel.getFetchError());

        return ConnectionManager.getDatabase(context).replace(DatabaseHelper.FEEDS_TABLE, null, values) >= 0;
    }

    public void addChannels(List<FeedChannel> channels)
    {
        if (channels == null)
            return;

        for (FeedChannel channel : channels) {
            ContentValues values = new ContentValues();

            values.put(DatabaseHelper.COLUMN_FEED_URL, channel.getUrl());
            values.put(DatabaseHelper.COLUMN_FEED_NAME, channel.getName());
            values.put(DatabaseHelper.COLUMN_FEED_LAST_UPDATE, channel.getLastUpdate());
            values.put(DatabaseHelper.COLUMN_FEED_AUTO_DOWNLOAD, (channel.isAutoDownload() ? 1 : 0));
            values.put(DatabaseHelper.COLUMN_FEED_FILTER, channel.getFilter());
            values.put(DatabaseHelper.COLUMN_FEED_IS_REGEX_FILTER, (channel.isRegexFilter() ? 1 : 0));
            values.put(DatabaseHelper.COLUMN_FETCH_ERROR, channel.getFetchError());

            SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);
            try {
                sqLiteDatabase.beginTransaction();
                sqLiteDatabase.replace(DatabaseHelper.FEEDS_TABLE, null, values);
                sqLiteDatabase.setTransactionSuccessful();

            } finally {
                sqLiteDatabase.endTransaction();
            }
        }
    }

    public void updateChannel(FeedChannel channel)
    {
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_FEED_URL, channel.getUrl());
        values.put(DatabaseHelper.COLUMN_FEED_NAME, channel.getName());
        values.put(DatabaseHelper.COLUMN_FEED_LAST_UPDATE, channel.getLastUpdate());
        values.put(DatabaseHelper.COLUMN_FEED_AUTO_DOWNLOAD, (channel.isAutoDownload() ? 1 : 0));
        values.put(DatabaseHelper.COLUMN_FEED_FILTER, channel.getFilter());
        values.put(DatabaseHelper.COLUMN_FEED_IS_REGEX_FILTER, (channel.isRegexFilter() ? 1 : 0));
        values.put(DatabaseHelper.COLUMN_FETCH_ERROR, channel.getFetchError());

        ConnectionManager.getDatabase(context).update(DatabaseHelper.FEEDS_TABLE,
                values,
                DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                new String[]{ channel.getUrl() });
    }

    public void deleteChannel(FeedChannel channel)
    {
        ConnectionManager.getDatabase(context).delete(DatabaseHelper.FEEDS_TABLE,
                DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                new String[]{ channel.getUrl() });
        deleteFeedItems(channel.getUrl());
    }

    public void deleteChannel(String url)
    {
        ConnectionManager.getDatabase(context).delete(DatabaseHelper.FEEDS_TABLE,
                DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                new String[]{ url });
        deleteFeedItems(url);
    }

    public FeedChannel getChannelByUrl(String url)
    {
        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEEDS_TABLE,
                allFeedsColumns,
                DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                new String[]{ url },
                null,
                null,
                null);

        FeedChannel channel = null;

        ColumnIndexCache indexCache = new ColumnIndexCache();

        if (cursor.moveToNext())
            channel = cursorToChannel(cursor, indexCache);

        cursor.close();
        indexCache.clear();

        return channel;
    }

    public List<FeedChannel> getAllChannels()
    {
        List<FeedChannel> channels = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEEDS_TABLE,
                allFeedsColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();

        while (cursor.moveToNext())
            channels.add(cursorToChannel(cursor, indexCache));

        cursor.close();
        indexCache.clear();

        return channels;
    }

    /*
     * Returns map with URL as key.
     */

    public Map<String, FeedChannel> getAllChannelsAsMap()
    {
        Map<String, FeedChannel> channels = new HashMap<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEEDS_TABLE,
                allFeedsColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();

        while (cursor.moveToNext()) {
            FeedChannel channel = cursorToChannel(cursor, indexCache);
            channels.put(channel.getUrl(), channel);
        }

        cursor.close();
        indexCache.clear();

        return channels;
    }

    public boolean channelExists(FeedChannel channel)
    {
        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEEDS_TABLE,
                allFeedsColumns,
                DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                new String[]{ channel.getUrl() },
                null,
                null,
                null);

        if (cursor.moveToNext()) {
            cursor.close();

            return true;
        }

        cursor.close();

        return false;
    }

    public boolean channelExists(String url)
    {
            Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEEDS_TABLE,
                    allFeedsColumns,
                    DatabaseHelper.COLUMN_FEED_URL + " = ? ",
                    new String[]{ url },
                    null,
                    null,
                    null);

        if (cursor.moveToNext()) {
            cursor.close();

            return true;
        }

        cursor.close();

        return false;
    }

    private FeedChannel cursorToChannel(Cursor cursor, ColumnIndexCache indexCache)
    {
        String url = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_URL));
        String name = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_NAME));
        long lastUpdate = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_LAST_UPDATE));
        boolean autoDownload = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_AUTO_DOWNLOAD)) > 0;
        String filter = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_FILTER));
        boolean isRegexFilter = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_IS_REGEX_FILTER)) > 0;
        String fetchError = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FETCH_ERROR));

        return new FeedChannel(url, name, lastUpdate,
                autoDownload, filter, isRegexFilter, fetchError);
    }

    public static String serializeChannels(ArrayList<FeedChannel> channels)
    {
        return new Gson().toJson(channels);
    }

    public static ArrayList<FeedChannel> deserializeChannels(String data)
    {
        return new Gson().fromJson(data, new TypeToken<ArrayList<FeedChannel>>(){}.getType());
    }

    /*
     * Add item and ignore duplicate
     */

    public boolean addItem(FeedItem item)
    {
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL, item.getFeedUrl());
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_TITLE, item.getTitle());
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_DOWNLOAD_URL, item.getDownloadUrl());
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_PUB_DATE, item.getPubDate());
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_FETCH_DATE, item.getFetchDate());
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_READ, (item.isRead() ? 1 : 0));
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_ARTICLE_URL, item.getArticleUrl());

        return ConnectionManager.getDatabase(context).insertWithOnConflict(DatabaseHelper.FEED_ITEMS_TABLE, null,
                values, SQLiteDatabase.CONFLICT_IGNORE) >= 0;
    }

    /*
     * Add items and ignore duplicates. Return added items
     */

    public List<FeedItem> addItems(List<FeedItem> items)
    {
        ArrayList<FeedItem> addedItems = new ArrayList<>();
        if (items == null)
            return addedItems;

        for (FeedItem item : items) {
            ContentValues values = new ContentValues();

            values.put(DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL, item.getFeedUrl());
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_TITLE, item.getTitle());
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_DOWNLOAD_URL, item.getDownloadUrl());
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_PUB_DATE, item.getPubDate());
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_FETCH_DATE, item.getFetchDate());
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_READ, (item.isRead() ? 1 : 0));
            values.put(DatabaseHelper.COLUMN_FEED_ITEM_ARTICLE_URL, item.getArticleUrl());

            SQLiteDatabase sqLiteDatabase = ConnectionManager.getDatabase(context);
            try {
                sqLiteDatabase.beginTransaction();
                if (sqLiteDatabase.insertWithOnConflict(DatabaseHelper.FEED_ITEMS_TABLE, null,
                        values, SQLiteDatabase.CONFLICT_IGNORE) >= 0)
                    addedItems.add(item);
                sqLiteDatabase.setTransactionSuccessful();

            } finally {
                sqLiteDatabase.endTransaction();
            }
        }

        return addedItems;
    }

    public void markAsRead(FeedItem item)
    {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_READ, 1);

        ConnectionManager.getDatabase(context).update(DatabaseHelper.FEED_ITEMS_TABLE,
                values,
                DatabaseHelper.COLUMN_FEED_ITEM_TITLE + " = ? ",
                new String[]{ item.getTitle() });
    }

    public void markAllAsRead()
    {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_FEED_ITEM_READ, 1);

        ConnectionManager.getDatabase(context).update(DatabaseHelper.FEED_ITEMS_TABLE,
                values,
                null,
                null);
    }

    public void deleteItems(List<FeedItem> items)
    {
        if (items == null)
            return;

        ArrayList<String> titles = new ArrayList<>();
        for (FeedItem item : items)
            titles.add(item.getTitle());

        ConnectionManager.getDatabase(context).delete(DatabaseHelper.FEED_ITEMS_TABLE,
                DatabaseHelper.COLUMN_FEED_ITEM_TITLE + " IN (?)",
                new String[]{ TextUtils.join(",", titles) });
    }

    public void deleteItemsOlderThan(long keepDateBorderTime)
    {
        ConnectionManager.getDatabase(context).delete(DatabaseHelper.FEED_ITEMS_TABLE,
                DatabaseHelper.COLUMN_FEED_ITEM_FETCH_DATE + " < " +  keepDateBorderTime,
                null);
    }

    public void deleteFeedItems(String feedUrl)
    {
        ConnectionManager.getDatabase(context).delete(DatabaseHelper.FEED_ITEMS_TABLE,
                DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL + " = ? ",
                new String[]{ feedUrl });
    }

    public List<FeedItem> getItemsByFeedUrl(String feedUrl)
    {
        List<FeedItem> items = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEED_ITEMS_TABLE,
                allFeedItemsColumns,
                DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL + " = ? ",
                new String[]{ feedUrl },
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();

        while (cursor.moveToNext())
            items.add(cursorToItem(cursor, indexCache));

        cursor.close();
        indexCache.clear();

        return items;
    }

    public FeedItem getItemByTitle(String title)
    {
        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEED_ITEMS_TABLE,
                allFeedItemsColumns,
                DatabaseHelper.COLUMN_FEED_ITEM_TITLE + " = ? ",
                new String[]{ title },
                null,
                null,
                null);

        FeedItem item = null;

        ColumnIndexCache indexCache = new ColumnIndexCache();

        if (cursor.moveToNext())
            item = cursorToItem(cursor, indexCache);

        cursor.close();
        indexCache.clear();

        return item;
    }

    public List<FeedItem> getAllItems()
    {
        List<FeedItem> items = new ArrayList<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEED_ITEMS_TABLE,
                allFeedItemsColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();

        while (cursor.moveToNext())
            items.add(cursorToItem(cursor, indexCache));

        cursor.close();
        indexCache.clear();

        return items;
    }

    /*
     * Returns map with title as key.
     */

    public Map<String, FeedItem> getAllItemsAsMap()
    {
        Map<String, FeedItem> items = new HashMap<>();

        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEED_ITEMS_TABLE,
                allFeedItemsColumns,
                null,
                null,
                null,
                null,
                null);

        ColumnIndexCache indexCache = new ColumnIndexCache();

        while (cursor.moveToNext()) {
            FeedItem item = cursorToItem(cursor, indexCache);
            items.put(item.getTitle(), item);
        }

        cursor.close();
        indexCache.clear();

        return items;
    }

    public boolean itemExists(FeedItem item)
    {
        Cursor cursor = ConnectionManager.getDatabase(context).query(DatabaseHelper.FEED_ITEMS_TABLE,
                allFeedsColumns,
                DatabaseHelper.COLUMN_FEED_ITEM_TITLE + " = ? ",
                new String[]{ item.getTitle() },
                null,
                null,
                null);

        if (cursor.moveToNext()) {
            cursor.close();

            return true;
        }

        cursor.close();

        return false;
    }

    private FeedItem cursorToItem(Cursor cursor, ColumnIndexCache indexCache)
    {
        String title = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_TITLE));
        String feedUrl = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_FEED_URL));
        String downloadUrl = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_DOWNLOAD_URL));
        long pubDate = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_PUB_DATE));
        long fetchDate = cursor.getLong(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_FETCH_DATE));
        boolean read = cursor.getInt(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_READ)) > 0;
        String articleUrl = cursor.getString(indexCache.getColumnIndex(cursor, DatabaseHelper.COLUMN_FEED_ITEM_ARTICLE_URL));

        FeedItem item = new FeedItem(feedUrl, downloadUrl, articleUrl, title, pubDate);
        item.setFetchDate(fetchDate);
        item.setRead(read);

        return item;
    }

    /*
     * Using a cache to speed up data retrieval from Cursors.
     */

    private class ColumnIndexCache
    {
        private ArrayMap<String, Integer> map = new ArrayMap<>();

        public int getColumnIndex(Cursor cursor, String columnName)
        {
            if (!map.containsKey(columnName))
                map.put(columnName, cursor.getColumnIndex(columnName));

            return map.get(columnName);
        }

        public void clear()
        {
            map.clear();
        }
    }
}
