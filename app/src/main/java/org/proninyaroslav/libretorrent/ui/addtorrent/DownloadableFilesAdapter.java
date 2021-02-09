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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.databinding.ItemTorrentDownloadableFileBinding;

/*
 * The adapter for directory or file chooser dialog.
 */

public class DownloadableFilesAdapter extends ListAdapter<DownloadableFileItem, DownloadableFilesAdapter.ViewHolder>
{
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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTorrentDownloadableFileBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_torrent_downloadable_file,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        holder.bind(getItem(position), clickListener);
    }

    public static final DiffUtil.ItemCallback<DownloadableFileItem> diffCallback = new DiffUtil.ItemCallback<DownloadableFileItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull DownloadableFileItem oldItem,
                                          @NonNull DownloadableFileItem newItem)
        {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull DownloadableFileItem oldItem,
                                       @NonNull DownloadableFileItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private ItemTorrentDownloadableFileBinding binding;

        public ViewHolder(ItemTorrentDownloadableFileBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(DownloadableFileItem item, ClickListener listener)
        {
            Context context = itemView.getContext();

            itemView.setOnClickListener((v) -> {
                if (item.isFile) {
                    /* Check file if it clicked */
                    binding.selected.performClick();
                }
                if (listener != null)
                    listener.onItemClicked(item);
            });
            binding.selected.setOnClickListener((View v) -> {
                if (listener != null)
                    listener.onItemCheckedChanged(item, binding.selected.isChecked());
            });

            binding.name.setText(item.name);

            boolean isParentDir = item.name.equals(BencodeFileTree.PARENT_DIR);

            if (item.isFile) {
                binding.icon.setImageResource(R.drawable.ic_file_grey600_24dp);
                binding.icon.setContentDescription(context.getString(R.string.file));

            } else {
                if (isParentDir) {
                    binding.icon.setImageResource(R.drawable.ic_arrow_up_bold_grey600_24dp);
                    binding.icon.setContentDescription(context.getString(R.string.parent_folder));
                } else {
                    binding.icon.setImageResource(R.drawable.ic_folder_grey600_24dp);
                    binding.icon.setContentDescription(context.getString(R.string.folder));
                }
            }

            if (isParentDir) {
                binding.selected.setVisibility(View.GONE);
                binding.size.setVisibility(View.GONE);
            } else {
                binding.selected.setVisibility(View.VISIBLE);
                binding.selected.setChecked(item.selected);
                binding.size.setVisibility(View.VISIBLE);
                binding.size.setText(Formatter.formatFileSize(context, item.size));
            }
        }
    }

    public interface ClickListener
    {
        void onItemClicked(@NonNull DownloadableFileItem item);

        void onItemCheckedChanged(@NonNull DownloadableFileItem item, boolean selected);
    }
}