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

package org.proninyaroslav.libretorrent.core.model.filetree;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * The class represents a file tree model hierarchy. Based on trie.
 */

public class FileTree<F extends FileTree> implements FileNode<FileTree>, Serializable
{
    public static final String ROOT = File.separator;
    /* The name for pointer to the parent node */
    public static final String PARENT_DIR = "..";

    protected int index;
    protected String name;
    protected long size;
    protected boolean isLeaf;
    protected F parent;
    protected Map<String, F> children = new LinkedHashMap<>();

    public FileTree(String name, long size, int type)
    {
        this(-1, name, size, type, null);
    }

    public FileTree(String name, long size, int type, F parent)
    {
        this(-1, name, size, type, parent);
    }

    public FileTree(int index, String name, long size, int type)
    {
        this(index, name, size, type, null);
    }

    public FileTree(int index, String name, long size, int type, F parent)
    {
        this.index = index;
        this.name = name;
        this.size = size;
        isLeaf = (type == FileNode.Type.FILE);
        this.parent = parent;
    }

    public synchronized void addChild(F node)
    {
        children.put(node.getName(), node);
    }

    public boolean contains(String name)
    {
        return children.containsKey(name);
    }

    public F getChild(String name)
    {
        return children.get(name);
    }

    public Collection<F> getChildren()
    {
        return children.values();
    }

    public Set<String> getChildrenName()
    {
        return children.keySet();
    }

    public List<Integer> getChildrenIndexes()
    {
        List<Integer> indexes = new ArrayList<>();

        for (F child : children.values())
            indexes.add(child.getIndex());

        return indexes;
    }

    public int getChildrenCount()
    {
        return children.size();
    }

    public boolean isFile()
    {
        return isLeaf;
    }

    public F getParent()
    {
        return parent;
    }

    public void setParent(F parent)
    {
        this.parent = parent;
    }

    @Override
    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void setIndex(int index)
    {
        this.index = index;
    }

    public int getIndex()
    {
        return index;
    }

    @Override
    public int getType()
    {
        return (isLeaf ? FileNode.Type.FILE : FileNode.Type.DIR);
    }

    @Override
    public void setType(int type)
    {
        isLeaf = (type == FileNode.Type.FILE);
    }

    public long size()
    {
        if (size == 0 && children.size() != 0)
            for (F child : children.values())
                size += child.size();

        return size;
    }

    public String getPath()
    {
        String path = "";
        FileTree curNode = this;

        while (curNode.parent != null) {
            path = curNode.name + File.separator + path;
            curNode = curNode.parent;
        }

        return path;
    }

    @Override
    public int compareTo(@NonNull FileTree another)
    {
        return name.compareTo(another.getName());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (!(o instanceof FileTree))
            return false;

        if (o == this)
            return true;

        FileTree fileTree = (FileTree)o;

        return index == fileTree.index &&
                (name == null || name.equals(fileTree.name)) &&
                size == fileTree.size &&
                isLeaf == fileTree.isLeaf;
    }

    @Override
    public String toString()
    {
        return "FileTree{" +
                "index=" + index +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", isLeaf=" + isLeaf +
                ", parent=" + parent +
                ", children=" + children.size() +
                '}';
    }
}
