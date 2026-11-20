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

package org.proninyaroslav.libretorrent.core.settings;

import static org.junit.Assert.*;

import org.junit.Test;

public class SessionSettingsTest
{
    @Test
    public void i2pDefaults()
    {
        SessionSettings s = new SessionSettings();

        assertFalse("I2P should be disabled by default", s.i2pEnabled);
        assertEquals("Default I2P hostname should be 127.0.0.1",
                "127.0.0.1", s.i2pHostname);
        assertEquals("Default I2P port should be 7656",
                7656, s.i2pPort);
        assertFalse("I2P mixed mode should be disabled by default",
                s.allowI2pMixed);
        assertEquals("Default inbound tunnel quantity should be 3",
                3, s.i2pInboundQuantity);
        assertEquals("Default outbound tunnel quantity should be 3",
                3, s.i2pOutboundQuantity);
        assertEquals("Default inbound tunnel length should be 3",
                3, s.i2pInboundLength);
        assertEquals("Default outbound tunnel length should be 3",
                3, s.i2pOutboundLength);
        assertEquals("Default inbound length variance should be 0",
                0, s.i2pInboundLengthVariance);
        assertEquals("Default outbound length variance should be 0",
                0, s.i2pOutboundLengthVariance);
    }

    @Test
    public void i2pCopyConstructor()
    {
        SessionSettings original = new SessionSettings();
        original.i2pEnabled = true;
        original.i2pHostname = "192.168.1.1";
        original.i2pPort = 4444;
        original.allowI2pMixed = true;
        original.i2pInboundQuantity = 5;
        original.i2pOutboundQuantity = 7;
        original.i2pInboundLength = 2;
        original.i2pOutboundLength = 4;
        original.i2pInboundLengthVariance = 1;
        original.i2pOutboundLengthVariance = 2;

        SessionSettings copy = new SessionSettings(original);

        assertTrue("i2pEnabled should be copied", copy.i2pEnabled);
        assertEquals("i2pHostname should be copied",
                "192.168.1.1", copy.i2pHostname);
        assertEquals("i2pPort should be copied", 4444, copy.i2pPort);
        assertTrue("allowI2pMixed should be copied", copy.allowI2pMixed);
        assertEquals("i2pInboundQuantity should be copied",
                5, copy.i2pInboundQuantity);
        assertEquals("i2pOutboundQuantity should be copied",
                7, copy.i2pOutboundQuantity);
        assertEquals("i2pInboundLength should be copied",
                2, copy.i2pInboundLength);
        assertEquals("i2pOutboundLength should be copied",
                4, copy.i2pOutboundLength);
        assertEquals("i2pInboundLengthVariance should be copied",
                1, copy.i2pInboundLengthVariance);
        assertEquals("i2pOutboundLengthVariance should be copied",
                2, copy.i2pOutboundLengthVariance);
    }

    @Test
    public void i2pCopyConstructorDoesNotAlias()
    {
        SessionSettings original = new SessionSettings();
        original.i2pEnabled = true;
        original.i2pHostname = "original";

        SessionSettings copy = new SessionSettings(original);
        copy.i2pHostname = "modified";

        assertEquals("Original should be unchanged",
                "original", original.i2pHostname);
        assertEquals("Copy should have new value",
                "modified", copy.i2pHostname);
    }

    @Test
    public void i2pConstants()
    {
        assertEquals("127.0.0.1", SessionSettings.DEFAULT_I2P_HOSTNAME);
        assertEquals(7656, SessionSettings.DEFAULT_I2P_PORT);
        assertFalse(SessionSettings.DEFAULT_ALLOW_I2P_MIXED);
        assertEquals(3, SessionSettings.DEFAULT_I2P_INBOUND_QUANTITY);
        assertEquals(3, SessionSettings.DEFAULT_I2P_OUTBOUND_QUANTITY);
        assertEquals(3, SessionSettings.DEFAULT_I2P_INBOUND_LENGTH);
        assertEquals(3, SessionSettings.DEFAULT_I2P_OUTBOUND_LENGTH);
        assertEquals(0, SessionSettings.DEFAULT_I2P_INBOUND_LENGTH_VARIANCE);
        assertEquals(0, SessionSettings.DEFAULT_I2P_OUTBOUND_LENGTH_VARIANCE);
    }
}
