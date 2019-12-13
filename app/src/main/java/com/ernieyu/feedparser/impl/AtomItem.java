package com.ernieyu.feedparser.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ernieyu.feedparser.*;
import com.ernieyu.feedparser.mediarss.MediaRss;
import org.xml.sax.Attributes;

/**
 * Item implementation for Atom feeds.
 */
class AtomItem extends BaseItem {
    // XML elements for Atom items.
    private static final String TITLE = "title";
    private static final String LINK = "link";
    private static final String UPDATED = "updated";
    private static final String ID = "id";
    private static final String CONTENT = "content";
    private static final String SUMMARY = "summary";
    private static final String AUTHOR = "author";
    private static final String NAME = "name";
    private static final String CATEGORY = "category";
    
    /**
     * Constructs an AtomItem with the specified namespace uri, name and 
     * attributes.
     */
    public AtomItem(String uri, String name, Attributes attributes) {
        super(uri, name, attributes);
    }
    
    @Override
    public FeedType getType() {
        return FeedType.ATOM_1_0;
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
            links.add(element.getAttributes().getValue("href"));
        }

        return links;
    }

    @Override
    public String getDescription() {
        Element descr = getElement(CONTENT);
        if (descr == null) descr = getElement(SUMMARY);
        return (descr != null) ? descr.getContent() : null;
    }

    @Override
    public String getAuthor() {
        Element author = getElement(AUTHOR);
        if (author != null) {
            Element name = author.getElement(NAME);
            return (name != null) ? name.getContent() : null;
        }
        return null;
    }

    @Override
    public String getGuid() {
        Element guid = getElement(ID);
        return (guid != null) ? guid.getContent() : null;
    }

    @Override
    public Date getPubDate() {
        Element pubDate = getElement(UPDATED);
        return (pubDate != null) ? FeedUtils.convertAtomDate(pubDate.getContent()) : null;
    }

    @Override
    public List<String> getCategories() {
        List<Element> elementList = getElementList(CATEGORY);
        
        // Create list of category terms.
        List<String> categories = new ArrayList<String>();
        for (Element element : elementList) {
            categories.add(element.getAttributes().getValue("term"));
        }
        
        return categories;
    }

    @Override
    public List<Enclosure> getEnclosures() {
        ArrayList<Enclosure> enclosures = new ArrayList<Enclosure>();
        List<Element> links = getElementList(LINK);

        for (Element link : links) {
            Attributes attr = link.getAttributes();
            String rel = attr.getValue("rel");
            if (rel == null || !rel.equalsIgnoreCase("enclosure"))
                continue;

            String url = attr.getValue("href");
            String type = attr.getValue("type");
            String lengthStr = attr.getValue("length");
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
