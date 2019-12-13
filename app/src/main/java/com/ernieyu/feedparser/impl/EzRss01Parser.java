package com.ernieyu.feedparser.impl;

import com.ernieyu.feedparser.Element;
import com.ernieyu.feedparser.EzRssTorrentItem;

/**
 * A parser for EzRSS 0.1 namespace (http://xmlns.ezrss.it/0.1)
 */

class EzRss01Parser {
    private static final String EZRSS_NAMESPACE_URI = "http://xmlns.ezrss.it/0.1";
    private static final String EZRSS_FILE_NAME = "fileName";
    private static final String EZRSS_MAGNET_URI = "magnetURI";
    private static final String EZRSS_INFO_HASH = "infoHash";
    private static final String EZRSS_CONTENT_LENGTH = "contentLength";
    private static final String EZRSS_SEEDS = "seeds";
    private static final String EZRSS_PEERS = "peers";
    private static final String EZRSS_VERIFIED = "verified";

    private Rss2Item item;

    public EzRss01Parser(Rss2Item item) {
        this.item = item;
    }

    public EzRssTorrentItem parse() {
        String fileName = parseFileName();
        String magnetUri = parseMagnetUri();
        String infoHash = parseInfoHash();
        long contentLength = parseContentLength();
        int seeds = parseSeeds();
        int peers = parsePeers();
        boolean verified = parseVerified();

        return new EzRssTorrentItem(fileName, magnetUri, infoHash,
            contentLength, seeds, peers, verified);
    }

    private String parseFileName() {
        Element element = item.getElement(EZRSS_FILE_NAME);
        if (element == null || !isEzRssElement(element))
            return null;

        return element.getContent();
    }

    private String parseMagnetUri() {
        Element element = item.getElement(EZRSS_MAGNET_URI);
        if (element == null || !isEzRssElement(element))
            return null;

        return element.getContent();
    }

    private String parseInfoHash() {
        Element element = item.getElement(EZRSS_INFO_HASH);
        if (element == null || !isEzRssElement(element))
            return null;

        return element.getContent();
    }

    private long parseContentLength() {
        Element element = item.getElement(EZRSS_CONTENT_LENGTH);
        if (element == null || !isEzRssElement(element))
            return 0;

        long contentLength = 0;
        try {
            contentLength = Long.parseLong(element.getContent());

        } catch (NumberFormatException e) {
            return contentLength;
        }

        return contentLength;
    }

    private int parseSeeds() {
        Element element = item.getElement(EZRSS_SEEDS);
        if (element == null || !isEzRssElement(element))
            return 0;

        return parseInt(element.getContent());
    }

    private int parsePeers() {
        Element element = item.getElement(EZRSS_PEERS);
        if (element == null || !isEzRssElement(element))
            return 0;

        return parseInt(element.getContent());
    }

    private boolean parseVerified() {
        Element element = item.getElement(EZRSS_VERIFIED);
        if (element == null || !isEzRssElement(element))
            return false;

        int verifiedVal = parseInt(element.getContent());

        return verifiedVal == 1;
    }

    private int parseInt(String s)
    {
        int i = 0;
        try {
            i = Integer.parseInt(s);

        } catch (NumberFormatException e) {
            return i;
        }

        return i;
    }

    private boolean isEzRssElement(Element element) {
        return element.getUri() != null &&
            element.getUri().startsWith(EZRSS_NAMESPACE_URI);
    }
}
