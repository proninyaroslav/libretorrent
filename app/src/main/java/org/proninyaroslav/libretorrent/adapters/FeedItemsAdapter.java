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
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.FeedItem;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class FeedItemsAdapter extends RecyclerView.Adapter<FeedItemsAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedItemsAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    private List<FeedItem> items;
    private Comparator<FeedItem> sorting = new Comparator<FeedItem>()
    {
        @Override
        public int compare(FeedItem o1, FeedItem o2)
        {
            return Long.valueOf(o2.getPubDate()).compareTo(o1.getPubDate());
        }
    };

    public FeedItemsAdapter(List<FeedItem> items, Context context, int rowLayout,
                            ViewHolder.ClickListener clickListener)
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
        FeedItem item = items.get(position);

        int styleAttr;
        if (item.isRead())
            styleAttr = android.R.attr.textColorSecondary;
        else
            styleAttr = android.R.attr.textColorPrimary;
        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[]{ styleAttr });
        holder.title.setTextColor(a.getColor(0, 0));
        a.recycle();
        Utils.setTextViewStyle(context, holder.title, (item.isRead() ? R.style.normalText : R.style.boldText));
        holder.title.setText(item.getTitle());

        holder.pubDate.setText(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.getPubDate())));
    }

    public synchronized void addItems(Collection<FeedItem> itms)
    {
        if (itms == null)
            return;

        items.addAll(itms);
        Collections.sort(items, sorting);
        notifyItemRangeInserted(0, itms.size());
    }

    public synchronized void updateItem(FeedItem item)
    {
        if (item == null)
            return;

        int position = items.indexOf(item);
        if (position >= 0) {
            items.set(position, item);
            notifyItemChanged(position);
        }
    }

    public synchronized void updateItems(Collection<FeedItem> itms)
    {
        if (itms == null)
            return;

        items.clear();
        items.addAll(itms);
        notifyItemRangeChanged(0, items.size());
    }

    public void clearAll()
    {
        int size = items.size();
        if (size > 0) {
            items.clear();
            notifyItemRangeRemoved(0, size);
        }
    }

    public FeedItem getItem(int position)
    {
        if (position < 0 || position >= items.size())
            return null;

        return items.get(position);
    }

    public int getItemPosition(FeedItem item)
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
            implements View.OnClickListener
    {
        private ClickListener listener;
        private List<FeedItem> items;
        TextView title;
        TextView pubDate;
        ImageButton menu;

        public ViewHolder(View itemView, final ClickListener listener, final List<FeedItem> items)
        {
            super(itemView);

            this.listener = listener;
            this.items = items;
            itemView.setOnClickListener(this);

            title = itemView.findViewById(R.id.item_title);
            pubDate = itemView.findViewById(R.id.item_pub_date);
            menu = itemView.findViewById(R.id.item_menu);
            menu.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    PopupMenu popup = new PopupMenu(v.getContext(), v);
                    popup.inflate(R.menu.feed_item_popup);
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
                    {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem)
                        {
                            int position = getAdapterPosition();

                            if (listener != null && position >= 0) {
                                FeedItem item = items.get(position);
                                listener.onMenuItemClicked(menuItem.getItemId(), item);
                            }

                            return true;
                        }
                    });
                    popup.show();
                }
            });
        }

        @Override
        public void onClick(View v)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                FeedItem item = items.get(position);
                listener.onItemClicked(position, item);
            }
        }

        public interface ClickListener
        {
            void onItemClicked(int position, FeedItem item);

            void onMenuItemClicked(int id, FeedItem item);
        }
    }
}
