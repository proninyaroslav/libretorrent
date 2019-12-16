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

import androidx.annotation.Nullable;

import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.AlertType;
import org.libtorrent4j.alerts.DhtLogAlert;
import org.libtorrent4j.alerts.LogAlert;
import org.libtorrent4j.alerts.PeerLogAlert;
import org.libtorrent4j.alerts.PortmapLogAlert;
import org.libtorrent4j.alerts.TorrentLogAlert;

import java.util.EnumSet;

class SessionLogger
{
    private EnumSet<SessionLogType> allowedLogTypes;

    SessionLogger()
    {
        allowedLogTypes = EnumSet.allOf(SessionLogType.class);
    }

    void setAllowedLogTypes(@Nullable EnumSet<SessionLogType> types)
    {
        allowedLogTypes = (types == null ? EnumSet.noneOf(SessionLogType.class) : types);
    }

    @Nullable
    SessionLogMsg makeLogMsg(Alert<?> alert)
    {
        AlertType t = alert.type();
        if (t == AlertType.LOG && allowedLogTypes.contains(SessionLogType.SESSION_LOG)) {
            return new SessionLogMsg(SessionLogType.SESSION_LOG, ((LogAlert)alert).logMessage());

        } else if (t == AlertType.DHT_LOG && allowedLogTypes.contains(SessionLogType.DHT_LOG)) {
            DhtLogAlert dhtLogAlert = (DhtLogAlert)alert;
            String msg = "[" + dhtLogAlert.module().name() + "]" + dhtLogAlert.logMessage();
            return new SessionLogMsg(SessionLogType.DHT_LOG, msg);

        } else if (t == AlertType.PEER_LOG && allowedLogTypes.contains(SessionLogType.PEER_LOG)) {
            return new SessionLogMsg(SessionLogType.PEER_LOG, ((PeerLogAlert)alert).logMessage());

        } else if (t == AlertType.PORTMAP_LOG && allowedLogTypes.contains(SessionLogType.PORTMAP_LOG)) {
            PortmapLogAlert portmapLogAlert = (PortmapLogAlert)alert;
            String msg = "[" + portmapLogAlert.mapType().name() + "]" + portmapLogAlert.logMessage();
            return new SessionLogMsg(SessionLogType.PORTMAP_LOG, msg);

        } else if (t == AlertType.TORRENT_LOG && allowedLogTypes.contains(SessionLogType.TORRENT_LOG)) {
            return new SessionLogMsg(SessionLogType.TORRENT_LOG, ((TorrentLogAlert)alert).logMessage());

        } else {
            return null;
        }
    }
}
