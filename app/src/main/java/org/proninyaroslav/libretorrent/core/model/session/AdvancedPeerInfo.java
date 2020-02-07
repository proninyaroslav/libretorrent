/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core.model.session;

import org.libtorrent4j.PeerInfo;
import org.libtorrent4j.PieceIndexBitfield;
import org.libtorrent4j.swig.peer_info;

/*
 * Extension of org.libtorrent4j.PeerInfo class with additional information
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
