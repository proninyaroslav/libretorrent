/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.databinding.ItemLogListBinding;

class LogAdapter extends PagedListAdapter<LogEntry, LogAdapter.ViewHolder>
{
    private ClickListener listener;

    LogAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemLogListBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_log_list,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        LogEntry entry = getItem(position);
        if (entry != null)
            holder.bind(entry, listener);
    }

    private static final DiffUtil.ItemCallback<LogEntry> diffCallback = new DiffUtil.ItemCallback<LogEntry>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull LogEntry oldItem,
                                          @NonNull LogEntry newItem)
        {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull LogEntry oldItem,
                                       @NonNull LogEntry newItem)
        {
            return oldItem.getId() == newItem.getId();
        }
    };

    public interface ClickListener
    {
        void onItemClicked(@NonNull LogEntry entry);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private ItemLogListBinding binding;

        ViewHolder(ItemLogListBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(@NonNull LogEntry entry, ClickListener listener)
        {
            itemView.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemClicked(entry);
            });

            binding.tag.setText(entry.getTag());
            binding.msg.setText(entry.getMsg());
            binding.timeStamp.setText(entry.getTimeStampAsString());
        }
    }
}
