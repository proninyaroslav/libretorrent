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

import org.proninyaroslav.libretorrent.core.settings.SessionSettings;

public class SessionInitParams
{
    public int portRangeFirst;
    public int portRangeSecond;
    public SessionSettings.ProxyType proxyType;
    public String proxyAddress;
    public int proxyPort;
    public boolean proxyPeersToo;
    public boolean proxyRequiresAuth;
    public String proxyLogin;
    public String proxyPassword;

    public SessionInitParams()
    {
        portRangeFirst = SessionSettings.DEFAULT_PORT_RANGE_FIRST;
        portRangeSecond = SessionSettings.DEFAULT_PORT_RANGE_SECOND;
        proxyType = SessionSettings.DEFAULT_PROXY_TYPE;
        proxyAddress = SessionSettings.DEFAULT_PROXY_ADDRESS;
        proxyPort = SessionSettings.DEFAULT_PROXY_PORT;
        proxyPeersToo = SessionSettings.DEFAULT_PROXY_PEERS_TOO;
        proxyRequiresAuth = SessionSettings.DEFAULT_PROXY_REQUIRES_AUTH;
        proxyLogin = SessionSettings.DEFAULT_PROXY_LOGIN;
        proxyPassword = SessionSettings.DEFAULT_PROXY_PASSWORD;
    }
}
