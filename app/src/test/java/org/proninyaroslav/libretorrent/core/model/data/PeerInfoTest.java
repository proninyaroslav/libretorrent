/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class PeerInfoTest
{
    @Test
    public void i2pPeerEquality()
    {
        PeerInfo i2p1 = new PeerInfo(
                "0.0.0.0", "client", 0, 0, 0.0,
                PeerInfo.ConnectionType.BITTORRENT, 0, 0, 0, 0, true);

        PeerInfo i2p2 = new PeerInfo(
                "0.0.0.0", "client", 0, 0, 0.0,
                PeerInfo.ConnectionType.BITTORRENT, 0, 0, 0, 0, true);

        PeerInfo nonI2p = new PeerInfo(
                "0.0.0.0", "client", 0, 0, 0.0,
                PeerInfo.ConnectionType.BITTORRENT, 0, 0, 0, 0, false);

        assertEquals("Two I2P peers with same fields should be equal",
                i2p1, i2p2);
        assertNotEquals("I2P peer and non-I2P peer with same IP should differ",
                i2p1, nonI2p);
    }

    @Test
    public void i2pPeerHashCode()
    {
        PeerInfo i2p1 = new PeerInfo(
                "0.0.0.0", "client", 0, 0, 0.0,
                PeerInfo.ConnectionType.BITTORRENT, 0, 0, 0, 0, true);

        PeerInfo i2p2 = new PeerInfo(
                "0.0.0.0", "client", 0, 0, 0.0,
                PeerInfo.ConnectionType.BITTORRENT, 0, 0, 0, 0, true);

        assertEquals("Equal objects must have equal hashCodes",
                i2p1.hashCode(), i2p2.hashCode());
    }
}
