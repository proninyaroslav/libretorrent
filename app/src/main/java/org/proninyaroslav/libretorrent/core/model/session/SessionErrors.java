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

import androidx.annotation.NonNull;

import org.libtorrent4j.ErrorCode;

class SessionErrors
{
    private static class Error
    {
        int errCode;
        String errMsg;

        Error(int errCode, String errMsg)
        {
            this.errCode = errCode;
            this.errMsg = errMsg;
        }
    }

    private static final Error[] errors = new Error[] {
            new Error(11, "Try again"),
            new Error(22, "Invalid argument"),
    };

    static boolean isNonCritical(@NonNull ErrorCode error)
    {
        if (error.isError())
            return true;

        for (Error nonCriticalError : errors) {
            if (error.value() == nonCriticalError.errCode &&
                nonCriticalError.errMsg.equalsIgnoreCase(error.message()))
                return true;
        }

        return false;
    }

    static String getErrorMsg(ErrorCode error)
    {
        return (error == null ? "" : error.message() + ", code " + error.value());
    }
}
