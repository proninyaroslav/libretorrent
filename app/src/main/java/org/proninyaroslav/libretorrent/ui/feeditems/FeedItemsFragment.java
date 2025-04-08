/*
 * Copyright (C) 2018-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentFeedItemsBinding;
import org.proninyaroslav.libretorrent.ui.NavBarFragmentDirections;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedItemsFragment extends Fragment {
    private static final String TAG = FeedItemsFragment.class.getSimpleName();

    private static final String TAG_ITEMS_LIST_STATE = "items_list_state";

    private MainActivity activity;
    private FragmentFeedItemsBinding binding;
    private FeedItemsViewModel viewModel;
    private FeedItemsAdapter adapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable itemsListState;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity a) {
            activity = a;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFeedItemsBinding.inflate(inflater, container, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.swipeContainer, WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.swipeContainer, WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }
        if (Utils.isTwoPane(activity)) {
            binding.appBar.setNavigationIcon(null);
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        var args = FeedItemsFragmentArgs.fromBundle(getArguments());
        viewModel = new ViewModelProvider(this).get(FeedItemsViewModel.class);
        viewModel.setFeedId(args.getFeedId());

        layoutManager = new LinearLayoutManager(activity);
        binding.feedItemsList.setLayoutManager(layoutManager);
        binding.feedItemsList.addItemDecoration(Utils.buildListDivider(activity));
        binding.feedItemsList.setEmptyView(binding.emptyViewFeedItems);

        adapter = new FeedItemsAdapter(itemClickListener);
        binding.feedItemsList.setAdapter(adapter);

        binding.swipeContainer.setOnRefreshListener(() -> viewModel.refreshChannel());

        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());
        binding.appBar.setOnMenuItemClickListener((item) -> {
            int itemId = item.getItemId();
            if (itemId == R.id.refresh_feed_channel_menu) {
                viewModel.refreshChannel();
            } else if (itemId == R.id.mark_as_read_menu) {
                viewModel.markAllAsRead();
            }
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
        subscribeRefreshStatus();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (itemsListState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(itemsListState);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            itemsListState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_ITEMS_LIST_STATE, itemsListState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            itemsListState = savedInstanceState.getParcelable(TAG_ITEMS_LIST_STATE);
        }
    }

    private void subscribeAdapter() {
        disposables.add(observeFeedItems());
    }

    private Disposable observeFeedItems() {
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

    private void subscribeRefreshStatus() {
        disposables.add(viewModel.observeRefreshStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.swipeContainer::setRefreshing));
    }

    private final FeedItemsAdapter.ClickListener itemClickListener = new FeedItemsAdapter.ClickListener() {
        @Override
        public void onItemClicked(@NonNull FeedItemsListItem item) {
            openDownloadUrl(item);
        }

        @Override
        public void onItemMenuClicked(int menuId, @NonNull FeedItemsListItem item) {
            if (menuId == R.id.open_article_menu) {
                openArticle(item);
            } else if (menuId == R.id.mark_as_read_menu) {
                viewModel.markAsRead(item.id);
            } else if (menuId == R.id.mark_as_unread_menu) {
                viewModel.markAsUnread(item.id);
            }
        }
    };

    private void openArticle(FeedItemsListItem item) {
        if (TextUtils.isEmpty(item.articleUrl)) {
            Snackbar.make(binding.coordinatorLayout,
                            R.string.feed_item_url_not_found,
                            Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        viewModel.markAsRead(item.id);

        var i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(item.articleUrl));
        startActivity(i);
    }

    private void openDownloadUrl(FeedItemsListItem item) {
        if (TextUtils.isEmpty(item.downloadUrl)) {
            Snackbar.make(binding.coordinatorLayout,
                            R.string.feed_item_url_not_found,
                            Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        viewModel.markAsRead(item.id);

        var navController = activity.getRootNavController();
        var action = NavBarFragmentDirections.actionAddTorrent(Uri.parse(item.downloadUrl));
        navController.navigate(action);
    }
}
