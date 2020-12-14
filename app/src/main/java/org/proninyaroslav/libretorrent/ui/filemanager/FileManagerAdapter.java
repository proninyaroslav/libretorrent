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

package org.proninyaroslav.libretorrent.ui.filemanager;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*
 * The adapter for directory or file chooser dialog.
 */

public class FileManagerAdapter extends ListAdapter<FileManagerNode, FileManagerAdapter.ViewHolder>
{
    private static final String TAG = FileManagerAdapter.class.getSimpleName();

    private ViewHolder.ClickListener clickListener;
    private List<String> highlightFileTypes;

    private static final Comparator<FileManagerNode> directoryFirstCmp = (n1, n2) -> {
        int byName = n1.compareTo(n2);
        int directoryFirst = Boolean.compare(n2.isDirectory(), n1.isDirectory());

        return (directoryFirst == 0 ? byName : directoryFirst);
    };

    public FileManagerAdapter(List<String> highlightFileTypes, ViewHolder.ClickListener clickListener)
    {
        super(diffCallback);

        this.clickListener = clickListener;
        this.highlightFileTypes = highlightFileTypes;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_filemanager, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        holder.bind(getItem(position), highlightFileTypes, clickListener);
    }

    @Override
    public void submitList(@Nullable List<FileManagerNode> list)
    {
        if (list != null)
            Collections.sort(list, directoryFirstCmp);

        super.submitList(list);
    }

    public static final DiffUtil.ItemCallback<FileManagerNode> diffCallback = new DiffUtil.ItemCallback<FileManagerNode>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull FileManagerNode oldItem,
                                          @NonNull FileManagerNode newItem)
        {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull FileManagerNode oldItem,
                                       @NonNull FileManagerNode newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private TextView fileName;
        private ImageView fileIcon;

        public ViewHolder(View itemView)
        {
            super(itemView);

            fileName = itemView.findViewById(R.id.file_name);
            fileIcon = itemView.findViewById(R.id.file_icon);
        }

        void bind(FileManagerNode item, List<String> highlightFileTypes, ClickListener listener)
        {
            Context context = itemView.getContext();

            itemView.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemClicked(item);
            });

            itemView.setEnabled(item.isEnabled());
            FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(context);
            if (item.isEnabled()) {
                if (highlightFileTypes != null && highlightFileTypes.contains(fs.getExtension(item.getName()))) {
                    fileName.setTextColor(Utils.getAttributeColor(context, R.attr.colorSecondary));
                } else {
                    TypedArray a = context.obtainStyledAttributes(new TypedValue().data,
                            new int[]{ android.R.attr.textColorPrimary });
                    fileName.setTextColor(a.getColor(0, 0));
                    a.recycle();
                }

            } else {
                TypedArray a = context.obtainStyledAttributes(new TypedValue().data,
                        new int[]{ android.R.attr.textColorSecondary });
                fileName.setTextColor(a.getColor(0, 0));
                a.recycle();
            }

            fileName.setText(item.getName());

            if (item.isDirectory()) {
                if (item.getName().equals(FileManagerNode.PARENT_DIR)) {
                    fileIcon.setImageResource(R.drawable.ic_arrow_up_bold_grey600_24dp);
                    fileIcon.setContentDescription(context.getString(R.string.parent_folder));
                } else {
                    fileIcon.setImageResource(R.drawable.ic_folder_grey600_24dp);
                    fileIcon.setContentDescription(context.getString(R.string.folder));
                }

            } else {
                fileIcon.setImageResource(R.drawable.ic_file_grey600_24dp);
                fileIcon.setContentDescription(context.getString(R.string.file));
            }
        }

        public interface ClickListener
        {
            void onItemClicked(FileManagerNode item);
        }
    }
}