package com.ernieyu.feedparser;

import java.io.InputStream;

/**
 * A parser for web feeds.
 */
public interface FeedParser {

	/**
	 * Parses the feed from the specified URL string. 
	 * 
	 * @param inStream InputStream for the web feed
	 * @return Feed object containing parsed data
	 */
	Feed parse(InputStream inStream) throws FeedException;
}
