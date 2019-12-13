package com.ernieyu.feedparser;

/**
 * A class that represents the bitTorrent 0.1 DTD,
 * a simple XML format, based on RSS 2.0 and written by EZTV (https://eztv.io)
 * for describing collections of one or more torrents.
 *
 * Example:
 *
 * <torrent:fileName>Example</torrent:fileName>
 * <torrent:contentLength>178291324</torrent:contentLength>
 * <torrent:infoHash>adc83b19e793491b1c6ea0fd8b46cd9f32e592fc</torrent:infoHash>
 *
 * Formal spec available here: https://pastebin.com/WcwuQX8v
 */

public class EzRssTorrentItem {
    private String fileName;
    private String magnetUri;
    private String infoHash;
    private long contentLength;
    private int seeds;
    private int peers;
    private boolean verified;

    public EzRssTorrentItem(String fileName, String magnetUri,
                            String infoHash, long contentLength,
                            int seeds, int peers, boolean verified)
    {
        this.fileName = fileName;
        this.magnetUri = magnetUri;
        this.infoHash = infoHash;
        this.contentLength = contentLength;
        this.seeds = seeds;
        this.peers = peers;
        this.verified = verified;
    }

    /**
     * @return file name or <b>null</b> if none
     */

    public String getFileName() {
        return fileName;
    }

    /**
     * @return magnet link or <b>null</b> if none
     */

    public String getMagnetUri() {
        return magnetUri;
    }

    /**
     * @return sha-1 info hash or <b>null</b> if none
     */

    public String getInfoHash() {
        return infoHash;
    }

    /**
     * @return torrent content length in bytes or <b>0</b> if none.
     */

    public long getContentLength() {
        return contentLength;
    }

    /**
     * @return torrent seeds or <b>0</b> if none.
     */

    public int getSeeds() {
        return seeds;
    }

    /**
     * @return torrent peers or <b>0</b> if none.
     */

    public int getPeers() {
        return peers;
    }

    /**
     * @return true if the torrent is verified
     */

    public boolean isVerified() {
        return verified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EzRssTorrentItem that = (EzRssTorrentItem) o;

        if (contentLength != that.contentLength) return false;
        if (seeds != that.seeds) return false;
        if (peers != that.peers) return false;
        if (verified != that.verified) return false;
        if (fileName != null ? !fileName.equals(that.fileName) : that.fileName != null)
            return false;
        if (magnetUri != null ? !magnetUri.equals(that.magnetUri) : that.magnetUri != null)
            return false;
        return infoHash != null ? infoHash.equals(that.infoHash) : that.infoHash == null;
    }

    @Override
    public int hashCode() {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (magnetUri != null ? magnetUri.hashCode() : 0);
        result = 31 * result + (infoHash != null ? infoHash.hashCode() : 0);
        result = 31 * result + (int) (contentLength ^ (contentLength >>> 32));
        result = 31 * result + seeds;
        result = 31 * result + peers;
        result = 31 * result + (verified ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TorrentMetadata{" +
            "fileName='" + fileName + '\'' +
            ", magnetUri='" + magnetUri + '\'' +
            ", infoHash='" + infoHash + '\'' +
            ", contentLength=" + contentLength +
            ", seeds=" + seeds +
            ", peers=" + peers +
            ", verified=" + verified +
            '}';
    }
}
