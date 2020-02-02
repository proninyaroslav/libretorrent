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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.proninyaroslav.libretorrent.core.collections.FixedRingBuffer;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class Logger
{
    protected static final long POLL_TIME_INTERVAL = 250; /* ms */

    protected FixedRingBuffer<LogEntry> inputBuf;
    protected  FixedRingBuffer<LogEntry> outputBuf;
    /* `numPendingLogs` <= `maxStoredLogs` */
    protected int numPendingLogs;
    protected HashMap<String, LogFilter> filters = new HashMap<>();
    protected ReentrantLock logLock = new ReentrantLock();
    protected int maxStoredLogs;
    protected PublishSubject<DataSetChange> dataSetChangedPublish = PublishSubject.create();
    protected ExecutorService sender = Executors.newSingleThreadExecutor();
    protected Thread pendingThread;
    protected boolean paused;
    protected boolean recording;
    protected int recordStartIndex = -1;

    public Logger(int maxStoredLogs)
    {
        if (maxStoredLogs <= 0)
            throw new IllegalArgumentException("Maximum stored logs must be greater than 0");

        this.maxStoredLogs = maxStoredLogs;
    }

    private FixedRingBuffer<LogEntry> lazyGetInputBuf()
    {
        if (inputBuf == null)
            inputBuf = new FixedRingBuffer<>(calcInitBufCapacity(maxStoredLogs), maxStoredLogs);

        if (pendingThread == null) {
            pendingThread = new Thread(this::periodicSwapBuffers);
            pendingThread.start();

        } else if (!pendingThread.isAlive()) {
            try {
                pendingThread.start();

            } catch (IllegalThreadStateException e) {
                /* Already started, ignore */
            }
        }

        return inputBuf;
    }

    private FixedRingBuffer<LogEntry> lazyGetOutputBuf()
    {
        if (outputBuf == null)
            outputBuf = new FixedRingBuffer<>(calcInitBufCapacity(maxStoredLogs), maxStoredLogs);

        return outputBuf;
    }

    private int calcInitBufCapacity(int capacity)
    {
        return (int) Math.floor(capacity / 2);
    }

    protected void send(@NonNull LogEntry entry)
    {
        logLock.lock();

        try {
            lazyGetInputBuf().add(entry);
            if (numPendingLogs < maxStoredLogs)
                numPendingLogs++;

        } finally {
            logLock.unlock();
        }
    }

    private void periodicSwapBuffers()
    {
        while (!Thread.interrupted()) {
            if (!paused) {
                if (logLock.tryLock()) {
                    try {
                        if (Thread.interrupted())
                            break;

                        swapBuffers();

                    } finally {
                        logLock.unlock();
                    }
                }
            }

            try {
                Thread.sleep(POLL_TIME_INTERVAL);

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void swapBuffers()
    {
        if (numPendingLogs == 0)
            return;

        FixedRingBuffer<LogEntry> inputBuf = lazyGetInputBuf();
        if (inputBuf.isEmpty())
            return;
        FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();

        ArrayList<LogEntry> newEntries = new ArrayList<>(numPendingLogs);
        int size = inputBuf.size();
        for (int i = size - numPendingLogs; i < size; i++) {
            LogEntry entry = applyFilters(inputBuf.get(i));
            if (entry != null) {
                outputBuf.add(entry);
                newEntries.add(entry);

                /* Move old position back */
                if (recording && recordStartIndex > 0)
                    recordStartIndex--;
            }
        }
        numPendingLogs = 0;

        if (!newEntries.isEmpty())
            submitDataSetChanged(new DataSetChange(DataSetChange.Reason.NEW_ENTRIES, newEntries));
    }

    private void submitDataSetChanged(DataSetChange change)
    {
        sender.submit(() -> dataSetChangedPublish.onNext(change));
    }

    /*
     * Set the maximum number of stored log entries in memory
     */

    public void setMaxStoredLogs(int max)
    {
        logLock.lock();

        try {
            doClean();
            maxStoredLogs = max;

        } finally {
            logLock.unlock();
        }
    }

    public int getMaxStoredLogs()
    {
        logLock.lock();

        try {
            return maxStoredLogs;

        } finally {
            logLock.unlock();
        }
    }

    public void addFilter(@NonNull NewFilter... filters)
    {
        logLock.lock();

        try {
            int addedFilters = 0;
            for (NewFilter filter : filters) {
                if (filter == null)
                    continue;

                this.filters.put(filter.name, filter.filter);
                addedFilters++;
            }
            if (addedFilters > 0)
                forceFilterBuf();

        } finally {
            logLock.unlock();
        }
    }

    public void removeFilter(@NonNull String... filterNames)
    {
        logLock.lock();

        try {
            int removedFilters = 0;
            for (String name : filterNames) {
                if (name == null)
                    continue;

                if (filters.remove(name) != null)
                    removedFilters++;
            }
            if (removedFilters > 0)
                forceFilterBuf();

        } finally {
            logLock.unlock();
        }
    }

    private void forceFilterBuf()
    {
        FixedRingBuffer<LogEntry> inputBuf = lazyGetInputBuf();
        FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();

        outputBuf.clear();
        for (LogEntry entry : inputBuf) {
            entry = applyFilters(entry);
            if (entry != null)
                outputBuf.add(entry);
        }

        submitDataSetChanged(new DataSetChange(DataSetChange.Reason.FILTER));
    }

    public Observable<DataSetChange> observeDataSetChanged()
    {
        return dataSetChangedPublish;
    }

    /*
     * Returns a list of log entries for a given range.
     * `startPos` must be less than number of maximum stored logs.
     * There can be fewer entries than `maxSize`.
     * Entries can be null.
     */

    public List<LogEntry> getEntries(int startPos, int maxSize)
    {
        logLock.lock();

        try {
            if (startPos < 0 || startPos >= maxStoredLogs)
                throw new IllegalArgumentException("Invalid start position = " + startPos);
            if (maxSize < 0)
                throw new IllegalArgumentException("Size must be greater than 0");

            FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();
            swapBuffers();

            ArrayList<LogEntry> res = new ArrayList<>(maxSize);
            int endPos = startPos + maxSize;
            for (int i = startPos; i < endPos; i++) {
                if (i >= outputBuf.size())
                    continue;

                res.add(outputBuf.get(i));
            }

            return res;

        } finally {
            logLock.unlock();
        }
    }

    @Nullable
    public LogEntry getEntry(int pos)
    {
        logLock.lock();

        try {
            FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();
            swapBuffers();

            if (pos < 0 || pos >= outputBuf.size())
                throw new IllegalArgumentException("Invalid position = " + pos);

            return outputBuf.get(pos);

        } finally {
            logLock.unlock();
        }
    }

    private LogEntry applyFilters(LogEntry entry)
    {
        for (LogFilter f : filters.values()) {
            if (!f.apply(entry))
                return null;
        }

        return entry;
    }

    public void startRecording()
    {
        logLock.lock();

        try {
            swapBuffers();

            recording = true;
            int size = lazyGetOutputBuf().size();
            recordStartIndex = (size > 0 ? size - 1 : 0);

        } finally {
            logLock.unlock();
        }
    }

    public boolean isRecording()
    {
        logLock.lock();

        try {
            return recording;

        } finally {
            logLock.unlock();
        }
    }

    /*
     * If `os` != null, writes entries from the recording start position to the last entry.
     * Returns the number of written log entries
     */

    public int stopRecording()
    {
        return stopRecording(null, false);
    }

    public int stopRecording(@Nullable OutputStream os)
    {
        return stopRecording(os, false);
    }

    public int stopRecording(@Nullable OutputStream os, boolean timeStamp)
    {
        logLock.lock();

        try {
            int count = 0;

            if (os != null) {
                swapBuffers();

                if (recordStartIndex < 0)
                    return count;

                FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();

                return write(outputBuf, os, recordStartIndex, outputBuf.size() - 1, timeStamp);
            }

            return count;

        } finally {
            recording = false;
            recordStartIndex = -1;

            logLock.unlock();
        }
    }

    /*
     * Writes all entries from the start position to the last entry.
     * Returns the number of written log entries
     */

    public int write(@NonNull OutputStream os)
    {
        return write(os, false);
    }

    public int write(@NonNull OutputStream os, boolean timeStamp)
    {
        logLock.lock();

        try {
            swapBuffers();
            FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();

            return write(outputBuf, os, 0, outputBuf.size() - 1, timeStamp);

        } finally {
            logLock.unlock();
        }
    }

    private int write(FixedRingBuffer<LogEntry> buf, OutputStream os,
                      int startPos, int endPos, boolean timeStamp)
    {
        if (startPos < 0)
            throw new IllegalArgumentException("startPos < 0");

        int count = 0;

        PrintStream printStream = new PrintStream(os, true);

        for (int i = startPos; i <= endPos; i++) {
            LogEntry entry = buf.get(i);
            printStream.println(timeStamp ? entry.toStringWithTimeStamp() : entry.toString());
            if (!printStream.checkError())
                count++;
        }

        return count;
    }

    public int getNumEntries()
    {
        logLock.lock();

        try {
            FixedRingBuffer<LogEntry> outputBuf = lazyGetOutputBuf();
            swapBuffers();

            return outputBuf.size();

        } finally {
            logLock.unlock();
        }
    }

    public void pause()
    {
        paused = true;
    }

    public void resume()
    {
        paused = false;
    }

    public boolean isPaused()
    {
        return paused;
    }

    public void clean()
    {
        logLock.lock();

        try {
            doClean();

        } finally {
            logLock.unlock();
        }
    }

    private void doClean()
    {
        if (pendingThread != null)
            pendingThread.interrupt();
        pendingThread = null;
        inputBuf = null;
        outputBuf = null;
        numPendingLogs = 0;
        if (recording)
            recordStartIndex = 0;

        submitDataSetChanged(new DataSetChange(DataSetChange.Reason.NEW_ENTRIES));
    }

    public static class NewFilter
    {
        String name;
        LogFilter filter;

        public NewFilter(@NonNull String name, @NonNull LogFilter filter)
        {
            this.name = name;
            this.filter = filter;
        }
    }

    public static class DataSetChange
    {
        public enum Reason
        {
            NEW_ENTRIES,
            CLEAN,
            FILTER,
        }

        @Nullable
        public final List<LogEntry> entries;
        @NonNull
        public final Reason reason;

        DataSetChange(@NonNull Reason reason)
        {
            this(reason, null);
        }

        DataSetChange(@NonNull Reason reason, @Nullable List<LogEntry> entries)
        {
            this.entries = entries;
            this.reason = reason;
        }
    }
}
