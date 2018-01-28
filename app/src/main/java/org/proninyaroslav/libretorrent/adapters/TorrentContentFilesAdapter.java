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
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.filetree.TorrentContentFileTree;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TorrentContentFilesAdapter
        extends BaseFileListAdapter<TorrentContentFilesAdapter.ViewHolder, TorrentContentFileTree>
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentContentFilesAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;

    public TorrentContentFilesAdapter(List<TorrentContentFileTree> files, Context context,
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

    @SuppressWarnings("ResourceType")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        final TorrentContentFileTree file = files.get(position);

        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                R.attr.defaultSelectRect,
                R.attr.defaultRectRipple
        });

        if (isSelected(position)) {
            Utils.setBackground(
                    holder.itemView,
                    a.getDrawable(0));
        } else {
            Utils.setBackground(
                    holder.itemFileList,
                    a.getDrawable(1));
        }
        a.recycle();

        holder.fileName.setText(file.getName());

        if (file.getType() == FileNode.Type.DIR) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder_grey600_24dp);

        } else if (file.getType() == FileNode.Type.FILE) {
            holder.fileIcon.setImageResource(R.drawable.ic_file_grey600_24dp);
        }

        if (file.getName().equals(BencodeFileTree.PARENT_DIR)) {
            holder.fileSelected.setVisibility(View.GONE);
            holder.fileStatus.setVisibility(View.GONE);
            holder.fileProgress.setVisibility(View.GONE);
        } else {
            long totalBytes = file.size();
            long receivedBytes = file.getReceivedBytes();
            int progress = (receivedBytes == totalBytes ? 100 : (int)((receivedBytes * 100.0f) / totalBytes));

            String total = Formatter.formatFileSize(context, file.size());
            String received = Formatter.formatFileSize(context, file.getReceivedBytes());

            String priority = "";
            switch (file.getFilePriority().getType()) {
                case NORMAL:
                    priority = context.getString(R.string.file_priority_normal);
                    break;
                case IGNORE:
                    priority = context.getString(R.string.file_priority_low);
                    break;
                case MIXED:
                    priority = context.getString(R.string.file_priority_mixed);
                    break;
                case HIGH:
                    priority = context.getString(R.string.file_priority_high);
                    break;
            }
            double avail = file.getAvailability();
            String availability;
            if (avail < 0)
                availability = context.getString(R.string.not_available);
            else
                availability =  String.format(Locale.getDefault(), "%.1f%%", (avail >= 1 ? 100 : avail * 100));

            if (file.getSelectState() == TorrentContentFileTree.SelectState.DISABLED) {
                holder.fileSelected.setVisibility(View.GONE);
                holder.fileProgress.setVisibility(View.VISIBLE);

                String statusTemplate = context.getString(R.string.file_downloading_status_template);
                holder.fileStatus.setText(
                        String.format(statusTemplate, priority,
                                      received, total, progress, availability));
                holder.fileProgress.setProgress(progress);
            } else {
                holder.fileSelected.setVisibility(View.VISIBLE);
                holder.fileSelected.setChecked(file.getSelectState() ==
                        TorrentContentFileTree.SelectState.SELECTED);

                String statusTemplate = context.getString(R.string.file_status_template);

                holder.fileStatus.setText(String.format(statusTemplate, priority, total));
                holder.fileProgress.setVisibility(View.GONE);
            }

            holder.fileStatus.setVisibility(View.VISIBLE);
        }
    }

    public void updateItem(TorrentContentFileTree file)
    {
        int position = files.indexOf(file);

        if (position >= 0) {
            files.set(position, file);

            notifyItemChanged(position);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener
    {
        private Context context;
        private ClickListener listener;
        private List<TorrentContentFileTree> files;
        RelativeLayout itemFileList;
        TextView fileName;
        TextView fileStatus;
        ImageView fileIcon;
        CheckBox fileSelected;
        ProgressBar fileProgress;

        public ViewHolder(View itemView, final ClickListener listener,
                          final List<TorrentContentFileTree> files)
        {
            super(itemView);

            this.context = itemView.getContext();
            this.listener = listener;
            this.files = files;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            itemFileList = itemView.findViewById(R.id.item_file_list);
            fileName = itemView.findViewById(R.id.file_name);
            fileStatus = itemView.findViewById(R.id.file_status);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileSelected = itemView.findViewById(R.id.file_selected);
            fileSelected.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (listener != null) {
                        listener.onItemCheckedChanged(files.get(getAdapterPosition()),
                                fileSelected.isChecked());
                    }
                }
            });
            fileProgress = itemView.findViewById(R.id.file_progress);
            Utils.colorizeProgressBar(context, fileProgress);
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                TorrentContentFileTree file = files.get(position);

                listener.onItemClicked(position, file);
            }
        }

        @Override
        public boolean onLongClick(View view)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                TorrentContentFileTree file = files.get(position);

                listener.onItemLongClicked(position, file);

                return true;
            }

            return false;
        }

        public interface ClickListener
        {
            void onItemClicked(int position, TorrentContentFileTree node);

            boolean onItemLongClicked(int position, TorrentContentFileTree node);

            void onItemCheckedChanged(TorrentContentFileTree node, boolean selected);
        }
    }
}
