package com.ernieyu.feedparser.impl;

import java.util.Stack;

import org.apache.commons.text.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.ernieyu.feedparser.Feed;
import com.ernieyu.feedparser.FeedType;

/**
 * SAX parser content handler to process feed XML.
 */
class FeedHandler extends DefaultHandler {
    private static final String RDF = "rdf";
    private static final String RSS = "rss";
    private static final String FEED = "feed";
    private static final String ITEM = "item";
    private static final String ENTRY = "entry";

    private Stack<BaseElement> elementStack;
    private Feed feed;
    private FeedType type;
    private StringBuilder buffer;
    
    /**
     * Returns the feed.
     */
    public Feed getFeed() {
        return feed;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        buffer.append(ch, start, length);
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        elementStack = new Stack<BaseElement>();
    }

    @Override
    public void endDocument() throws SAXException {
        elementStack.clear();
        super.endDocument();
    }
    
    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);
        
        BaseElement newElement;
        
        if (RDF.equalsIgnoreCase(localName)) {
            // Create feed for RSS 1.0.
            Rss1Feed newFeed = new Rss1Feed(uri, RDF, attributes);
            feed = newFeed;
            type = FeedType.RSS_1_0;
            newElement = newFeed;
            
        } else if (RSS.equalsIgnoreCase(localName)) {
            // Create feed for RSS 2.0.
            Rss2Feed newFeed = new Rss2Feed(uri, RSS, attributes);
            feed = newFeed;
            type = FeedType.RSS_2_0;
            newElement = newFeed;
            
        } else if (FEED.equalsIgnoreCase(localName)) {
            // Create feed for Atom 1.0.
            AtomFeed newFeed = new AtomFeed(uri, FEED, attributes);
            feed = newFeed;
            type = FeedType.ATOM_1_0;
            newElement = newFeed;
            
        } else if (ITEM.equalsIgnoreCase(localName)) {
            // Create RSS item.
            switch (type) {
            case RSS_1_0:
                newElement = new Rss1Item(uri, localName, attributes);
                break;
            case RSS_2_0:
                newElement = new Rss2Item(uri, localName, attributes);
                break;
            default:
                throw new SAXException("Unknown feed type");
            }

        } else if (ENTRY.equalsIgnoreCase(localName)) {
            // Create Atom item.
            newElement = new AtomItem(uri, localName, attributes);

        } else {
            // Create new XML element.
            newElement = new BaseElement(uri, localName, attributes);
        }

        elementStack.push(newElement);
        
        // Initialize content buffer.
        buffer = new StringBuilder();
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);
        
        // Save content in current element.
        BaseElement currentElement = elementStack.pop();
        String content = buffer.toString();
        // If contains ampersand, unescape escaped symbols
        if (content.contains("&")) {
            content = StringEscapeUtils.unescapeXml(content);
        }
        currentElement.setContent(content);
        
        // Add current element to its parent.
        if (!elementStack.empty()) {
            BaseElement parent = elementStack.peek();
            parent.addElement(localName, currentElement);
        }

        // Clear content buffer.
        buffer.delete(0, buffer.length());
    }
}
