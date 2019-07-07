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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerInfo;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ItemTrackersListBinding;

import java.util.Collections;
import java.util.List;

public class TrackerListAdapter extends ListAdapter<TrackerItem, TrackerListAdapter.ViewHolder>
        implements Selectable<TrackerItem>
{
    @SuppressWarnings("unused")
    private static final String TAG = TrackerListAdapter.class.getSimpleName();

    private SelectionTracker<TrackerItem> selectionTracker;

    public TrackerListAdapter()
    {
        super(diffCallback);
    }

    public void setSelectionTracker(SelectionTracker<TrackerItem> selectionTracker)
    {
        this.selectionTracker = selectionTracker;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTrackersListBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_trackers_list,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        TrackerItem item = getItem(position);
        if (selectionTracker != null)
            holder.setSelected(selectionTracker.isSelected(item));

        holder.bind(item);
    }

    @Override
    public void submitList(@Nullable List<TrackerItem> list)
    {
        if (list != null)
            Collections.sort(list);

        super.submitList(list);
    }

    @Override
    public TrackerItem getItemKey(int position)
    {
        if (position < 0 || position >= getCurrentList().size())
            return null;

        return getItem(position);
    }

    @Override
    public int getItemPosition(TrackerItem key)
    {
        return getCurrentList().indexOf(key);
    }

    private static final DiffUtil.ItemCallback<TrackerItem> diffCallback = new DiffUtil.ItemCallback<TrackerItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull TrackerItem oldItem,
                                          @NonNull TrackerItem newItem)
        {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull TrackerItem oldItem,
                                       @NonNull TrackerItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    interface ViewHolderWithDetails
    {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements ViewHolderWithDetails
    {
        private ItemTrackersListBinding binding;
        /* For selection support */
        private ItemDetails details = new ItemDetails();
        private boolean isSelected;

        public ViewHolder(ItemTrackersListBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(TrackerItem item)
        {
            Context context = itemView.getContext();

            details.adapterPosition = getAdapterPosition();
            details.selectionKey = item;

            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                    R.attr.defaultSelectRect,
                    R.attr.defaultRectRipple
            });

            if (isSelected)
                Utils.setBackground(itemView, a.getDrawable(0));
            else
                Utils.setBackground(itemView, a.getDrawable(1));
            a.recycle();

            binding.url.setText(item.url);

            String status = "";
            switch (item.status) {
                case TrackerInfo.Status.NOT_CONTACTED:
                    status = context.getString(R.string.tracker_state_not_contacted);
                    break;
                case TrackerInfo.Status.WORKING:
                    status = context.getString(R.string.tracker_state_working);
                    break;
                case TrackerInfo.Status.UPDATING:
                    status = context.getString(R.string.tracker_state_updating);
                    break;
                case TrackerInfo.Status.NOT_WORKING:
                    status = context.getString(R.string.tracker_state_not_working);
                    break;
            }
            binding.status.setText(status);
            if (!TextUtils.isEmpty(item.message)) {
                binding.message.setVisibility(View.VISIBLE);
                binding.message.setText(item.message);
            } else {
                binding.message.setVisibility(View.GONE);
            }

            if (item.status == TrackerInfo.Status.WORKING)
                binding.status.setTextColor(ContextCompat.getColor(context, R.color.ok));
            else if (item.status == TrackerInfo.Status.NOT_WORKING)
                binding.status.setTextColor(ContextCompat.getColor(context, R.color.error));
            else
                binding.status.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        private void setSelected(boolean isSelected)
        {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails()
        {
            return details;
        }
    }

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<TrackerItem>
    {
        Selectable<TrackerItem> selectable;

        public KeyProvider(Selectable<TrackerItem> selectable)
        {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public TrackerItem getKey(int position)
        {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull TrackerItem key)
        {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<TrackerItem>
    {
        public TrackerItem selectionKey;
        public int adapterPosition;

        @Nullable
        @Override
        public TrackerItem getSelectionKey()
        {
            return selectionKey;
        }

        @Override
        public int getPosition()
        {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<TrackerItem>
    {
        private final RecyclerView recyclerView;

        public ItemLookup(RecyclerView recyclerView)
        {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<TrackerItem> getItemDetails(@NonNull MotionEvent e)
        {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ViewHolder)
                    return ((ViewHolder)viewHolder).getItemDetails();
            }

            return null;
        }
    }
}
