package com.ernieyu.feedparser.mediarss;

/**
 * Optional element for P2P link.
 *
 * <media:peerLink type="application/x-bittorrent" href="http://www.example.org/sampleFile.torrent" />
 */

public class PeerLink {
    private String href;
    private String type;

    public PeerLink(String href, String type) {
        this.href = href;
        this.type = type;
    }

    /**
     * Returns a peer link.
     * <p>
     *
     * @return a peer link, <b>null</b> if none.
     */

    public String getHref() {
        return href;
    }

    /**
     * Returns the peer link MIME type.
     * <p>
     *
     * @return the peer link MIME type, <b>null</b> if none.
     */

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PeerLink that = (PeerLink) o;

        if (href != null ? !href.equals(that.href) : that.href != null)
            return false;
        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        int result = href != null ? href.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MediaRssPeerLink{" +
            "href='" + href + '\'' +
            ", type='" + type + '\'' +
            '}';
    }
}
