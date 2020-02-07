package com.ernieyu.feedparser.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ernieyu.feedparser.*;
import com.ernieyu.feedparser.mediarss.MediaRss;

import org.xml.sax.Attributes;

/**
 * Item implementation for RSS 2.0 feeds.
 */
class Rss2Item extends BaseItem {
    // XML elements for RSS items.
    private static final String TITLE = "title";
    private static final String LINK = "link";
    private static final String DESCRIPTION = "description";
    private static final String PUB_DATE = "pubDate";
    private static final String AUTHOR = "author";
    private static final String GUID = "guid";
    private static final String CATEGORY = "category";
    private static final String ENCLOSURE = "enclosure";
    
    /**
     * Constructs an Rss2Item with the specified namespace uri, name and
     * attributes.
     */
    public Rss2Item(String uri, String name, Attributes attributes) {
        super(uri, name, attributes);
    }
    
    @Override
    public FeedType getType() {
        return FeedType.RSS_2_0;
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
        Element author = getElement(AUTHOR);
        return (author != null) ? author.getContent() : null;
    }

    @Override
    public String getGuid() {
        Element guid = getElement(GUID);
        return (guid != null) ? guid.getContent() : null;
    }

    @Override
    public Date getPubDate() {
        Element pubDate = getElement(PUB_DATE);
        return (pubDate != null) ? FeedUtils.convertRss2Date(pubDate.getContent()) : null;
    }

    @Override
    public List<String> getCategories() {
        List<Element> elementList = getElementList(CATEGORY);
        
        // Create list of category terms.
        List<String> categories = new ArrayList<String>();
        for (Element element : elementList) {
            categories.add(element.getContent());
        }
        
        return categories;
    }

    @Override
    public List<Enclosure> getEnclosures() {
        ArrayList<Enclosure> enclosures = new ArrayList<Enclosure>();
        List<Element> enclosuresElem = getElementList(ENCLOSURE);

        for (Element enclosure : enclosuresElem) {
            Attributes attr = enclosure.getAttributes();
            String url = attr.getValue("url");
            String type = attr.getValue("type");
            String lengthStr = attr.getValue("length");
            long length = 0;
            if (lengthStr != null)
                length = Long.parseLong(lengthStr);

            enclosures.add(new Enclosure(url, type, length));
        }

        return enclosures;
    }

    @Override
    public MediaRss getMediaRss() {
       return new MediaRssParser(this).parse();
    }

    @Override
    public EzRssTorrentItem getEzRssTorrentItem() {
        return new EzRss01Parser(this).parse();
    }
}
