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

package org.proninyaroslav.libretorrent.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.ernieyu.feedparser.Enclosure;
import com.ernieyu.feedparser.Feed;
import com.ernieyu.feedparser.FeedParserFactory;
import com.ernieyu.feedparser.Item;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.FeedChannel;
import org.proninyaroslav.libretorrent.core.FeedItem;
import org.proninyaroslav.libretorrent.core.storage.FeedStorage;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FeedFetcherService extends JobIntentService
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedFetcherService.class.getSimpleName();

    public static final String ACTION_FETCH_CHANNEL = "org.proninyaroslav.libretorrent.services.FeedFetcherService.ACTION_FETCH_CHANNEL";
    public static final String ACTION_FETCH_CHANNEL_LIST = "org.proninyaroslav.libretorrent.services.FeedFetcherService.ACTION_FETCH_CHANNEL_LIST";
    public static final String ACTION_FETCH_ALL_CHANNELS = "org.proninyaroslav.libretorrent.services.FeedFetcherService.ACTION_FETCH_ALL_CHANNELS";
    public static final String ACTION_CHANNEL_RESULT = "org.proninyaroslav.libretorrent.services.FeedFetcherService.ACTION_CHANNEL_RESULT";
    public static final String TAG_CHANNEL_URL_ARG = "channel_url_arg";
    public static final String TAG_CHANNEL_URL_LIST_ARG = "channel_url_list_arg";
    public static final String TAG_CHANNEL_URL_RESULT = "channel_url_result";

    private FeedStorage storage;

    public static void enqueueWork(Context context, Intent i)
    {
        enqueueWork(context, FeedFetcherService.class, TAG.hashCode(), i);
    }

    @Override
    protected void onHandleWork(@Nullable Intent intent)
    {
        if (intent == null || intent.getAction() == null)
            return;

        switch (intent.getAction()) {
            case ACTION_FETCH_CHANNEL:
                fetchChannel(intent.getStringExtra(TAG_CHANNEL_URL_ARG));
                break;
            case ACTION_FETCH_CHANNEL_LIST:
                fetchChannelsByUrl(intent.getStringArrayListExtra(TAG_CHANNEL_URL_LIST_ARG));
                break;
            case ACTION_FETCH_ALL_CHANNELS:
                fetchChannels(storage.getAllChannels());
                break;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "Start " + FeedFetcherService.class.getSimpleName());

        storage = new FeedStorage(getApplicationContext());
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + FeedFetcherService.class.getSimpleName());
    }

    private void fetchChannelsByUrl(List<String> urls)
    {
        deleteOldItems();
        if (urls == null)
            return;
        for (String url : urls) {
            if (url == null)
                continue;
            fetchChannel(url);
        }
    }

    private void fetchChannels(List<FeedChannel> channels)
    {
        deleteOldItems();
        if (channels == null)
            return;
        for (FeedChannel channel : channels) {
            if (channel == null)
                continue;
            fetchChannel(channel.getUrl());
        }
    }

    private void fetchChannel(String url)
    {
        if (url == null || !storage.channelExists(url))
            return;

        Feed feed;
        ByteArrayInputStream bsStream = null;
        try {
            byte[] response = Utils.fetchHttpUrl(getApplicationContext(), url);
            if (response == null)
                return;
            bsStream = new ByteArrayInputStream(response);
            feed = FeedParserFactory.newParser().parse(bsStream);

        } catch (Exception e) {
            FeedChannel channel = storage.getChannelByUrl(url);
            if (channel != null) {
                channel.setFetchError(e.getMessage());
                storage.updateChannel(channel);
            }
            Intent i = new Intent(ACTION_CHANNEL_RESULT);
            i.putExtra(TAG_CHANNEL_URL_RESULT, url);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);

            return;
        } finally {
            if (bsStream != null) {
                try {
                    bsStream.close();
                } catch (Exception e) {
                    /* Ignore */
                }
            }
        }

        List<FeedItem> items = parseItems(url, feed);
        items = storage.addItems(items); /* Added items */

        FeedChannel channel = storage.getChannelByUrl(url);
        if (channel != null) {
            channel.setFetchError(null);
            if (channel.getName() == null || TextUtils.isEmpty(channel.getName()))
                channel.setName(feed.getTitle());
            channel.setLastUpdate(System.currentTimeMillis());
            storage.updateChannel(channel);
        }

        Intent i = new Intent(ACTION_CHANNEL_RESULT);
        i.putExtra(TAG_CHANNEL_URL_RESULT, url);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        if (channel != null && channel.isAutoDownload())
            sendUrls(channel, items);
    }

    private List<FeedItem> parseItems(String feedUrl, Feed feed)
    {
        List<FeedItem> items = new ArrayList<>();
        for (Item item : feed.getItemList()) {
            String articleUrl = item.getLink();
            String downloadUrl = articleUrl;
            Enclosure enclosure = item.getEnclosure();
            String enclosureUrl = null;
            if (enclosure != null)
                enclosureUrl = enclosure.getUrl();

            /* Watch enclosure for links*/
            if (downloadUrl != null && enclosureUrl != null) {
                String type = enclosure.getType();
                if (enclosureUrl.endsWith(".torrent") ||
                    enclosureUrl.startsWith(Utils.MAGNET_PREFIX) ||
                    (type != null && type.equals(Utils.MIME_TORRENT)))
                {
                    downloadUrl = enclosureUrl;
                }
            } else if (downloadUrl == null)
                downloadUrl = enclosureUrl;

            Date pubDate = item.getPubDate();
            long pubDateTime = 0;
            if (pubDate != null)
                pubDateTime = pubDate.getTime();

            FeedItem feedItem = new FeedItem(feedUrl, downloadUrl, articleUrl, item.getTitle(), pubDateTime);
            feedItem.setFetchDate(System.currentTimeMillis());
            items.add(feedItem);
        }

        return items;
    }

    private void deleteOldItems()
    {
        SharedPreferences pref = SettingsManager.getPreferences(getApplicationContext());
        long keepTime = pref.getLong(getString(R.string.pref_key_feed_keep_items_time),
                                     SettingsManager.Default.feedItemKeepTime);
        long keepDateBorderTime = (keepTime > 0 ? System.currentTimeMillis() - keepTime : 0);
        storage.deleteItemsOlderThan(keepDateBorderTime);
    }

    private void sendUrls(FeedChannel channel, List<FeedItem> items)
    {
        ArrayList<String> urlsForDownload = new ArrayList<>();
        for (FeedItem item : items) {
            if (item.isRead())
                continue;
            String url = item.getDownloadUrl();
            if (url == null || TextUtils.isEmpty(url))
                return;
            if (isMatch(item, channel.getFilter(), channel.isRegexFilter())) {
                urlsForDownload.add(url);
                storage.markAsRead(item);
            }
        }

        if (!urlsForDownload.isEmpty()) {
            Intent i = new Intent(getApplicationContext(), FeedDownloaderService.class);
            i.setAction(FeedDownloaderService.ACTION_DOWNLOAD_TORRENT_LIST);
            i.putExtra(FeedDownloaderService.TAG_URL_LIST_ARG, urlsForDownload);
            FeedDownloaderService.enqueueWork(this, i);
        }
    }

    private boolean isMatch(FeedItem item, String filter, boolean isRegex)
    {
        if (filter == null || TextUtils.isEmpty(filter) || item.getTitle() == null)
            return true;

        if (isRegex) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(filter);
            } catch (PatternSyntaxException e) {
                //TODO: maybe there is an option better?
                Log.e(TAG, "Invalid pattern: " + filter);
                return true;
            }

            return pattern.matcher(item.getTitle()).matches();
        } else {
            String[] words = filter.split(FeedStorage.FILTER_SEPARATOR);
            for (String word : words)
                if (item.getTitle().toLowerCase().contains(word.toLowerCase().trim()))
                    return true;
        }

        return false;
    }

//    private void fetchChannel(RssChannel channel)
//    {
//        SyndFeed feed;
//        try {
//            feed = new SyndFeedInput().build(new XmlReader(new URL(channel.getUrl())));
//        } catch (Exception e) {
//            Intent i = new Intent(ACTION_CHANNEL_RESULT);
//            i.putExtra(TAG_CHANNEL_RESULT, e);
//            i.putExtra(TAG_ERROR_RESULT, e);
//            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
//
//            return;
//        }
//
//        if (channel.getName() == null || TextUtils.isEmpty(channel.getName()))
//            channel.setName(feed.getTitle());
//
//        ArrayList<RssChannel.Item> items = parseItems(feed);
//        Collections.sort(items, RssChannel.itemComparator);
//    }
//
//    private ArrayList<RssChannel.Item> parseItems(SyndFeed feed)
//    {
//        ArrayList<RssChannel.Item> items = new ArrayList<>();
//        for (SyndEntry item : feed.getEntries()) {
//            String url = item.getLink();
//            if (url == null) {
//                /* Watch enclosures for links*/
//                List<SyndEnclosure> enclosures = item.getEnclosures();
//                if (enclosures != null)
//                    for (SyndEnclosure enclosure : enclosures)
//                        url = enclosure.getUrl();
//            }
//            Date pubDate = item.getPublishedDate();
//            if (pubDate == null)
//                pubDate = item.getUpdatedDate();
//
//            items.add(new RssChannel.Item(url, item.getTitle(), pubDate.getTime()));
//        }
//
//        return items;
//    }
}
