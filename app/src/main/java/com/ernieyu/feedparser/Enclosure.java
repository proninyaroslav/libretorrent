package com.ernieyu.feedparser;

/**
 * Represents an enclosure from the RSS item.
 */

public class Enclosure {
	private String url;
	private String type;
	private long length;

	public Enclosure(String url, String type, long length) {
		this.url = url;
		this.type = type;
		this.length = length;
	}

	/**
	 * Returns the enclosure URL.
	 * <p>
	 *
	 * @return the enclosure URL, <b>null</b> if none.
	 *
	 */

	public String getUrl(){
		return url;
	}

	/**
	 * Returns the enclosure type.
	 * <p>
	 *
	 * @return the enclosure type, <b>null</b> if none.
	 *
	 */

	public String getType() {
		return type;
	}

	/**
	 * Returns the enclosure length.
	 * <p>
	 *
	 * @return the enclosure length, <b>0</b> if none.
	 *
	 */

	public long getLength() {
		return length;
	}

	@Override
	public String toString() {
		return "Enclosure{" +
			"url='" + url + '\'' +
			", type='" + type + '\'' +
			", length=" + length +
			'}';
	}
}
