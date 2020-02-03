package com.ernieyu.feedparser.impl;

import org.xml.sax.Attributes;

import com.ernieyu.feedparser.FeedUtils;
import com.ernieyu.feedparser.Item;

import java.util.List;

/**
 * Base class for feed items.
 */
abstract class BaseItem extends BaseElement implements Item {

    /**
     * Constructs a BaseItem with the specified namespace uri, name and 
     * attributes.
     */
    public BaseItem(String uri, String name, Attributes attributes) {
        super(uri, name, attributes);
    }
    
    /**
     * Returns the unique identifier for item equality.  This is usually based
     * on the Guid value.  This method uses alternate values when the Guid 
     * value is missing.
     */
    private String getUniqueId() {
        String id = getGuid();
        if (id == null) {
            List<String> links = getLinks();
            if (links.size() == 1)
                id = links.get(0);
        }
        if (id == null) {
            id = getTitle();
        }
        if (id == null) {
            id = getDescription();
        }
        return id;
    }

    @Override
    public String toString() {
        return getTitle();
    }
    
    @Override
    public boolean equals(Object obj) {
        // Compare ids for equality.
        if (obj instanceof BaseItem) {
            String id1 = getUniqueId();
            String id2 = ((BaseItem) obj).getUniqueId();
            return FeedUtils.equalsOrNull(id1, id2);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        // Use same fields as equals method. 
        String id = getUniqueId();
        return (id != null) ? id.hashCode() : 0;
    }
}
