/*
 * Copyright (C) 2016-2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.filetree;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class TorrentContentFileTree extends FileTree<TorrentContentFileTree> implements Serializable
{
    private FilePriority priority = new FilePriority(FilePriority.Type.IGNORE);
    private long receivedBytes = 0L;
    private double availability = -1;
    private long numChangedChildren = 0;

    public TorrentContentFileTree(String name, long size, int type)
    {
        super(name, size, type);
    }

    public TorrentContentFileTree(int index, String name,
                                  long size, int type,
                                  TorrentContentFileTree parent)
    {
        super(index, name, size, type, parent);
    }

    public TorrentContentFileTree(String name, long size,
                                  int type, TorrentContentFileTree parent)
    {
        super(name, size, type, parent);
    }

    public FilePriority getFilePriority()
    {
        return priority;
    }

    public long getReceivedBytes()
    {
        if (children.size() != 0) {
            receivedBytes = 0;
            for (TorrentContentFileTree node : children.values())
                receivedBytes += node.getReceivedBytes();
        }

        return receivedBytes;
    }

    public synchronized void setReceivedBytes(long bytes)
    {
        receivedBytes = bytes;
    }

    /*
     * By default, a parent is updated only when all
     * children are updated, for performance reasons.
     * You can override this with the `forceUpdateParent` option
     */

    public void setPriority(@NonNull FilePriority priority, boolean forceUpdateParent)
    {
        changePriority(priority, true, forceUpdateParent);
    }

    private void changePriority(FilePriority p,
                                boolean updateParent,
                                boolean forceUpdateParent)
    {
        priority = p;

        /* Sending change event up the tree */
        if (updateParent && parent != null)
            parent.onChangePriority(p, forceUpdateParent);

        /* Sending change event down the tree */
        if (children.size() != 0)
            for (TorrentContentFileTree node : children.values())
                if (node.priority.getType() != p.getType())
                    node.changePriority(p, false, forceUpdateParent);
    }

    private synchronized void onChangePriority(FilePriority p, boolean forceUpdateParent)
    {
        ++numChangedChildren;

        boolean allChildrenChanged = numChangedChildren == children.size();
        if (allChildrenChanged)
            numChangedChildren = 0;

        if (children.size() != 0 && (forceUpdateParent || allChildrenChanged)) {
            boolean isMixedPriority = false;

            for (TorrentContentFileTree child : children.values()) {
                if (p == null)
                    p = child.getFilePriority();

                if (child.priority.getType() != p.getType()) {
                    isMixedPriority = true;
                    break;
                }
            }

            if (p != null)
                priority = (isMixedPriority ? new FilePriority(FilePriority.Type.MIXED) : p);

            /* Sending change event up the tree */
            if (parent != null)
                parent.onChangePriority(priority, forceUpdateParent);
        }
    }

    public long nonIgnoreFileSize()
    {
        long size = 0;

        if (children.size() != 0) {
            for (TorrentContentFileTree child : children.values())
                if (child.priority.getType() != FilePriority.Type.IGNORE)
                    size += child.nonIgnoreFileSize();
        } else if (priority.getType() != FilePriority.Type.IGNORE) {
            size = this.size();
        }

        return size;
    }

    public synchronized void setAvailability(double availability)
    {
        this.availability = availability;
    }

    public double getAvailability()
    {
        if (children.size() != 0) {
            double avail = 0;
            long size = 0;
            for (TorrentContentFileTree node : children.values()) {
                if (node.getFilePriority().getType() == FilePriority.Type.IGNORE)
                    continue;
                double childAvail = node.getAvailability();
                long childSize = node.size();
                if (childAvail >= 0)
                    avail += childAvail * childSize;
                size += childSize;
            }
            if (size > 0)
                availability = avail / size;
            else
                availability = -1;
        }

        return availability;
    }

    @Override
    public String toString()
    {
        return "TorrentContentFileTree{" +
                super.toString() +
                ", priority=" + priority +
                ", receivedBytes=" + receivedBytes +
                ", availability=" + availability +
                '}';
    }
}
