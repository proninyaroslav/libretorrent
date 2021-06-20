/*
 * Copyright (C) 2018-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
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
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ItemFeedChannelListBinding;
import org.proninyaroslav.libretorrent.ui.Selectable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FeedChannelListAdapter extends ListAdapter<FeedChannelItem, FeedChannelListAdapter.ViewHolder>
        implements Selectable<FeedChannelItem>
{
    private static final String TAG = FeedChannelListAdapter.class.getSimpleName();

    private ClickListener listener;
    private SelectionTracker<FeedChannelItem> selectionTracker;
    private AtomicReference<FeedChannelItem> currOpenFeed = new AtomicReference<>();
    private boolean onBind = false;

    public FeedChannelListAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    public void setSelectionTracker(SelectionTracker<FeedChannelItem> selectionTracker)
    {
        this.selectionTracker = selectionTracker;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemFeedChannelListBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_feed_channel_list,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        onBind = true;

        FeedChannelItem item = getItem(position);
        if (selectionTracker != null)
            holder.setSelected(selectionTracker.isSelected(item));

        boolean isOpened = false;
        FeedChannelItem currFeed = currOpenFeed.get();
        if (currFeed != null)
            isOpened = item.id == currFeed.id;

        holder.bind(item, isOpened, listener);

        onBind = false;
    }

    @Override
    public void submitList(@Nullable List<FeedChannelItem> list)
    {
        if (list != null)
            Collections.sort(list);

        super.submitList(list);
    }

    @Override
    public FeedChannelItem getItemKey(int position)
    {
        if (position < 0 || position >= getCurrentList().size())
            return null;

        return getItem(position);
    }

    @Override
    public int getItemPosition(FeedChannelItem key)
    {
        return getCurrentList().indexOf(key);
    }

    public void markAsOpen(FeedChannelItem item)
    {
        FeedChannelItem prevItem = currOpenFeed.getAndSet(item);

        if (onBind)
            return;

        int position = getItemPosition(prevItem);
        if (position >= 0)
            notifyItemChanged(position);

        if (item != null) {
            position = getItemPosition(item);
            if (position >= 0)
                notifyItemChanged(position);
        }
    }

    public FeedChannelItem getOpenedItem()
    {
        return currOpenFeed.get();
    }

    private static final DiffUtil.ItemCallback<FeedChannelItem> diffCallback = new DiffUtil.ItemCallback<FeedChannelItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull FeedChannelItem oldItem,
                                          @NonNull FeedChannelItem newItem)
        {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull FeedChannelItem oldItem,
                                       @NonNull FeedChannelItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    public interface ClickListener
    {
        void onItemClicked(@NonNull FeedChannelItem item);
    }

    interface ViewHolderWithDetails
    {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements ViewHolderWithDetails
    {
        private ItemFeedChannelListBinding binding;
        /* For selection support */
        private FeedChannelItem selectionKey;
        private boolean isSelected;

        public ViewHolder(ItemFeedChannelListBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(FeedChannelItem item, boolean isOpened, ClickListener listener)
        {
            Context context = itemView.getContext();

            selectionKey = item;

            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                    R.attr.selectableColor,
                    R.attr.defaultRectRipple
            });
            Drawable d;
            if (isSelected)
                d = a.getDrawable(0);
            else
                d = a.getDrawable(1);
            if (d != null)
                Utils.setBackground(itemView, d);
            a.recycle();

            itemView.setOnClickListener((v) -> {
                /* Skip selecting and deselecting */
                if (isSelected)
                    return;

                if (listener != null)
                    listener.onItemClicked(item);
            });

            if (TextUtils.isEmpty(item.name)) {
                binding.url.setVisibility(View.GONE);
                binding.name.setText(item.url);
            } else {
                binding.url.setVisibility(View.VISIBLE);
                binding.url.setText(item.url);
                binding.name.setText(item.name);
            }

            String date;
            if (item.lastUpdate == 0)
                date = context.getString(R.string.feed_last_update_never);
            else
                date = SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(item.lastUpdate));
            binding.lastUpdate.setText(context.getString(R.string.feed_last_update_template, date));

            if (item.fetchError != null) {
                binding.error.setVisibility(View.VISIBLE);
                binding.error.setText(item.fetchError);
            } else {
                binding.error.setVisibility(View.GONE);
            }

            if (isOpened)
                d = ContextCompat.getDrawable(context, R.color.primary_light);
            else
                d = ContextCompat.getDrawable(context, android.R.color.transparent);
            if (d != null)
                Utils.setBackground(binding.indicatorCurOpenFeed, d);
        }

        private void setSelected(boolean isSelected)
        {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails()
        {
            return new ItemDetails(selectionKey, getBindingAdapterPosition());
        }
    }

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<FeedChannelItem>
    {
        private Selectable<FeedChannelItem> selectable;

        KeyProvider(Selectable<FeedChannelItem> selectable)
        {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public FeedChannelItem getKey(int position)
        {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull FeedChannelItem key)
        {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<FeedChannelItem>
    {
        private FeedChannelItem selectionKey;
        private int adapterPosition;

        ItemDetails(FeedChannelItem selectionKey, int adapterPosition)
        {
            this.selectionKey = selectionKey;
            this.adapterPosition = adapterPosition;
        }

        @Nullable
        @Override
        public FeedChannelItem getSelectionKey()
        {
            return selectionKey;
        }

        @Override
        public int getPosition()
        {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<FeedChannelItem>
    {
        private final RecyclerView recyclerView;

        ItemLookup(RecyclerView recyclerView)
        {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<FeedChannelItem> getItemDetails(@NonNull MotionEvent e)
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
