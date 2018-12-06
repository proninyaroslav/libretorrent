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

package org.proninyaroslav.libretorrent.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.proninyaroslav.libretorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.FeedItemsAdapter;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.FeedItem;
import org.proninyaroslav.libretorrent.core.storage.FeedStorage;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.EmptyRecyclerView;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.services.FeedFetcherService;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;

import java.util.ArrayList;

public class FeedItemsFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedItemsFragment.class.getSimpleName();

    private static final String TAG_FETCH_ERROR_DIALOG = "fetch_error_dialog";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_FEED_URL = "torrent_id";
    private static final String TAG_ITEMS_LIST_STATE = "items_list_state";

    private static final int ADD_TORRENT_REQUEST = 1;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private CoordinatorLayout coordinatorLayout;
    private FeedItemsAdapter adapter;
    private LinearLayoutManager layoutManager;
    private EmptyRecyclerView itemList;
    private SwipeRefreshLayout swipeRefreshLayout;
    /* Save state scrolling */
    private Parcelable itemsListState;
    private ArrayList<FeedItem> items = new ArrayList<>();
    private String feedUrl;
    private FeedStorage storage;

    public static FeedItemsFragment newInstance(String feedUrl)
    {
        FeedItemsFragment fragment = new FeedItemsFragment();
        fragment.feedUrl = feedUrl;

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_feed_items, container, false);

        toolbar = v.findViewById(R.id.toolbar);
        coordinatorLayout = v.findViewById(R.id.coordinator_layout);
        swipeRefreshLayout = v.findViewById(R.id.swipe_container);
        itemList = v.findViewById(R.id.feed_items_list);

        return v;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        Utils.showColoredStatusBar_KitKat(activity);

        if (Utils.isTwoPane(activity)) {
            toolbar.inflateMenu(R.menu.feed_items);
            toolbar.setNavigationIcon(ContextCompat.getDrawable(activity.getApplicationContext(),
                    R.drawable.ic_arrow_back_white_24dp));
            toolbar.setNavigationOnClickListener((View view) -> onBackPressed());
            toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        } else {
            if (toolbar != null) {
                toolbar.setTitle(R.string.feed_items);
                activity.setSupportActionBar(toolbar);
                setHasOptionsMenu(true);
            }
            if (activity.getSupportActionBar() != null)
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        layoutManager = new LinearLayoutManager(activity);
        itemList.setLayoutManager(layoutManager);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator()
        {
            @Override
            public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder)
            {
                return true;
            }
        };
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });
        itemList.setItemAnimator(animator);
        itemList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        itemList.setEmptyView(activity.findViewById(R.id.empty_view_feed_items));
        a.recycle();

        storage = new FeedStorage(activity.getApplicationContext());
        if (savedInstanceState != null) {
            items = savedInstanceState.getParcelableArrayList(TAG_ITEMS);
            feedUrl = savedInstanceState.getString(TAG_FEED_URL);
        } else {
            if (feedUrl != null)
                items.addAll(storage.getItemsByFeedUrl(feedUrl));
        }
        adapter = new FeedItemsAdapter(new ArrayList<>(items), activity,
                R.layout.item_feed_items_list, feedItemsListener);
        itemList.setAdapter(adapter);

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.accent));
        swipeRefreshLayout.setOnRefreshListener(this::refreshChannel);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (itemsListState != null && layoutManager != null)
            layoutManager.onRestoreInstanceState(itemsListState);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        LocalBroadcastManager.getInstance(activity).registerReceiver(
                feedManagerReceiver, new IntentFilter(FeedFetcherService.ACTION_CHANNEL_RESULT));
    }

    @Override
    public void onStop()
    {
        super.onStop();

        LocalBroadcastManager.getInstance(activity).unregisterReceiver(feedManagerReceiver);
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;
        Fragment fragment = fm.findFragmentByTag(TAG_FETCH_ERROR_DIALOG);

        /* Prevents leak the dialog in portrait mode */
        if (Utils.isLargeScreenDevice(activity) && fragment != null)
            ((BaseAlertDialog)fragment).dismiss();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putString(TAG_FEED_URL, feedUrl);
        outState.putParcelableArrayList(TAG_ITEMS, items);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            itemsListState = savedInstanceState.getParcelable(TAG_ITEMS_LIST_STATE);
    }

    public void setFeedUrl(String feedUrl)
    {
        this.feedUrl = feedUrl;
    }

    public String getFeedUrl()
    {
        return feedUrl;
    }

    BroadcastReceiver feedManagerReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent i)
        {
            if (i == null || i.getAction() == null)
                return;

            if (i.getAction().equals(FeedFetcherService.ACTION_CHANNEL_RESULT)) {
                String url = i.getStringExtra(FeedFetcherService.TAG_CHANNEL_URL_RESULT);
                if (url == null)
                    return;
                if (!url.equals(feedUrl))
                    return;
                replaceItems();
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    };

    private void replaceItems()
    {
        if (feedUrl == null)
            return;

        items.clear();
        items.addAll(storage.getItemsByFeedUrl(feedUrl));
        adapter.clearAll();
        if (items.size() == 0)
            adapter.notifyDataSetChanged();
        else
            adapter.addItems(items);
    }

    private void markAllAsRead()
    {
        if (feedUrl == null)
            return;

        storage.markAllAsRead();
        items.clear();
        items.addAll(storage.getItemsByFeedUrl(feedUrl));
        adapter.clearAll();
        if (items.size() == 0)
            adapter.notifyDataSetChanged();
        else
            adapter.addItems(items);
    }

    private void markAsRead(FeedItem item)
    {
        if (item == null || item.isRead())
            return;

        storage.markAsRead(item);
        item.setRead(true);
        adapter.updateItem(item);
    }

    FeedItemsAdapter.ViewHolder.ClickListener feedItemsListener = new FeedItemsAdapter.ViewHolder.ClickListener()
    {
        @Override
        public void onItemClicked(int position, FeedItem item)
        {
            markAsRead(item);
            openDownloadUrl(item);
        }

        @Override
        public void onMenuItemClicked(int id, FeedItem item)
        {
            if (id == R.id.open_article_menu) {
                markAsRead(item);
                openArticle(item);
            }
        }
    };

    private void openArticle(FeedItem item)
    {
        if (item == null)
            return;

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(item.getArticleUrl()));
        startActivity(i);
    }

    private void openDownloadUrl(FeedItem item)
    {
        if (item == null)
            return;

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, Uri.parse(item.getDownloadUrl()));
        startActivityForResult(i, ADD_TORRENT_REQUEST);
    }

    private void refreshChannel()
    {
        swipeRefreshLayout.setRefreshing(true);

        Intent i = new Intent(activity, FeedFetcherService.class);
        i.setAction(FeedFetcherService.ACTION_FETCH_CHANNEL);
        i.putExtra(FeedFetcherService.TAG_CHANNEL_URL_ARG, feedUrl);
        FeedFetcherService.enqueueWork(activity, i);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.feed_items, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.refresh_feed_channel_menu:
                refreshChannel();
                break;
            case R.id.mark_as_read_menu:
                markAllAsRead();
                break;
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == ADD_TORRENT_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data.hasExtra(AddTorrentActivity.TAG_ADD_TORRENT_PARAMS)) {
                AddTorrentParams params = data.getParcelableExtra(AddTorrentActivity.TAG_ADD_TORRENT_PARAMS);
                if (params != null) {
                    Intent i = new Intent(activity.getApplicationContext(), TorrentTaskService.class);
                    i.setAction(TorrentTaskService.ACTION_ADD_TORRENT);
                    i.putExtra(TorrentTaskService.TAG_ADD_TORRENT_PARAMS, params);
                    activity.startService(i);
                }
            }
        }
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
