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

package org.proninyaroslav.libretorrent.ui.main.drawer;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DrawerEmptyTagsListItemBinding;
import org.proninyaroslav.libretorrent.databinding.DrawerNoTagsListItemBinding;
import org.proninyaroslav.libretorrent.databinding.DrawerTagsListItemBinding;

public class TagsAdapter extends ListAdapter<AbstractTagItem, TagsAdapter.AbstractViewHolder> {
    private static final int TYPE_EMPTY_ITEM = 0;
    private static final int TYPE_TAG_ITEM = 1;
    private static final int TYPE_NO_TAGS_ITEM = 2;

    private AbstractTagItem selectedItem;

    @NonNull
    private final OnClickListener listener;

    public TagsAdapter(@NonNull OnClickListener listener) {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public AbstractViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return switch (viewType) {
            case TYPE_EMPTY_ITEM -> {
                var binding = DrawerEmptyTagsListItemBinding.inflate(inflater, parent, false);
                yield new EmptyItemViewHolder(binding);
            }
            case TYPE_TAG_ITEM -> {
                var binding = DrawerTagsListItemBinding.inflate(inflater, parent, false);
                yield new ItemViewHolder(binding);
            }
            case TYPE_NO_TAGS_ITEM -> {
                var binding = DrawerNoTagsListItemBinding.inflate(inflater, parent, false);
                yield new NoTagsItemViewHolder(binding);
            }
            default -> throw new IllegalStateException("Unknown item type: " + viewType);
        };

    }

    @Override
    public void onBindViewHolder(@NonNull AbstractViewHolder holder, int position) {
        AbstractTagItem item = getItem(position);
        if (item != null) {
            holder.bind(item, listener);
        }
    }

    @Override
    public int getItemViewType(int position) {
        AbstractTagItem item = getItem(position);
        if (item instanceof TagItem) {
            return TYPE_TAG_ITEM;
        } else if (item instanceof EmptyTagItem) {
            return TYPE_EMPTY_ITEM;
        } else if (item instanceof NoTagsItem) {
            return TYPE_NO_TAGS_ITEM;
        }

        throw new IllegalStateException("Unknown item: " + item);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedItem(@NonNull AbstractTagItem item) {
        selectedItem = item;
        int position = getCurrentList().indexOf(item);
        if (position != -1) {
            notifyDataSetChanged();
        }
    }

    public AbstractTagItem getSelectedItem() {
        return selectedItem;
    }

    static final DiffUtil.ItemCallback<AbstractTagItem> diffCallback =
            new DiffUtil.ItemCallback<AbstractTagItem>() {
                @Override
                public boolean areContentsTheSame(
                        @NonNull AbstractTagItem oldItem,
                        @NonNull AbstractTagItem newItem
                ) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areItemsTheSame(
                        @NonNull AbstractTagItem oldItem,
                        @NonNull AbstractTagItem newItem
                ) {
                    return oldItem.isSame(newItem);
                }
            };

    public interface OnClickListener {
        void onTagSelected(@NonNull AbstractTagItem item);

        void onTagMenuClicked(@NonNull AbstractTagItem item, int menuId);
    }

    public static abstract class AbstractViewHolder extends RecyclerView.ViewHolder {
        public AbstractViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(
                @NonNull AbstractTagItem item,
                OnClickListener listener
        );
    }

    class ItemViewHolder extends AbstractViewHolder {
        @NonNull
        private final DrawerTagsListItemBinding binding;

        public ItemViewHolder(@NonNull DrawerTagsListItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        @Override
        void bind(
                @NonNull AbstractTagItem item,
                OnClickListener listener
        ) {
            if (item instanceof TagItem) {
                bind((TagItem) item, listener);
            }
        }

        private void bind(
                @NonNull TagItem item,
                OnClickListener listener
        ) {
            binding.label.setText(item.info.name);
            binding.color.setColor(item.info.color);
            binding.getRoot().setOnClickListener((v) -> {
                setSelectedItem(item);
                if (listener != null) {
                    listener.onTagSelected(item);
                }
            });
            binding.menu.setOnClickListener((v) -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.inflate(R.menu.tag_item_popup);
                popup.setOnMenuItemClickListener((MenuItem menuItem) -> {
                    if (listener != null) {
                        listener.onTagMenuClicked(item, menuItem.getItemId());
                    }
                    return true;
                });
                popup.show();
            });

            binding.card.setChecked(item.isSame(selectedItem));
        }
    }

    class EmptyItemViewHolder extends AbstractViewHolder {
        @NonNull
        private final DrawerEmptyTagsListItemBinding binding;

        public EmptyItemViewHolder(@NonNull DrawerEmptyTagsListItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        @Override
        void bind(
                @NonNull AbstractTagItem item,
                OnClickListener listener
        ) {
            if (item instanceof EmptyTagItem) {
                bind((EmptyTagItem) item, listener);
            }
        }

        private void bind(
                @NonNull EmptyTagItem item,
                OnClickListener listener
        ) {
            binding.getRoot().setOnClickListener((v) -> {
                setSelectedItem(item);
                if (listener != null) {
                    listener.onTagSelected(item);
                }
            });

            binding.card.setChecked(item.isSame(selectedItem));
        }
    }

    class NoTagsItemViewHolder extends AbstractViewHolder {
        @NonNull
        private final DrawerNoTagsListItemBinding binding;

        public NoTagsItemViewHolder(@NonNull DrawerNoTagsListItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        @Override
        void bind(
                @NonNull AbstractTagItem item,
                OnClickListener listener
        ) {
            if (item instanceof NoTagsItem) {
                bind((NoTagsItem) item, listener);
            }
        }

        private void bind(
                @NonNull NoTagsItem item,
                OnClickListener listener
        ) {
            binding.getRoot().setOnClickListener((v) -> {
                setSelectedItem(item);
                if (listener != null) {
                    listener.onTagSelected(item);
                }
            });

            binding.card.setChecked(item.isSame(selectedItem));
        }
    }
}
