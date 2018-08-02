package org.proninyaroslav.libretorrent.core;

import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.swig.peer_info;

/*
 * Extension of com.frostwire.jlibtorrent.PeerInfo class with additional information
 */

public class AdvancedPeerInfo extends PeerInfo
{
    protected int port;
    protected PieceIndexBitfield pieces;
    protected boolean isUtp;

    public AdvancedPeerInfo(peer_info p)
    {
        super(p);

        port = p.getIp().port();
        pieces = new PieceIndexBitfield(p.getPieces());
        isUtp = p.getFlags().and_(peer_info.utp_socket).nonZero();
    }

    public int port()
    {
        return port;
    }

    /*
     * A bitfield, with one bit per piece in the torrent. Each bit tells you
     * if the peer has that piece (if it's set to 1) or if the peer miss that
     * piece (set to 0).
     */

    public PieceIndexBitfield pieces()
    {
        return pieces;
    }

    public boolean isUtp()
    {
        return isUtp;
    }
}
