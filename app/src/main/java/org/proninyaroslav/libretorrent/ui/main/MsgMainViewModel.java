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

package org.proninyaroslav.libretorrent.ui.main;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class MsgMainViewModel extends ViewModel
{
    private PublishSubject<String> torrentDetailsOpenedEvents = PublishSubject.create();
    private PublishSubject<Boolean> torrentDetailsClosedEvents = PublishSubject.create();

    public void torrentDetailsOpened(@NonNull String id)
    {
        torrentDetailsOpenedEvents.onNext(id);
    }

    public Observable<String> observeTorrentDetailsOpened()
    {
        return torrentDetailsOpenedEvents;
    }

    public void torrentDetailsClosed()
    {
        torrentDetailsClosedEvents.onNext(true);
    }

    public Observable<Boolean> observeTorrentDetailsClosed()
    {
        return torrentDetailsClosedEvents;
    }
}
