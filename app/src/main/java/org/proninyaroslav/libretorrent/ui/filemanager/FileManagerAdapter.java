/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.databinding.ItemFileManagerBinding;

import java.util.Comparator;
import java.util.List;

/*
 * The adapter for directory or file chooser dialog.
 */

public class FileManagerAdapter extends ListAdapter<FileManagerNode, FileManagerAdapter.ViewHolder> {
    public static final int VIEW_TYPE_PARENT_DIR = 0;
    public static final int VIEW_TYPE_FILE_OR_DIR = 1;

    private final ViewHolder.ClickListener clickListener;
    private final List<String> highlightFileTypes;

    private static final Comparator<FileManagerNode> directoryFirstCmp = (n1, n2) -> {
        int byName = n1.compareTo(n2);
        int directoryFirst = Boolean.compare(n2.isDirectory(), n1.isDirectory());

        return (directoryFirst == 0 ? byName : directoryFirst);
    };

    public FileManagerAdapter(List<String> highlightFileTypes, ViewHolder.ClickListener clickListener) {
        super(diffCallback);

        this.clickListener = clickListener;
        this.highlightFileTypes = highlightFileTypes;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var inflater = LayoutInflater.from(parent.getContext());
        var binding = ItemFileManagerBinding.inflate(inflater, parent, false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), highlightFileTypes, clickListener);
    }

    @Override
    public void submitList(@Nullable List<FileManagerNode> list) {
        if (list != null) {
            list.sort(directoryFirstCmp);
        }

        super.submitList(list);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getName().equals(FileManagerNode.PARENT_DIR)
                ? VIEW_TYPE_PARENT_DIR
                : VIEW_TYPE_FILE_OR_DIR;
    }

    public static final DiffUtil.ItemCallback<FileManagerNode> diffCallback = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areContentsTheSame(@NonNull FileManagerNode oldItem,
                                          @NonNull FileManagerNode newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull FileManagerNode oldItem,
                                       @NonNull FileManagerNode newItem) {
            return oldItem.equals(newItem);
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFileManagerBinding binding;
        private final ColorStateList labelIconTint;
        private final Typeface labelTypeface;

        public ViewHolder(ItemFileManagerBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
            labelIconTint = TextViewCompat.getCompoundDrawableTintList(binding.label);
            labelTypeface = binding.label.getTypeface();
        }

        void bind(FileManagerNode item, List<String> highlightFileTypes, ClickListener listener) {
            Context context = itemView.getContext();

            itemView.setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onItemClicked(item);
                }
            });

            itemView.setEnabled(item.isEnabled());
            FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(context);
            if (item.isEnabled()) {
                if (highlightFileTypes != null && highlightFileTypes.contains(fs.getExtension(item.getName()))) {
                    binding.label.setTypeface(labelTypeface, Typeface.BOLD);
                } else {
                    binding.label.setTypeface(labelTypeface, Typeface.NORMAL);
                }
            }
            binding.label.setEnabled(item.isEnabled());
            binding.label.setText(item.getName());

            if (item.isDirectory()) {
                if (item.getName().equals(FileManagerNode.PARENT_DIR)) {
                    binding.label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_arrow_upward_alt_24px, 0, 0, 0);
                    binding.label.setContentDescription(context.getString(R.string.parent_folder));
                } else {
                    binding.label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_folder_24px, 0, 0, 0);
                    binding.label.setContentDescription(context.getString(R.string.folder));
                }
                TextViewCompat.setCompoundDrawableTintList(binding.label, labelIconTint);

            } else {
                binding.label.setContentDescription(context.getString(R.string.file));
                binding.label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_file_24px, 0, 0, 0);
                if (item.isEnabled()) {
                    try (TypedArray a = context.obtainStyledAttributes(
                            new TypedValue().data,
                            new int[]{R.attr.colorPrimaryVariant}
                    )) {
                        TextViewCompat.setCompoundDrawableTintList(
                                binding.label,
                                ColorStateList.valueOf(a.getColor(0, -1))
                        );
                    }
                } else {
                    TextViewCompat.setCompoundDrawableTintList(binding.label, labelIconTint);
                }
            }
        }

        public interface ClickListener {
            void onItemClicked(FileManagerNode item);
        }
    }
}