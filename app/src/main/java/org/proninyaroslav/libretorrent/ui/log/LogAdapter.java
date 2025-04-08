/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ItemLogListBinding;

class LogAdapter extends PagingDataAdapter<LogEntry, LogAdapter.ViewHolder> {
    private final ClickListener listener;

    LogAdapter(ClickListener listener) {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var inflater = LayoutInflater.from(parent.getContext());
        var binding = ItemLogListBinding.inflate(inflater, parent, false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        var entry = getItem(position);
        if (entry != null) {
            holder.bind(entry, listener);
        }
    }

    private static final DiffUtil.ItemCallback<LogEntry> diffCallback = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areContentsTheSame(@NonNull LogEntry oldItem,
                                          @NonNull LogEntry newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull LogEntry oldItem,
                                       @NonNull LogEntry newItem) {
            return oldItem.getId() == newItem.getId();
        }
    };

    public interface ClickListener {
        void onItemClicked(@NonNull LogEntry entry);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLogListBinding binding;

        ViewHolder(ItemLogListBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(@NonNull LogEntry entry, ClickListener listener) {
            binding.card.setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onItemClicked(entry);
                }
            });

            binding.tag.setTypeface(Utils.getBoldTypeface(binding.tag.getTypeface()));
            binding.tag.setText(entry.getTag());
            binding.msg.setText(entry.getMsg());
            binding.timeStamp.setText(entry.getTimeStampAsString());
        }
    }
}
