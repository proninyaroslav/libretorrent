/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.FeedChannel;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FeedChannelListAdapter extends SelectableAdapter<FeedChannelListAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedChannelListAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    private List<FeedChannel> items;
    private AtomicReference<FeedChannel> curOpenChannel = new AtomicReference<>();
    private Comparator<FeedChannel> sorting = new Comparator<FeedChannel>()
    {
        @Override
        public int compare(FeedChannel o1, FeedChannel o2)
        {
            String cmp1 = (o1.getName() == null || TextUtils.isEmpty(o1.getName()) ? o1.getUrl() : o1.getName());
            String cmp2 = (o2.getName() == null || TextUtils.isEmpty(o2.getName()) ? o2.getUrl() : o2.getName());

            return cmp1.compareTo(cmp2);
        }
    };

    public FeedChannelListAdapter(List<FeedChannel> items, Context context,
                                  int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.clickListener = clickListener;
        this.items = items;
        Collections.sort(this.items, sorting);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(rowLayout, parent, false);

        return new ViewHolder(v, clickListener, items);
    }

    @SuppressWarnings("ResourceType")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        FeedChannel item = items.get(position);

        Utils.setBackground(holder.indicatorCurOpenTorrent,
                ContextCompat.getDrawable(context, android.R.color.transparent));
        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                R.attr.defaultSelectRect,
                R.attr.defaultRectRipple
        });
        if (isSelected(position)) {
            Utils.setBackground(
                    holder.itemRssChannelList,
                    a.getDrawable(0));
        } else {
            Utils.setBackground(
                    holder.itemRssChannelList,
                    a.getDrawable(1));
        }
        a.recycle();

        holder.name.setText(item.getName());
        holder.url.setText(item.getUrl());
        String lastUpdateTemplate = context.getString(R.string.feed_last_update_template);
        String date;
        if (item.getLastUpdate() == 0)
            date = context.getString(R.string.feed_last_update_never);
        else
            date = SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(item.getLastUpdate()));
        holder.lastUpdate.setText(String.format(lastUpdateTemplate, date));

        if (item.getFetchError() != null) {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(item.getFetchError());
        } else {
            holder.error.setVisibility(View.GONE);
        }

        FeedChannel curChannel = curOpenChannel.get();
        if (curChannel != null) {
            if (getItemPosition(curChannel) == position && Utils.isTwoPane(context)) {
                if (!isSelected(position)) {
                    a = context.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.curOpenTorrentIndicator });
                    Utils.setBackground(holder.itemRssChannelList, a.getDrawable(0));
                    a.recycle();
                }

                Utils.setBackground(holder.indicatorCurOpenTorrent,
                        ContextCompat.getDrawable(context, R.color.accent));
            }
        }
    }

    public synchronized void addItem(FeedChannel item)
    {
        if (item == null)
            return;

        items.add(item);
        Collections.sort(items, sorting);
        notifyItemInserted(items.indexOf(item));
    }

    public synchronized void setItems(Collection<FeedChannel> itms)
    {
        if (itms == null)
            return;

        items.clear();
        items.addAll(itms);
        if (items.size() != 0) {
            Collections.sort(items, sorting);
            notifyItemRangeInserted(0, itms.size());
        } else {
            notifyDataSetChanged();
        }
    }

    /*
     * Mark the torrent as currently open.
     */

    public void markAsOpen(FeedChannel item)
    {
        curOpenChannel.set(item);
        notifyDataSetChanged();
    }

    public synchronized void replaceItem(FeedChannel oldItem, FeedChannel item)
    {
        if (item == null || oldItem == null)
            return;

        int position = items.indexOf(oldItem);
        if (position >= 0) {
            items.set(position, item);
            Collections.sort(items, sorting);
            notifyItemChanged(position);
        }
    }

    public synchronized void updateItem(FeedChannel item)
    {
        if (item == null)
            return;

        int position = items.indexOf(item);
        if (position >= 0) {
            items.set(position, item);
            Collections.sort(items, sorting);
            int newPosition = items.indexOf(item);
            if (newPosition == position)
                notifyItemChanged(position);
            else
                notifyDataSetChanged();
        }
    }

    public void clearAll()
    {
        int size = items.size();
        if (size > 0) {
            items.clear();
            notifyItemRangeRemoved(0, size);
        }
    }

    public void deleteItem(FeedChannel item)
    {
        if (item == null)
            return;

        items.remove(item);
        notifyDataSetChanged();
    }

    public FeedChannel getItem(int position)
    {
        if (position < 0 || position >= items.size())
            return null;

        return items.get(position);
    }

    public int getItemPosition(FeedChannel item)
    {
        if (item == null)
            return -1;

        return items.indexOf(item);
    }

    public boolean isEmpty()
    {
        return items.isEmpty();
    }

    @Override
    public int getItemCount()
    {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener
    {
        private ClickListener listener;
        private List<FeedChannel> items;
        LinearLayout itemRssChannelList;
        TextView name;
        TextView url;
        TextView lastUpdate;
        TextView error;
        View indicatorCurOpenTorrent;

        public ViewHolder(View itemView, final ClickListener listener, final List<FeedChannel> items)
        {
            super(itemView);

            this.listener = listener;
            this.items = items;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            itemRssChannelList = itemView.findViewById(R.id.item_feed_channel_list);
            name = itemView.findViewById(R.id.feed_name);
            url = itemView.findViewById(R.id.feed_url);
            lastUpdate = itemView.findViewById(R.id.feed_last_update);
            error = itemView.findViewById(R.id.feed_channel_error);
            indicatorCurOpenTorrent = itemView.findViewById(R.id.indicator_cur_open_torrent);
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                FeedChannel item = items.get(position);
                listener.onItemClicked(position, item);
            }
        }

        @Override
        public boolean onLongClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                FeedChannel item = items.get(getAdapterPosition());
                listener.onItemLongClicked(position, item);

                return true;
            }

            return false;
        }

        public interface ClickListener
        {
            void onItemClicked(int position, FeedChannel item);

            boolean onItemLongClicked(int position, FeedChannel item);
        }
    }
}
