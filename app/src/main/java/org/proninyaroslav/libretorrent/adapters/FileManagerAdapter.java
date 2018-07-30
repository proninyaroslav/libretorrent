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

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.io.FilenameUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerNode;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;

import java.util.Collections;
import java.util.List;

/*
 * The adapter for directory or file chooser dialog.
 */

public class FileManagerAdapter extends BaseFileListAdapter<FileManagerAdapter.ViewHolder, FileManagerNode>
{
    @SuppressWarnings("unused")
    private static final String TAG = FileManagerAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    private List<String> highlightFileTypes;

    public FileManagerAdapter(List<FileManagerNode> files, List<String> highlightFileTypes,
                              Context context, int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.clickListener = clickListener;
        Collections.sort(files);
        this.files = files;
        this.highlightFileTypes = highlightFileTypes;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(rowLayout, parent, false);

        return new ViewHolder(v, clickListener, files);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        final FileManagerNode file = files.get(position);

        holder.file = file;

        holder.itemView.setEnabled(file.isEnabled());
        if (file.isEnabled()) {
            if (highlightFileTypes != null && highlightFileTypes.contains(FilenameUtils.getExtension(file.getName()))) {
                holder.fileName.setTextColor(ContextCompat.getColor(context, R.color.file_manager_highlight));
            } else {
                TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[]{ android.R.attr.textColorPrimary });
                holder.fileName.setTextColor(a.getColor(0, 0));
                a.recycle();
            }

        } else {
            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[]{ android.R.attr.textColorSecondary });
            holder.fileName.setTextColor(a.getColor(0, 0));
            a.recycle();
        }

        holder.fileName.setText(file.getName());
        if (file.getType() == FileNode.Type.DIR) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder_grey600_24dp);

        } else if (file.getType() == FileNode.Type.FILE) {
            holder.fileIcon.setImageResource(R.drawable.ic_file_grey600_24dp);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        private ClickListener listener;
        private FileManagerNode file;
        TextView fileName;
        ImageView fileIcon;

        public ViewHolder(View itemView, final ClickListener listener, final List<FileManagerNode> files)
        {
            super(itemView);

            this.listener = listener;
            itemView.setOnClickListener(this);

            fileName = (TextView) itemView.findViewById(R.id.file_name);
            fileIcon = (ImageView) itemView.findViewById(R.id.file_icon);
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                listener.onItemClicked(file.getName(), file.getType());
            }
        }

        public interface ClickListener
        {
            void onItemClicked(String objectName, int objectType);
        }
    }
}