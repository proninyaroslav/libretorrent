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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.trackers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsTrackerListBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsViewModel;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying bittorrent trackers list. Part of DetailTorrentFragment.
 */

public class TorrentDetailsTrackersFragment extends Fragment {
    private static final String TAG = TorrentDetailsTrackersFragment.class.getSimpleName();

    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_LIST_TRACKER_STATE = "list_tracker_state";
    private static final String KEY_DELETE_TRACKERS_DIALOG_REQUEST = TAG + "_delete_trackers_dialog";

    private AppCompatActivity activity;
    private FragmentTorrentDetailsTrackerListBinding binding;
    private TorrentDetailsViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private SelectionTracker<TrackerItem> selectionTracker;
    private ActionMode actionMode;
    private TrackerListAdapter adapter;
    /* Save state scrolling */
    private Parcelable listTrackerState;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static TorrentDetailsTrackersFragment newInstance() {
        var fragment = new TorrentDetailsTrackersFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDeleteTrackersDialogListener();
    }

    private void setDeleteTrackersDialogListener() {
        requireParentFragment().getParentFragmentManager().setFragmentResultListener(
                KEY_DELETE_TRACKERS_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var resultValue = (DeleteTrackersDialog.Result) result
                            .getSerializable(DeleteTrackersDialog.KEY_RESULT_VALUE);
                    if (resultValue == null) {
                        return;
                    }
                    switch (resultValue) {
                        case DELETE -> deleteTrackers();
                        case CANCEL -> {
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTorrentDetailsTrackerListBinding.inflate(inflater, container, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.trackerList,
                    WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.trackerList,
                    WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (actionMode != null) {
            actionMode.finish();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        ViewModelProvider provider = new ViewModelProvider(requireParentFragment());
        viewModel = provider.get(TorrentDetailsViewModel.class);

        layoutManager = new LinearLayoutManager(activity);
        binding.trackerList.setLayoutManager(layoutManager);
        binding.trackerList.setEmptyView(binding.emptyViewTrackerList);
        adapter = new TrackerListAdapter();
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
        binding.trackerList.setItemAnimator(animator);
        binding.trackerList.addItemDecoration(Utils.buildListDivider(activity));
        binding.trackerList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.trackerList,
                new TrackerListAdapter.KeyProvider(adapter),
                new TrackerListAdapter.ItemLookup(binding.trackerList),
                StorageStrategy.createParcelableStorage(TrackerItem.class))
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

                    /* Show/hide menu items after change selected files */
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
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            listTrackerState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_LIST_TRACKER_STATE, listTrackerState);
        }
        if (selectionTracker != null) {
            selectionTracker.onSaveInstanceState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listTrackerState = savedInstanceState.getParcelable(TAG_LIST_TRACKER_STATE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (listTrackerState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(listTrackerState);
        }
    }

    private void subscribeAdapter() {
        disposables.add(viewModel.observeTrackers()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((items) ->
                        Flowable.fromIterable(items)
                                .map(TrackerItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((items) -> adapter.submitList(items)));
    }

    private void setActionModeTitle(int itemCount) {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final androidx.appcompat.view.ActionMode.Callback actionModeCallback = new androidx.appcompat.view.ActionMode.Callback() {
        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.torrent_details_trackers_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.delete_tracker_url) {
                deleteTrackersDialog();
            } else if (itemId == R.id.share_url_menu) {
                shareUrl();
                mode.finish();
            } else if (itemId == R.id.select_all_trackers_menu) {
                selectAllTrackers();
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
            selectionTracker.clearSelection();
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void deleteTrackersDialog() {
        if (!isAdded()) {
            return;
        }

        var action = TorrentDetailsFragmentDirections.actionDeleteTrackersDialog(
                selectionTracker.getSelection().size(),
                KEY_DELETE_TRACKERS_DIALOG_REQUEST
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void deleteTrackers() {
        MutableSelection<TrackerItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.url))
                .toList()
                .subscribe((urls) -> viewModel.deleteTrackers(urls)));

        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void shareUrl() {
        MutableSelection<TrackerItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.url))
                .toList()
                .subscribe((urls) -> {
                    Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                    sharingIntent.setType("text/plain");
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "url");

                    if (urls.size() == 1)
                        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, urls.get(0));
                    else
                        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                                TextUtils.join(Utils.getLineSeparator(), urls));

                    startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
                }));
    }

    @SuppressLint("RestrictedApi")
    private void selectAllTrackers() {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }
}
