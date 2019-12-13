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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Enclosure enclosure = (Enclosure) o;

		if (length != enclosure.length) return false;
		if (url != null ? !url.equals(enclosure.url) : enclosure.url != null)
			return false;
		return type != null ? type.equals(enclosure.type) : enclosure.type == null;
	}

	@Override
	public int hashCode() {
		int result = url != null ? url.hashCode() : 0;
		result = 31 * result + (type != null ? type.hashCode() : 0);
		result = 31 * result + (int) (length ^ (length >>> 32));
		return result;
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
