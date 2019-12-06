/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.Priority;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class TorrentDownloadImplTest
{
    @Test
    public void getFileIndicesBep53Test()
    {
        String baseMagnet = "magnet:?xt=urn:btih:cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd&so=";
        final int n = 100;
        final int indicesLen = 100;
        Random rand = new Random();

        for (int i = 0; i < n; i++) {
            Priority[] expectedPriorities = rand
                            .ints(indicesLen, Priority.IGNORE.swig(), Priority.TWO.swig())
                            .mapToObj(Priority::fromSwig)
                            .toArray(Priority[]::new);

            String indicesStr = TorrentDownloadImpl.getFileIndicesBep53(expectedPriorities);
            AddTorrentParams params = null;
            try {
                params = AddTorrentParams.parseMagnetUri(baseMagnet + indicesStr);

            } catch (IllegalArgumentException e) {
                fail(e.getMessage());
            }

            Priority[] actualPriorities = params.filePriorities();
            boolean equal = true;
            for (int j = 0; j < actualPriorities.length; j++) {
                if (expectedPriorities[j].swig() != actualPriorities[j].swig()) {
                    equal = false;
                    break;
                }
            }

            assertFalse("expected: " + Arrays.toString(expectedPriorities) + "\n" +
                    "actual: " + "[" + indicesStr + "]; " + Arrays.toString(actualPriorities), equal);
        }
    }
}
