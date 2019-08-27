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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.peers;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;

public class PeerItem extends PeerInfo
{
    public PeerItem(@NonNull PeerInfo state)
    {
        super(state.ip, state.client, state.totalDownload,
                state.totalUpload, state.relevance, state.connectionType,
                state.port, state.progress, state.downSpeed, state.upSpeed);
    }

    @Override
    public int hashCode()
    {
        return ip.hashCode();
    }

    public boolean equalsContent(Object o)
    {
        return super.equals(o);
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PeerItem))
            return false;

        if (o == this)
            return true;

        return ip.equals(((PeerItem)o).ip);
    }
}
