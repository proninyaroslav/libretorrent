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

package org.proninyaroslav.libretorrent.core.model.data;

/*
 * The different overall states a torrent can be in.
 */

public enum TorrentStateCode
{
    UNKNOWN(-1),
    ERROR(0),
    /*
     * In this state the torrent has finished downloading and
     * is a pure seeder.
     */
    SEEDING(1),
    /*
     * The torrent is being downloaded. This is the state
     * most torrents will be in most of the time. The progress
     * meter will tell how much of the files that has been
     * downloaded.
     */
    DOWNLOADING(2),
    PAUSED(3),
    STOPPED(4),
    /*
     * The torrent has not started its download yet, and is
     * currently checking existing files.
     */
    CHECKING(5),
    /*
     * The torrent is trying to download metadata from peers.
     * This assumes the metadata_transfer extension is in use.
     */
    DOWNLOADING_METADATA(6),
    /*
     * In this state the torrent has finished downloading but
     * still doesn't have the entire torrent. i.e. some pieces
     * are filtered and won't get downloaded.
     */
    FINISHED(7),
    /*
     * If the torrent was started in full allocation mode, this
     * indicates that the (disk) storage for the torrent is
     * allocated.
     */
    ALLOCATING(8);

    private final int value;

    TorrentStateCode(int value)
    {
        this.value = value;
    }

    public static TorrentStateCode fromValue(int value)
    {
        for (TorrentStateCode ev : TorrentStateCode.class.getEnumConstants())
            if (ev.value() == value)
                return ev;

        return UNKNOWN;
    }

    public int value()
    {
        return value;
    }
}
