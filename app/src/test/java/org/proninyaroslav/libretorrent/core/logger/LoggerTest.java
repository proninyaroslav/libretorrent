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

package org.proninyaroslav.libretorrent.core.logger;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

import static org.junit.Assert.*;

public class LoggerTest
{
    @Test
    public void testSend()
    {
        Logger logger = new Logger(5);

        logger.send(new LogEntry(0, "TEST", "1", 1));
        logger.send(new LogEntry(1, "TEST", "2", 2));
        logger.send(new LogEntry(2, "TEST", "3", 3));
        logger.send(new LogEntry(3, "TEST", "4", 4));
        logger.send(new LogEntry(4, "TEST", "5", 5));
        logger.send(new LogEntry(5, "TEST", "6", 6));

        assertEquals(1, logger.getEntry(0).getId());
        assertEquals(2, logger.getEntry(1).getId());
        assertEquals(3, logger.getEntry(2).getId());
        assertEquals(4, logger.getEntry(3).getId());
        assertEquals(5, logger.getEntry(4).getId());
    }

    @Test
    public void testMaxStoredLogs()
    {
        Logger logger = new Logger(5);
        assertEquals(5, logger.getMaxStoredLogs());

        for (int i = 0; i < 100; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i + 1));

        LogEntry[] expected = new LogEntry[] {
                new LogEntry(95, "TEST", "95", 96),
                new LogEntry(96, "TEST", "96", 97),
                new LogEntry(97, "TEST", "97", 98),
                new LogEntry(98, "TEST", "98", 99),
                new LogEntry(99, "TEST", "99", 100),
        };
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], logger.getEntry(i));

        logger.setMaxStoredLogs(6);
        assertEquals(6, logger.getMaxStoredLogs());

        for (int i = 0; i < 11; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        expected = new LogEntry[] {
                new LogEntry(5, "TEST", "5", 5),
                new LogEntry(6, "TEST", "6", 6),
                new LogEntry(7, "TEST", "7", 7),
                new LogEntry(8, "TEST", "8", 8),
                new LogEntry(9, "TEST", "9", 9),
                new LogEntry(10, "TEST", "10", 10),
        };
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], logger.getEntry(i));
    }

    @Test
    public void testGetLogEntry()
    {
        Logger logger = new Logger(1);

        LogEntry entry = new LogEntry(1, "TEST", "1", 1);
        logger.send(entry);

        assertEquals(entry, logger.getEntry(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetLogEntry_outOfBounds()
    {
        Logger logger = new Logger(1);

        LogEntry entry = new LogEntry(1, "TEST", "1", 1);
        logger.send(entry);

        assertEquals(entry, logger.getEntry(1));
    }

    @Test
    public void testGetLogEntries()
    {
        Logger logger = new Logger(50);

        for (int i = 1; i <= 100; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        LogEntry[] expected = new LogEntry[] {
                new LogEntry(60, "TEST", "60", 60),
                new LogEntry(61, "TEST", "61", 61),
                new LogEntry(62, "TEST", "62", 62),
                new LogEntry(63, "TEST", "63", 63),
                new LogEntry(64, "TEST", "64", 64),
                new LogEntry(65, "TEST", "65", 65),
        };

        List<LogEntry> actual = logger.getEntries(9, 5);
        for (int i = 0; i < actual.size(); i++)
            assertEquals(expected[i], actual.get(i));
    }

    @Test
    public void testGetLogEntries_entriesLessThanMaxSize()
    {
        Logger logger = new Logger(10);

        for (int i = 1; i <= 5; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        LogEntry[] expected = new LogEntry[] {
                new LogEntry(3, "TEST", "3", 3),
                new LogEntry(4, "TEST", "4", 4),
                new LogEntry(5, "TEST", "5", 5),
        };

        List<LogEntry> actual = logger.getEntries(2, 5);
        for (int i = 0; i < actual.size(); i++)
            assertEquals(expected[i], actual.get(i));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetLogEntries_indexOutOfBound()
    {
        Logger logger = new Logger(3);

        logger.send(new LogEntry(1, "TEST", "1", 1));
        logger.send(new LogEntry(2, "TEST", "2", 2));
        logger.send(new LogEntry(3, "TEST", "3", 3));

        logger.getEntries(5, 5);
    }

    @Test
    public void testFilter()
    {
        Logger logger = new Logger(20);
        logger.addFilter(new Logger.NewFilter("filter1", (entry) -> entry.getId() % 2 != 0),
                         new Logger.NewFilter("filter2", (entry) -> entry.getId() / 10 == 1));

        for (int i = 1; i <= 20; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        LogEntry[] expected = new LogEntry[]{
                new LogEntry(11, "TEST", "11", 11),
                new LogEntry(13, "TEST", "13", 13),
                new LogEntry(15, "TEST", "15", 15),
                new LogEntry(17, "TEST", "17", 17),
                new LogEntry(19, "TEST", "19", 19),
        };

        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], logger.getEntry(i));

        logger.removeFilter("filter1", "filter2");
        for (int i = 0; i < 20; i++)
            assertEquals(i + 1, logger.getEntry(i).getId());
    }

    @Test
    public void testFilter_alreadyAddedEntries()
    {
        Logger logger = new Logger(20);

        for (int i = 1; i <= 20; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        logger.addFilter(new Logger.NewFilter("filter1", (entry) -> entry.getId() % 2 != 0),
                         new Logger.NewFilter("filter2", (entry) -> entry.getId() / 10 == 1));

        LogEntry[] expected = new LogEntry[]{
                new LogEntry(11, "TEST", "11", 11),
                new LogEntry(13, "TEST", "13", 13),
                new LogEntry(15, "TEST", "15", 15),
                new LogEntry(17, "TEST", "17", 17),
                new LogEntry(19, "TEST", "19", 19),
        };

        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], logger.getEntry(i));
    }

    @Test
    public void testObserveNewLogEntries()
    {
        Logger logger = new Logger(100);
        CountDownLatch c = new CountDownLatch(100);

        Disposable d = logger.observeDataSetChanged()
                .subscribe((change) -> {
                    assertEquals(Logger.DataSetChange.Reason.NEW_ENTRIES, change.reason);
                    assertNotNull(change.entries);
                    for (LogEntry entry : change.entries) {
                        assertNotNull(entry);
                        c.countDown();
                    }
                });

        new Thread(() -> {
            for (int i = 1; i <= 100; i++)
                logger.send(new LogEntry(i, "TEST", "" + i, i));
        }).start();

        try {
            boolean success = c.await(30, TimeUnit.SECONDS);
            assertTrue(success);

        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        d.dispose();
    }

    @Test
    public void testRecording()
    {
        Logger logger = new Logger(10);

        for (int i = 0; i < 16; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        logger.startRecording();
        assertTrue(logger.isRecording());

        for (int i = 16; i < 20; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        String expected =
                "[TEST] 15\n" +
                "[TEST] 16\n" +
                "[TEST] 17\n" +
                "[TEST] 18\n" +
                "[TEST] 19\n";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        assertEquals(5, logger.stopRecording(os));
        assertFalse(logger.isRecording());
        assertEquals(expected, os.toString());
    }

    @Test
    public void testRecording_bufferFull()
    {
        Logger logger = new Logger(10);

        logger.startRecording();
        assertTrue(logger.isRecording());

        for (int i = 0; i < 20; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        String expected =
                "[TEST] 10\n" +
                "[TEST] 11\n" +
                "[TEST] 12\n" +
                "[TEST] 13\n" +
                "[TEST] 14\n" +
                "[TEST] 15\n" +
                "[TEST] 16\n" +
                "[TEST] 17\n" +
                "[TEST] 18\n" +
                "[TEST] 19\n";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        assertEquals(10, logger.stopRecording(os));
        assertFalse(logger.isRecording());
        assertEquals(expected, os.toString());
    }

    @Test
    public void testWrite()
    {
        Logger logger = new Logger(5);

        for (int i = 0; i < 10; i++)
            logger.send(new LogEntry(i, "TEST", "" + i, i));

        String expected =
                "[TEST] 5\n" +
                "[TEST] 6\n" +
                "[TEST] 7\n" +
                "[TEST] 8\n" +
                "[TEST] 9\n";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        assertEquals(5, logger.write(os));
        assertEquals(expected, os.toString());
    }
}