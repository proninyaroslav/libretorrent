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

import java.text.SimpleDateFormat;
import java.util.Locale;

public class LogEntry
{
    private static final String defaultTimeStampFormatter = "yyyy-MM-dd HH:mm:ss.SSS";

    private int id;
    @NonNull
    private String tag;
    @NonNull
    private String msg;
    private long timeStamp;
    private SimpleDateFormat timeStampFormatter;

    public LogEntry(int id, @NonNull String tag,@NonNull String msg, long timeStamp)
    {
        this.id = id;
        this.tag = tag;
        this.msg = msg;
        this.timeStamp = timeStamp;
        this.timeStampFormatter = new SimpleDateFormat(defaultTimeStampFormatter,
                Locale.getDefault());
    }

    public int getId()
    {
        return id;
    }

    @NonNull
    public String getTag()
    {
        return tag;
    }

    @NonNull
    public String getMsg()
    {
        return msg;
    }

    public long getTimeStamp()
    {
        return timeStamp;
    }

    public String getTimeStampAsString()
    {
        return timeStampFormatter.format(this.timeStamp);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        LogEntry entry = (LogEntry)o;

        return id == entry.id &&
                timeStamp == entry.timeStamp &&
                tag.equals(entry.tag) &&
                msg.equals(entry.msg);
    }

    @Override
    public int hashCode()
    {
        int result = id;
        result = 31 * result + tag.hashCode();
        result = 31 * result + msg.hashCode();
        result = 31 * result + (int) (timeStamp ^ (timeStamp >>> 32));

        return result;
    }

    public String toStringWithTimeStamp()
    {
        return getTimeStampAsString() + " " + toString();
    }

    @Override
    public String toString()
    {
        return "[" + tag + "] " + msg;
    }
}
