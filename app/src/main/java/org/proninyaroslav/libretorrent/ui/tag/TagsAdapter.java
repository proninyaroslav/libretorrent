/*
 * Copyright (C) 2021-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.tag;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.databinding.ItemTagsListBinding;

public class TagsAdapter extends ListAdapter<TagItem, TagsAdapter.ViewHolder> {
    @NonNull
    private final OnClickListener listener;

    public TagsAdapter(@NonNull OnClickListener listener) {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var inflater = LayoutInflater.from(parent.getContext());
        var binding = ItemTagsListBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        var item = getItem(position);
        if (item != null) {
            holder.bind(item, listener);
        }
    }

    static final DiffUtil.ItemCallback<TagItem> diffCallback =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areContentsTheSame(
                        @NonNull TagItem oldItem,
                        @NonNull TagItem newItem
                ) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areItemsTheSame(
                        @NonNull TagItem oldItem,
                        @NonNull TagItem newItem
                ) {
                    return oldItem.isSame(newItem);
                }
            };

    public interface OnClickListener {
        void onTagClicked(@NonNull TagItem item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final ItemTagsListBinding binding;

        public ViewHolder(@NonNull ItemTagsListBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(@NonNull TagItem item, OnClickListener listener) {
            binding.name.setText(item.info().name);
            binding.color.setColor(item.info().color);
            binding.getRoot().setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onTagClicked(item);
                }
            });
        }
    }
}
