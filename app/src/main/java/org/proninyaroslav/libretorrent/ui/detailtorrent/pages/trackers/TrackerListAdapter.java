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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.trackers;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.databinding.ItemTrackersListBinding;
import org.proninyaroslav.libretorrent.ui.Selectable;

import java.util.Collections;
import java.util.List;

public class TrackerListAdapter extends ListAdapter<TrackerItem, TrackerListAdapter.ViewHolder> implements Selectable<TrackerItem> {
    private SelectionTracker<TrackerItem> selectionTracker;

    public TrackerListAdapter() {
        super(diffCallback);
    }

    public void setSelectionTracker(SelectionTracker<TrackerItem> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var inflater = LayoutInflater.from(parent.getContext());
        var binding = ItemTrackersListBinding.inflate(inflater, null, false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        var item = getItem(position);
        if (selectionTracker != null) {
            holder.setSelected(selectionTracker.isSelected(item));
        }

        holder.bind(item);
    }

    @Override
    public void submitList(@Nullable List<TrackerItem> list) {
        if (list != null) {
            Collections.sort(list);
        }

        super.submitList(list);
    }

    @Override
    public TrackerItem getItemKey(int position) {
        if (position < 0 || position >= getCurrentList().size()) {
            return null;
        }

        return getItem(position);
    }

    @Override
    public int getItemPosition(TrackerItem key) {
        return getCurrentList().indexOf(key);
    }

    private static final DiffUtil.ItemCallback<TrackerItem> diffCallback = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areContentsTheSame(@NonNull TrackerItem oldItem,
                                          @NonNull TrackerItem newItem) {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull TrackerItem oldItem,
                                       @NonNull TrackerItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    interface ViewHolderWithDetails {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements ViewHolderWithDetails {
        private final ItemTrackersListBinding binding;
        /* For selection support */
        private TrackerItem selectionKey;
        private boolean isSelected;
        private final ColorStateList statusTextColor;

        public ViewHolder(ItemTrackersListBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
            statusTextColor = binding.status.getTextColors();
        }

        void bind(TrackerItem item) {
            var context = itemView.getContext();
            selectionKey = item;

            binding.card.setChecked(isSelected);
            binding.url.setText(item.url);

            var status = switch (item.status) {
                case TrackerInfo.Status.NOT_CONTACTED ->
                        context.getString(R.string.tracker_state_not_contacted);
                case TrackerInfo.Status.WORKING ->
                        context.getString(R.string.tracker_state_working);
                case TrackerInfo.Status.UPDATING ->
                        context.getString(R.string.tracker_state_updating);
                case TrackerInfo.Status.NOT_WORKING ->
                        context.getString(R.string.tracker_state_not_working);
                default -> "";
            };
            if (TextUtils.isEmpty(status)) {
                binding.status.setVisibility(View.GONE);
            } else {
                binding.status.setVisibility(View.VISIBLE);
                binding.status.setText(status);
            }
            if (TextUtils.isEmpty(item.message)) {
                binding.message.setVisibility(View.GONE);
            } else {
                binding.message.setVisibility(View.VISIBLE);
                binding.message.setText(item.message);
            }

            if (item.status == TrackerInfo.Status.WORKING) {
                binding.status.setTextColor(MaterialColors.getColor(binding.status, R.attr.colorOk));
            } else if (item.status == TrackerInfo.Status.NOT_WORKING) {
                binding.status.setTextColor(MaterialColors.getColor(binding.status, R.attr.colorError));
            } else {
                binding.status.setTextColor(statusTextColor);
            }
        }

        private void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails() {
            return new ItemDetails(selectionKey, getBindingAdapterPosition());
        }
    }

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<TrackerItem> {
        private final Selectable<TrackerItem> selectable;

        KeyProvider(Selectable<TrackerItem> selectable) {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public TrackerItem getKey(int position) {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull TrackerItem key) {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<TrackerItem> {
        private final TrackerItem selectionKey;
        private final int adapterPosition;

        ItemDetails(TrackerItem selectionKey, int adapterPosition) {
            this.selectionKey = selectionKey;
            this.adapterPosition = adapterPosition;
        }

        @Nullable
        @Override
        public TrackerItem getSelectionKey() {
            return selectionKey;
        }

        @Override
        public int getPosition() {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<TrackerItem> {
        private final RecyclerView recyclerView;

        ItemLookup(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<TrackerItem> getItemDetails(@NonNull MotionEvent e) {
            var view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                var viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ViewHolderWithDetails v) {
                    return v.getItemDetails();
                }
            }

            return null;
        }
    }
}
