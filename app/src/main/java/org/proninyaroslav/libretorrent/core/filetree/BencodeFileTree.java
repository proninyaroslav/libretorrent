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

import java.io.Serializable;

/*
 * The class represents a tree model hierarchy of downloadable file.
 */

public class BencodeFileTree extends FileTree<BencodeFileTree> implements Serializable
{
    private boolean selected = false;

    public BencodeFileTree(String name, long size, int type)
    {
        super(name, size, type);
    }

    public BencodeFileTree(int index, String name, long size, int type, BencodeFileTree parent)
    {
        super(index, name, size, type, parent);
    }

    public BencodeFileTree(String name, long size, int type, BencodeFileTree parent)
    {
        super(name, size, type, parent);
    }

    public boolean isSelected()
    {
        return selected;
    }

    public void select(boolean check)
    {
        selected = check;

        /* Sending select change event up the parent */
        if (parent != null && parent.selected != check) {
            parent.onChildSelectChange();
        }

        /* Sending select change event down the tree */
        if (getChildrenCount() != 0) {
            for (BencodeFileTree node : children.values()) {
                if (node.selected != check) {
                    node.select(check);
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
            long childrenCheckNum = 0;
            for (BencodeFileTree child : children.values()) {
                if (child.selected) {
                    ++childrenCheckNum;
                }
            }

        /* Uncheck parent only if don't left selected children nodes */
            selected = childrenCheckNum > 0;
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
            for (BencodeFileTree child : children.values()) {
                if (child.selected) {
                    size += child.selectedFileSize();
                }
            }

        } else if (selected) {
            size = this.size();
        }

        return size;
    }

    @Override
    public String toString()
    {
        return "BencodeFileTree{" +
                super.toString() +
                ", selected=" + selected +
                '}';
    }
}
