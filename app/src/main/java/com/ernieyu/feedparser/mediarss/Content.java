package com.ernieyu.feedparser.mediarss;

/**
 * Partially implemented.
 *
 * <media:content> is a sub-element of either <item> or <media:group>.
 * Media objects that are not the same content should not be included in the same
 * <media:group> element. The sequence of these items implies the order of presentation.
 * While many of the attributes appear to be audio/video specific,
 * this element can be used to publish any type of media.
 * It contains 14 attributes, most of which are optional.
 *
 * <media:content
 *   url="http://www.foo.com/movie.mov"
 *   fileSize="12216320"
 *   type="video/quicktime"
 *   medium="video"
 *   isDefault="true"
 *   expression="full"
 *   bitrate="128"
 *   framerate="25"
 *   samplingrate="44.1"
 *   channels="2"
 *   duration="185"
 *   height="200"
 *   width="300"
 *   lang="en" />
 */

public class Content {
    private String url;
    private String type;

    public Content(String url, String type) {
        this.url = url;
        this.type = type;
    }

    /**
     * Returns the content URL.
     * <p>
     *
     * @return the content URL, <b>null</b> if none.
     */

    public String getUrl() {
        return url;
    }

    /**
     * Returns the content MIME type.
     * <p>
     *
     * @return the content MIME type, <b>null</b> if none.
     */

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Content content = (Content) o;

        if (url != null ? !url.equals(content.url) : content.url != null)
            return false;
        return type != null ? type.equals(content.type) : content.type == null;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MediaRssContent{" +
            "url='" + url + '\'' +
            ", type='" + type + '\'' +
            '}';
    }
}
