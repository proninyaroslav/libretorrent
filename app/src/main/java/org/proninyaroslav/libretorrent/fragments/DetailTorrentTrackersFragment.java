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

package org.proninyaroslav.libretorrent.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.adapters.TrackerListAdapter;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.dialogs.SupportBaseAlertDialog;

import java.util.ArrayList;
import java.util.Collections;

/*
 * The fragment for displaying bittorrent trackers list. Part of DetailTorrentFragment.
 */

public class DetailTorrentTrackersFragment extends Fragment
        implements
        TrackerListAdapter.ViewHolder.ClickListener,
        SupportBaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentTrackersFragment.class.getSimpleName();

    private static final String TAG_TRACKER_LIST = "tracker_list";
    private static final String TAG_LIST_TRACKER_STATE = "list_tracker_state";
    private static final String TAG_SELECTABLE_ADAPTER = "selectable_adapter";
    private static final String TAG_SELECTED_TRACKERS = "selected_files";
    private static final String TAG_IN_ACTION_MODE = "in_action_mode";
    private static final String TAG_DELETE_TRACKERS_DIALOG = "delete_trackers_dialog";

    private AppCompatActivity activity;
    private RecyclerView trackersList;
    private TrackerListAdapter adapter;
    /* Save state scrolling */
    private Parcelable listTrackerState;
    private LinearLayoutManager layoutManager;
    private ActionMode actionMode;
    private ActionModeCallback actionModeCallback = new ActionModeCallback();
    private boolean inActionMode = false;
    private ArrayList<String> selectedTrackers = new ArrayList<>();
    private DetailTorrentFragment.Callback callback;

    private ArrayList<TrackerStateParcel> trackers = new ArrayList<>();

    public static DetailTorrentTrackersFragment newInstance() {
        DetailTorrentTrackersFragment fragment = new DetailTorrentTrackersFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    /* For API < 23 */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        if (activity instanceof AppCompatActivity) {
            this.activity = (AppCompatActivity) activity;

            if (activity instanceof DetailTorrentFragment.Callback) {
                callback = (DetailTorrentFragment.Callback) activity;
            }
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            trackers = savedInstanceState.getParcelableArrayList(TAG_TRACKER_LIST);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_detail_torrent_tracker_list, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        trackersList = (RecyclerView) activity.findViewById(R.id.tracker_list);
        if (trackersList != null) {
            layoutManager = new LinearLayoutManager(activity);
            trackersList.setLayoutManager(layoutManager);

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

            int resId = R.drawable.list_divider;
            if (Utils.isDarkTheme(activity.getApplicationContext()) || Utils.isBlackTheme(activity.getApplicationContext())) {
                resId = R.drawable.list_divider_dark;
            }
            
            trackersList.setItemAnimator(animator);
            trackersList.addItemDecoration(
                    new RecyclerViewDividerDecoration(
                            activity.getApplicationContext(), resId));

            adapter = new TrackerListAdapter(trackers, activity, R.layout.item_trackers_list, this);
            trackersList.setAdapter(adapter);
        }

        if (savedInstanceState != null) {
            selectedTrackers = savedInstanceState.getStringArrayList(TAG_SELECTED_TRACKERS);
            if (savedInstanceState.getBoolean(TAG_IN_ACTION_MODE, false)) {
                actionMode = activity.startActionMode(actionModeCallback);
                adapter.setSelectedItems(savedInstanceState.getIntegerArrayList(TAG_SELECTABLE_ADAPTER));
                actionMode.setTitle(String.valueOf(adapter.getSelectedItemCount()));
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        if (layoutManager != null) {
            listTrackerState = layoutManager.onSaveInstanceState();
        }
        outState.putParcelable(TAG_LIST_TRACKER_STATE, listTrackerState);
        outState.putSerializable(TAG_TRACKER_LIST, trackers);
        if (adapter != null) {
            outState.putIntegerArrayList(TAG_SELECTABLE_ADAPTER, adapter.getSelectedItems());
        }
        outState.putBoolean(TAG_IN_ACTION_MODE, inActionMode);
        outState.putStringArrayList(TAG_SELECTED_TRACKERS, selectedTrackers);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listTrackerState = savedInstanceState.getParcelable(TAG_LIST_TRACKER_STATE);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listTrackerState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(listTrackerState);
        }
    }

    public void setTrackersList(ArrayList<TrackerStateParcel> trackers)
    {
        Collections.sort(trackers);
        this.trackers = trackers;

        adapter.updateItems(trackers);
    }

    @Override
    public void onItemClicked(int position, TrackerStateParcel state)
    {
        if (actionMode != null) {
            String url = state.url;
            if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
                return;
            }

            onItemSelected(url, position);
        }
    }

    @Override
    public boolean onItemLongClicked(int position, TrackerStateParcel state)
    {
        String url = state.url;
        if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
            return false;
        }

        if (actionMode == null) {
            actionMode = activity.startActionMode(actionModeCallback);
        }

        onItemSelected(url, position);

        return true;
    }

    private void onItemSelected(String url, int position)
    {
        toggleSelection(position);

        if (selectedTrackers.contains(url)) {
            selectedTrackers.remove(url);
        } else {
            selectedTrackers.add(url);
        }
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        int count = adapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (getFragmentManager().findFragmentByTag(TAG_DELETE_TRACKERS_DIALOG) != null) {
            deleteTrackers();
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        selectedTrackers.clear();
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }

    private void deleteTrackers()
    {
        if (!selectedTrackers.isEmpty()) {
            if (callback != null) {
                ArrayList<String> urls = new ArrayList<>();
                for (TrackerStateParcel tracker : trackers) {
                    if (!selectedTrackers.contains(tracker.url)) {
                        urls.add(tracker.url);
                    }
                }

                callback.onTrackersChanged(urls, true);

                selectedTrackers.clear();
            }
        }
    }

    private void shareUrl()
    {
        if (!selectedTrackers.isEmpty()) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "url");

            if (selectedTrackers.size() == 1) {
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, selectedTrackers.get(0));
            } else {
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                        TextUtils.join(Utils.getLineSeparator(), selectedTrackers));
            }

            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));

            selectedTrackers.clear();
        }
    }

    private class ActionModeCallback implements ActionMode.Callback
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            inActionMode = true;
            mode.getMenuInflater().inflate(R.menu.detail_torrent_trackers_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            switch (item.getItemId()) {
                case R.id.share_url_menu:
                    mode.finish();

                    shareUrl();
                    break;
                case R.id.delete_tracker_url:
                    mode.finish();

                    if (getFragmentManager().findFragmentByTag(TAG_DELETE_TRACKERS_DIALOG) == null) {
                         SupportBaseAlertDialog deleteTrackersDialog = SupportBaseAlertDialog.newInstance(
                                 getString(R.string.deleting),
                                (selectedTrackers.size() > 1 ? getString(R.string.delete_selected_trackers) :
                                        getString(R.string.delete_selected_tracker)),
                                0,
                                 getString(R.string.ok),
                                 getString(R.string.cancel),
                                null,
                                DetailTorrentTrackersFragment.this);

                        deleteTrackersDialog.show(getFragmentManager(), TAG_DELETE_TRACKERS_DIALOG);
                    }
                    break;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            adapter.clearSelection();
            actionMode = null;
            inActionMode = false;
            Utils.showActionModeStatusBar(activity, false);

        }
    }
}
