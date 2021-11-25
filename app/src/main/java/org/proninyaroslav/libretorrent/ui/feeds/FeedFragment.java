/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentFeedBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.addfeed.AddFeedActivity;
import org.proninyaroslav.libretorrent.ui.customviews.RecyclerViewDividerDecoration;

import java.util.Iterator;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedFragment extends Fragment
        implements FeedChannelListAdapter.ClickListener
{
    private static final String TAG = FeedFragment.class.getSimpleName();

    private static final String TAG_FEED_LIST_STATE = "feed_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_DELETE_FEEDS_DIALOG = "delete_feeds_dialog";

    private AppCompatActivity activity;
    private FeedChannelListAdapter adapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable feedListState;
    private SelectionTracker<FeedChannelItem> selectionTracker;
    private ActionMode actionMode;
    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private MsgFeedViewModel msgViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog deleteFeedsDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_feed, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(FeedViewModel.class);
        msgViewModel = provider.get(MsgFeedViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        adapter = new FeedChannelListAdapter(this);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        };
        layoutManager = new LinearLayoutManager(activity);
        binding.feedList.setLayoutManager(layoutManager);
        binding.feedList.setItemAnimator(animator);
        binding.feedList.setEmptyView(binding.emptyViewFeeds);
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{R.attr.divider});
        binding.feedList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        a.recycle();
        binding.feedList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.feedList,
                new FeedChannelListAdapter.KeyProvider(adapter),
                new FeedChannelListAdapter.ItemLookup(binding.feedList),
                StorageStrategy.createParcelableStorage(FeedChannelItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<FeedChannelItem>() {
            @Override
            public void onSelectionChanged()
            {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = activity.startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null)
                        actionMode.finish();
                    actionMode = null;

                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());

                    /* Show/hide menu items after change selected feeds */
                    int size = selectionTracker.getSelection().size();
                    if (size == 1 || size == 2)
                        actionMode.invalidate();
                }
            }

            @Override
            public void onSelectionRestored()
            {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null)
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        adapter.setSelectionTracker(selectionTracker);

        binding.swipeContainer.setOnRefreshListener(() -> viewModel.refreshAllFeeds());

        binding.addChannel.setOnClickListener((v) ->
                startActivity(new Intent(activity, AddFeedActivity.class)));

        FragmentManager fm = getChildFragmentManager();
        deleteFeedsDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_DELETE_FEEDS_DIALOG);
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
        subscribeAlertDialog();
        subscribeMsgViewModel();
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

        if (feedListState != null)
            layoutManager.onRestoreInstanceState(feedListState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            feedListState = savedInstanceState.getParcelable(TAG_FEED_LIST_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        feedListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_FEED_LIST_STATE, feedListState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    private void subscribeAdapter()
    {
        getAllFeedsSingle();
        disposables.add(observeFeeds());
    }

    private Disposable observeFeeds()
    {
        return viewModel.observerAllFeeds()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((feedList) ->
                        Flowable.fromIterable(feedList)
                                .map(FeedChannelItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting feed list error: " +
                                Log.getStackTraceString(t)));
    }

    private void getAllFeedsSingle()
    {
        disposables.add(viewModel.getAllFeedsSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((feedList) ->
                        Observable.fromIterable(feedList)
                                .map(FeedChannelItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting feed list error: " +
                                Log.getStackTraceString(t))));
    }

    private void subscribeMsgViewModel()
    {
        disposables.add(msgViewModel.observeFeedItemsClosed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));

        disposables.add(msgViewModel.observeFeedsDeleted()
                .filter((ids) -> {
                    FeedChannelItem item = adapter.getOpenedItem();
                    if (item == null)
                        return false;

                    return ids.contains(item.id);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null)
                        return;

                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_FEEDS_DIALOG) && deleteFeedsDialog != null) {
                                deleteFeeds();
                                deleteFeedsDialog.dismiss();
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_FEEDS_DIALOG) && deleteFeedsDialog != null)
                                deleteFeedsDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void subscribeRefreshStatus()
    {
        disposables.add(viewModel.observeRefreshStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.swipeContainer::setRefreshing));
    }

    @Override
    public void onItemClicked(@NonNull FeedChannelItem item)
    {
        if (Utils.isTwoPane(activity))
            adapter.markAsOpen(item);
        msgViewModel.feedItemsOpened(item.id);
    }

    private void setActionModeTitle(int itemCount)
    {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback()
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            MenuItem edit = menu.findItem(R.id.edit_feed_channel_menu);
            MenuItem refresh = menu.findItem(R.id.refresh_feed_channel_menu);
            MenuItem copy = menu.findItem(R.id.copy_feed_channel_url_menu);

            boolean show = selectionTracker.getSelection().size() <= 1;
            edit.setVisible(show);
            refresh.setVisible(show);
            copy.setVisible(show);

            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.feed_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            int itemId = item.getItemId();
            if (itemId == R.id.delete_feed_channel_menu) {
                deleteFeedsDialog();
            } else if (itemId == R.id.edit_feed_channel_menu) {
                editChannel();
                mode.finish();
            } else if (itemId == R.id.copy_feed_channel_url_menu) {
                copyChannelUrl();
                mode.finish();
            } else if (itemId == R.id.refresh_feed_channel_menu) {
                refreshSelectedFeeds();
                mode.finish();
            } else if (itemId == R.id.select_all_channels_menu) {
                selectAllFeeds();
            } else if (itemId == R.id.mark_as_read_menu) {
                markAsReadFeeds();
                mode.finish();
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            selectionTracker.clearSelection();
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void deleteFeedsDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_DELETE_FEEDS_DIALOG) == null) {
            deleteFeedsDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    (selectionTracker.getSelection().size() > 1 ?
                            getString(R.string.delete_selected_channels) :
                            getString(R.string.delete_selected_channel)),
                    0,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            deleteFeedsDialog.show(fm, TAG_DELETE_FEEDS_DIALOG);
        }
    }

    @SuppressLint("RestrictedApi")
    private void selectAllFeeds()
    {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }

    private void deleteFeeds()
    {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection) -> (FeedChannel)selection)
                .toList()
                .doOnSuccess(viewModel::deleteFeeds)
                .flatMap((feeds) ->
                        Observable.fromIterable(feeds)
                                .map((channel) -> channel.id)
                                .toList()
                )
                .subscribe(msgViewModel::feedsDeleted));

        if (actionMode != null)
            actionMode.finish();
    }

    private void copyChannelUrl()
    {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<FeedChannelItem> it = selections.iterator();
        if (!it.hasNext())
            return;

        if (viewModel.copyFeedUrlToClipboard(it.next())) {
          Toast.makeText(activity,
                  R.string.link_copied_to_clipboard,
                  Toast.LENGTH_SHORT)
                  .show();
        }
    }

    private void refreshSelectedFeeds()
    {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        long [] ids = new long[selections.size()];
        int i = 0;
        for (FeedChannelItem selection : selections)
            ids[i++] = selection.id;

        viewModel.refreshFeeds(ids);
    }

    private void editChannel()
    {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<FeedChannelItem> it = selections.iterator();
        if (!it.hasNext())
            return;

        Intent i = new Intent(activity, AddFeedActivity.class);
        i.setAction(AddFeedActivity.ACTION_EDIT_FEED);
        i.putExtra(AddFeedActivity.TAG_FEED_ID, it.next().id);
        startActivity(i);
    }

    private void markAsReadFeeds()
    {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.id))
                .toList()
                .subscribe(viewModel::markAsReadFeeds));
    }
}
