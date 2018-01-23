package org.proninyaroslav.libretorrent.adapters;

import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;

/*
 * An item of TorrentListAdapter
 */

public class TorrentListItem implements Comparable<TorrentListItem>
{
    public String torrentId = "";
    public String name = "";
    public TorrentStateCode stateCode = TorrentStateCode.STOPPED;
    public int progress = 0;
    public long receivedBytes = 0L;
    public long uploadedBytes = 0L;
    public long totalBytes = 0L;
    public long downloadSpeed = 0L;
    public long uploadSpeed = 0L;
    public long ETA = 0L;
    public long dateAdded = 0L;
    public int totalPeers = 0;
    public int peers = 0;

    public TorrentListItem() { }

    public TorrentListItem(BasicStateParcel state)
    {
        copyFrom(state);
    }

    public void copyFrom(BasicStateParcel state)
    {
        if (state == null)
            return;

        this.torrentId = state.torrentId;
        this.name = state.name;
        this.stateCode = state.stateCode;
        this.progress = state.progress;
        this.receivedBytes = state.receivedBytes;
        this.uploadedBytes = state.uploadedBytes;
        this.totalBytes = state.totalBytes;
        this.downloadSpeed = state.downloadSpeed;
        this.uploadSpeed = state.uploadSpeed;
        this.ETA = state.ETA;
        this.dateAdded = state.dateAdded;
        this.totalPeers = state.totalPeers;
        this.peers = state.peers;
    }

    @Override
    public int compareTo(TorrentListItem another)
    {
        return torrentId.compareTo(another.torrentId);
    }

    @Override
    public int hashCode()
    {
        return torrentId.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof TorrentListItem && (torrentId.equals(((TorrentListItem) o).torrentId)));
    }
}
