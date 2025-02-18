/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.AbstractListDetailFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.search.SearchView;
import com.google.android.material.snackbar.Snackbar;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilterCollection;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.MainDrawerContentBinding;
import org.proninyaroslav.libretorrent.databinding.MainListPaneBinding;
import org.proninyaroslav.libretorrent.databinding.MainNavRailHeaderBinding;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;
import org.proninyaroslav.libretorrent.ui.log.LogActivity;
import org.proninyaroslav.libretorrent.ui.main.drawer.AbstractTagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerExpandableAdapter;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroup;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroupItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.EmptyTagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.NoTagsItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.TagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.TagsAdapter;

import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The list of torrents.
 */

public class MainFragment extends AbstractListDetailFragment
        implements TorrentListAdapter.ClickListener {
    private static final String TAG = MainFragment.class.getSimpleName();

    private static final String TAG_TORRENT_LIST_STATE = "torrent_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";

    private MainActivity activity;
    private TorrentListAdapter adapter;
    private TorrentListAdapter searchAdapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable torrentListState;
    private SelectionTracker<TorrentListItem> selectionTracker;
    private MainListPaneBinding binding;
    private MainDrawerContentBinding drawerBinding;
    private MainNavRailHeaderBinding navRailHeaderBinding = null;

    private TorrentInfoProvider infoProvider;
    private MainViewModel viewModel;
    private MsgMainViewModel msgViewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final CompositeDisposable searchDisposables = new CompositeDisposable();

    private DrawerExpandableAdapter drawerAdapter;
    private RecyclerViewExpandableItemManager drawerItemManager;
    private TagsAdapter tagsAdapter;

    @NonNull
    @Override
    public View onCreateListPaneView(
            @NonNull LayoutInflater layoutInflater,
            @Nullable ViewGroup viewGroup,
            @Nullable Bundle bundle
    ) {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.main_list_pane, viewGroup, false);
        drawerBinding = MainDrawerContentBinding.inflate(getLayoutInflater());
        var navBarFragment = activity.getNavBarFragment(this);
        if (Utils.isLargeScreenDevice(activity)) {
            navRailHeaderBinding = MainNavRailHeaderBinding.inflate(getLayoutInflater());
            if (navBarFragment != null) {
                navBarFragment.setNavRailHeaderView(navRailHeaderBinding.getRoot());
            }
            binding.addTorrentFab.hide();
            binding.searchBar.setNavigationIcon(R.drawable.ic_search_24px);
        }
        if (navBarFragment != null) {
            Utils.applyWindowInsets(navBarFragment.getNavigationView(), drawerBinding.getRoot());
        }
        Utils.applyWindowInsets(binding.torrentList, WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);

        if (navBarFragment != null) {
            setOpenTorrentFileDialogListener(navBarFragment);
        }
        setDeleteDialogListener();

        return binding.getRoot();
    }

    private void setOpenTorrentFileDialogListener(NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                FileManagerFragment.KEY_RESULT,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT_VALUE);
                    if (resultValue == null) {
                        return;
                    }
                    Uri uri = resultValue.uri();
                    if (uri == null) {
                        Snackbar.make(
                                activity,
                                binding.mainCoordinatorLayout,
                                getString(R.string.error_open_torrent_file),
                                Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        var action = NavBarFragmentDirections.actionAddTorrent(uri);
                        activity.getRootNavController().navigate(action);
                    }
                }
        );
    }

    private void setDeleteDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                DeleteTorrentDialog.KEY_RESULT,
                this,
                (requestKey, result) -> {
                    var resultValue = (DeleteTorrentDialog.Result) result.getSerializable(
                            DeleteTorrentDialog.KEY_RESULT_VALUE
                    );
                    if (resultValue == null) {
                        return;
                    }
                    switch (resultValue) {
                        case DELETE -> deleteTorrents(false);
                        case DELETE_WITH_FILES -> deleteTorrents(true);
                        case CANCEL -> finishContextualMode();
                    }
                }
        );
    }

    private FloatingActionButton getAddTorrentFab() {
        if (navRailHeaderBinding != null) {
            return navRailHeaderBinding.addTorrentFab;
        } else {
            return binding.addTorrentFab;
        }
    }

    private void setNavigationIconListener(View.OnClickListener l) {
        if (navRailHeaderBinding != null) {
            navRailHeaderBinding.drawerButton.setOnClickListener(l);
        } else {
            binding.searchBar.setNavigationOnClickListener(l);
        }
    }

    @Override
    public void onListPaneViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onListPaneViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        infoProvider = TorrentInfoProvider.getInstance(activity.getApplicationContext());
        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(MainViewModel.class);
        msgViewModel = provider.get(MsgMainViewModel.class);

        adapter = new TorrentListAdapter(this);
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
        binding.torrentList.setLayoutManager(layoutManager);
        binding.torrentList.setItemAnimator(animator);
        binding.torrentList.setEmptyView(binding.emptyViewTorrentList);
        binding.torrentList.addItemDecoration(buildListDivider());
        binding.torrentList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.torrentList,
                new TorrentListAdapter.KeyProvider(adapter),
                new TorrentListAdapter.ItemLookup(binding.torrentList),
                StorageStrategy.createParcelableStorage(TorrentListItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<>() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();

                var addTorrentFab = getAddTorrentFab();
                if (selectionTracker.hasSelection()) {
                    startContextualMode();
                    setContextualAppBarTitle(selectionTracker.getSelection().size());
                } else if (!selectionTracker.hasSelection()) {
                    finishContextualMode();
                    addTorrentFab.show();
                } else {
                    addTorrentFab.hide();
                    setContextualAppBarTitle(selectionTracker.getSelection().size());
                }
            }

            @Override
            public void onSelectionRestored() {
                super.onSelectionRestored();

                var addTorrentFab = getAddTorrentFab();
                startContextualMode();
                var size = selectionTracker.getSelection().size();
                setContextualAppBarTitle(size);
                if (size > 0) {
                    addTorrentFab.hide();
                } else {
                    addTorrentFab.show();
                }
            }
        });

        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }
        adapter.setSelectionTracker(selectionTracker);

        getAddTorrentFab().setOnClickListener(this::showFabMenu);

        Intent i = activity.getIntent();
        if (i != null && MainActivity.ACTION_ADD_TORRENT_SHORTCUT.equals(i.getAction())) {
            /* Prevents re-reading action after device configuration changes */
            i.setAction(null);
            showAddTorrentMenu();
        }

        initDrawer();
        initSearch();

        setNavigationIconListener((v) -> {
            var navBarFragment = activity.getNavBarFragment(this);
            if (navBarFragment != null) {
                navBarFragment.getDrawerLayout().open();
            }
        });
        binding.searchBar.setOnMenuItemClickListener(this::onMenuItemClickListener);

        binding.contextualAppBar.setOnMenuItemClickListener(this::onContextualMenuItemClickListener);
        binding.contextualAppBar.setNavigationOnClickListener((v) -> finishContextualMode());
    }

    @NonNull
    @Override
    public NavHostFragment onCreateDetailPaneNavHostFragment() {
        return NavHostFragment.create(R.navigation.main_two_pane_nav_graph);
    }

    boolean onMenuItemClickListener(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.about_menu) {
            showAboutDialog();
        } else if (itemId == R.id.shutdown_app_menu) {
            viewModel.stopEngine();
            activity.finish();
        } else if (itemId == R.id.pause_all_menu) {
            viewModel.pauseAll();
        } else if (itemId == R.id.resume_all_menu) {
            viewModel.resumeAll();
        } else if (itemId == R.id.log_menu) {
            startActivity(new Intent(activity, LogActivity.class));
        }

        return true;
    }

    private void showAboutDialog() {
        var action = MainFragmentDirections.actionAboutDialog();
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void initDrawer() {
        var navBarFragment = activity.getNavBarFragment(this);
        if (navBarFragment != null) {
            navBarFragment.getNavigationView().addView(drawerBinding.getRoot());
        }

        drawerItemManager = new RecyclerViewExpandableItemManager(null);
        drawerItemManager.setDefaultGroupsExpandedState(false);
        drawerItemManager.setOnGroupCollapseListener((groupPosition, fromUser, payload) -> {
            if (fromUser) {
                saveGroupExpandState(groupPosition, false);
            }
        });
        drawerItemManager.setOnGroupExpandListener((groupPosition, fromUser, payload) -> {
            if (fromUser) {
                saveGroupExpandState(groupPosition, true);
            }
        });
        GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();
        /*
         * Change animations are enabled by default since support-v7-recyclerview v22.
         * Need to disable them when using animation indicator.
         */
        animator.setSupportsChangeAnimations(false);

        List<DrawerGroup> groups = Utils.getNavigationDrawerItems(
                activity,
                PreferenceManager.getDefaultSharedPreferences(activity)
        );
        drawerAdapter = new DrawerExpandableAdapter(groups, drawerItemManager, this::onDrawerItemSelected);
        var wrappedDrawerAdapter = drawerItemManager.createWrappedAdapter(drawerAdapter);
        onDrawerGroupsCreated();

        drawerBinding.drawerItemsList.setLayoutManager(new LinearLayoutManager(activity) {
            @Override
            public boolean canScrollVertically() {
                /* Disable scroll, because RecyclerView is wrapped in ScrollView */
                return false;
            }
        });
        drawerBinding.drawerItemsList.setAdapter(wrappedDrawerAdapter);
        drawerBinding.drawerItemsList.setItemAnimator(animator);
        drawerBinding.drawerItemsList.setHasFixedSize(false);

        drawerItemManager.attachRecyclerView(drawerBinding.drawerItemsList);

        drawerBinding.sessionDhtNodesStat.setText(getString(R.string.session_stats_dht_nodes, 0));
        String downloadUploadFmt = getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(activity, 0),
                Formatter.formatFileSize(activity, 0));
        drawerBinding.sessionDownloadStat.setText(downloadUploadFmt);
        drawerBinding.sessionUploadStat.setText(downloadUploadFmt);
        drawerBinding.sessionListenPortStat.setText(getString(R.string.session_stats_listen_port,
                getString(R.string.not_available)));

        drawerBinding.tagsList.setLayoutManager(new LinearLayoutManager(activity));
        tagsAdapter = new TagsAdapter(tagsClickListener);
        drawerBinding.tagsList.setAdapter(tagsAdapter);
        drawerBinding.addTagButton.setOnClickListener((v) -> {
            var action = MainFragmentDirections.actionAddTagDialog();
            NavHostFragment.findNavController(this).navigate(action);
        });

        boolean tagsExpanded = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.drawer_tags_is_expanded), false);
        drawerBinding.tagsGroupHeader.setExpanded(tagsExpanded);
        drawerBinding.tagsExpandable.setExpanded(tagsExpanded);
        drawerBinding.tagsGroupHeader.setOnClickListener((v) -> {
            drawerBinding.tagsExpandable.toggle();
            drawerBinding.tagsGroupHeader.toggleExpand();
            PreferenceManager.getDefaultSharedPreferences(activity)
                    .edit()
                    .putBoolean(
                            getString(R.string.drawer_tags_is_expanded),
                            drawerBinding.tagsExpandable.isExpanded()
                    )
                    .apply();
        });
    }

    private void saveGroupExpandState(int groupPosition, boolean expanded) {
        DrawerGroup group = drawerAdapter.getGroup(groupPosition);
        if (group == null)
            return;

        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_status_id))
            prefKey = getString(R.string.drawer_status_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_sorting_id))
            prefKey = getString(R.string.drawer_sorting_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_date_added_id))
            prefKey = getString(R.string.drawer_time_is_expanded);

        if (prefKey != null)
            PreferenceManager.getDefaultSharedPreferences(activity)
                    .edit()
                    .putBoolean(prefKey, expanded)
                    .apply();
    }

    private void onDrawerGroupsCreated() {
        for (int pos = 0; pos < drawerAdapter.getGroupCount(); pos++) {
            DrawerGroup group = drawerAdapter.getGroup(pos);
            if (group == null)
                return;

            Resources res = getResources();
            if (group.id == res.getInteger(R.integer.drawer_status_id)) {
                viewModel.setStatusFilter(
                        Utils.getDrawerGroupStatusFilter(activity, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
                viewModel.setSort(Utils.getDrawerGroupItemSorting(activity, group.getSelectedItemId()), false);
            } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
                viewModel.setDateAddedFilter(Utils.getDrawerGroupDateAddedFilter(activity, group.getSelectedItemId()), false);
            }

            applyExpandState(group, pos);
        }
    }

    private void applyExpandState(DrawerGroup group, int pos) {
        if (group.getDefaultExpandState()) {
            drawerItemManager.expandGroup(pos);
        } else {
            drawerItemManager.collapseGroup(pos);
        }
    }

    private void onDrawerItemSelected(DrawerGroup group, DrawerGroupItem item) {
        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_status_id)) {
            prefKey = getString(R.string.drawer_status_selected_item);
            viewModel.setStatusFilter(Utils.getDrawerGroupStatusFilter(activity, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
            prefKey = getString(R.string.drawer_sorting_selected_item);
            viewModel.setSort(Utils.getDrawerGroupItemSorting(activity, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
            prefKey = getString(R.string.drawer_time_selected_item);
            viewModel.setDateAddedFilter(Utils.getDrawerGroupDateAddedFilter(activity, item.id), true);
        }

        if (prefKey != null) {
            saveSelectionState(prefKey, item);
        }

        var navBarFragment = activity.getNavBarFragment(this);
        if (navBarFragment != null) {
            navBarFragment.getDrawerLayout().closeDrawer(GravityCompat.START);
        }
    }

    private void saveSelectionState(String prefKey, DrawerGroupItem item) {
        PreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putLong(prefKey, item.id)
                .apply();
    }

    private final TagsAdapter.OnClickListener tagsClickListener = new TagsAdapter.OnClickListener() {
        @Override
        public void onTagSelected(@NonNull AbstractTagItem item) {
            if (item instanceof TagItem) {
                viewModel.setTagFilter(
                        TorrentFilterCollection.tag(((TagItem) item).info),
                        true
                );
            } else if (item instanceof EmptyTagItem) {
                viewModel.setTagFilter(TorrentFilterCollection.all(), true);
            } else if (item instanceof NoTagsItem) {
                viewModel.setTagFilter(TorrentFilterCollection.noTags(), true);
            }

            saveSelectedTag(item);

            var navBarFragment = activity.getNavBarFragment(MainFragment.this);
            if (navBarFragment != null) {
                navBarFragment.getDrawerLayout().closeDrawer(GravityCompat.START);
            }
        }

        @Override
        public void onTagMenuClicked(@NonNull AbstractTagItem abstractItem, int menuId) {
            if (!(abstractItem instanceof TagItem item)) {
                return;
            }
            if (menuId == R.id.edit_tag_menu) {
                var action = MainFragmentDirections
                        .actionEditTagDialog()
                        .setTag(item.info);
                NavHostFragment.findNavController(MainFragment.this).navigate(action);
            } else if (menuId == R.id.delete_tag_menu) {
                disposables.add(viewModel.deleteTag(item.info)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    if (item.isSame(tagsAdapter.getSelectedItem())) {
                                        EmptyTagItem emptyItem = new EmptyTagItem();
                                        saveSelectedTag(emptyItem);
                                        tagsAdapter.setSelectedItem(emptyItem);
                                        viewModel.setTagFilter(TorrentFilterCollection.all(), true);
                                    }
                                },
                                (e) -> {
                                    Log.e(TAG, Log.getStackTraceString(e));
                                    Snackbar.make(
                                            binding.mainCoordinatorLayout,
                                            R.string.tag_deleting_failed,
                                            Snackbar.LENGTH_LONG
                                    ).show();
                                }
                        )
                );
            }
        }
    };

    private void saveSelectedTag(@NonNull AbstractTagItem item) {
        String tagId = null;
        if (item instanceof TagItem) {
            tagId = Long.toString(((TagItem) item).info.id);
        } else if (item instanceof EmptyTagItem) {
            tagId = getString(R.string.tag_empty_item);
        } else if (item instanceof NoTagsItem) {
            tagId = getString(R.string.tag_no_tags_item);
        }

        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(
                        getString(R.string.drawer_tags_selected_item),
                        tagId
                )
                .apply();
    }

    private void initSearch() {
        searchAdapter = new TorrentListAdapter(this);
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
        binding.searchTorrentList.setLayoutManager(new LinearLayoutManager(activity));
        binding.searchTorrentList.setItemAnimator(animator);
        binding.searchTorrentList.setEmptyView(binding.emptyViewSearchTorrentList);
        binding.searchTorrentList.addItemDecoration(buildListDivider());
        binding.searchTorrentList.setAdapter(searchAdapter);

        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        viewModel.resetSearch();
    }

    SearchView.TransitionListener searchViewListener = (searchView, previousState, newState) -> {
        if (previousState == SearchView.TransitionState.HIDDEN && newState == SearchView.TransitionState.SHOWN
                || newState == SearchView.TransitionState.SHOWING) {
            subscribeSearchList();
        } else if (newState == SearchView.TransitionState.HIDDEN) {
            unsubscribeSearchList();
        }
    };

    private void showAddTorrentMenu() {
        /* Show add torrent menu after window is displayed */
        View v = activity.getWindow().findViewById(android.R.id.content);
        if (v == null)
            return;
        v.post(() -> {
            registerForContextMenu(v);
            activity.openContextMenu(v);
            unregisterForContextMenu(v);
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
        subscribeForceSortAndFilter();
        subscribeTorrentsDeleted();
        subscribeMsgViewModel();
        subscribeSessionStats();
        subscribeNeedStartEngine();
        subscribeTags();

        binding.searchView.addTransitionListener(searchViewListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        binding.searchView.removeTransitionListener(searchViewListener);
        disposables.clear();
        unsubscribeSearchList();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (torrentListState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(torrentListState);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            torrentListState = savedInstanceState.getParcelable(TAG_TORRENT_LIST_STATE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            torrentListState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_TORRENT_LIST_STATE, torrentListState);
        }
        if (selectionTracker != null) {
            selectionTracker.onSaveInstanceState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    private void subscribeAdapter() {
        disposables.add(observeTorrents());
    }

    private Disposable observeTorrents() {
        return viewModel.observeAllTorrentsInfo()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoList) ->
                        Flowable.fromIterable(infoList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private void subscribeSearchList() {
        searchDisposables.clear();
        searchDisposables.add(observeSearchListTorrents());
        searchDisposables.add(observeForceSearch());
    }

    private void unsubscribeSearchList() {
        searchDisposables.clear();
        viewModel.resetSearch();
    }

    private Disposable observeSearchListTorrents() {
        return viewModel.observeAllTorrentsInfo()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoList) ->
                        Flowable.fromIterable(infoList)
                                .filter(viewModel.getSearchFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(searchAdapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private Disposable observeForceSearch() {
        return viewModel.observeForceSearch()
                .filter((force) -> force)
                .observeOn(Schedulers.io())
                .subscribe((force) -> searchDisposables.add(getSearchTorrentsSingle()));
    }

    private Disposable getSearchTorrentsSingle() {
        return viewModel.getAllTorrentsInfoSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((infoList) ->
                        Observable.fromIterable(infoList)
                                .filter(viewModel.getSearchFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(searchAdapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private void subscribeForceSortAndFilter() {
        disposables.add(viewModel.observeForceSortAndFilter()
                .filter((force) -> force)
                .observeOn(Schedulers.io())
                .subscribe((force) -> disposables.add(getAllTorrentsSingle())));
    }

    private Disposable getAllTorrentsSingle() {
        return viewModel.getAllTorrentsInfoSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((infoList) ->
                        Observable.fromIterable(infoList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private void subscribeTorrentsDeleted() {
        disposables.add(viewModel.observeTorrentsDeleted()
                .subscribeOn(Schedulers.io())
                .filter((id) -> {
                    TorrentListItem item = adapter.getOpenedItem();
                    if (item == null)
                        return false;

                    return id.equals(item.torrentId);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));
    }

    private void subscribeMsgViewModel() {
        disposables.add(msgViewModel.observeTorrentDetailsClosed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));
    }

    private void subscribeSessionStats() {
        disposables.add(infoProvider.observeSessionStats()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateSessionStats));
    }

    private void subscribeNeedStartEngine() {
        disposables.add(viewModel.observeNeedStartEngine()
                .subscribeOn(Schedulers.io())
                .filter((needStart) -> needStart)
                .subscribe((needStart) -> viewModel.startEngine()));
    }

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((list) ->
                        Flowable.concat(
                                Flowable.just(new EmptyTagItem()),
                                Flowable.just(new NoTagsItem()),
                                Flowable.fromIterable(list).map(TagItem::new)
                        ).toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((items) -> {
                    if (tagsAdapter.getItemCount() == 0) {
                        setInitSelection(items);
                    }
                    tagsAdapter.submitList(items);
                })
        );
    }

    private void setInitSelection(List<AbstractTagItem> items) {
        String selectedTagIdStr = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.drawer_tags_selected_item),
                        getString(R.string.tag_empty_item)
                );
        if (selectedTagIdStr.equals(getString(R.string.tag_empty_item))) {
            tagsAdapter.setSelectedItem(new EmptyTagItem());
            viewModel.setTagFilter(TorrentFilterCollection.all(), true);
        } else if (selectedTagIdStr.equals(getString(R.string.tag_no_tags_item))) {
            tagsAdapter.setSelectedItem(new NoTagsItem());
            viewModel.setTagFilter(TorrentFilterCollection.noTags(), true);
        } else {
            long selectedTagId;
            try {
                selectedTagId = Long.parseLong(selectedTagIdStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Unable to parse tag id: " + Log.getStackTraceString(e));
                tagsAdapter.setSelectedItem(new EmptyTagItem());
                viewModel.setTagFilter(TorrentFilterCollection.all(), true);
                return;
            }
            disposables.add(Observable.fromIterable(items)
                    .subscribeOn(Schedulers.computation())
                    .filter((item) -> item instanceof TagItem &&
                            ((TagItem) item).info.id == selectedTagId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((item) -> {
                        tagsAdapter.setSelectedItem(item);
                        viewModel.setTagFilter(
                                TorrentFilterCollection.tag(((TagItem) item).info),
                                true
                        );
                    })
            );
        }
    }

    private void updateSessionStats(SessionStats stats) {
        long dhtNodes = 0;
        long totalDownload = 0;
        long totalUpload = 0;
        long downloadSpeed = 0;
        long uploadSpeed = 0;
        int listenPort = -1;

        if (stats != null) {
            dhtNodes = stats.dhtNodes;
            totalDownload = stats.totalDownload;
            totalUpload = stats.totalUpload;
            downloadSpeed = stats.downloadSpeed;
            uploadSpeed = stats.uploadSpeed;
            listenPort = stats.listenPort;
        }

        drawerBinding.sessionDhtNodesStat.setText(getString(R.string.session_stats_dht_nodes, dhtNodes));
        drawerBinding.sessionDownloadStat.setText(getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(activity, totalDownload),
                Formatter.formatFileSize(activity, downloadSpeed)));
        drawerBinding.sessionUploadStat.setText(getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(activity, totalUpload),
                Formatter.formatFileSize(activity, uploadSpeed)));
        drawerBinding.sessionListenPortStat.setText(getString(R.string.session_stats_listen_port,
                listenPort <= 0 ?
                        getString(R.string.not_available) :
                        Integer.toString(listenPort)));
    }


    @Override
    public void onItemClicked(@NonNull TorrentListItem item) {

        if (Utils.isTwoPane(activity)) {
            adapter.markAsOpen(item);
        }
        // TODO: handle open details
        getDetailPaneNavHostFragment().getNavController().navigate(R.id.blank_fragment);
        getSlidingPaneLayout().open();

        msgViewModel.torrentDetailsOpened(item.torrentId);
        binding.searchView.handleBackInvoked();
    }

    @Override
    public void onItemPauseClicked(@NonNull TorrentListItem item) {
        viewModel.pauseResumeTorrent(item.torrentId);
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        activity.getMenuInflater().inflate(R.menu.main_context, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.add_link_menu) {
            addLinkDialog();
        } else if (itemId == R.id.open_file_menu) {
            openTorrentFileDialog();
        }

        return true;
    }


    @SuppressLint("RestrictedApi")
    private void showFabMenu(View v) {
        getAddTorrentFab().setImageResource(R.drawable.add_to_close_anim);
        startFabIconAnim();

        var popupWrapper = new ContextThemeWrapper(activity, R.style.App_Components_FloatingActionButton_Menu);
        var popup = new PopupMenu(popupWrapper, v, Gravity.TOP);
        popup.getMenuInflater().inflate(R.menu.main_fab, popup.getMenu());

        if (popup.getMenu() instanceof MenuBuilder menuBuilder) {
            menuBuilder.setOptionalIconsVisible(true);
            var items = menuBuilder.getVisibleItems();
            for (int i = 0; i < items.size(); i++) {
                int iconMarginPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        12.0f,
                        getResources().getDisplayMetrics()
                );
                var item = items.get(i);
                if (item.getIcon() != null) {
                    item.setIcon(new InsetDrawable(item.getIcon(), iconMarginPx, 0, iconMarginPx, 0) {
                        @Override
                        public int getIntrinsicWidth() {
                            return getIntrinsicHeight() + iconMarginPx + iconMarginPx;
                        }
                    });
                }
            }
        }

        popup.setOnDismissListener((menu) -> {
            getAddTorrentFab().setImageResource(R.drawable.close_to_add_anim);
            startFabIconAnim();
        });

        popup.setOnMenuItemClickListener((item) -> {
            int itemId = item.getItemId();
            if (itemId == R.id.create_torrent) {
                createTorrentDialog();
            } else if (itemId == R.id.add_link) {
                addLinkDialog();
            } else if (itemId == R.id.open_file) {
                openTorrentFileDialog();
            }

            return true;
        });

        popup.show();
    }

    private void startFabIconAnim() {
        ((AnimatedVectorDrawable) getAddTorrentFab().getDrawable()).start();
    }

    private void setContextualAppBarTitle(int itemCount) {
        binding.contextualAppBar.setTitle(String.valueOf(itemCount));
    }

    private void startContextualMode() {
        binding.searchBar.expand(binding.contextualAppBarContainer, binding.appBarLayout);
    }

    private void finishContextualMode() {
        binding.searchBar.collapse(binding.contextualAppBarContainer, binding.appBarLayout);
        selectionTracker.clearSelection();
    }

    boolean onContextualMenuItemClickListener(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.delete_torrent_menu) {
            deleteTorrentsDialog();
        } else if (itemId == R.id.select_all_torrent_menu) {
            selectAllTorrents();
        } else if (itemId == R.id.force_recheck_torrent_menu) {
            forceRecheckTorrents();
            finishContextualMode();
        } else if (itemId == R.id.force_announce_torrent_menu) {
            forceAnnounceTorrents();
            finishContextualMode();
        }

        return true;
    }

    private void deleteTorrentsDialog() {
        if (!isAdded()) {
            return;
        }

        var action = MainFragmentDirections.actionDeleteTorrentDialog(
                selectionTracker.getSelection().size()
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void addLinkDialog() {
        NavHostFragment.findNavController(this)
                .navigate(MainFragmentDirections.actionAddLinkDialog());
    }

    private void openTorrentFileDialog() {
        var config = new FileManagerConfig(null,
                getString(R.string.torrent_file_chooser_title),
                FileManagerConfig.Mode.FILE_CHOOSER);
        config.highlightFileTypes = Collections.singletonList("torrent");

        var action = NavBarFragmentDirections.actionOpenTorrentFile(config);
        activity.getRootNavController().navigate(action);
    }

    private void createTorrentDialog() {
        var action = NavBarFragmentDirections.actionCreateTorrent();
        activity.getRootNavController().navigate(action);
    }

    private void deleteTorrents(boolean withFiles) {
        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.deleteTorrents(ids, withFiles)));

        finishContextualMode();
    }

    @SuppressLint("RestrictedApi")
    private void selectAllTorrents() {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }

    private void forceRecheckTorrents() {
        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.forceRecheckTorrents(ids)));
    }

    private void forceAnnounceTorrents() {
        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.forceAnnounceTorrents(ids)));
    }

    private RecyclerView.ItemDecoration buildListDivider() {
        var divider = new MaterialDividerItemDecoration(activity, LinearLayoutManager.VERTICAL);
        divider.setDividerInsetEnd(32);
        divider.setDividerInsetStart(32);
        divider.setLastItemDecorated(false);

        return divider;
    }
}
