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

package org.proninyaroslav.libretorrent.core.utils;

import org.proninyaroslav.libretorrent.core.filetree.FileTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * The modification of algorithm depth-first search for bypass BencodeFileTree.
 *
 * TODO: make non-recursive
 */

public class FileTreeDepthFirstSearch<F extends FileTree<F>>
{
    private List<F> leaves = new ArrayList<>();
    private Map<Integer, F> leavesAsMap = new HashMap<>();
    private int findIndex = -1;
    private F findResult;

    /*
     * Not recommended for use with a large number of nodes
     * due to deterioration of performance, use getLeaves() method
     */

    public F find(F node, int index)
    {
        if (node == null) {
            return null;
        }

        if (findIndex == -1) {
            findIndex = index;
        }

        if (node.getIndex() == findIndex) {
            findResult = node;
        } else {
            for (String n : node.getChildrenName()) {
                if (!node.isFile()) {
                    find(node.getChild(n), index);
                }
            }
        }

        findIndex = -1;

        return findResult;
    }

    /*
     * Returns the leaf nodes of the tree.
     */

    public List<F> getLeaves(F node)
    {
        if (node == null) {
            return null;
        }

        if (node.isFile()) {
            leaves.add(node);
        }

        for (F n : node.getChildren()) {
            if (!node.isFile()) {
                getLeaves(n);
            }
        }

        return leaves;
    }

    public Map<Integer, F> getLeavesAsMap(F node)
    {
        if (node == null) {
            return null;
        }

        if (node.isFile()) {
            leavesAsMap.put(node.getIndex(), node);
        }

        for (F n : node.getChildren()) {
            if (!node.isFile()) {
                getLeavesAsMap(n);
            }
        }

        return leavesAsMap;
    }
}
