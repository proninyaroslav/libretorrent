/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

/*
 * The class encapsulates the proxy settings passed in TorrentEngine.
 */

public class ProxySettingsPack
{
    private ProxyType type = ProxyType.NONE;
    private String address = "";
    private String login = "";
    private String  password = "";
    private int port = 0;
    private boolean proxyPeersToo = true;
    private boolean forceProxy = true;

    public enum ProxyType
    {
        NONE(0),
        TOR(1),
        SOCKS4(2),
        SOCKS5(3),
        HTTP(4);

        private final int value;

        ProxyType(int value)
        {
            this.value = value;
        }

        public static ProxyType fromValue(int value)
        {
            ProxyType[] enumValues = ProxyType.class.getEnumConstants();
            for (ProxyType ev : enumValues) {
                if (ev.value() == value) {
                    return ev;
                }
            }

            return NONE;
        }

        public int value()
        {
            return value;
        }
    }

    public ProxySettingsPack()
    {

    }

    public ProxySettingsPack(ProxyType type,
                             String address,
                             String login,
                             String password,
                             int port,
                             boolean proxyPeersToo,
                             boolean forceProxy)
    {
        this.type = type;
        this.address = address;
        this.login = login;
        this.password = password;
        this.port = port;
        this.proxyPeersToo = proxyPeersToo;
        this.forceProxy = forceProxy;
    }

    public ProxyType getType()
    {
        return type;
    }

    public void setType(ProxyType type)
    {
        this.type = type;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getLogin()
    {
        return login;
    }

    public void setLogin(String login)
    {
        this.login = login;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public boolean isProxyPeersToo()
    {
        return proxyPeersToo;
    }

    public void setProxyPeersToo(boolean proxyPeersToo)
    {
        this.proxyPeersToo = proxyPeersToo;
    }

    public boolean isForceProxy()
    {
        return forceProxy;
    }

    public void setForceProxy(boolean forceProxy)
    {
        this.forceProxy = forceProxy;
    }
}
