/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import java.util.Stack;

/*
 * The modification of algorithm depth-first search for bypass BencodeFileTree.
 */

public class FileTreeDepthFirstSearch<F extends FileTree<F>>
{
    /*
     * Not recommended for use with a large number of nodes
     * due to deterioration of performance, use getLeaves() method
     */

    public F find(F startNode, int index)
    {
        F findResult = null;

        Stack<F> stack = new Stack<>();
        stack.push(startNode);

        while (!stack.empty()) {
            F node = stack.pop();
            if (node == null)
                continue;

            if (node.getIndex() == index)
                findResult = node;
            else
                for (String n : node.getChildrenName())
                    if (!node.isFile())
                        stack.push(node.getChild(n));
        }

        return findResult;
    }

    /*
     * Returns the leaf nodes of the tree.
     */

    public List<F> getLeaves(F startNode)
    {
        List<F> leaves = new ArrayList<>();

        Stack<F> stack = new Stack<>();
        stack.push(startNode);

        while (!stack.empty()) {
            F node = stack.pop();
            if (node == null)
                continue;

            if (node.isFile())
                leaves.add(node);

            for (F n : node.getChildren())
                if (!node.isFile())
                    stack.push(n);
        }

        return leaves;
    }

    public Map<Integer, F> getLeavesAsMap(F startNode)
    {
        Map<Integer, F> leavesAsMap = new HashMap<>();

        Stack<F> stack = new Stack<>();
        stack.push(startNode);

        while (!stack.empty()) {
            F node = stack.pop();
            if (node == null)
                continue;

            if (node.isFile())
                leavesAsMap.put(node.getIndex(), node);

            for (F n : node.getChildren())
                if (!node.isFile())
                    stack.push(n);
        }

        return leavesAsMap;
    }
}
