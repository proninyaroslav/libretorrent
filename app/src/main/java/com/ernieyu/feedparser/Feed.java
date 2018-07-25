package com.ernieyu.feedparser;

import java.util.Date;
import java.util.List;

/**
 * A web feed that contains the items.
 */
public interface Feed extends Element {

    /**
     * Returns the feed type.
     */
    public FeedType getType();
    
    /**
     * Convenience method to retrieve the feed title.
     */
    public String getTitle();
    
    /**
     * Convenience method to retrieve the feed link.
     */
    public String getLink();
    
    /**
     * Convenience method to retrieve the feed description.
     */
    public String getDescription();
    
    /**
     * Convenience method to retrieve the language.
     */
    public String getLanguage();
    
    /**
     * Convenience method to retrieve the copyright.
     */
    public String getCopyright();
    
    /**
     * Convenience method to retrieve the published date.
     */
    public Date getPubDate();
    
    /**
     * Convenience method to retrieve a list of categories.
     */
    public List<String> getCategories();
    
    /**
     * Returns a list of Items in the feed.
     */
    public List<Item> getItemList();
}
