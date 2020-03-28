/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.ernieyu.feedparser.Enclosure;
import com.ernieyu.feedparser.EzRssTorrentItem;
import com.ernieyu.feedparser.Feed;
import com.ernieyu.feedparser.FeedParserFactory;
import com.ernieyu.feedparser.Item;
import com.ernieyu.feedparser.mediarss.Content;
import com.ernieyu.feedparser.mediarss.Hash;
import com.ernieyu.feedparser.mediarss.MediaRss;

import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
 * Parser RSS/Atom feeds.
 *
 * Tags are supported for items:
 *  - <link>
 *  - <enclosure>
 *  - <media:content>
 *  - <media:hash>
 *  - <guid> (sometimes the torrent link is encoded as the GUID in RSS feeds)
 *  - ezRSS <torrent: ... > namespace
 */

public class FeedParser
{
    private FeedChannel feedChannel;
    private Feed feed;

    public FeedParser(@NonNull Context context, @NonNull FeedChannel feedChannel) throws Exception
    {
        this.feedChannel = feedChannel;
        ByteArrayInputStream bsStream = null;
        try {
            byte[] response = Utils.fetchHttpUrl(context, feedChannel.url);
            if (response == null)
                return;
            bsStream = new ByteArrayInputStream(response);
            feed = FeedParserFactory.newParser().parse(bsStream);

        } finally {
            if (bsStream != null) {
                try {
                    bsStream.close();
                } catch (Exception e) {
                    /* Ignore */
                }
            }
        }
    }

    public String getTitle()
    {
        return feed.getTitle();
    }

    public List<FeedItem> getItems()
    {
        List<FeedItem> items = new ArrayList<>();
        if (feed == null)
            return items;

        for (Item item : feed.getItemList()) {
            List<String> links = item.getLinks();
            String articleUrl = getFirstNotNullLink(links);
            String downloadUrl = watchDownloadableLink(links);

            /* Find url with torrent/magnet */
            if (downloadUrl == null)
                downloadUrl = findDownloadUrl(item);

            Date pubDate = item.getPubDate();
            long pubDateTime = 0;
            if (pubDate != null)
                pubDateTime = pubDate.getTime();

            FeedItem feedItem = new FeedItem(feedChannel.id, downloadUrl,
                    articleUrl, item.getTitle(), pubDateTime);
            feedItem.fetchDate = System.currentTimeMillis();
            items.add(feedItem);
        }

        return items;
    }

    private String getFirstNotNullLink(List<String> links)
    {
        for (String link : links) {
            if (!TextUtils.isEmpty(link))
                return link;
        }

        return null;
    }

    private String watchDownloadableLink(List<String> links)
    {
        for (String link : links) {
            if (link == null)
                continue;

            if (isMagnetOrTorrent(link))
                return link;
        }

        return null;
    }

    private String findDownloadUrl(Item item)
    {
        /*
         * Watch tags in descending order of importance
         * (hashes in the last place)
         */
        String enclosureUrl = watchEnclosure(item);
        if (enclosureUrl != null)
            return enclosureUrl;

        String infoHash = watchTorrentInfoHash(item);
        if (infoHash != null)
            return infoHash;

        String mediaContentUrl = watchMediaContent(item);
        if (mediaContentUrl != null)
            return mediaContentUrl;

        String guid = watchGuid(item);
        if (guid != null)
            return guid;

        return null;
    }

    /*
     * Parse element like this:
     * <enclosure type="application/x-bittorrent" url="http://foo.com/bar.torrent"/>
     */

    private String watchEnclosure(Item item)
    {
        for (Enclosure enclosure : item.getEnclosures()) {
            if (enclosure == null)
                continue;

            String url = enclosure.getUrl();
            String type = enclosure.getType();
            if (isMagnetOrTorrent(url) || (type != null && type.equals(Utils.MIME_TORRENT)))
                return url;
        }

        return null;
    }

    /*
     * Parse element like this:
     * <media:content type="application/x-bittorrent" url="http://foo.com/bar.torrent"/>
     */

    private String watchMediaContent(Item item)
    {
        MediaRss media = item.getMediaRss();
        if (media == null)
            return null;

        List<Content> contentList = media.getContent();
        for (Content content : contentList) {
            if (content == null)
                continue;

            String url = content.getUrl();
            if (url == null)
                return watchMediaHash(media);
            String type = content.getType();
            if (isMagnetOrTorrent(url) || (type != null && type.equals(Utils.MIME_TORRENT)))
                return url;
        }

        return watchMediaHash(media);
    }

    /*
     * Parse element like this:
     * <media:hash algo="sha1">8c056e06fbc16d2a2be79cefbf3e4ddc15396abe</media:hash>
     */

    private String watchMediaHash(MediaRss media)
    {
        Hash hash = media.getHash();
        if (hash == null)
            return null;

        String hashStr = hash.getValue();
        if (hashStr == null)
            return null;
        String algo = hash.getAlgorithm();
        if (Utils.isHash(hashStr) && algo != null && algo.equalsIgnoreCase("sha1"))
            return Utils.normalizeMagnetHash(hashStr);

        return null;
    }

    /*
     * Parse element like this:
     * <torrent:infoHash>8c056e06fbc16d2a2be79cefbf3e4ddc15396abe</torrent:infoHash>
     */

    private String watchTorrentInfoHash(Item item)
    {
        EzRssTorrentItem torrentItem = item.getEzRssTorrentItem();
        if (torrentItem == null)
            return null;

        String infoHash = torrentItem.getInfoHash();
        if (infoHash != null && Utils.isHash(infoHash))
            return Utils.normalizeMagnetHash(infoHash);

        return null;
    }

    /*
     * Parse element like this:
     * <guid>http://foo.com/bar.torrent</guid>
     */

    private String watchGuid(Item item)
    {
        String url = item.getGuid();
        if (url != null && isMagnetOrTorrent(url))
            return url;

        return null;
    }

    private boolean isMagnetOrTorrent(String url)
    {
        return url.endsWith(".torrent") || url.startsWith(Utils.MAGNET_PREFIX);
    }
}
