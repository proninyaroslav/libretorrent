package com.ernieyu.feedparser.impl;

import com.ernieyu.feedparser.Element;
import com.ernieyu.feedparser.mediarss.Content;
import com.ernieyu.feedparser.mediarss.Hash;
import com.ernieyu.feedparser.mediarss.MediaRss;
import com.ernieyu.feedparser.mediarss.PeerLink;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * A parser for Media RSS namespace (http://search.yahoo.com/mrss)
 */

class MediaRssParser {
    private static final String MEDIA_RSS_NAMESPACE_URI = "http://search.yahoo.com/mrss";
    private static final String MEDIA_RSS_CONTENT = "content";
    private static final String MEDIA_RSS_HASH = "hash";
    private static final String MEDIA_RSS_PEER_LINK = "peerLink";

    private Rss2Item item;

    public MediaRssParser(Rss2Item item) {
        this.item = item;
    }

    public MediaRss parse()
    {
        MediaRssParser parser = new MediaRssParser(item);

        List<Content> content = parser.parseContent();
        Hash hash = parser.parseHash();
        List<PeerLink> peerLinks = parser.parsePeerLinks();

        return new MediaRss(content, hash, peerLinks);
    }

    public List<Content> parseContent() {
        ArrayList<Content> content = new ArrayList<Content>();
        List<Element> mediaElems = item.getElementList(MEDIA_RSS_CONTENT);

        for (Element mediaElement : mediaElems) {
            if (!isMediaRssElement(mediaElement))
                continue;

            Attributes attr = mediaElement.getAttributes();
            String url = attr.getValue("url");
            String type = attr.getValue("type");

            content.add(new Content(url, type));
        }

        return content;
    }

    public Hash parseHash() {
        Element mediaElement = item.getElement(MEDIA_RSS_HASH);
        if (mediaElement == null || !isMediaRssElement(mediaElement))
            return null;

        Attributes attr = mediaElement.getAttributes();
        String hash = mediaElement.getContent();
        if (hash == null)
            return null;

        Hash mediaHash = new Hash(hash);
        String algo = attr.getValue("algo");
        if (algo != null)
            mediaHash.setAlgorithm(algo);

        return mediaHash;
    }

    public List<PeerLink> parsePeerLinks() {
        ArrayList<PeerLink> peerLinks = new ArrayList<PeerLink>();
        List<Element> mediaElems = item.getElementList(MEDIA_RSS_PEER_LINK);

        for (Element mediaElement : mediaElems) {
            if (!isMediaRssElement(mediaElement))
                continue;

            Attributes attr = mediaElement.getAttributes();
            String href = attr.getValue("href");
            String type = attr.getValue("type");

            peerLinks.add(new PeerLink(href, type));
        }

        return peerLinks;
    }

    private boolean isMediaRssElement(Element element) {
        return element.getUri() != null &&
            element.getUri().startsWith(MEDIA_RSS_NAMESPACE_URI);
    }
}
