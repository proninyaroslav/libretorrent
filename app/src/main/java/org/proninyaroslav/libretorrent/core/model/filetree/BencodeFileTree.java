/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import java.io.Serializable;

/*
 * The class represents a tree model hierarchy of downloadable file.
 */

public class BencodeFileTree extends FileTree<BencodeFileTree> implements Serializable
{
    private boolean selected = false;
    private long numChangedChildren = 0;

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

    /*
     * By default, a parent is updated only when all
     * children are updated, for performance reasons.
     * You can override this with the `forceUpdateParent` option
     */

    public void select(boolean check, boolean forceUpdateParent)
    {
        doSelect(check, true, forceUpdateParent);
    }

    private void doSelect(boolean check, boolean updateParent, boolean forceUpdateParent)
    {
        selected = check;

        /* Sending select change event up the parent */
        if (updateParent && parent != null)
            parent.onChildSelectChange(forceUpdateParent);

        /* Sending select change event down the tree */
        if (getChildrenCount() != 0)
            for (BencodeFileTree node : children.values())
                if (node.selected != check)
                    node.doSelect(check, false, forceUpdateParent);
    }

    /*
     * Sending select change events up the tree.
     */

    private synchronized void onChildSelectChange(boolean forceUpdateParent)
    {
        ++numChangedChildren;

        boolean allChildrenChanged = numChangedChildren == children.size();
        if (allChildrenChanged)
            numChangedChildren = 0;

        if (children.size() != 0 && (forceUpdateParent || allChildrenChanged)) {
            long childrenCheckNum = 0;
            for (BencodeFileTree child : children.values())
                if (child.selected)
                    ++childrenCheckNum;
            /* Uncheck parent only if don't left selected children nodes */
            selected = childrenCheckNum > 0;

            /* Sending select change event up the parent */
            if (parent != null)
                parent.onChildSelectChange(forceUpdateParent);
        }
    }

    public long selectedFileSize()
    {
        long size = 0;

        if (children.size() != 0) {
            for (BencodeFileTree child : children.values())
                if (child.selected)
                    size += child.selectedFileSize();

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
