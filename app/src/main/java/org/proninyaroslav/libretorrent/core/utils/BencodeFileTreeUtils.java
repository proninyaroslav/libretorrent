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

import androidx.core.util.Pair;

import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.model.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.model.filetree.FileTree;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * The static class for create and using BencodeFileTree objects.
 *
 * TODO: need refactor (with TorrentContentFileTreeUtils)
 */

public class BencodeFileTreeUtils
{
    /*
     * Returns tree and its files
     */

    public static Pair<BencodeFileTree, BencodeFileTree[]> buildFileTree(List<BencodeFileItem> files)
    {
        BencodeFileTree root = new BencodeFileTree(FileTree.ROOT, 0L, FileNode.Type.DIR);
        BencodeFileTree parentTree = root;
        BencodeFileTree[] leaves = new BencodeFileTree[files.size()];
        /* It allows reduce the number of iterations on the paths with equal beginnings */
        String prevPath = "";
        List<BencodeFileItem> filesCopy = new ArrayList<>(files);
        /* Sort reduces the returns number to root */
        Collections.sort(filesCopy);

        for (BencodeFileItem file : filesCopy) {
            String path;
            /*
             * Compare previous path with new path.
             * Example:
             * prev = dir1/dir2/
             * cur  = dir1/dir2/file1
             *        |________|
             *          equal
             *
             * prev = dir1/dir2/
             * cur  = dir3/file2
             *        |________|
             *         not equal
             */
            if (!prevPath.isEmpty() &&
                    file.getPath().regionMatches(true, 0, prevPath, 0, prevPath.length())) {
                /*
                 * If beginning paths are equal, remove previous path from the new path.
                 * Example:
                 * prev = dir1/dir2/
                 * cur  = dir1/dir2/file1
                 * new  = file1
                 */
                path = file.getPath().substring(prevPath.length());
            } else {
                /* If beginning paths are not equal, return to root */
                path = file.getPath();
                parentTree = root;
            }

            String[] nodes = path.split(File.separator);
            /*
             * Remove last node (file) from previous path.
             * Example:
             * cur = dir1/dir2/file1
             * new = dir1/dir2/
             */
            prevPath = file.getPath()
                    .substring(0, file.getPath().length() - nodes[nodes.length - 1].length());

            /* Iterates path nodes */
            for (int i = 0; i < nodes.length; i++) {
                if (!parentTree.contains(nodes[i])) {
                    /* The last leaf item is a file */
                    BencodeFileTree leaf = makeObject(file.getIndex(), nodes[i],
                                                    file.getSize(), parentTree,
                                                    i == (nodes.length - 1));
                    leaves[file.getIndex()] = leaf;
                    parentTree.addChild(leaf);
                }

                BencodeFileTree nextParent = parentTree.getChild(nodes[i]);
                /* Skipping leaf nodes */
                if (!nextParent.isFile())
                    parentTree = nextParent;
            }
        }

        return Pair.create(root, leaves);
    }

    private static BencodeFileTree makeObject(int index, String name,
                                              long size, BencodeFileTree parent,
                                              boolean isFile)
    {
        return (isFile ?
                new BencodeFileTree(index, name, size, FileNode.Type.FILE, parent) :
                new BencodeFileTree(name, 0L, FileNode.Type.DIR, parent));
    }
}
