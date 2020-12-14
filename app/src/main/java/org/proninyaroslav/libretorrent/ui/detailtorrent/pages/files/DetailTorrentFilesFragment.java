/*
 * Copyright (C) 2016, 2017, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.files;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentDetailTorrentFilesBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentViewModel;
import org.proninyaroslav.libretorrent.ui.detailtorrent.MsgDetailTorrentViewModel;

import java.util.Collections;
import java.util.Iterator;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for list files of torrent. Part of DetailTorrentFragment.
 */

public class DetailTorrentFilesFragment extends Fragment
        implements TorrentContentFilesAdapter.ClickListener
{
    private static final String TAG = DetailTorrentFilesFragment.class.getSimpleName();

    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_PRIORITY_DIALOG = "priority_dialog";

    private AppCompatActivity activity;
    private FragmentDetailTorrentFilesBinding binding;
    private DetailTorrentViewModel viewModel;
    private MsgDetailTorrentViewModel msgViewModel;
    private LinearLayoutManager layoutManager;
    private SelectionTracker<TorrentContentFileItem> selectionTracker;
    private ActionMode actionMode;
    private TorrentContentFilesAdapter adapter;
    /* Save state scrolling */
    private Parcelable listFilesState;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog priorityDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    public static DetailTorrentFilesFragment newInstance()
    {
        DetailTorrentFilesFragment fragment = new DetailTorrentFilesFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail_torrent_files, container, false);

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

    private void subscribeAdapter()
    {
        disposables.add(viewModel.getDirChildren()
                .subscribeOn(Schedulers.computation())
                .flatMapSingle((children) ->
                        Flowable.fromIterable(children)
                                .map(TorrentContentFileItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((children) -> {
                    adapter.submitList(children);
                    updateFileSize();
                }));
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_PRIORITY_DIALOG) || priorityDialog == null)
                        return;
                    switch (event.type) {
                        case DIALOG_SHOWN:
                            initPriorityDialog();
                            break;
                        case POSITIVE_BUTTON_CLICKED:
                            changePriority();
                            priorityDialog.dismiss();
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            priorityDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(DetailTorrentViewModel.class);
        binding.setViewModel(viewModel);
        msgViewModel = provider.get(MsgDetailTorrentViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        layoutManager = new LinearLayoutManager(activity);
        binding.fileList.setLayoutManager(layoutManager);
        adapter = new TorrentContentFilesAdapter(this);
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
        binding.fileList.setItemAnimator(animator);
        binding.fileList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.fileList,
                new TorrentContentFilesAdapter.KeyProvider(adapter),
                new TorrentContentFilesAdapter.ItemLookup(binding.fileList),
                StorageStrategy.createParcelableStorage(TorrentContentFileItem.class))
                .withSelectionPredicate(new SelectionTracker.SelectionPredicate<TorrentContentFileItem>() {
                    @Override
                    public boolean canSetStateForKey(@NonNull TorrentContentFileItem key, boolean nextState)
                    {
                        return !key.name.equals(BencodeFileTree.PARENT_DIR);
                    }

                    @Override
                    public boolean canSetStateAtPosition(int position, boolean nextState)
                    {
                        return true;
                    }

                    @Override
                    public boolean canSelectMultiple()
                    {
                        return true;
                    }
                })
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<TorrentContentFileItem>() {
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

        FragmentManager fm = getChildFragmentManager();
        priorityDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_PRIORITY_DIALOG);
    }

    private void updateFileSize()
    {
        if (viewModel.fileTree == null)
            return;

        binding.filesSize.setText(getString(R.string.files_size,
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.nonIgnoreFileSize()),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.size())));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        listFilesState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            listFilesState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listFilesState != null)
            layoutManager.onRestoreInstanceState(listFilesState);
    }

    @Override
    public void onItemClicked(@NonNull TorrentContentFileItem item)
    {
        if (item.name.equals(BencodeFileTree.PARENT_DIR)) {
            viewModel.upToParentDirectory();
        } else if (!item.isFile) {
            viewModel.chooseDirectory(item.name);
        } else {
            disposables.add(viewModel.getFilePath(item.name)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            (path) -> openFile(item.name, path),
                            this::handleOpenFileError
                    ));
        }
    }

    @Override
    public void onItemCheckedChanged(@NonNull TorrentContentFileItem item, boolean selected)
    {
        viewModel.applyPriority(Collections.singletonList(item.name),
                new FilePriority(selected ? FilePriority.Type.NORMAL : FilePriority.Type.IGNORE));
        updateFileSize();
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
            MenuItem shareStreamUrl = menu.findItem(R.id.share_stream_url_menu);
            shareStreamUrl.setVisible(false);

            Selection<TorrentContentFileItem> selection = selectionTracker.getSelection();
            if (selection.size() != 1)
                return true;
            Iterator<TorrentContentFileItem> it = selection.iterator();
            if (it.hasNext() && !viewModel.isFile(it.next().name))
                return true;

            shareStreamUrl.setVisible(true);

            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.detail_torrent_files_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);
            msgViewModel.fragmentInActionMode(true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            int itemId = item.getItemId();
            if (itemId == R.id.change_priority_menu) {
                showPriorityDialog();
            } else if (itemId == R.id.share_stream_url_menu) {
                shareStreamUrl();
                mode.finish();
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            selectionTracker.clearSelection();
            msgViewModel.fragmentInActionMode(false);
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void showPriorityDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm  = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_PRIORITY_DIALOG) == null) {
            priorityDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_change_priority_title),
                    null,
                    R.layout.dialog_change_priority,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            priorityDialog.show(fm, TAG_PRIORITY_DIALOG);
        }
    }

    private void initPriorityDialog()
    {
        MutableSelection<TorrentContentFileItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.name))
                .toList()
                .subscribe((fileNames) -> {
                    Dialog dialog = priorityDialog.getDialog();
                    if (dialog == null)
                        return;

                    FilePriority priority = viewModel.getFilesPriority(fileNames);
                    if (priority == null)
                        return;

                    int resId = -1;
                    switch (priority.getType()) {
                        case IGNORE:
                            resId = R.id.dialog_priority_low;
                            break;
                        case HIGH:
                            resId = R.id.dialog_priority_high;
                            break;
                        case NORMAL:
                            resId = R.id.dialog_priority_normal;
                            break;
                    }

                    if (resId == -1) {
                        RadioGroup group = dialog.findViewById(R.id.dialog_priorities_group);
                        group.clearCheck();

                    } else {
                        RadioButton button = dialog.findViewById(resId);
                        button.setChecked(true);
                    }
                }));
    }

    private void changePriority()
    {
        Dialog dialog = priorityDialog.getDialog();
        if (dialog == null)
            return;

        RadioGroup group = dialog.findViewById(R.id.dialog_priorities_group);
        int radioButtonId = group.getCheckedRadioButtonId();

        FilePriority.Type priorityType = null;
        if (radioButtonId == R.id.dialog_priority_low) {
            priorityType = FilePriority.Type.IGNORE;
        } else if (radioButtonId == R.id.dialog_priority_normal) {
            priorityType = FilePriority.Type.NORMAL;
        } else if (radioButtonId == R.id.dialog_priority_high) {
            priorityType = FilePriority.Type.HIGH;
        }
        if (priorityType != null) {
            FilePriority priority = new FilePriority(priorityType);
            MutableSelection<TorrentContentFileItem> selections = new MutableSelection<>();
            selectionTracker.copySelection(selections);

            disposables.add(Observable.fromIterable(selections)
                    .map((selection -> selection.name))
                    .toList()
                    .subscribe((fileNames) -> viewModel.applyPriority(fileNames, priority)));
        }

        if (actionMode != null)
            actionMode.finish();
    }

    private void shareStreamUrl()
    {
        MutableSelection<TorrentContentFileItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<TorrentContentFileItem> it = selections.iterator();
        if (!it.hasNext())
            return;
        int fileIndex = it.next().index;

        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "url");
        sharingIntent.putExtra(Intent.EXTRA_TEXT, viewModel.getStreamUrl(fileIndex));

        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }

    private void openFile(String fileName, Uri path)
    {
        if (fileName == null || path == null)
            return;

        startActivity(viewModel.makeOpenFileIntent(fileName, path));
    }

    private void handleOpenFileError(Throwable err)
    {
        Log.e(TAG, "Unable to open file: " + Log.getStackTraceString(err));
        Toast.makeText(activity,
                R.string.unable_to_open_file,
                Toast.LENGTH_SHORT)
                .show();
    }
}
