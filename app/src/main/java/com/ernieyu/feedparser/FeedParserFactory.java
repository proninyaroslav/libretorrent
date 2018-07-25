package com.ernieyu.feedparser;

import com.ernieyu.feedparser.impl.DefaultFeedParser;

/**
 * A factory for retrieving web feed parsers.
 * 
 * <p>To parse a web feed, use the factory to get a FeedParser and call its
 * parse() method.<br/>
 * <pre>
 *     FeedParser parser = FeedParserFactory.newParser();
 *     Feed feed = parser.parse(feedStream);
 * </pre>
 * </p>
 */
public class FeedParserFactory {

	/**
	 * Creates a new instance of FeedParser.
	 * 
	 * @return new instance of FeedParser
	 */
	public static FeedParser newParser() {
	    // TODO add options for "minimal" parser, max item count
		return new DefaultFeedParser();
	}
}
