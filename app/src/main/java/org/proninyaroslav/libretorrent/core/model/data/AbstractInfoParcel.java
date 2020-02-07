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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

/*
 * The class provides an abstract package model, sent from the service.
 */

public abstract class AbstractInfoParcel
        implements Parcelable, Comparable
{
    public String parcelId;

    protected AbstractInfoParcel()
    {
        parcelId = UUID.randomUUID().toString();
    }

    protected AbstractInfoParcel(String parcelId)
    {
        this.parcelId = parcelId;
    }

    protected AbstractInfoParcel(Parcel source)
    {
        parcelId = source.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(parcelId);
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object o);
}
