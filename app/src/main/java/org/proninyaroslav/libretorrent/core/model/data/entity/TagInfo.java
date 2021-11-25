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

package org.proninyaroslav.libretorrent.core.model.data.entity;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class TagInfo implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public final String name;
    public final int color;

    public TagInfo(long id, @NonNull String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    @Ignore
    public TagInfo(@NonNull String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Ignore
    public TagInfo(Parcel source) {
        id = source.readLong();
        name = source.readString();
        color = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeInt(color);
    }

    public static final Parcelable.Creator<TagInfo> CREATOR = new Parcelable.Creator<TagInfo>() {
        @Override
        public TagInfo createFromParcel(Parcel source) {
            return new TagInfo(source);
        }

        @Override
        public TagInfo[] newArray(int size) {
            return new TagInfo[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagInfo tagInfo = (TagInfo) o;

        if (id != tagInfo.id) return false;
        if (color != tagInfo.color) return false;
        return name.equals(tagInfo.name);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + name.hashCode();
        result = 31 * result + color;
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "TagInfo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", color=" + color +
                '}';
    }
}
