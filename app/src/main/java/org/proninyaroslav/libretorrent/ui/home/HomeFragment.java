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

package org.proninyaroslav.libretorrent.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
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
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.search.SearchView;
import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilterCollection;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentListState;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.sorting.BaseSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentHomeBinding;
import org.proninyaroslav.libretorrent.databinding.HomeDrawerContentBinding;
import org.proninyaroslav.libretorrent.databinding.MainNavRailHeaderBinding;
import org.proninyaroslav.libretorrent.ui.NavBarFragment;
import org.proninyaroslav.libretorrent.ui.NavBarFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.BlankFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsFragmentArgs;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerDateAddedFilter;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerSort;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerSortDirection;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerStatusFilter;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerTagFilter;
import org.proninyaroslav.libretorrent.ui.home.model.TorrentListItemState;
import org.proninyaroslav.libretorrent.ui.tag.TorrentTagChip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The list of torrents.
 */

public class HomeFragment extends AbstractListDetailFragment {
    private static final String TAG = HomeFragment.class.getSimpleName();

    private static final String TAG_TORRENT_LIST_STATE = "torrent_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String KEY_FILE_MANAGER_DIALOG_REQUEST = TAG + "_file_manager_dialog";
    private static final String KEY_DELETE_TORRENT_DIALOG_REQUEST = TAG + "_delete_torrent_dialog";

    private MainActivity activity;
    private TorrentListAdapter adapter;
    private TorrentListAdapter searchAdapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable torrentListState;
    private SelectionTracker<TorrentListItem> selectionTracker;
    private FragmentHomeBinding binding;
    private HomeDrawerContentBinding drawerBinding;
    private MainNavRailHeaderBinding navRailHeaderBinding = null;

    private TorrentInfoProvider infoProvider;
    private HomeViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final CompositeDisposable searchDisposables = new CompositeDisposable();

    @NonNull
    @Override
    public View onCreateListPaneView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
        binding = FragmentHomeBinding.inflate(layoutInflater, viewGroup, false);
        drawerBinding = HomeDrawerContentBinding.inflate(layoutInflater);
        var navBarFragment = activity.findNavBarFragment(this);

        if (Utils.isLargeScreenDevice(activity)) {
            navRailHeaderBinding = MainNavRailHeaderBinding.inflate(getLayoutInflater());
            if (navBarFragment != null) {
                navBarFragment.setNavRailHeaderView(navRailHeaderBinding.getRoot());
            }
            binding.addTorrentFab.hide();
            binding.searchBar.setNavigationIcon(R.drawable.ic_search_24px);
            Utils.applyWindowInsets(binding.torrentList, WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.torrentList, WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }
        if (navBarFragment != null) {
            Utils.applyWindowInsets(navBarFragment.getNavigationView(), drawerBinding.getRoot());
        }

        if (navBarFragment != null) {
            setOpenTorrentFileDialogListener(navBarFragment);
        }
        setDeleteDialogListener();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        var navBarFragment = activity.findNavBarFragment(this);
        if (navBarFragment != null) {
            navBarFragment.removeDrawerNavigationView();
            navBarFragment.removeNavRailHeaderView();
        }
    }

    private void setOpenTorrentFileDialogListener(@NonNull NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                KEY_FILE_MANAGER_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.FILE_CHOOSER) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        Snackbar.make(
                                activity,
                                binding.coordinatorLayout,
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
                KEY_DELETE_TORRENT_DIALOG_REQUEST,
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

        getSlidingPaneLayout().setLockMode(SlidingPaneLayout.LOCK_MODE_LOCKED);

        infoProvider = TorrentInfoProvider.getInstance(activity.getApplicationContext());
        var provider = new ViewModelProvider(activity);
        viewModel = provider.get(HomeViewModel.class);

        adapter = new TorrentListAdapter(torrentClickListener);
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
        binding.torrentList.setLoadingView(binding.loadingViewTorrentList);
        binding.torrentList.addItemDecoration(Utils.buildListDivider(activity));
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

        var i = activity.getIntent();
        if (i != null) {
            if (MainActivity.ACTION_ADD_TORRENT_SHORTCUT.equals(i.getAction())) {
                // Prevents re-reading action after device configuration changes
                i.setAction(null);
                showAddTorrentMenu();
            } else if (MainActivity.ACTION_OPEN_TORRENT_DETAILS.equals(i.getAction())) {
                // Prevents re-reading action after device configuration changes
                i.setAction(null);
                var torrentId = i.getStringExtra(MainActivity.KEY_TORRENT_ID);
                if (torrentId != null) {
                    var lifecycle = getDetailPaneNavHostFragment().getLifecycle();
                    lifecycle.addObserver(new DefaultLifecycleObserver() {
                        @Override
                        public void onStart(@NonNull LifecycleOwner owner) {
                            lifecycle.removeObserver(this);
                            openTorrentDetails(torrentId);
                        }
                    });
                }
            }
        }

        initDrawer();
        initSearch();

        setNavigationIconListener((v) -> {
            var navBarFragment = activity.findNavBarFragment(this);
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
        return NavHostFragment.create(R.navigation.home_two_pane_graph);
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
            var action = HomeFragmentDirections.actionOpenLog();
            NavHostFragment.findNavController(this).navigate(action);
        }

        return true;
    }

    private void showAboutDialog() {
        var action = HomeFragmentDirections.actionAboutDialog();
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void initSearch() {
        searchAdapter = new TorrentListAdapter(torrentClickListener);
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
        binding.searchTorrentList.setLoadingView(binding.loadingViewSearchTorrentList);
        binding.searchTorrentList.addItemDecoration(Utils.buildListDivider(activity));
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
        binding.addTorrentFab.post(() -> showFabMenu(binding.addTorrentFab));
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
        subscribeForceSortAndFilter();
        subscribeTorrentsDeleted();
        subscribeSessionStats();
        subscribeNeedStartEngine();
        subscribeTags();

        binding.searchView.addTransitionListener(searchViewListener);

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

    private final NavController.OnDestinationChangedListener destinationListener =
            (navController, navDestination, arguments) -> {
                if (!Utils.isTwoPane(activity)) {
                    return;
                }
                try {
                    Bundle rawArgs;
                    if (navDestination.getId() == R.id.torrentDetailsFragment) {
                        rawArgs = arguments;
                    } else {
                        var entry = navController.getBackStackEntry(R.id.torrentDetailsFragment);
                        rawArgs = entry.getArguments();
                    }
                    if (rawArgs == null) {
                        return;
                    }
                    var args = TorrentDetailsFragmentArgs.fromBundle(rawArgs);
                    adapter.markAsOpen(args.getTorrentId());
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            };

    private void setDetailsNavAnimation(NavOptions.Builder options) {
        var slidingPaneLayout = getSlidingPaneLayout();
        if (slidingPaneLayout.isOpen()) {
            options.setEnterAnim(R.anim.nav_slide_enter_anim)
                    .setExitAnim(R.anim.nav_fade_exit_anim);
        }
    }

    private Disposable observeTorrents() {
        return viewModel.observeAllTorrentsInfo()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((state) -> {
                    if (state instanceof TorrentListState.Initial) {
                        return Single.just((TorrentListItemState) new TorrentListItemState.Initial());
                    } else if (state instanceof TorrentListState.Loaded loaded) {
                        return Flowable.fromIterable(loaded.list())
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                                .map(TorrentListItemState.Loaded::new);
                    }
                    throw new IllegalStateException("Unknown state: " + state);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((state) -> {
                            if (state instanceof TorrentListItemState.Initial) {
                                binding.torrentList.setLoading(true);
                            } else if (state instanceof TorrentListItemState.Loaded loaded) {
                                binding.torrentList.setLoading(false);
                                adapter.submitList(loaded.list());
                            }
                        },
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
                .flatMapSingle((state) -> {
                    if (state instanceof TorrentListState.Initial) {
                        return Single.just((TorrentListItemState) new TorrentListItemState.Initial());
                    } else if (state instanceof TorrentListState.Loaded loaded) {
                        return Flowable.fromIterable(loaded.list())
                                .filter(viewModel.getSearchFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                                .map(TorrentListItemState.Loaded::new);
                    }
                    throw new IllegalStateException("Unknown state: " + state);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((state) -> {
                            if (state instanceof TorrentListItemState.Initial) {
                                binding.searchTorrentList.setLoading(true);
                            } else if (state instanceof TorrentListItemState.Loaded loaded) {
                                binding.searchTorrentList.setLoading(false);
                                searchAdapter.submitList(loaded.list());
                            }
                        },
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
                    if (!isAdded()) {
                        return false;
                    }
                    try {
                        var navController = getDetailPaneNavHostFragment().getNavController();
                        var entry = navController.getBackStackEntry(R.id.torrentDetailsFragment);
                        var args = TorrentDetailsFragmentArgs.fromBundle(entry.getArguments());
                        return args.getTorrentId().equals(id);
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
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::initTagChips)
        );
    }

    private void initDrawer() {
        var navBarFragment = activity.findNavBarFragment(this);
        if (navBarFragment != null) {
            navBarFragment.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            navBarFragment.getNavigationView().addView(drawerBinding.getRoot());
        }

        drawerBinding.statusClearButton.setOnClickListener((v) -> {
            drawerBinding.drawerStatusChipGroup.clearCheck();
            viewModel.setStatusFilter(List.of(TorrentFilterCollection.all()), true);
        });
        drawerBinding.dateAddedClearButton.setOnClickListener((v) -> {
            drawerBinding.drawerDateAddedChipGroup.clearCheck();
            viewModel.setDateAddedFilter(List.of(TorrentFilterCollection.all()), true);
        });
        drawerBinding.tagsClearButton.setOnClickListener((v) -> {
            drawerBinding.drawerTagsChipGroup.clearCheck();
            viewModel.setTagFilter(TorrentFilterCollection.all(), true);
        });
        drawerBinding.addTagButton.setOnClickListener((v) -> {
            var action = HomeFragmentDirections.actionAddTagDialog();
            NavHostFragment.findNavController(this).navigate(action);
        });

        var status = getSavedStatusFilters();
        initStatusFilter(status);
        var sort = getSavedSorting();
        var direction = getSavedSortingDirection();
        initSorting(sort, direction);
        var dateAdded = getSavedDateAddedFilters();
        initDateAddedFilter(dateAdded);
        onAfterFiltersInit(status, sort, direction, dateAdded);

        drawerBinding.sessionDhtNodesStat.setText(getString(R.string.session_stats_dht_nodes, 0));
        String downloadUploadFmt = getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(activity, 0),
                Formatter.formatFileSize(activity, 0));
        drawerBinding.sessionDownloadStat.setText(downloadUploadFmt);
        drawerBinding.sessionUploadStat.setText(downloadUploadFmt);
        drawerBinding.sessionListenPortStat.setText(getString(R.string.session_stats_listen_port,
                getString(R.string.not_available)));
    }

    private void onAfterFiltersInit(
            Set<DrawerStatusFilter> status,
            DrawerSort sort,
            DrawerSortDirection direction,
            Set<DrawerDateAddedFilter> dateAdded
    ) {
        viewModel.setStatusFilter(status.stream()
                        .map(Utils::getStatusFilterById)
                        .collect(Collectors.toList()),
                false);
        var d = Utils.getSortingDirection(direction);
        viewModel.setSort(Utils.getSortingById(sort, d), false);
        viewModel.setDateAddedFilter(dateAdded.stream()
                        .map(Utils::getDateAddedFilterById)
                        .collect(Collectors.toList()),
                true);
    }

    private void initStatusFilter(Set<DrawerStatusFilter> status) {
        if (status.contains(DrawerStatusFilter.Downloaded)) {
            drawerBinding.drawerStatusDownloaded.setChecked(true);
        }
        if (status.contains(DrawerStatusFilter.Downloading)) {
            drawerBinding.drawerStatusDownloading.setChecked(true);
        }
        if (status.contains(DrawerStatusFilter.DownloadingMetadata)) {
            drawerBinding.drawerStatusDownloadingMetadata.setChecked(true);
        }
        if (status.contains(DrawerStatusFilter.Error)) {
            drawerBinding.drawerStatusError.setChecked(true);
        }

        drawerBinding.drawerStatusChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            viewModel.setStatusFilter(checkedIds.stream()
                            .map((id) -> {
                                var s = Utils.getDrawerStatusFilterByChip(id);
                                return s == null ? null : Utils.getStatusFilterById(s);
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()),
                    true);
            saveStatusFilters(checkedIds);
        });
    }

    private void initSorting(DrawerSort sort, DrawerSortDirection direction) {
        switch (sort) {
            case None -> {
            }
            case DateAdded -> drawerBinding.drawerSortingDateAdded.setChecked(true);
            case Size -> drawerBinding.drawerSortingSize.setChecked(true);
            case Name -> drawerBinding.drawerSortingName.setChecked(true);
            case Progress -> drawerBinding.drawerSortingProgress.setChecked(true);
            case Eta -> drawerBinding.drawerSortingETA.setChecked(true);
            case Peers -> drawerBinding.drawerSortingPeers.setChecked(true);
        }

        switch (direction) {
            case Ascending -> drawerBinding.sortDirectionToggleButton.check(R.id.sort_asc_button);
            case Descending -> drawerBinding.sortDirectionToggleButton.check(R.id.sort_desc_button);
        }

        drawerBinding.sortAscButton.setOnClickListener((v) -> {
            setSortDirection(BaseSorting.Direction.ASC);
            saveSortingDirection(DrawerSortDirection.Ascending);
        });
        drawerBinding.sortDescButton.setOnClickListener((v) -> {
            setSortDirection(BaseSorting.Direction.DESC);
            saveSortingDirection(DrawerSortDirection.Descending);
        });

        drawerBinding.drawerSortingChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            var checkedId = checkedIds.isEmpty() ? View.NO_ID : checkedIds.get(0);
            var d = getSavedSortingDirection();
            var sortComparator = Utils.getSortingById(
                    Utils.getDrawerSortingByChip(checkedId),
                    Utils.getSortingDirection(d)
            );
            viewModel.setSort(sortComparator, true);
            saveSorting(checkedId);
        });
    }

    private void initDateAddedFilter(Set<DrawerDateAddedFilter> dateAdded) {
        if (dateAdded.contains(DrawerDateAddedFilter.Today)) {
            drawerBinding.drawerDateAddedToday.setChecked(true);
        }
        if (dateAdded.contains(DrawerDateAddedFilter.Yesterday)) {
            drawerBinding.drawerDateAddedYesterday.setChecked(true);
        }
        if (dateAdded.contains(DrawerDateAddedFilter.Week)) {
            drawerBinding.drawerDateAddedWeek.setChecked(true);
        }
        if (dateAdded.contains(DrawerDateAddedFilter.Month)) {
            drawerBinding.drawerDateAddedMonth.setChecked(true);
        }
        if (dateAdded.contains(DrawerDateAddedFilter.Year)) {
            drawerBinding.drawerDateAddedYear.setChecked(true);
        }

        drawerBinding.drawerDateAddedChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            viewModel.setDateAddedFilter(checkedIds.stream()
                            .map((id) -> {
                                var d = Utils.getDrawerDateAddedFilterByChip(id);
                                return d == null ? null : Utils.getDateAddedFilterById(d);
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()),
                    true);
            saveDateAddedFilters(checkedIds);
        });
    }

    private void initTagChips(List<TagInfo> tags) {
        var savedTags = getSavedTags();
        drawerBinding.drawerTagsChipGroup.removeAllViews();

        var noTagsChip = buildNoTagsChip(savedTags);
        drawerBinding.drawerTagsChipGroup.addView(noTagsChip);

        for (var tag : tags) {
            var chip = new TorrentTagChip(activity, tag);
            chip.setClickable(true);
            if (savedTags.contains(new DrawerTagFilter.Item(tag.id))) {
                chip.setChecked(true);
            }
            chip.setCloseIcon(ResourcesCompat.getDrawable(
                    getResources(), R.drawable.ic_more_vert_24px, null));
            chip.setOnCloseIconClickListener((v) -> {
                var popup = new PopupMenu(v.getContext(), v);
                popup.inflate(R.menu.tag_item_popup);
                popup.setOnMenuItemClickListener((menuItem) -> {
                    var menuId = menuItem.getItemId();
                    if (menuId == R.id.edit_tag_menu) {
                        var action = HomeFragmentDirections
                                .actionEditTagDialog()
                                .setTag(tag);
                        NavHostFragment.findNavController(this).navigate(action);
                    } else if (menuId == R.id.delete_tag_menu) {
                        disposables.add(viewModel.deleteTag(tag)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> {
                                            drawerBinding.drawerTagsChipGroup.removeView(chip);
                                            updateTagsGroup(noTagsChip);
                                        },
                                        (e) -> {
                                            Log.e(TAG, Log.getStackTraceString(e));
                                            Snackbar.make(
                                                    binding.coordinatorLayout,
                                                    R.string.tag_deleting_failed,
                                                    Snackbar.LENGTH_LONG
                                            ).show();
                                        }));
                    }
                    return true;
                });
                popup.show();
            });
            chip.setOnClickListener((v) -> updateTagsGroup(noTagsChip));
            drawerBinding.drawerTagsChipGroup.addView(chip);
        }

        drawerBinding.drawerTagsChipGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> saveTags(checkedIds)
        );

        applyTagFilter();
    }

    @NonNull
    private TorrentTagChip buildNoTagsChip(Set<DrawerTagFilter> savedTags) {
        var noTagsChip = new TorrentTagChip(
                activity,
                R.drawable.ic_label_off_24px,
                R.string.without_tags
        );
        noTagsChip.setId(R.id.tag_no_tags_item);
        noTagsChip.setClickable(true);
        if (savedTags.contains(new DrawerTagFilter.NoTags())) {
            noTagsChip.setChecked(true);
        }
        noTagsChip.setCloseIconVisible(false);
        noTagsChip.setOnClickListener((v) -> {
            drawerBinding.drawerTagsChipGroup.clearCheck();
            noTagsChip.setChecked(true);
            viewModel.setTagFilter(TorrentFilterCollection.noTags(), true);
        });
        return noTagsChip;
    }

    private List<TagInfo> getTagsByChipId(List<Integer> ids) {
        return ids
                .stream()
                .map((id) -> {
                    TorrentTagChip chip = drawerBinding.drawerTagsChipGroup.findViewById(id);
                    return chip.getTag();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void updateTagsGroup(TorrentTagChip noTagsChip) {
        applyTagFilter();
        noTagsChip.setChecked(false);
    }

    private void applyTagFilter() {
        var checkedIds = drawerBinding.drawerTagsChipGroup.getCheckedChipIds();
        var checkedTags = getTagsByChipId(checkedIds);
        if (checkedTags.isEmpty()) {
            viewModel.setTagFilter(TorrentFilterCollection.all(), true);
        } else {
            viewModel.setTagFilter(TorrentFilterCollection.tags(checkedTags), true);
        }
    }

    private Set<DrawerStatusFilter> getSavedStatusFilters() {
        var json = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.pref_key_drawer_status_selected_items),
                        null
                );
        return viewModel.decodeDrawerStatusFilter(json);
    }

    private void saveStatusFilters(@NonNull List<Integer> chipIds) {
        var list = chipIds.stream()
                .map(Utils::getDrawerStatusFilterByChip)
                .collect(Collectors.toList());
        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(
                        getString(R.string.pref_key_drawer_status_selected_items),
                        viewModel.encodeDrawerStatusFilter(list)
                )
                .apply();
    }

    private DrawerSort getSavedSorting() {
        var json = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.pref_key_drawer_sorting_selected_item),
                        null
                );
        return viewModel.decodeDrawerSorting(json);
    }

    private void saveSorting(int chipId) {
        var json = viewModel.encodeDrawerSorting(Utils.getDrawerSortingByChip(chipId));
        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(getString(R.string.pref_key_drawer_sorting_selected_item), json)
                .apply();
    }

    private DrawerSortDirection getSavedSortingDirection() {
        var json = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.pref_key_drawer_sorting_direction),
                        null
                );

        return viewModel.decodeDrawerSortingDirection(json);
    }

    private void saveSortingDirection(DrawerSortDirection direction) {
        var json = viewModel.encodeDrawerSortingDirection(direction);
        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(getString(R.string.pref_key_drawer_sorting_direction), json)
                .apply();
    }

    private void setSortDirection(BaseSorting.Direction direction) {
        var comparator = viewModel.getSorting();
        var sort = comparator.sorting();
        viewModel.setSort(new TorrentSortingComparator(new TorrentSorting(
                        TorrentSorting.SortingColumns.fromValue(sort.getColumnName()), direction
                )),
                true
        );
    }

    private Set<DrawerTagFilter> getSavedTags() {
        var json = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.pref_key_drawer_tags_selected_items),
                        null
                );

        return viewModel.decodeDrawerTagFilter(json);
    }

    private void saveTags(List<Integer> chipIds) {
        List<DrawerTagFilter> tags = chipIds
                .stream()
                .map((id) -> {
                    TorrentTagChip chip = drawerBinding.drawerTagsChipGroup.findViewById(id);
                    if (chip.getId() == R.id.tag_no_tags_item) {
                        return new DrawerTagFilter.NoTags();
                    } else {
                        var tag = chip.getTag();
                        return tag == null ? null : new DrawerTagFilter.Item(tag.id);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        var json = viewModel.encodeDrawerTagFilter(tags);
        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(getString(R.string.pref_key_drawer_tags_selected_items), json)
                .apply();
    }

    private Set<DrawerDateAddedFilter> getSavedDateAddedFilters() {
        var json = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(
                        getString(R.string.pref_key_drawer_date_added_selected_items),
                        null
                );
        return viewModel.decodeDrawerDateAddedFilter(json);
    }

    private void saveDateAddedFilters(@NonNull List<Integer> chipIds) {
        var list = chipIds.stream()
                .map(Utils::getDrawerDateAddedFilterByChip)
                .collect(Collectors.toList());
        var json = viewModel.encodeDrawerDateAddedFilter(list);
        PreferenceManager
                .getDefaultSharedPreferences(activity)
                .edit()
                .putString(getString(R.string.pref_key_drawer_date_added_selected_items), json)
                .apply();
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

    private final TorrentListAdapter.ClickListener torrentClickListener = new TorrentListAdapter.ClickListener() {
        @Override
        public void onItemClicked(@NonNull TorrentListItem item) {
            openTorrentDetails(item.torrentId);
        }

        @Override
        public void onItemPauseClicked(@NonNull TorrentListItem item) {
            viewModel.pauseResumeTorrent(item.torrentId);
        }
    };

    private void openTorrentDetails(String torrentId) {
        var navController = getDetailPaneNavHostFragment().getNavController();
        var slidingPaneLayout = getSlidingPaneLayout();

        // Clear back stack
        if (getDetailPaneNavHostFragment().getChildFragmentManager().getBackStackEntryCount() > 0) {
            navController.popBackStack();
        }

        var action = TorrentDetailsFragmentDirections.actionTorrentDetails(torrentId);
        var options = new NavOptions.Builder();
        setDetailsNavAnimation(options);
        navController.navigate(action, options.build());
        slidingPaneLayout.open();

        binding.searchView.handleBackInvoked();
    }

    @SuppressLint("RestrictedApi")
    private void showFabMenu(View v) {
        getAddTorrentFab().setImageResource(R.drawable.add_to_close_anim);
        startFabIconAnim();

        var popupWrapper = new ContextThemeWrapper(activity, R.style.App_Components_FloatingActionButton_Menu);
        var popup = new PopupMenu(popupWrapper, v, Gravity.TOP);
        popup.getMenuInflater().inflate(R.menu.home_fab, popup.getMenu());

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

        var action = HomeFragmentDirections.actionDeleteTorrentDialog(
                selectionTracker.getSelection().size(),
                KEY_DELETE_TORRENT_DIALOG_REQUEST
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void addLinkDialog() {
        NavHostFragment.findNavController(this)
                .navigate(HomeFragmentDirections.actionAddLinkDialog());
    }

    private void openTorrentFileDialog() {
        var config = new FileManagerConfig(null,
                getString(R.string.torrent_file_chooser_title),
                FileManagerConfig.Mode.FILE_CHOOSER);
        config.highlightFileTypes = Collections.singletonList("torrent");

        var action = NavBarFragmentDirections
                .actionOpenFileDialog(config, KEY_FILE_MANAGER_DIALOG_REQUEST);
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
}
