/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

public class SessionLogMsg
{
    private SessionLogType type;
    private String logMessage;

    public SessionLogMsg(SessionLogType type, String logMessage)
    {
        this.type = type;
        this.logMessage = logMessage;
    }

    public SessionLogType getType()
    {
        return type;
    }

    public String getLogMessage()
    {
        return logMessage;
    }

    @Override
    public String toString()
    {
        return "SessionLogMsg{" +
                "type=" + type +
                ", logMessage='" + logMessage + '\'' +
                '}';
    }
}
