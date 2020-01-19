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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.trackers;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentDetailTorrentTrackerListBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentViewModel;
import org.proninyaroslav.libretorrent.ui.detailtorrent.MsgDetailTorrentViewModel;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying bittorrent trackers list. Part of DetailTorrentFragment.
 */

public class DetailTorrentTrackersFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentTrackersFragment.class.getSimpleName();

    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_LIST_TRACKER_STATE = "list_tracker_state";
    private static final String TAG_DELETE_TRACKERS_DIALOG = "delete_trackers_dialog";

    private AppCompatActivity activity;
    private FragmentDetailTorrentTrackerListBinding binding;
    private DetailTorrentViewModel viewModel;
    private MsgDetailTorrentViewModel msgViewModel;
    private LinearLayoutManager layoutManager;
    private SelectionTracker<TrackerItem> selectionTracker;
    private ActionMode actionMode;
    private TrackerListAdapter adapter;
    /* Save state scrolling */
    private Parcelable listTrackerState;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog deleteTrackersDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    public static DetailTorrentTrackersFragment newInstance()
    {
        DetailTorrentTrackersFragment fragment = new DetailTorrentTrackersFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail_torrent_tracker_list, container, false);

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        if (actionMode != null)
            actionMode.finish();
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
        subscribeAlertDialog();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        viewModel = ViewModelProviders.of(activity).get(DetailTorrentViewModel.class);
        msgViewModel = ViewModelProviders.of(activity).get(MsgDetailTorrentViewModel.class);
        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);

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
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{R.attr.divider});
        binding.trackerList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        a.recycle();
        binding.trackerList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.trackerList,
                new TrackerListAdapter.KeyProvider(adapter),
                new TrackerListAdapter.ItemLookup(binding.trackerList),
                StorageStrategy.createParcelableStorage(TrackerItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<TrackerItem>() {
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

                    /* Show/hide menu items after change selected files */
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

        FragmentManager fm = getSupportFragmentManager();
        deleteTrackersDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_DELETE_TRACKERS_DIALOG);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        listTrackerState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_TRACKER_STATE, listTrackerState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            listTrackerState = savedInstanceState.getParcelable(TAG_LIST_TRACKER_STATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listTrackerState != null)
            layoutManager.onRestoreInstanceState(listTrackerState);
    }

    private void subscribeAdapter()
    {
        disposables.add(viewModel.observeTrackers()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((children) ->
                        Flowable.fromIterable(children)
                                .map(TrackerItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((children) -> adapter.submitList(children)));
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (!event.dialogTag.equals(TAG_DELETE_TRACKERS_DIALOG) || deleteTrackersDialog == null)
                        return;
                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            deleteTrackers();
                            deleteTrackersDialog.dismiss();
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            deleteTrackersDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void setActionModeTitle(int itemCount)
    {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final androidx.appcompat.view.ActionMode.Callback actionModeCallback = new androidx.appcompat.view.ActionMode.Callback()
    {
        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.detail_torrent_trackers_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);
            msgViewModel.fragmentInActionMode(true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item)
        {
            switch (item.getItemId()) {
                case R.id.delete_tracker_url:
                    deleteTrackersDialog();
                    break;
                case R.id.share_url_menu:
                    shareUrl();
                    mode.finish();
                    break;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode)
        {
            selectionTracker.clearSelection();
            msgViewModel.fragmentInActionMode(false);
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void deleteTrackersDialog()
    {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_DELETE_TRACKERS_DIALOG) == null) {
            deleteTrackersDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    (selectionTracker.getSelection().size() > 1 ?
                            getString(R.string.delete_selected_trackers) :
                            getString(R.string.delete_selected_tracker)),
                    0,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            deleteTrackersDialog.show(fm, TAG_DELETE_TRACKERS_DIALOG);
        }
    }

    private void deleteTrackers()
    {
        MutableSelection<TrackerItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.url))
                .toList()
                .subscribe((urls) -> viewModel.deleteTrackers(urls)));

        if (actionMode != null)
            actionMode.finish();
    }

    private void shareUrl()
    {
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

    /*
     * Use only getChildFragmentManager() instead of getSupportFragmentManager(),
     * to remove all nested fragments in two-pane interface mode
     */

    public FragmentManager getSupportFragmentManager()
    {
        return getChildFragmentManager();
    }
}
