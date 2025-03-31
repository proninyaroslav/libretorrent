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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ActionMode;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.AbstractListDetailFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonSyntaxException;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedUnreadCount;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentFeedBinding;
import org.proninyaroslav.libretorrent.ui.NavBarFragment;
import org.proninyaroslav.libretorrent.ui.NavBarFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.BlankFragmentDirections;
import org.proninyaroslav.libretorrent.ui.feeditems.FeedItemsFragmentArgs;
import org.proninyaroslav.libretorrent.ui.feeditems.FeedItemsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedFragment extends AbstractListDetailFragment {
    private static final String TAG = FeedFragment.class.getSimpleName();

    private static final String REQUEST_SAVE_BACKUP_DIALOG_KEY = TAG + "_save_backup_dialog";
    private static final String REQUEST_RESTORE_BACKUP_DIALOG_KEY = TAG + "_restore_backup_dialog";
    private static final String REQUEST_DELETE_FEED_DIALOG_KEY = TAG + "_delete_feed_dialog";

    private static final String TAG_FEED_LIST_STATE = "feed_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";

    private MainActivity activity;
    private FeedChannelListAdapter adapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable feedListState;
    private SelectionTracker<FeedChannelItem> selectionTracker;
    private ActionMode actionMode;
    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @NonNull
    @Override
    public View onCreateListPaneView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
        binding = FragmentFeedBinding.inflate(layoutInflater, viewGroup, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.swipeContainer, WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.swipeContainer, WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        var navBarFragment = activity.findNavBarFragment(this);

        if (navBarFragment != null) {
            setSaveBackupDialogListener(navBarFragment);
            setRestoreBackupDialogListener(navBarFragment);
        }
        setDeleteFeedDialogListener();

        return binding.getRoot();
    }

    private void setSaveBackupDialogListener(@NonNull NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                REQUEST_SAVE_BACKUP_DIALOG_KEY,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.SAVE_FILE) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        backupFeedsErrorDialog(null);
                    } else {
                        backupFeeds(uri);
                    }
                }
        );
    }

    private void setDeleteFeedDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                REQUEST_DELETE_FEED_DIALOG_KEY,
                this,
                (requestKey, result) -> {
                    var isDelete = result.getBoolean(DeleteFeedDialog.KEY_RESULT_VALUE);
                    if (isDelete) {
                        deleteFeeds();
                    }
                }
        );
    }

    private void setRestoreBackupDialogListener(@NonNull NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                REQUEST_RESTORE_BACKUP_DIALOG_KEY,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.FILE_CHOOSER) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        restoreFeedsBackupErrorDialog(null);
                    } else {
                        restoreFeedsBackup(uri);
                    }
                }
        );
    }

    @NonNull
    @Override
    public NavHostFragment onCreateDetailPaneNavHostFragment() {
        return NavHostFragment.create(R.navigation.feed_two_pane_graph);
    }

    @Override
    public void onListPaneViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onListPaneViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        getSlidingPaneLayout().setLockMode(SlidingPaneLayout.LOCK_MODE_LOCKED);

        var provider = new ViewModelProvider(this);
        viewModel = provider.get(FeedViewModel.class);

        adapter = new FeedChannelListAdapter(feedClickListener);
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
        binding.feedList.setEmptyView(binding.emptyViewFeedList);
        binding.feedList.addItemDecoration(Utils.buildListDivider(activity));
        binding.feedList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.feedList,
                new FeedChannelListAdapter.KeyProvider(adapter),
                new FeedChannelListAdapter.ItemLookup(binding.feedList),
                StorageStrategy.createParcelableStorage(FeedChannelItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<>() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = activity.startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                    actionMode = null;
                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());
                    /* Show/hide menu items after change selected feeds */
                    int size = selectionTracker.getSelection().size();
                    if (size == 1 || size == 2) {
                        actionMode.invalidate();
                    }
                }
            }

            @Override
            public void onSelectionRestored() {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }
        adapter.setSelectionTracker(selectionTracker);

        binding.swipeContainer.setOnRefreshListener(() -> viewModel.refreshAllFeeds());

        binding.addChannel.setOnClickListener((v) -> showAddFeedDialog(null));

        binding.appBar.setOnMenuItemClickListener(this::onMenuItemClickListener);

        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSlidingPaneLayout().isOpen() && getSlidingPaneLayout().isSlideable()) {
                    getSlidingPaneLayout().closePane();
                } else {
                    NavHostFragment.findNavController(FeedFragment.this).navigateUp();
                }
            }
        });

        var args = FeedFragmentArgs.fromBundle(getArguments());
        if (args.getUri() != null) {
            showAddFeedDialog(args.getUri());
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity a) {
            activity = a;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        var navController = getDetailPaneNavHostFragment().getNavController();
        var dest = navController.getCurrentDestination();
        if (dest != null && dest.getId() == R.id.blankFragment) {
            getSlidingPaneLayout().close();
        }

        subscribeAdapter();
        subscribeFeedsDeleted();
        subscribeRefreshStatus();

        getDetailPaneNavHostFragment()
                .getNavController()
                .addOnDestinationChangedListener(destinationListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        getDetailPaneNavHostFragment()
                .getNavController()
                .removeOnDestinationChangedListener(destinationListener);

        disposables.clear();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (feedListState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(feedListState);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            feedListState = savedInstanceState.getParcelable(TAG_FEED_LIST_STATE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            feedListState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_FEED_LIST_STATE, feedListState);
        }
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    private void subscribeAdapter() {
        disposables.add(observeFeeds());
    }

    private Disposable observeFeeds() {
        return Flowable.combineLatest(viewModel.observerAllFeeds(),
                        viewModel.observeUnreadItemsCount(),
                        Pair::create
                )
                .subscribeOn(Schedulers.io())
                .flatMapSingle((pair) -> {
                            var unreadCountMap = buildUnreadCountItemMap(pair.second);
                            return Flowable.fromIterable(pair.first)
                                    .map((feed) -> {
                                        var unreadCountItem = unreadCountMap.get(feed.id);
                                        return buildFeedItem(feed, unreadCountItem);
                                    })
                                    .toList();
                        }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting feed list error: " +
                                Log.getStackTraceString(t)));
    }

    private Map<Long, FeedUnreadCount> buildUnreadCountItemMap(List<FeedUnreadCount> list) {
        return list.stream().collect(
                Collectors.toMap(FeedUnreadCount::feedId, item -> item)
        );
    }

    private FeedChannelItem buildFeedItem(FeedChannel feed, FeedUnreadCount unreadCountItem) {
        return new FeedChannelItem(
                feed,
                unreadCountItem == null ? 0 : unreadCountItem.count()
        );
    }

    private void subscribeFeedsDeleted() {
        disposables.add(viewModel.observeFeedsDeleted()
                .filter((idList) -> {
                    if (!isAdded()) {
                        return false;
                    }
                    try {
                        var navController = getDetailPaneNavHostFragment().getNavController();
                        var entry = navController.getBackStackEntry(R.id.feedItemsFragment);
                        var args = FeedItemsFragmentArgs.fromBundle(entry.getArguments());
                        return idList.contains(args.getFeedId());
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (!isAdded()) {
                        return;
                    }
                    var navController = getDetailPaneNavHostFragment().getNavController();
                    var slidingPaneLayout = getSlidingPaneLayout();

                    // Clear back stack
                    if (getDetailPaneNavHostFragment().getChildFragmentManager().getBackStackEntryCount() > 0) {
                        navController.popBackStack();
                    }

                    var action = BlankFragmentDirections.actionOpenBlank();
                    var options = new NavOptions.Builder();
                    setDetailsNavAnimation(options);
                    navController.navigate(action, options.build());
                    slidingPaneLayout.close();
                })
        );
    }

    private void subscribeRefreshStatus() {
        disposables.add(viewModel.observeRefreshStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.swipeContainer::setRefreshing));
    }

    private final FeedChannelListAdapter.ClickListener feedClickListener =
            (item) -> openFeedItems(item.id);

    private void openFeedItems(long feedId) {
        var navController = getDetailPaneNavHostFragment().getNavController();
        var slidingPaneLayout = getSlidingPaneLayout();

        // Clear back stack
        if (getDetailPaneNavHostFragment().getChildFragmentManager().getBackStackEntryCount() > 0) {
            navController.popBackStack();
        }

        var action = FeedItemsFragmentDirections.actionFeedItems(feedId);
        var options = new NavOptions.Builder();
        setDetailsNavAnimation(options);
        navController.navigate(action, options.build());
        slidingPaneLayout.open();
    }

    private void setDetailsNavAnimation(NavOptions.Builder options) {
        var slidingPaneLayout = getSlidingPaneLayout();
        if (slidingPaneLayout.isOpen()) {
            options.setEnterAnim(R.anim.nav_slide_enter_anim)
                    .setExitAnim(R.anim.nav_fade_exit_anim);
        }
    }

    private boolean onMenuItemClickListener(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_feed_channel_menu) {
            viewModel.refreshAllFeeds();
        } else if (itemId == R.id.backup_feed_channels_menu) {
            backupFeedsChooseDialog();
        } else if (itemId == R.id.restore_feed_channels_backup_menu) {
            restoreFeedsChooseDialog();
        }
        return true;
    }

    private void setActionModeTitle(int itemCount) {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
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
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.feed_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
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
        public void onDestroyActionMode(ActionMode mode) {
            selectionTracker.clearSelection();
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void deleteFeedsDialog() {
        if (!isAdded()) {
            return;
        }

        var action = FeedFragmentDirections.actionDeleteFeedsDialog(
                REQUEST_DELETE_FEED_DIALOG_KEY,
                selectionTracker.getSelection().size()
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    @SuppressLint("RestrictedApi")
    private void selectAllFeeds() {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }

    private void deleteFeeds() {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        viewModel.deleteFeeds(StreamSupport.stream(selections.spliterator(), false)
                .map(FeedChannel.class::cast)
                .toList());

        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void copyChannelUrl() {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<FeedChannelItem> it = selections.iterator();
        if (!it.hasNext()) {
            return;
        }

        if (viewModel.copyFeedUrlToClipboard(it.next())) {
            Toast.makeText(activity,
                            R.string.link_copied_to_clipboard,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void refreshSelectedFeeds() {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        long[] ids = new long[selections.size()];
        int i = 0;
        for (FeedChannelItem selection : selections) {
            ids[i++] = selection.id;
        }

        viewModel.refreshFeeds(ids);
    }

    private void editChannel() {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<FeedChannelItem> it = selections.iterator();
        if (!it.hasNext()) {
            return;
        }

        var action = FeedFragmentDirections.actionEditFeedDialog(it.next().id);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void markAsReadFeeds() {
        MutableSelection<FeedChannelItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.id))
                .toList()
                .subscribe(viewModel::markAsReadFeeds));
    }

    private void backupFeedsChooseDialog() {
        var action = NavBarFragmentDirections.actionSaveFileChooseDialog(
                viewModel.buildBackupFileManagerConfig(),
                REQUEST_SAVE_BACKUP_DIALOG_KEY
        );
        activity.getRootNavController().navigate(action);
    }

    private void restoreFeedsChooseDialog() {
        var action = NavBarFragmentDirections.actionOpenFileDialog(
                viewModel.buildRestoreFileManagerConfig(getString(R.string.feeds_backup_selection_dialog_title)),
                REQUEST_RESTORE_BACKUP_DIALOG_KEY
        );

        activity.getRootNavController().navigate(action);
    }

    private void backupFeedsErrorDialog(Throwable e) {
        var action = FeedFragmentDirections.actionErrorReportDialog(
                        getString(R.string.error_backup_feeds)
                )
                .setException(e);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void restoreFeedsBackupErrorDialog(Throwable e) {
        if (e instanceof JsonSyntaxException) {
            Snackbar.make(binding.coordinatorLayout,
                            R.string.error_import_invalid_format,
                            Snackbar.LENGTH_SHORT
                    )
                    .show();
        } else {
            var action = FeedFragmentDirections.actionErrorReportDialog(
                            getString(R.string.error_restore_feeds_backup)
                    )
                    .setException(e);
            NavHostFragment.findNavController(this).navigate(action);
        }
    }

    private void backupFeeds(Uri file) {
        disposables.add(
                Completable.fromCallable(() -> {
                            viewModel.saveFeedsSync(file);
                            return true;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> Toast.makeText(activity,
                                        R.string.backup_feeds_successfully,
                                        Toast.LENGTH_SHORT)
                                .show(), this::restoreFeedsBackupErrorDialog)
        );
    }

    private void restoreFeedsBackup(Uri file) {
        disposables.add(Observable.fromCallable(() -> viewModel.restoreFeedsSync(file))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((feedIdList) -> {
                    Toast.makeText(activity,
                                    R.string.restore_feeds_backup_successfully,
                                    Toast.LENGTH_SHORT)
                            .show();
                    viewModel.refreshFeeds(feedIdList);
                }, this::backupFeedsErrorDialog));
    }

    private void showAddFeedDialog(@Nullable Uri uri) {
        if (!isAdded()) {
            return;
        }

        var action = FeedFragmentDirections.actionAddFeedDialog()
                .setUri(uri);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private final NavController.OnDestinationChangedListener destinationListener =
            (navController, navDestination, arguments) -> {
                if (!Utils.isTwoPane(activity)) {
                    return;
                }
                try {
                    Bundle rawArgs;
                    if (navDestination.getId() == R.id.feedItemsFragment) {
                        rawArgs = arguments;
                    } else {
                        var entry = navController.getBackStackEntry(R.id.feedItemsFragment);
                        rawArgs = entry.getArguments();
                    }
                    if (rawArgs == null) {
                        return;
                    }
                    var args = FeedItemsFragmentArgs.fromBundle(rawArgs);
                    adapter.markAsOpen(args.getFeedId());
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            };
}
