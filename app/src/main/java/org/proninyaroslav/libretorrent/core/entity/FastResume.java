package org.proninyaroslav.libretorrent.core.entity;

/*
 * The class encapsulates the fast resume data,
 * more about it see https://www.libtorrent.org/manual-ref.html#fast-resume.
 */

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(indices = {@Index(value = "torrentId")},
        foreignKeys = @ForeignKey(
                entity = Torrent.class,
                parentColumns = "id",
                childColumns = "torrentId",
                onDelete = CASCADE))

public class FastResume
{
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String torrentId;
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    @NonNull
    public byte[] data;

    public FastResume(@NonNull String torrentId, @NonNull byte[] data)
    {
        this.torrentId = torrentId;
        this.data = data;
    }
}
