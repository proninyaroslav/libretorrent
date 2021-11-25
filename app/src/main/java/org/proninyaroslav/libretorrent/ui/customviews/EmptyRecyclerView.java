/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.ui.customviews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/*
 * A RecyclerView with empty view.
 */

public class EmptyRecyclerView extends RecyclerView
{
    private View emptyView;

    final private AdapterDataObserver observer = new AdapterDataObserver()
    {
        @Override
        public void onChanged()
        {
            checkEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount)
        {
            checkEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount)
        {
            checkEmpty();
        }
    };

    public EmptyRecyclerView(Context context)
    {
        super(context);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    void checkEmpty()
    {
        if (emptyView != null && getAdapter() != null) {
            boolean isEmptyViewVisible = getAdapter().getItemCount() == 0;

            emptyView.setVisibility((isEmptyViewVisible ? VISIBLE : GONE));
            setVisibility((isEmptyViewVisible ? GONE : VISIBLE));
        }
    }

    @Override
    public void setAdapter(Adapter adapter)
    {
        var oldAdapter = getAdapter();
        if (oldAdapter != null)
            oldAdapter.unregisterAdapterDataObserver(observer);

        super.setAdapter(adapter);

        if (adapter != null)
            adapter.registerAdapterDataObserver(observer);

        checkEmpty();
    }

    public void setEmptyView(View emptyView)
    {
        this.emptyView = emptyView;
        checkEmpty();
    }
}
