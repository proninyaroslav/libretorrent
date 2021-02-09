/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.core.util.Pair;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

public class IPFilterParserTest
{
    private String dat_file =
            "# Accept this ranges\n" +
            "000.000.000.000 - 000.255.255.255 , 000 , Bogon\n" +
            "2002:0000:0000:0:0:0:0:0 - 2002:00ff:ffff:0:0:0:0:0 , 000 , Bogon\n" +
            "001.002.004.000 - 001.002.004.255 , 000 , China Internet Information Center (CNNIC)\n" +
            "001.002.008.000 - 001.002.008.255 , 000 , China Internet Information Center (CNNIC)\n" +
            "001.009.096.105 - 001.009.096.105 , 000 , Botnet on Telekom Malaysia\n" +
            "001.009.102.251 - 001.009.102.251 , 000 , Botnet on Telekom Malaysia\n" +
            "001.009.106.186 - 001.009.106.186 , 000 , Botnet on Telekom Malaysia\n" +
            "001.016.000.000 - 001.019.255.255 , 000 , Korea Internet & Security Agency (KISA) - IPv6 Policy\n" +
            "001.055.241.140 - 001.055.241.140 , 000 , Botnet on FPT Telecom\n" +
            "// Ignore this ranges\n" +
            "1.093.021.147-001.093.021.147,200,SMSHoax FakeAV Fraud Trojan\n" +
            "001.093.026.097-001.093.026.97,200,SMSHoax FakeAV Fraud Trojan\n";

    private String p2p_file =
            "# This is a comment\n" +
            "Bogon : 000.000.000.000 - 000.255.255.255\n" +
            "China Internet Information Center (CNNIC) : 001.002.004.000 - 001.002.004.255\n" +
            "China Internet Information Center (CNNIC) : 001.002.008.000 - 001.002.008.255\n" +
            "Botnet on Telekom Malaysia : 001.009.096.105 - 001.009.096.105\n" +
            "Botnet on Telekom Malaysia : 001.009.102.251 - 001.009.102.251\n" +
            "Botnet on Telekom Malaysia : 001.009.106.186 - 001.009.106.186\n" +
            "Korea Internet & Security Agency (KISA) - IPv6 Policy : 001.016.000.000 - 001.019.255.255\n" +
            "Botnet on FPT Telecom : 001.055.241.140 - 001.055.241.140\n" +
            "// This is another comment\n" +
            "SMSHoax FakeAV Fraud Trojan:1.093.021.147-001.093.021.147\n" +
            "SMSHoax FakeAV Fraud Trojan:001.093.026.97-001.093.026.097\n";

    private Pair[] dat_expected_ranges = new Pair[] {
            Pair.create("000.000.000.000", "000.255.255.255"),
            Pair.create("2002:0000:0000:0:0:0:0:0", "2002:00ff:ffff:0:0:0:0:0"),
            Pair.create("001.002.004.000", "001.002.004.255"),
            Pair.create("001.002.008.000", "001.002.008.255"),
            Pair.create("001.009.096.105", "001.009.096.105"),
            Pair.create("001.009.102.251", "001.009.102.251"),
            Pair.create("001.009.106.186", "001.009.106.186"),
            Pair.create("001.016.000.000", "001.019.255.255"),
            Pair.create("001.055.241.140", "001.055.241.140"),
    };

    private Pair[] p2p_expected_ranges = new Pair[] {
            Pair.create("000.000.000.000", "000.255.255.255"),
            Pair.create("001.002.004.000", "001.002.004.255"),
            Pair.create("001.002.008.000", "001.002.008.255"),
            Pair.create("001.009.096.105", "001.009.096.105"),
            Pair.create("001.009.102.251", "001.009.102.251"),
            Pair.create("001.009.106.186", "001.009.106.186"),
            Pair.create("001.016.000.000", "001.019.255.255"),
            Pair.create("001.055.241.140", "001.055.241.140"),
            Pair.create("1.093.021.147", "001.093.021.147"),
            Pair.create("001.093.026.97", "001.093.026.097"),
    };

    @Test
    public void parseDAT()
    {
        FakeIPFilter filter = new FakeIPFilter();
        try (InputStream is = IOUtils.toInputStream(dat_file, "UTF-8")) {
            int ruleCount = new IPFilterParser(false).parseDAT(is, filter);
            assertEquals(dat_expected_ranges.length, ruleCount);

            List<Pair<String, String>> ranges = filter.getRanges();
            assertEquals(dat_expected_ranges.length, ranges.size());

            for (int i = 0; i < dat_expected_ranges.length; i++)
                assertEquals(dat_expected_ranges[i], ranges.get(i));

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    public void parseP2P()
    {
        FakeIPFilter filter = new FakeIPFilter();
        try (InputStream is = IOUtils.toInputStream(p2p_file, "UTF-8")) {
            int ruleCount = new IPFilterParser(false).parseP2P(is, filter);
            assertEquals(p2p_expected_ranges.length, ruleCount);

            List<Pair<String, String>> ranges = filter.getRanges();
            assertEquals(p2p_expected_ranges.length, ranges.size());

            for (int i = 0; i < p2p_expected_ranges.length; i++)
                assertEquals(p2p_expected_ranges[i], ranges.get(i));

        } catch (Exception e) {
            fail(e.toString());
        }
    }
}