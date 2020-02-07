package com.ernieyu.feedparser.mediarss;

import java.util.List;

/**
 * Media RSS is a new RSS module that supplements the <enclosure> capabilities of RSS 2.0.
 * RSS enclosures are already being used to syndicate audio files and images.
 * Media RSS extends enclosures to handle other media types, such as short films or TV,
 * as well as provide additional metadata with the media.
 * Media RSS enables content publishers and bloggers to syndicate multimedia content
 * such as TV and video clips, movies, images and audio.
 */

public class MediaRss {
    private List<Content> content;
    private Hash hash;
    private List<PeerLink> peerLinks;

    public MediaRss(List<Content> content,
                    Hash hash,
                    List<PeerLink> peerLinks)
    {
        this.content = content;
        this.hash = hash;
        this.peerLinks = peerLinks;
    }

    public List<Content> getContent() {
        return content;
    }

    public Hash getHash() {
        return hash;
    }

    public List<PeerLink> getPeerLinks() {
        return peerLinks;
    }

    @Override
    public String toString() {
        return "MediaRss{" +
            "content=" + content +
            ", hash=" + hash +
            '}';
    }
}
