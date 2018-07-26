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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;

import com.ernieyu.feedparser.Element;
import com.ernieyu.feedparser.Enclosure;
import com.ernieyu.feedparser.Feed;
import com.ernieyu.feedparser.FeedParserFactory;
import com.ernieyu.feedparser.Item;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.xml.sax.Attributes;

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
    private String feedUrl;
    private Feed feed;

    public FeedParser(Context context, String feedUrl) throws Exception
    {
        this.feedUrl = feedUrl;
        ByteArrayInputStream bsStream = null;
        try {
            byte[] response = Utils.fetchHttpUrl(context, feedUrl);
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
        if (feedUrl == null || feed == null)
            return items;

        for (Item item : feed.getItemList()) {
            String articleUrl = item.getLink();
            String downloadUrl = articleUrl;

            /* Find url with torrent/magnet */
            if (!isMagnetOrTorrent(downloadUrl)) {
                String found = findDownloadUrl(item);
                if (found != null)
                    /* Or article url if there are no other options */
                    downloadUrl = found;
            }

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

    private String findDownloadUrl(Item item)
    {
        /*
         * Watch tags in descending order of importance
         * (hashes in the last place)
         */
        String enclosureUrl = watchEnclosure(item);
        if (enclosureUrl != null)
            return enclosureUrl;

        String mediaContentUrl = watchMediaContent(item);
        if (mediaContentUrl != null)
            return mediaContentUrl;

        String guid = watchGuid(item);
        if (guid != null)
            return guid;

        String mediaHash = watchMediaHash(item);
        if (mediaHash != null)
            return mediaHash;

        String infoHash = watchTorrentInfoHash(item);
        if (infoHash != null)
            return infoHash;

        return null;
    }

    /*
     * Parse element like this:
     * <enclosure type="application/x-bittorrent" url="http://foo.com/bar.torrent"/>
     */

    private String watchEnclosure(Item item)
    {
        Enclosure enclosure = item.getEnclosure();
        String url;
        if (enclosure == null)
            return null;
        url = enclosure.getUrl();

        String type = enclosure.getType();
        if (isMagnetOrTorrent(url) || (type != null && type.equals(Utils.MIME_TORRENT)))
            return url;

        return null;
    }

    /*
     * Parse element like this:
     * <media:content type="application/x-bittorrent" url="http://foo.com/bar.torrent"/>
     */

    private String watchMediaContent(Item item)
    {
        Element mediaContent = item.getElement("content");
        if (mediaContent == null)
            return null;

        Attributes attr = mediaContent.getAttributes();
        String url = attr.getValue("url");
        if (url == null)
            return null;
        String type = attr.getValue("type");
        if (isMagnetOrTorrent(url) || (type != null && type.equals(Utils.MIME_TORRENT)))
            return url;

        return null;
    }

    /*
     * Parse element like this:
     * <media:hash algo="sha1">8c056e06fbc16d2a2be79cefbf3e4ddc15396abe</media:hash>
     */

    private String watchMediaHash(Item item)
    {
        Element mediaHash = item.getElement("hash");
        if (mediaHash == null)
            return null;

        Attributes attr = mediaHash.getAttributes();
        String hash = mediaHash.getContent();
        if (hash == null)
            return null;
        String algo = attr.getValue("algo");
        if (Utils.isHash(hash) && algo != null && algo.equalsIgnoreCase("sha1"))
            return Utils.normalizeMagnetHash(hash);

        return null;
    }

    /*
     * Parse element like this:
     * <torrent:infoHash>8c056e06fbc16d2a2be79cefbf3e4ddc15396abe</torrent:infoHash>
     */

    private String watchTorrentInfoHash(Item item)
    {
        Element infoHash = item.getElement("infoHash");
        if (infoHash == null)
            return null;

        String hash = infoHash.getContent();
        if (hash != null && Utils.isHash(hash))
            return Utils.normalizeMagnetHash(hash);

        return null;
    }

    /*
     * Parse element like this:
     * <guid>http://foo.com/bar.torrent</guid>
     */

    private String watchGuid(Item item)
    {
        Element guid = item.getElement("guid");
        if (guid == null)
            return null;

        String url = guid.getContent();
        if (url != null && isMagnetOrTorrent(url))
            return url;

        return null;
    }

    private boolean isMagnetOrTorrent(String url)
    {
        return url.endsWith(".torrent") || url.startsWith(Utils.MAGNET_PREFIX);
    }
}
