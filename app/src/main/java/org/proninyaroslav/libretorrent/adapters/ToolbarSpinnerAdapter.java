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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;

import java.util.ArrayList;
import java.util.List;

/*
 * The adapter for spinner in toolbar.
 */

public class ToolbarSpinnerAdapter extends BaseAdapter
{
    @SuppressWarnings("unused")
    private static final String TAG = ToolbarSpinnerAdapter.class.getSimpleName();

    private static final String DROPDOWN = "dropdown";
    private static final String NON_DROPDOWN = "non_dropdown";

    private List<String> items = new ArrayList<>();
    private Context context;

    public ToolbarSpinnerAdapter(Context context)
    {
        this.context = context;
    }

    public void clear()
    {
        items.clear();
    }

    public void addItem(String item)
    {
        items.add(item);
    }

    public void addItems(List<String> items)
    {
        this.items.addAll(items);
    }

    @Override
    public int getCount()
    {
        return items.size();
    }

    @Override
    public String getItem(int position)
    {
        return items.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getDropDownView(int position, View view, ViewGroup parent)
    {
        if (view == null || !view.getTag().toString().equals(DROPDOWN)) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.spinner_item_dropdown, parent, false);
            view.setTag(DROPDOWN);
        }

        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        textView.setText(getTitle(position));

        return view;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent)
    {
        if (view == null || !view.getTag().toString().equals(NON_DROPDOWN)) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.
                    toolbar_spinner_item_actionbar, parent, false);
            view.setTag(NON_DROPDOWN);
        }

        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        textView.setText(getTitle(position));

        return view;
    }

    private String getTitle(int position)
    {
        return (position >= 0 && position < items.size()) ? items.get(position) : "";
    }
}