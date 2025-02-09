/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.h6ah4i.android.widget.advrecyclerview.expandable.ExpandableItemState;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractExpandableItemViewHolder;

import org.proninyaroslav.libretorrent.databinding.DrawerGroupHeaderBinding;
import org.proninyaroslav.libretorrent.databinding.DrawerItemBinding;

import java.util.ArrayList;
import java.util.List;

/*
 * Adapter for expandable groups of clickable items (radio button-like behavior).
 */

public class DrawerExpandableAdapter extends AbstractExpandableItemAdapter<
        DrawerExpandableAdapter.GroupViewHolder,
        DrawerExpandableAdapter.ItemViewHolder> {
    private final List<DrawerGroup> groups;
    private final SelectionListener listener;
    private final RecyclerViewExpandableItemManager drawerItemManager;

    public DrawerExpandableAdapter(
            @NonNull List<DrawerGroup> groups,
            RecyclerViewExpandableItemManager drawerItemManager,
            SelectionListener listener
    ) {
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
    public GroupViewHolder onCreateGroupViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        var binding = DrawerGroupHeaderBinding.inflate(inflater, parent, false);

        return new GroupViewHolder(binding);
    }

    @Override
    @NonNull
    public ItemViewHolder onCreateChildViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        var binding = DrawerItemBinding.inflate(inflater, parent, false);

        return new ItemViewHolder(binding);
    }

    @Override
    public void onBindGroupViewHolder(
            @NonNull GroupViewHolder holder,
            int groupPosition,
            int viewType
    ) {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null) {
            return;
        }

        holder.bind(group);
    }

    @Override
    public void onBindChildViewHolder(
            @NonNull ItemViewHolder holder,
            int groupPosition,
            int childPosition,
            int viewType
    ) {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null) {
            return;
        }
        DrawerGroupItem item = group.items.get(childPosition);
        if (item == null) {
            return;
        }

        holder.bind(group, item);
        holder.itemView.setOnClickListener((v) -> {
            if (group.isItemSelected(item.id)) {
                return;
            }
            group.selectItem(item.id);
            drawerItemManager.notifyChildrenOfGroupItemChanged(groupPosition);
            if (listener != null) {
                listener.onItemSelected(group, item);
            }
        });
    }

    public DrawerGroup getGroup(int position) {
        if (position < 0 || position >= getGroupCount()) {
            throw new IndexOutOfBoundsException("position=" + position);
        }

        return groups.get(position);
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getChildCount(int groupPosition) {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null) {
            return 0;
        }

        return group.items.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null) {
            return RecyclerView.NO_ID;
        }

        return group.id;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        DrawerGroup group = groups.get(groupPosition);
        if (group == null) {
            return RecyclerView.NO_ID;
        }
        DrawerGroupItem item = group.items.get(childPosition);
        if (item == null) {
            return RecyclerView.NO_ID;
        }

        return item.id;
    }

    @Override
    public boolean onCheckCanExpandOrCollapseGroup(
            @NonNull GroupViewHolder holder,
            int groupPosition,
            int x,
            int y,
            boolean expand
    ) {
        return holder.itemView.isEnabled() && holder.itemView.isClickable();
    }

    public static class GroupViewHolder extends AbstractExpandableItemViewHolder {
        private final DrawerGroupHeaderBinding binding;

        GroupViewHolder(DrawerGroupHeaderBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(DrawerGroup group) {
            binding.groupHeader.setText(group.name);

            ExpandableItemState expandState = getExpandState();
            if (expandState.isUpdated()) {
                binding.groupHeader.setExpanded(
                        expandState.isExpanded(),
                        expandState.hasExpandedStateChanged()
                );
            }
        }
    }

    public static class ItemViewHolder extends AbstractExpandableItemViewHolder {
        private final DrawerItemBinding binding;

        ItemViewHolder(DrawerItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(DrawerGroup group, DrawerGroupItem item) {
            binding.label.setText(item.name);

            var isSelected = group.isItemSelected(item.id);
            binding.card.setChecked(isSelected);

            if (item.iconResId != -1) {
                binding.label.setCompoundDrawablesRelativeWithIntrinsicBounds(item.iconResId, 0, 0, 0);
                binding.label.setContentDescription(item.contentDescription);
            }
        }
    }

    public interface SelectionListener {
        void onItemSelected(DrawerGroup group, DrawerGroupItem item);
    }
}
