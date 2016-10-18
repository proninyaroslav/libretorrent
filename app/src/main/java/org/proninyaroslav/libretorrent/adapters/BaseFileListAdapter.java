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

package org.proninyaroslav.libretorrent.adapters;

import android.support.v7.widget.RecyclerView;

import org.proninyaroslav.libretorrent.core.filetree.FileNode;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * The base adapter, generalizing operations on file lists.
 */

public abstract class BaseFileListAdapter<VH extends RecyclerView.ViewHolder, F extends FileNode>
        extends SelectableAdapter<VH>
{
    @SuppressWarnings("unused")
    private static final String TAG = BaseFileListAdapter.class.getSimpleName();

    protected List<F> files;

    public synchronized void addFiles(Collection<F> files)
    {
        this.files.addAll(files);
        Collections.sort(this.files);
        notifyItemRangeInserted(0, files.size() - 1);
    }

    public void clearFiles()
    {
        int size = files.size();
        if (size > 0) {
            files.clear();

            this.notifyItemRangeRemoved(0, size);
        }
    }

    public void deleteFiles(Collection<F> files)
    {
        this.files.removeAll(files);
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position)
    {
        return files.get(position).getType();
    }

    @Override
    public int getItemCount()
    {
        return files == null ? 0 : files.size();
    }

    public boolean isEmpty()
    {
        return files.isEmpty();
    }
}
