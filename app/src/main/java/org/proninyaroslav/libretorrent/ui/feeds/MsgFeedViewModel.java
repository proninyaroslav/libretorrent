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

package org.proninyaroslav.libretorrent.ui.feeds;

import androidx.lifecycle.ViewModel;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class MsgFeedViewModel extends ViewModel
{
    private PublishSubject<Long> feedItemsOpenedEvents = PublishSubject.create();
    private PublishSubject<Boolean> feedItemsClosedEvents = PublishSubject.create();
    private PublishSubject<List<Long>> feedsDeletedEvents = PublishSubject.create();

    public void feedItemsOpened(long feedId)
    {
        feedItemsOpenedEvents.onNext(feedId);
    }

    public Observable<Long> observeFeedItemsOpened()
    {
        return feedItemsOpenedEvents;
    }

    public void feedItemsClosed()
    {
        feedItemsClosedEvents.onNext(true);
    }

    public Observable<Boolean> observeFeedItemsClosed()
    {
        return feedItemsClosedEvents;
    }

    public void feedsDeleted(List<Long> ids)
    {
        feedsDeletedEvents.onNext(ids);
    }

    public Observable<List<Long>> observeFeedsDeleted()
    {
        return feedsDeletedEvents;
    }
}
