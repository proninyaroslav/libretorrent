/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.h6ah4i.android.widget.advrecyclerview.expandable.ExpandableItemState;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemViewHolder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.customviews.ExpansionHeader;

import java.util.ArrayList;
import java.util.List;

/*
 * Adapter for expandable groups of clickable items (radio button-like behavior).
 */

public class DrawerExpandableAdapter extends AbstractExpandableItemAdapter<
        DrawerExpandableAdapter.GroupViewHolder,
        DrawerExpandableAdapter.ItemViewHolder>
{
    private List<DrawerGroup> groups;
    private SelectionListener listener;
    private RecyclerViewExpandableItemManager drawerItemManager;

    public DrawerExpandableAdapter(@NonNull List<DrawerGroup> groups,
                                   RecyclerViewExpandableItemManager drawerItemManager,
                                   SelectionListener listener)
    {
        this.groups = new ArrayList<>(groups);
        this.drawerItemManager = drawerItemManager;
        this.listener = listener;
        /*
         * ExpandableItemAdapter requires stable ID, and also
         * have to implement the getGroupItemId()/getChildItemId() methods appropriately.
         */
        setHasStableIds(true);
    }

    @Override
    @NonNull
    public GroupViewHolder onCreateGroupViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(R.layout.drawer_group_header, parent, false);

        return new GroupViewHolder(v);
    }

    @Override
    @NonNull
    public ItemViewHolder onCreateChildViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(R.layout.drawer_item, parent, false);

        return new ItemViewHolder(v);
    }

    @Override
    public void onBindGroupViewHolder(@NonNull GroupViewHolder holder, int groupPosition,
                                      int viewType)
    {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null)
            return;

        holder.bind(group);
    }

    @Override
    public void onBindChildViewHolder(@NonNull ItemViewHolder holder, int groupPosition,
                                      int childPosition, int viewType)
    {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null)
            return;
        DrawerGroupItem item = group.items.get(childPosition);
        if (item == null)
            return;

        holder.bind(group, item);
        holder.itemView.setOnClickListener((v) -> {
            if (group.isItemSelected(item.id))
                return;
            group.selectItem(item.id);
            drawerItemManager.notifyChildrenOfGroupItemChanged(groupPosition);
            if (listener != null)
                listener.onItemSelected(group, item);
        });
    }

    public DrawerGroup getGroup(int position)
    {
        if (position < 0 || position >= getGroupCount())
            throw new IndexOutOfBoundsException("position=" + position);

        return groups.get(position);
    }

    @Override
    public int getGroupCount()
    {
        return groups.size();
    }

    @Override
    public int getChildCount(int groupPosition)
    {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null)
            return 0;

        return group.items.size();
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null)
            return RecyclerView.NO_ID;

        return group.id;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null)
            return RecyclerView.NO_ID;
        DrawerGroupItem item = group.items.get(childPosition);
        if (item == null)
            return RecyclerView.NO_ID;

        return item.id;
    }

    @Override
    public int getGroupItemViewType(int groupPosition)
    {
        return 0;
    }

    @Override
    public int getChildItemViewType(int groupPosition, int childPosition)
    {
        return 0;
    }

    @Override
    public boolean onCheckCanExpandOrCollapseGroup(@NonNull GroupViewHolder holder, int groupPosition,
                                                   int x, int y, boolean expand)
    {
        return holder.itemView.isEnabled() && holder.itemView.isClickable();
    }

    public static class GroupViewHolder extends AbstractExpandableItemViewHolder
    {
        private ExpansionHeader groupHeader;

        GroupViewHolder(View itemView)
        {
            super(itemView);

            groupHeader = (ExpansionHeader)itemView;
        }

        void bind(DrawerGroup group)
        {
            groupHeader.setText(group.name);

            ExpandableItemState expandState = getExpandState();
            if (expandState.isUpdated())
                groupHeader.setExpanded(expandState.isExpanded(), expandState.hasExpandedStateChanged());
        }
    }

    public static class ItemViewHolder extends AbstractExpandableItemViewHolder
    {
        private ImageView icon;
        private TextView name;

        ItemViewHolder(View itemView)
        {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
            name = itemView.findViewById(R.id.name);
        }

        void bind(DrawerGroup group, DrawerGroupItem item)
        {
            Context context = itemView.getContext();

            name.setText(item.name);
            if (item.iconResId != -1)
                icon.setImageResource(item.iconResId);

            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                    R.attr.selectableDrawer,
                    R.attr.dialogRectRipple
            });
            int colorIdx = (group.isItemSelected(item.id) ? 0 : 1);
            Drawable d = a.getDrawable(colorIdx);
            if (d != null)
                Utils.setBackground(itemView, d);
            a.recycle();
        }
    }

    public interface SelectionListener
    {
        void onItemSelected(DrawerGroup group, DrawerGroupItem item);
    }
}
