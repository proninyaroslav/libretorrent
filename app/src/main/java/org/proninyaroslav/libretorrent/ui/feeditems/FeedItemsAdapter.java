/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.feeditems;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.ItemFeedItemsListBinding;
import org.proninyaroslav.libretorrent.ui.Selectable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class FeedItemsAdapter extends ListAdapter<FeedItemsListItem, FeedItemsAdapter.ViewHolder>
        implements Selectable<FeedItemsListItem>
{
    private static final String TAG = FeedItemsAdapter.class.getSimpleName();

    private ClickListener listener;

    public FeedItemsAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemFeedItemsListBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_feed_items_list,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        holder.bind(getItem(position), listener);
    }

    @Override
    public void submitList(@Nullable List<FeedItemsListItem> list)
    {
        if (list != null)
            Collections.sort(list);

        super.submitList(list);
    }

    @Override
    public FeedItemsListItem getItemKey(int position)
    {
        if (position < 0 || position >= getCurrentList().size())
            return null;

        return getItem(position);
    }

    @Override
    public int getItemPosition(FeedItemsListItem key)
    {
        return getCurrentList().indexOf(key);
    }

    private static final DiffUtil.ItemCallback<FeedItemsListItem> diffCallback = new DiffUtil.ItemCallback<FeedItemsListItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull FeedItemsListItem oldItem,
                                          @NonNull FeedItemsListItem newItem)
        {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull FeedItemsListItem oldItem,
                                       @NonNull FeedItemsListItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    public interface ClickListener
    {
        void onItemClicked(@NonNull FeedItemsListItem item);

        void onItemMenuClicked(int menuId, @NonNull FeedItemsListItem item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private ItemFeedItemsListBinding binding;

        public ViewHolder(ItemFeedItemsListBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(FeedItemsListItem item, ClickListener listener)
        {
            Context context = itemView.getContext();

            binding.menu.setOnClickListener((v) -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.inflate(R.menu.feed_item_popup);

                Menu menu = popup.getMenu();
                MenuItem markAsRead = menu.findItem(R.id.mark_as_read_menu);
                MenuItem markAsUnread = menu.findItem(R.id.mark_as_unread_menu);
                if (markAsRead != null)
                    markAsRead.setVisible(!item.read);
                if (markAsUnread != null)
                    markAsUnread.setVisible(item.read);

                popup.setOnMenuItemClickListener((MenuItem menuItem) -> {
                    if (listener != null)
                        listener.onItemMenuClicked(menuItem.getItemId(), item);
                    return true;
                });
                popup.show();
            });

            itemView.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemClicked(item);
            });

            int styleAttr;
            if (item.read)
                styleAttr = android.R.attr.textColorSecondary;
            else
                styleAttr = android.R.attr.textColorPrimary;
            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[]{ styleAttr });
            binding.title.setTextColor(a.getColor(0, 0));
            a.recycle();
            binding.title.setTextAppearance(item.read ? R.style.normalText : R.style.boldText);
            binding.title.setText(item.title);

            binding.pubDate.setText(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(item.pubDate)));
        }
    }
}
