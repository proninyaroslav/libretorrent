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
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class BencodeFileTreeTest
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
        Pair<BencodeFileTree, BencodeFileTree[]> res = BencodeFileTreeUtils.buildFileTree(files);
        BencodeFileTree tree = res.first;
        BencodeFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        assertNotNull(child[0]);
        assertTrue(child[0].isFile());
        assertEquals("file1.txt", child[0].getName());
        assertFalse(child[0].isSelected());
        assertEquals(files.get(0).getIndex(), child[0].getIndex());
        assertEquals(files.get(0).getSize(), child[0].size());
        assertEquals("foo/dir1/file1.txt/", child[0].getPath());

        assertNotNull(child[1]);
        assertTrue(child[1].isFile());
        assertEquals("file2.txt", child[1].getName());
        assertFalse(child[1].isSelected());
        assertEquals(files.get(1).getIndex(), child[1].getIndex());
        assertEquals(files.get(1).getSize(), child[1].size());
        assertEquals("foo/dir1/file2.txt/", child[1].getPath());

        assertNotNull(child[2]);
        assertTrue(child[2].isFile());
        assertEquals("file1.txt", child[2].getName());
        assertFalse(child[2].isSelected());
        assertEquals(files.get(2).getIndex(), child[2].getIndex());
        assertEquals(files.get(2).getSize(), child[2].size());
        assertEquals("foo/dir2/file1.txt/", child[2].getPath());

        assertNotNull(child[3]);
        assertTrue(child[3].isFile());
        assertEquals("file2.txt", child[3].getName());
        assertFalse(child[3].isSelected());
        assertEquals(files.get(3).getIndex(), child[3].getIndex());
        assertEquals(files.get(3).getSize(), child[3].size());
        assertEquals("foo/dir2/file2.txt/", child[3].getPath());

        assertNotNull(child[4]);
        assertTrue(child[4].isFile());
        assertEquals("file.txt", child[4].getName());
        assertFalse(child[4].isSelected());
        assertEquals(files.get(4).getIndex(), child[4].getIndex());
        assertEquals(files.get(4).getSize(), child[4].size());
        assertEquals("foo/file.txt/", child[4].getPath());
        

        BencodeFileTree parent0 = child[0].getParent();
        assertNotNull(parent0);
        assertFalse(parent0.isFile());
        assertEquals("dir1", parent0.getName());
        assertFalse(parent0.isSelected());
        assertEquals(-1, parent0.getIndex());
        assertEquals(child[0].size() + child[1].size(), parent0.size());
        assertTrue(parent0.contains(child[0].getName()));
        assertTrue(parent0.contains(child[1].getName()));
        assertEquals("foo/dir1/", parent0.getPath());

        BencodeFileTree parent1 = child[1].getParent();
        assertNotNull(parent1);
        assertEquals(parent0.size(), parent1.size());

        BencodeFileTree parent2 = child[2].getParent();
        assertNotNull(parent2);
        assertFalse(parent2.isFile());
        assertEquals("dir2", parent2.getName());
        assertFalse(parent2.isSelected());
        assertEquals(-1, parent2.getIndex());
        assertEquals(child[2].size() + child[3].size(), parent2.size());
        assertTrue(parent2.contains(child[2].getName()));
        assertTrue(parent2.contains(child[3].getName()));
        assertEquals("foo/dir2/", parent2.getPath());

        BencodeFileTree parent3 = child[3].getParent();
        assertNotNull(parent3);
        assertEquals(parent2, parent3);
        
        
        BencodeFileTree foo = parent0.getParent();
        assertNotNull(foo);
        assertFalse(foo.isFile());
        assertEquals(foo, parent2.getParent());
        assertEquals(foo, child[4].getParent());
        assertEquals("foo", foo.getName());
        assertFalse(foo.isSelected());
        assertEquals(-1, foo.getIndex());
        assertEquals(parent0.size() + parent2.size() + child[4].size(), foo.size());
        assertTrue(foo.contains(parent0.getName()));
        assertTrue(foo.contains(parent2.getName()));
        assertTrue(foo.contains(child[4].getName()));
        assertEquals("foo/", foo.getPath());

        BencodeFileTree root = foo.getParent();
        assertNotNull(root);
        assertEquals(tree, root);
        assertFalse(root.isFile());
        assertEquals(root, foo.getParent());
        assertEquals("/", root.getName());
        assertFalse(root.isSelected());
        assertEquals(-1, root.getIndex());
        assertEquals(foo.size(), root.size());
        assertTrue(root.contains(foo.getName()));
        assertEquals("", root.getPath());
    }

    @Test
    public void selectTest()
    {
        Pair<BencodeFileTree, BencodeFileTree[]> res = BencodeFileTreeUtils.buildFileTree(files);
        BencodeFileTree tree = res.first;
        BencodeFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.select(true, true);

        child[0].select(false, true);
        assertTrue(child[0].getParent().isSelected());
        child[1].select(false, true);
        assertFalse(child[1].getParent().isSelected());
        assertTrue(tree.isSelected());

        child[0].getParent().select(true, true);
        assertTrue(child[0].getParent().isSelected());
        assertTrue(child[0].isSelected());
        assertTrue(child[1].isSelected());
        assertTrue(tree.isSelected());
    }

    @Test
    public void selectedFileSizeTest()
    {
        Pair<BencodeFileTree, BencodeFileTree[]> res = BencodeFileTreeUtils.buildFileTree(files);
        BencodeFileTree tree = res.first;
        BencodeFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.select(true, true);
        child[2].select(false, true);
        child[4].select(false, true);

        assertEquals(child[0].size() + child[1].size() + child[3].size(), tree.selectedFileSize());
    }

    @Test
    public void selectTest_NoForceUpdateParent()
    {
        Pair<BencodeFileTree, BencodeFileTree[]> res = BencodeFileTreeUtils.buildFileTree(files);
        BencodeFileTree tree = res.first;
        BencodeFileTree[] child = res.second;
        assertNotNull(tree);
        assertNotNull(child);
        assertEquals(files.size(), child.length);

        tree.select(true, true);

        child[0].select(false, false);
        assertTrue(child[0].getParent().isSelected());
        child[1].select(false, false);
        assertFalse(child[1].getParent().isSelected());
    }
}