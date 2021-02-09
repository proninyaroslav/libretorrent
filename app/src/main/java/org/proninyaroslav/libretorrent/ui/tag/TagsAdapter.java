/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.TagsListItemBinding;

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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        TagsListItemBinding binding = DataBindingUtil.inflate(
                inflater,
                R.layout.tags_list_item,
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TagItem item = getItem(position);
        if (item != null) {
            holder.bind(item, listener);
        }
    }

    static final DiffUtil.ItemCallback<TagItem> diffCallback =
            new DiffUtil.ItemCallback<TagItem>() {
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final TagsListItemBinding binding;

        public ViewHolder(@NonNull TagsListItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(
                @NonNull TagItem item,
                OnClickListener listener
        ) {
            binding.name.setText(item.info.name);
            binding.color.setColor(item.info.color);
            binding.getRoot().setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onTagClicked(item);
                }
            });
        }
    }
}
