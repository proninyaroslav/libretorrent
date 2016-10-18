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

package org.proninyaroslav.libretorrent.core.filetree;

import com.frostwire.jlibtorrent.Priority;

import java.io.Serializable;

public class TorrentContentFileTree extends FileTree<TorrentContentFileTree> implements Serializable
{
    private SelectState selected = SelectState.UNSELECTED;
    private Priority priority = Priority.IGNORE;
    private long receivedBytes = 0L;

    public enum SelectState
    {
        SELECTED, UNSELECTED, DISABLED
    }

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

    public Priority getPriority()
    {
        return priority;
    }

    public void setPriority(Priority priority)
    {
        this.priority = priority;

        if (priority == Priority.IGNORE && selected == SelectState.SELECTED) {
            select(SelectState.UNSELECTED);
        } else if (priority != Priority.IGNORE && selected == SelectState.UNSELECTED) {
            select(SelectState.SELECTED);
        }

        /* Sending priority change event up the tree */
        if (parent != null && parent.priority != priority) {
            parent.onChildPriorityChanged(priority);
        }

        /* Sending priority change event down the tree */
        if (children.size() != 0) {
            for (TorrentContentFileTree node : children.values()) {
                if (node.priority != priority) {
                    node.setPriority(priority);
                }
            }
        }
    }

    private synchronized void onChildPriorityChanged(Priority priority)
    {
        if (children.size() != 0) {
            boolean isMixedPriority = false;
            for (TorrentContentFileTree child : children.values()) {
                if (child.priority != priority) {
                    isMixedPriority = true;
                }
            }

            this.priority = (isMixedPriority ? Priority.UNKNOWN : priority);
        }

        /* Sending priority change event up the parent */
        if (parent != null && parent.priority != priority) {
            parent.onChildPriorityChanged(priority);
        }
    }

    public long getReceivedBytes()
    {
        if (children.size() != 0) {
            receivedBytes = 0;
            for (TorrentContentFileTree node : children.values()) {
                receivedBytes += node.getReceivedBytes();
            }
        }

        return receivedBytes;
    }

    public synchronized void setReceivedBytes(long bytes)
    {
        receivedBytes = bytes;
    }

    public SelectState getSelectState()
    {
        return selected;
    }

    public void select(SelectState select)
    {
        selected = select;

        if (selected == SelectState.SELECTED && priority == Priority.IGNORE) {
            setPriority(Priority.NORMAL);
        } else if (selected == SelectState.UNSELECTED && priority != Priority.IGNORE) {
            setPriority(Priority.IGNORE);
        }

        /* Sending select change event up the parent */
        if (parent != null && parent.selected != select) {
            parent.onChildSelectChange();
        }

        /* Sending select change event down the tree */
        if (children.size() != 0) {
            for (TorrentContentFileTree node : children.values()) {
                if (node.selected != select) {
                    node.select(select);
                }
            }
        }
    }

    /*
     * Sending select change events up the tree.
     */

    private synchronized void onChildSelectChange()
    {
        if (children.size() != 0) {
            long childrenSelectedNum = 0, childrenDisabledNum = 0;

            for (TorrentContentFileTree child : children.values()) {
                if (child.selected == SelectState.SELECTED) {
                    ++childrenSelectedNum;
                } else if (child.selected == SelectState.DISABLED) {
                    ++childrenDisabledNum;
                }
            }

            if (childrenDisabledNum > 0) {
                selected = SelectState.DISABLED;
            } else {
            /* Unselect parent only if don't left selected children nodes */
                selected = (childrenSelectedNum > 0 ? SelectState.SELECTED : SelectState.UNSELECTED);
            }
        }

        /* Sending select change event up the parent */
        if (parent != null && parent.selected != selected) {
            parent.onChildSelectChange();
        }
    }

    public long selectedFileSize()
    {
        long size = 0;

        if (children.size() != 0) {
            for (TorrentContentFileTree child : children.values()) {
                if (child.selected != SelectState.UNSELECTED) {
                    size += child.selectedFileSize();
                }
            }

        } else if (selected != SelectState.UNSELECTED) {
            size = this.size();
        }

        return size;
    }

    @Override
    public String toString()
    {
        return "TorrentContentFileTree{" +
                super.toString() +
                ", selected=" + selected +
                ", priority=" + priority +
                ", receivedBytes=" + receivedBytes +
                '}';
    }
}
