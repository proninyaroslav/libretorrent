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

package org.proninyaroslav.libretorrent.adapters;

import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/*
 * The adapter for directory or file chooser dialog.
 */

public class DownloadableFilesAdapter extends ListAdapter<BencodeFileTree, DownloadableFilesAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = DownloadableFilesAdapter.class.getSimpleName();

    private ClickListener clickListener;

    public DownloadableFilesAdapter(ClickListener clickListener)
    {
        super(diffCallback);

        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_torrent_downloadable_file, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        holder.bind(getItem(position), clickListener);
    }

    @Override
    public void submitList(@Nullable List<BencodeFileTree> list)
    {
        if (list != null)
            Collections.sort(list);

        super.submitList(list);
    }

    public static final DiffUtil.ItemCallback<BencodeFileTree> diffCallback = new DiffUtil.ItemCallback<BencodeFileTree>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull BencodeFileTree oldItem,
                                          @NonNull BencodeFileTree newItem)
        {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull BencodeFileTree oldItem,
                                       @NonNull BencodeFileTree newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private TextView fileName;
        private ImageView fileIcon;
        private CheckBox selected;
        private TextView fileSize;

        public ViewHolder(View itemView)
        {
            super(itemView);

            fileName = itemView.findViewById(R.id.file_name);
            fileIcon = itemView.findViewById(R.id.file_icon);
            selected = itemView.findViewById(R.id.file_selected);
            fileSize = itemView.findViewById(R.id.file_size);
        }

        void bind(BencodeFileTree item, ClickListener listener)
        {
            Context context = itemView.getContext();

            itemView.setOnClickListener((v) -> {
                if (item.getType() == FileNode.Type.FILE) {
                    /* Check file if it clicked */
                    selected.performClick();
                }
                if (listener != null)
                    listener.onItemClicked(item);
            });
            selected.setOnClickListener((View v) -> {
                if (listener != null)
                    listener.onItemCheckedChanged(item, selected.isChecked());
            });

            fileName.setText(item.getName());

            if (item.getType() == FileNode.Type.DIR)
                fileIcon.setImageResource(R.drawable.ic_folder_grey600_24dp);
            else if (item.getType() == FileNode.Type.FILE)
                fileIcon.setImageResource(R.drawable.ic_file_grey600_24dp);

            if (item.getName().equals(BencodeFileTree.PARENT_DIR)) {
                selected.setVisibility(View.GONE);
                fileSize.setVisibility(View.GONE);
            } else {
                selected.setVisibility(View.VISIBLE);
                selected.setChecked(item.isSelected());
                fileSize.setVisibility(View.VISIBLE);
                fileSize.setText(Formatter.formatFileSize(context, item.size()));
            }
        }
    }

    public interface ClickListener
    {
        void onItemClicked(BencodeFileTree item);

        void onItemCheckedChanged(BencodeFileTree node, boolean selected);
    }
}