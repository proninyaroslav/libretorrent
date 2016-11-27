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
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.Collection;
import java.util.List;

public class TrackerListAdapter extends SelectableAdapter<TrackerListAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = TrackerListAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    private List<TrackerStateParcel> items;

    public TrackerListAdapter(List<TrackerStateParcel> items, Context context,
                              int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.items = items;
        this.clickListener = clickListener;
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
        TrackerStateParcel state = items.get(position);

        holder.state = state;

        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                R.attr.defaultSelectRect,
                R.attr.defaultRectRipple
        });

        if (isSelected(position)) {
            Utils.setBackground(
                    holder.itemTrackerList,
                    a.getDrawable(0));
        } else {
            Utils.setBackground(
                    holder.itemTrackerList,
                    a.getDrawable(1));
        }
        a.recycle();

        holder.url.setText(state.url);

        String status = "";
        switch (state.status) {
            case TrackerStateParcel.Status.NOT_CONTACTED:
                status = context.getString(R.string.tracker_state_not_contacted);
                break;
            case TrackerStateParcel.Status.WORKING:
                status = context.getString(R.string.tracker_state_working);
                break;
            case TrackerStateParcel.Status.UPDATING:
                status = context.getString(R.string.tracker_state_updating);
                break;
            case TrackerStateParcel.Status.NOT_WORKING:
                if (state.message != null && !TextUtils.isEmpty(state.message)) {
                    status = String.format(context.getString(R.string.tracker_state_error), state.message);
                } else {
                    status = context.getString(R.string.tracker_state_not_working);
                }
                break;
        }
        holder.status.setText(status);

        if (state.status == TrackerStateParcel.Status.WORKING) {
            holder.status.setTextColor(ContextCompat.getColor(context, R.color.ok));
        } else if (state.status == TrackerStateParcel.Status.NOT_WORKING) {
            holder.status.setTextColor(ContextCompat.getColor(context, R.color.error));
        } else {
            holder.status.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }
    }

    public synchronized void addItems(Collection<TrackerStateParcel> states)
    {
        items.addAll(states);
        notifyItemRangeInserted(0, states.size());
    }

    public void clearAll()
    {
        int size = items.size();
        if (size > 0) {
            items.clear();

            notifyItemRangeRemoved(0, size);
        }
    }

    public synchronized void updateItems(Collection<TrackerStateParcel> states)
    {
        items.clear();
        items.addAll(states);

        notifyDataSetChanged();
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
        private LinearLayout itemTrackerList;
        private TextView url;
        private TextView status;
        private ClickListener listener;
        private TrackerStateParcel state;

        public ViewHolder(View itemView, ClickListener listener, List<TrackerStateParcel> states)
        {
            super(itemView);

            this.listener = listener;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            itemTrackerList = (LinearLayout) itemView.findViewById(R.id.item_trackers_list);
            url = (TextView) itemView.findViewById(R.id.tracker_url);
            status = (TextView) itemView.findViewById(R.id.tracker_status);
        }

        @Override
        public void onClick(View view)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                listener.onItemClicked(position, state);
            }
        }

        @Override
        public boolean onLongClick(View view)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                listener.onItemLongClicked(position, state);

                return true;
            }

            return false;
        }

        public interface ClickListener
        {
            void onItemClicked(int position, TrackerStateParcel state);

            boolean onItemLongClicked(int position, TrackerStateParcel state);
        }
    }
}
