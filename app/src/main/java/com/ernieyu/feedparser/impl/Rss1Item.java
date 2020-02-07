package com.ernieyu.feedparser.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.ernieyu.feedparser.*;
import com.ernieyu.feedparser.mediarss.MediaRss;
import org.xml.sax.Attributes;

/**
 * Item implementation for RSS 1.0 feeds.
 */
class Rss1Item extends BaseItem {
    // XML elements for RSS items.
    private static final String TITLE = "title";
    private static final String LINK = "link";
    private static final String DESCRIPTION = "description";
    private static final String DATE = "pubDate";
    private static final String CREATOR = "creator";
    private static final String IDENTIFIER = "identifier";
    private static final String ENCLOSURE = "enclosure";
    
    /**
     * Constructs an Rss1Item with the specified namespace uri, name and
     * attributes.
     */
    public Rss1Item(String uri, String name, Attributes attributes) {
        super(uri, name, attributes);
    }
    
    @Override
    public FeedType getType() {
        return FeedType.RSS_1_0;
    }
    
    @Override
    public String getTitle() {
        Element title = getElement(TITLE);
        return (title != null) ? title.getContent() : null;
    }

    @Override
    public List<String> getLinks() {
        ArrayList<String> links = new ArrayList<String>();
        List<Element> elements = getElementList(LINK);
        for (Element element : elements) {
            links.add(element.getContent());
        }

        return links;
    }

    @Override
    public String getDescription() {
        Element descr = getElement(DESCRIPTION);
        return (descr != null) ? descr.getContent() : null;
    }

    @Override
    public String getAuthor() {
        // Use Dublin Core element.
        Element author = getElement(CREATOR);
        return (author != null) ? author.getContent() : null;
    }

    @Override
    public String getGuid() {
        // Use Dublin Core element.
        Element guid = getElement(IDENTIFIER);
        return (guid != null) ? guid.getContent() : null;
    }

    @Override
    public Date getPubDate() {
        // Use Dublin Core element.
        Element pubDate = getElement(DATE);
        return (pubDate != null) ? FeedUtils.convertRss1Date(pubDate.getContent()) : null;
    }

    @Override
    public List<String> getCategories() {
        return Collections.<String>emptyList();
    }

    @Override
    public List<Enclosure> getEnclosures() {
        ArrayList<Enclosure> enclosures = new ArrayList<Enclosure>();
        List<Element> enclosuresElem = getElementList(ENCLOSURE);

        for (Element enclosure : enclosuresElem) {
            Attributes attr = enclosure.getAttributes();
            String url = attr.getValue("rdf:resource");
            String type = attr.getValue("enc:type");
            String lengthStr = attr.getValue("enc:length");
            long length = 0;
            if (lengthStr != null)
                length = Long.parseLong(lengthStr);

            enclosures.add(new Enclosure(url, type, length));
        }

        return enclosures;
    }

    /**
     * Not supported
     */

    @Override
    public MediaRss getMediaRss() {
        return null;
    }

    /**
     * Not supported
     */

    @Override
    public EzRssTorrentItem getEzRssTorrentItem() {
        return null;
    }
}
