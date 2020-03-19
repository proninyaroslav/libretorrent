/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.core.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.utils.TorrentContentFileTreeUtils;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class TorrentContentFileTreeTest
{
    private ArrayList<BencodeFileItem> files = new ArrayList<>();

    @Before
    public void init()
    {
        files.add(new BencodeFileItem("foo/dir1/file1.txt", 0, 0));
        files.add(new BencodeFileItem("foo/dir1/file2.txt", 1, 1));
        files.add(new BencodeFileItem("foo/dir2/file1.txt", 2, 2));
        files.add(new BencodeFileItem("foo/dir2/file2.txt", 3, 3));
        files.add(new BencodeFileItem("foo/file.txt", 4, 4));
    }

    @Test
    public void makeTreeTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        assertNotNull(child[0]);
        assertTrue(child[0].isFile());
        assertEquals("file1.txt", child[0].getName());
        assertEquals(files.get(0).getIndex(), child[0].getIndex());
        assertEquals(files.get(0).getSize(), child[0].size());
        assertEquals("foo/dir1/file1.txt/", child[0].getPath());
        assertEquals(FilePriority.Type.IGNORE, child[0].getFilePriority().getType());
        assertEquals(0, child[0].getReceivedBytes());
        assertEquals(-1, child[0].getAvailability(), 0);

        assertNotNull(child[1]);
        assertTrue(child[1].isFile());
        assertEquals("file2.txt", child[1].getName());
        assertEquals(files.get(1).getIndex(), child[1].getIndex());
        assertEquals(files.get(1).getSize(), child[1].size());
        assertEquals("foo/dir1/file2.txt/", child[1].getPath());
        assertEquals(FilePriority.Type.IGNORE, child[1].getFilePriority().getType());
        assertEquals(0, child[1].getReceivedBytes());
        assertEquals(-1, child[1].getAvailability(), 0);


        assertNotNull(child[2]);
        assertTrue(child[2].isFile());
        assertEquals("file1.txt", child[2].getName());
        assertEquals(files.get(2).getIndex(), child[2].getIndex());
        assertEquals(files.get(2).getSize(), child[2].size());
        assertEquals("foo/dir2/file1.txt/", child[2].getPath());
        assertEquals(FilePriority.Type.IGNORE, child[2].getFilePriority().getType());
        assertEquals(0, child[2].getReceivedBytes());
        assertEquals(-1, child[2].getAvailability(), 0);

        assertNotNull(child[3]);
        assertTrue(child[3].isFile());
        assertEquals("file2.txt", child[3].getName());
        assertEquals(files.get(3).getIndex(), child[3].getIndex());
        assertEquals(files.get(3).getSize(), child[3].size());
        assertEquals("foo/dir2/file2.txt/", child[3].getPath());
        assertEquals(FilePriority.Type.IGNORE, child[3].getFilePriority().getType());
        assertEquals(0, child[3].getReceivedBytes());
        assertEquals(-1, child[3].getAvailability(), 0);

        assertNotNull(child[4]);
        assertTrue(child[4].isFile());
        assertEquals("file.txt", child[4].getName());
        assertEquals(files.get(4).getIndex(), child[4].getIndex());
        assertEquals(files.get(4).getSize(), child[4].size());
        assertEquals("foo/file.txt/", child[4].getPath());
        assertEquals(FilePriority.Type.IGNORE, child[4].getFilePriority().getType());
        assertEquals(0, child[4].getReceivedBytes());
        assertEquals(-1, child[4].getAvailability(), 0);


        TorrentContentFileTree parent0 = child[0].getParent();
        assertNotNull(parent0);
        assertFalse(parent0.isFile());
        assertEquals("dir1", parent0.getName());
        assertEquals(-1, parent0.getIndex());
        assertEquals(child[0].size() + child[1].size(), parent0.size());
        assertTrue(parent0.contains(child[0].getName()));
        assertTrue(parent0.contains(child[1].getName()));
        assertEquals("foo/dir1/", parent0.getPath());
        assertEquals(FilePriority.Type.IGNORE, parent0.getFilePriority().getType());
        assertEquals(0, parent0.getReceivedBytes());
        assertEquals(-1, parent0.getAvailability(), 0);

        TorrentContentFileTree parent1 = child[1].getParent();
        assertNotNull(parent1);
        assertEquals(parent0.size(), parent1.size());

        TorrentContentFileTree parent2 = child[2].getParent();
        assertNotNull(parent2);
        assertFalse(parent2.isFile());
        assertEquals("dir2", parent2.getName());
        assertEquals(-1, parent2.getIndex());
        assertEquals(child[2].size() + child[3].size(), parent2.size());
        assertTrue(parent2.contains(child[2].getName()));
        assertTrue(parent2.contains(child[3].getName()));
        assertEquals("foo/dir2/", parent2.getPath());
        assertEquals(FilePriority.Type.IGNORE, parent2.getFilePriority().getType());
        assertEquals(0, parent2.getReceivedBytes());
        assertEquals(-1, parent2.getAvailability(), 0);

        TorrentContentFileTree parent3 = child[3].getParent();
        assertNotNull(parent3);
        assertEquals(parent2, parent3);


        TorrentContentFileTree foo = parent0.getParent();
        assertNotNull(foo);
        assertFalse(foo.isFile());
        assertEquals(foo, parent2.getParent());
        assertEquals(foo, child[4].getParent());
        assertEquals("foo", foo.getName());
        assertEquals(-1, foo.getIndex());
        assertEquals(parent0.size() + parent2.size() + child[4].size(), foo.size());
        assertTrue(foo.contains(parent0.getName()));
        assertTrue(foo.contains(parent2.getName()));
        assertTrue(foo.contains(child[4].getName()));
        assertEquals("foo/", foo.getPath());
        assertEquals(FilePriority.Type.IGNORE, foo.getFilePriority().getType());
        assertEquals(0, foo.getReceivedBytes());
        assertEquals(-1, foo.getAvailability(), 0);

        TorrentContentFileTree root = foo.getParent();
        assertNotNull(root);
        assertEquals(tree, root);
        assertFalse(root.isFile());
        assertEquals(root, foo.getParent());
        assertEquals("/", root.getName());
        assertEquals(-1, root.getIndex());
        assertEquals(foo.size(), root.size());
        assertTrue(root.contains(foo.getName()));
        assertEquals("", root.getPath());
        assertEquals(FilePriority.Type.IGNORE, root.getFilePriority().getType());
        assertEquals(0, root.getReceivedBytes());
        assertEquals(-1, root.getAvailability(), 0);
    }

    @Test
    public void setPriorityTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(Priority.DEFAULT), true);
        assertEquals(FilePriority.Type.NORMAL, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[3].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[4].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getParent().getFilePriority().getType());

        child[0].setPriority(new FilePriority(Priority.IGNORE), true);
        assertEquals(FilePriority.Type.IGNORE, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, tree.getFilePriority().getType());

        child[1].setPriority(new FilePriority(Priority.IGNORE), true);
        assertEquals(FilePriority.Type.IGNORE, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, tree.getFilePriority().getType());
    }

    @Test
    public void setPriorityTest_NoForceUpdateParent()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(Priority.DEFAULT), false);
        assertEquals(FilePriority.Type.NORMAL, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[3].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[4].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getParent().getFilePriority().getType());

        child[0].setPriority(new FilePriority(Priority.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, tree.getFilePriority().getType());

        child[1].setPriority(new FilePriority(Priority.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, tree.getFilePriority().getType());

        child[2].setPriority(new FilePriority(Priority.IGNORE), false);
        child[3].setPriority(new FilePriority(Priority.IGNORE), false);
        child[4].setPriority(new FilePriority(Priority.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[2].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, tree.getFilePriority().getType());
    }

    @Test
    public void selectTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(FilePriority.Type.NORMAL), true);
        assertEquals(FilePriority.Type.NORMAL, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[3].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[4].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getParent().getFilePriority().getType());

        child[0].setPriority(new FilePriority(FilePriority.Type.IGNORE), true);
        assertEquals(FilePriority.Type.IGNORE, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, tree.getFilePriority().getType());

        child[1].setPriority(new FilePriority(FilePriority.Type.IGNORE), true);
        assertEquals(FilePriority.Type.IGNORE, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.MIXED, tree.getFilePriority().getType());
    }

    @Test
    public void selectTest_NoForceUpdateParent()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(FilePriority.Type.NORMAL), false);
        assertEquals(FilePriority.Type.NORMAL, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[3].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[4].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[2].getParent().getFilePriority().getType());

        child[0].setPriority(new FilePriority(FilePriority.Type.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[0].getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, tree.getFilePriority().getType());

        child[1].setPriority(new FilePriority(FilePriority.Type.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[1].getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, child[1].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, child[0].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.NORMAL, tree.getFilePriority().getType());

        child[2].setPriority(new FilePriority(FilePriority.Type.IGNORE), false);
        child[3].setPriority(new FilePriority(FilePriority.Type.IGNORE), false);
        child[4].setPriority(new FilePriority(FilePriority.Type.IGNORE), false);
        assertEquals(FilePriority.Type.IGNORE, child[2].getParent().getFilePriority().getType());
        assertEquals(FilePriority.Type.IGNORE, tree.getFilePriority().getType());
    }

    @Test
    public void selectedFileSizeTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(FilePriority.Type.NORMAL), true);
        child[2].setPriority(new FilePriority(FilePriority.Type.IGNORE), true);
        child[4].setPriority(new FilePriority(FilePriority.Type.IGNORE), true);

        assertEquals(child[0].size() + child[1].size() + child[3].size(), tree.nonIgnoreFileSize());
    }

    @Test
    public void getReceivedBytesTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        child[0].setReceivedBytes(0);
        child[1].setReceivedBytes(1);
        child[2].setReceivedBytes(1);

        assertEquals(0, child[0].getReceivedBytes());
        assertEquals(1, child[1].getReceivedBytes());
        assertEquals(1, child[2].getReceivedBytes());
        assertEquals(1, child[0].getParent().getReceivedBytes());
        assertEquals(1, child[2].getParent().getReceivedBytes());
        assertEquals(2, tree.getReceivedBytes());
    }

    @Test
    public void getAvailabilityTest()
    {
        Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
        TorrentContentFileTree tree = res.first;
        TorrentContentFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.setPriority(new FilePriority(FilePriority.Type.NORMAL), true);

        child[0].setReceivedBytes(0);
        child[0].setAvailability(1);
        child[1].setReceivedBytes(1);
        child[1].setAvailability(1);
        child[2].setReceivedBytes(1);
        child[2].setAvailability(1);

        assertEquals(1, child[0].getAvailability(), 0);
        assertEquals(1, child[1].getAvailability(), 0);
        assertEquals(1, child[2].getAvailability(), 0);
        assertEquals(1, child[0].getParent().getAvailability(), 0.001);
        assertEquals(0.4, child[2].getParent().getAvailability(), 0.001);
        assertEquals(0.3, tree.getAvailability(), 0.001);
    }
}