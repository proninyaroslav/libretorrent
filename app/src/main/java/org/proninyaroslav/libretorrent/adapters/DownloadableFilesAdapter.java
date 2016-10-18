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
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;

import java.util.Collections;
import java.util.List;

/*
 * The adapter for representation of downloadable files in a file tree view.
 */

public class DownloadableFilesAdapter
        extends BaseFileListAdapter<DownloadableFilesAdapter.ViewHolder, BencodeFileTree>
{
    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;

    public DownloadableFilesAdapter(List<BencodeFileTree> files, Context context,
                                    int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.clickListener = clickListener;
        Collections.sort(files);
        this.files = files;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(rowLayout, parent, false);

        return new ViewHolder(v, clickListener, files);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position)
    {
        final BencodeFileTree file = files.get(position);

        holder.fileName.setText(file.getName());

        if (file.getType() == FileNode.Type.DIR) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder_grey600_24dp);

        } else if (file.getType() == FileNode.Type.FILE) {
            holder.fileIcon.setImageResource(R.drawable.ic_file_grey600_24dp);
        }

        if (file.getName().equals(BencodeFileTree.PARENT_DIR)) {
            holder.fileSelected.setVisibility(View.GONE);
            holder.fileSize.setVisibility(View.GONE);
        } else {
            holder.fileSelected.setVisibility(View.VISIBLE);
            holder.fileSelected.setChecked(file.isSelected());
            holder.fileSize.setVisibility(View.VISIBLE);
            holder.fileSize.setText(Formatter.formatFileSize(context, file.size()));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        private ClickListener listener;
        private List<BencodeFileTree> files;
        TextView fileName;
        TextView fileSize;
        ImageView fileIcon;
        CheckBox fileSelected;

        public ViewHolder(View itemView, final ClickListener listener, final List<BencodeFileTree> files)
        {
            super(itemView);

            this.listener = listener;
            this.files = files;
            itemView.setOnClickListener(this);

            fileName = (TextView) itemView.findViewById(R.id.file_name);
            fileSize = (TextView) itemView.findViewById(R.id.file_size);
            fileIcon = (ImageView) itemView.findViewById(R.id.file_icon);
            fileSelected = (CheckBox) itemView.findViewById(R.id.file_selected);
            fileSelected.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (listener != null) {
                        listener.onItemCheckedChanged(ViewHolder.this.files.get(getAdapterPosition()),
                                                      fileSelected.isChecked());
                    }
                }
            });
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                BencodeFileTree file = files.get(position);

                if (file.getType() == FileNode.Type.FILE) {
                    /* Check file if it clicked */
                    fileSelected.performClick();
                }

                listener.onItemClicked(file);
            }
        }

        public interface ClickListener
        {
            void onItemClicked(BencodeFileTree node);

            void onItemCheckedChanged(BencodeFileTree node, boolean selected);
        }
    }
}
