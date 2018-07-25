package com.ernieyu.feedparser;

import java.util.List;
import java.util.Set;

import org.xml.sax.Attributes;

/**
 * An XML element in the feed.
 */
public interface Element {

    /**
     * Returns the namespace URI.
     */
    public String getUri();

    /**
     * Returns the element name.
     */
    public String getName();
    
    /**
     * Returns the attributes associated with the element.
     */
    public Attributes getAttributes();
    
    /**
     * Returns the element content.
     */
    public String getContent();
    
    /**
     * Returns the first child element associated with the specified name.
     * Returns null if the element does not exist.
     */
    public Element getElement(String name);
    
    /**
     * Returns a list of child elements associated with the specified name.
     * Returns an empty list if no elements are available.
     */
    public List<Element> getElementList(String name);
    
    /**
     * Returns a set of keys for all child elements.  This allows an 
     * application to iterate through the elements.
     */
    public Set<String> getElementKeys();
}
