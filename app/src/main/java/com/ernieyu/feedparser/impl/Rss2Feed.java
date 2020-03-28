package com.ernieyu.feedparser.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.xml.sax.Attributes;

import com.ernieyu.feedparser.Element;
import com.ernieyu.feedparser.Feed;
import com.ernieyu.feedparser.FeedType;
import com.ernieyu.feedparser.FeedUtils;
import com.ernieyu.feedparser.Item;

/**
 * Feed implementation for RSS 2.0.
 */
class Rss2Feed extends BaseElement implements Feed {
    // XML elements for RSS feeds.
    private static final String CHANNEL = "channel";
    private static final String TITLE = "title";
    private static final String LINK = "link";
    private static final String DESCRIPTION = "description";
    private static final String LANGUAGE = "language";
    private static final String COPYRIGHT = "copyright";
    private static final String PUB_DATE = "pubDate";
    private static final String LAST_BUILD_DATE = "lastBuildDate";
    private static final String CATEGORY = "category";
    private static final String ITEM = "item";

    /**
     * Constructs an Rss2Feed with the specified namespace uri, name and
     * attributes.
     */
    public Rss2Feed(String uri, String name, Attributes attributes) {
        super(uri, name, attributes);
    }

    @Override
    public FeedType getType() {
        return FeedType.RSS_2_0;
    }

    @Override
    public String getTitle() {
        Element channel = getElement(CHANNEL);
        Element title = (channel == null ? null : channel.getElement(TITLE));
        return (title != null) ? title.getContent() : null;
    }

    @Override
    public String getLink() {
        Element channel = getElement(CHANNEL);
        if (channel == null) {
            return null;
        }
        for (Element element : channel.getElementList(LINK)) {
            if (!element.getContent().isEmpty()) {
                return element.getContent();
            }
        }
        return null;
    }

    @Override
    public String getDescription() {
        Element channel = getElement(CHANNEL);
        Element descr = (channel == null ? null : channel.getElement(DESCRIPTION));
        return (descr != null) ? descr.getContent() : null;
    }

    @Override
    public String getLanguage() {
        Element channel = getElement(CHANNEL);
        Element language = (channel == null ? null : channel.getElement(LANGUAGE));
        return (language != null) ? language.getContent() : null;
    }

    @Override
    public String getCopyright() {
        Element channel = getElement(CHANNEL);
        Element copy = (channel == null ? null : channel.getElement(COPYRIGHT));
        return (copy != null) ? copy.getContent() : null;
    }

    @Override
    public Date getPubDate() {
        Element channel = getElement(CHANNEL);
        if (channel == null) {
            return null;
        }
        Element pubDate = channel.getElement(PUB_DATE);
        if (pubDate == null) pubDate = channel.getElement(LAST_BUILD_DATE);
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
    public List<Item> getItemList() {
        // Get element list for items.
        Element channel = getElement(CHANNEL);
        if (channel == null) {
            return new ArrayList<Item>();
        }
        List<Element> elementList = channel.getElementList(ITEM);
        List<Item> itemList = new ArrayList<Item>();

        // Build item list.
        if (elementList != null) {
            for (Element element : elementList) {
                itemList.add((Item) element);
            }
        }

        return itemList;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
