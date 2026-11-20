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

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class PeerInfoTest
{
    @Test
    public void i2pPeerParcelRoundTrip()
    {
        PeerInfo original = new PeerInfo(
                "0.0.0.0",     /* ip - will be 0.0.0.0 for I2P peers */
                "test-client",
                1000L,           /* totalDownload */
                2000L,           /* totalUpload */
                0.5,             /* relevance */
                PeerInfo.ConnectionType.BITTORRENT,
                0,               /* port - 0 for I2P peers */
                50,              /* progress */
                1024,            /* downSpeed */
                2048,            /* upSpeed */
                true             /* isI2p */
        );

        // Write to Parcel
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        // Read back
        PeerInfo restored = new PeerInfo(parcel);

        assertEquals("IP should match", original.ip, restored.ip);
        assertEquals("Client should match", original.client, restored.client);
        assertEquals("totalDownload should match",
                original.totalDownload, restored.totalDownload);
        assertEquals("totalUpload should match",
                original.totalUpload, restored.totalUpload);
        assertEquals("relevance should match",
                original.relevance, restored.relevance, 0.001);
        assertEquals("connectionType should match",
                original.connectionType, restored.connectionType);
        assertEquals("port should match", original.port, restored.port);
        assertEquals("progress should match",
                original.progress, restored.progress);
        assertEquals("downSpeed should match",
                original.downSpeed, restored.downSpeed);
        assertEquals("upSpeed should match",
                original.upSpeed, restored.upSpeed);
        assertTrue("isI2p should be preserved", restored.isI2p);

        parcel.recycle();
    }

    @Test
    public void i2pPeerParcelRoundTrip_nonI2p()
    {
        PeerInfo original = new PeerInfo(
                "1.2.3.4",
                "test-client",
                500L,
                600L,
                0.8,
                PeerInfo.ConnectionType.UTP,
                6881,
                100,
                512,
                256,
                false  /* not I2P */
        );

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PeerInfo restored = new PeerInfo(parcel);

        assertEquals("IP should match", "1.2.3.4", restored.ip);
        assertEquals("port should match", 6881, restored.port);
        assertFalse("isI2p should be false", restored.isI2p);

        parcel.recycle();
    }
}
