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

package org.proninyaroslav.libretorrent.ui.feeditems;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentFeedItemsBinding;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.addtorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.ui.customviews.RecyclerViewDividerDecoration;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedItemsFragment extends Fragment
    implements FeedItemsAdapter.ClickListener
{
    private static final String TAG = FeedItemsFragment.class.getSimpleName();

    private static final String TAG_ITEMS_LIST_STATE = "items_list_state";

    private AppCompatActivity activity;
    private FragmentFeedItemsBinding binding;
    private FeedItemsViewModel viewModel;
    private FeedItemsAdapter adapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable itemsListState;
    private long feedId;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static FeedItemsFragment newInstance(long feedId)
    {
        FeedItemsFragment fragment = new FeedItemsFragment();
        fragment.setFeedId(feedId);
        fragment.setArguments(new Bundle());

        return fragment;
    }

    public long getFeedId()
    {
        return feedId;
    }

    public void setFeedId(long feedId)
    {
        this.feedId = feedId;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_feed_items, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = new ViewModelProvider(activity).get(FeedItemsViewModel.class);

        /* Remove previous data if fragment changed */
        if (Utils.isTwoPane(activity))
            viewModel.clearData();
        viewModel.setFeedId(feedId);

        if (Utils.isTwoPane(activity)) {
            binding.toolbar.inflateMenu(R.menu.feed_items);
            binding.toolbar.setNavigationIcon(ContextCompat.getDrawable(activity.getApplicationContext(),
                    R.drawable.ic_arrow_back_white_24dp));
            binding.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        } else {
            binding.toolbar.setTitle(R.string.details);
            activity.setSupportActionBar(binding.toolbar);
            setHasOptionsMenu(true);
            if (activity.getSupportActionBar() != null)
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener((v) -> onBackPressed());

        layoutManager = new LinearLayoutManager(activity);
        binding.feedItemsList.setLayoutManager(layoutManager);
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });
        binding.feedItemsList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        a.recycle();
        binding.feedItemsList.setEmptyView(binding.emptyViewFeedItems);

        adapter = new FeedItemsAdapter(this);
        binding.feedItemsList.setAdapter(adapter);

        binding.swipeContainer.setOnRefreshListener(() -> viewModel.refreshChannel());
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
        subscribeRefreshStatus();
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (itemsListState != null)
            layoutManager.onRestoreInstanceState(itemsListState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        itemsListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_ITEMS_LIST_STATE, itemsListState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            itemsListState = savedInstanceState.getParcelable(TAG_ITEMS_LIST_STATE);
    }

    private void subscribeAdapter()
    {
        getAllFeedItemsSingle();
        disposables.add(observeFeedItems());
    }

    private Disposable observeFeedItems()
    {
        return viewModel.observeItemsByFeedId()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((itemList) ->
                        Flowable.fromIterable(itemList)
                                .map(FeedItemsListItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting item list error: " +
                                Log.getStackTraceString(t)));
    }

    private void getAllFeedItemsSingle()
    {
        disposables.add(viewModel.getItemsByFeedIdSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((itemList) ->
                        Observable.fromIterable(itemList)
                                .map(FeedItemsListItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting item list error: " +
                                Log.getStackTraceString(t))));
    }

    private void subscribeRefreshStatus()
    {
        disposables.add(viewModel.observeRefreshStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.swipeContainer::setRefreshing));
    }

    @Override
    public void onItemClicked(@NonNull FeedItemsListItem item)
    {
        openDownloadUrl(item);
    }

    @Override
    public void onItemMenuClicked(int menuId, @NonNull FeedItemsListItem item)
    {
        if (menuId == R.id.open_article_menu) {
            openArticle(item);
        } else if (menuId == R.id.mark_as_read_menu) {
            viewModel.markAsRead(item.id);
        } else if (menuId == R.id.mark_as_unread_menu) {
            viewModel.markAsUnread(item.id);
        }
    }

    private void openArticle(FeedItemsListItem item)
    {
        if (TextUtils.isEmpty(item.articleUrl)) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.feed_item_url_not_found,
                    Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        viewModel.markAsRead(item.id);

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(item.articleUrl));
        startActivity(i);
    }

    private void openDownloadUrl(FeedItemsListItem item)
    {
        if (TextUtils.isEmpty(item.downloadUrl)) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.feed_item_url_not_found,
                    Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        viewModel.markAsRead(item.id);

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, Uri.parse(item.downloadUrl));
        startActivity(i);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.feed_items, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem)
    {
        int itemId = menuItem.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
        } else if (itemId == R.id.refresh_feed_channel_menu) {
            viewModel.refreshChannel();
        } else if (itemId == R.id.mark_as_read_menu) {
            viewModel.markAllAsRead();
        }

        return true;
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback)activity).onFragmentFinished(this, intent, code);
    }
}
