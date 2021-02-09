/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import org.apache.commons.lang3.ArrayUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.databinding.FragmentAddTorrentInfoBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.ui.tag.SelectTagActivity;
import org.proninyaroslav.libretorrent.ui.tag.TorrentTagsList;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of AddTorrentFragment.
 */

public class AddTorrentInfoFragment extends Fragment {
    private static final String TAG = AddTorrentInfoFragment.class.getSimpleName();

    private static final String TAG_OPEN_DIR_ERROR_DIALOG = "open_dir_error_dialog";

    private AppCompatActivity activity;
    private AddTorrentViewModel viewModel;
    private FragmentAddTorrentInfoBinding binding;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static AddTorrentInfoFragment newInstance() {
        AddTorrentInfoFragment fragment = new AddTorrentInfoFragment();

        Bundle b = new Bundle();
        fragment.setArguments(b);

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_torrent_info, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(activity).get(AddTorrentViewModel.class);
        binding.setViewModel(viewModel);

        binding.info.folderChooserButton.setOnClickListener((v) -> showChooseDirDialog());
        binding.info.name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkNameField(s);
            }
        });
        binding.info.tagsList.setListener(tagsListener);
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeTags();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    final ActivityResultLauncher<Intent> selectTag = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (result) -> {
                Intent data = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                    TagInfo tag = data.getParcelableExtra(SelectTagActivity.TAG_RESULT_SELECTED_TAG);
                    if (tag != null) {
                        viewModel.addTag(tag);
                    }
                }
            }
    );

    final ActivityResultLauncher<Intent> chooseDir = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), (result) -> {
                if (result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Intent data = result.getData();
                if (data == null || data.getData() == null) {
                    showOpenDirErrorDialog();
                    return;
                }
                viewModel.mutableParams.getDirPath().set(data.getData());
            });

    private final TorrentTagsList.Listener tagsListener = new TorrentTagsList.Listener() {
        @Override
        public void onAddTagClick() {
            disposables.add(Observable.fromIterable(viewModel.getCurrentTags())
                    .map((tag) -> tag.id)
                    .toList()
                    .subscribe((ids) -> {
                        Intent i = new Intent(activity, SelectTagActivity.class);
                        i.putExtra(
                                SelectTagActivity.TAG_EXCLUDE_TAGS_ID,
                                ArrayUtils.toPrimitive(ids.toArray(new Long[0]))
                        );
                        selectTag.launch(i);
                    })
            );
        }

        @Override
        public void onTagRemoved(@NonNull TagInfo info) {
            viewModel.removeTag(info);
        }
    };

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribe(binding.info.tagsList::submit)
        );
    }

    private void showChooseDirDialog() {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(
                activity.getApplicationContext()
        );
        String path;
        try {
            path = fs.getDirPath(viewModel.mutableParams.getDirPath().get());
        } catch (UnknownUriException e) {
            path = null;
        }

        FileManagerConfig config = new FileManagerConfig(
                path,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.DIR_CHOOSER_MODE
        );

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        chooseDir.launch(i);
    }

    private void showOpenDirErrorDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_OPEN_DIR_ERROR_DIALOG) == null) {
            BaseAlertDialog openDirErrorDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.unable_to_open_folder),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            openDirErrorDialog.show(fm, TAG_OPEN_DIR_ERROR_DIALOG);
        }
    }

    private void checkNameField(Editable s) {
        if (TextUtils.isEmpty(s)) {
            binding.info.layoutName.setErrorEnabled(true);
            binding.info.layoutName.setError(getString(R.string.error_field_required));
            binding.info.layoutName.requestFocus();
        } else {
            binding.info.layoutName.setErrorEnabled(false);
            binding.info.layoutName.setError(null);
        }
    }
}