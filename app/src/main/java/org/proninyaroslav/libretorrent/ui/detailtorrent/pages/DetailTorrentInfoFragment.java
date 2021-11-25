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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages;

import android.app.Activity;
import android.app.Dialog;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.apache.commons.lang3.ArrayUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.databinding.FragmentDetailTorrentInfoBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentViewModel;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.ui.tag.SelectTagActivity;
import org.proninyaroslav.libretorrent.ui.tag.TorrentTagsList;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of DetailTorrentFragment.
 */

public class DetailTorrentInfoFragment extends Fragment {
    private static final String TAG = DetailTorrentInfoFragment.class.getSimpleName();

    private static final String TAG_OPEN_DIR_ERROR_DIALOG = "open_dir_error_dialog";
    private static final String TAG_EDIT_NAME_DIALOG = "edit_name_dialog";

    private AppCompatActivity activity;
    private DetailTorrentViewModel viewModel;
    private FragmentDetailTorrentInfoBinding binding;
    private BaseAlertDialog editNameDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static DetailTorrentInfoFragment newInstance() {
        DetailTorrentInfoFragment fragment = new DetailTorrentInfoFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

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
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail_torrent_info, container, false);

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(DetailTorrentViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);
        binding.setViewModel(viewModel);

        FragmentManager fm = getChildFragmentManager();
        editNameDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_EDIT_NAME_DIALOG);

        binding.folderChooserButton.setOnClickListener((v) -> showChooseDirDialog());
        binding.editNameButton.setOnClickListener((v) -> showEditNameDialog());
        binding.tagsList.setListener(tagsListener);

        return binding.getRoot();
    }

    final ActivityResultLauncher<Intent> chooseDir = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (result) -> {
                if (result.getResultCode() != Activity.RESULT_OK)
                    return;

                Intent data = result.getData();
                if (data == null || data.getData() == null) {
                    showOpenDirErrorDialog();
                    return;
                }

                viewModel.mutableParams.setDirPath(data.getData());
            });

    final ActivityResultLauncher<Intent> selectTag = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (result) -> {
                Intent data = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                    TagInfo tag = data.getParcelableExtra(SelectTagActivity.TAG_RESULT_SELECTED_TAG);
                    if (tag != null) {
                        disposables.add(viewModel.addTag(tag)
                                .subscribeOn(Schedulers.io())
                                .subscribe()
                        );
                    }
                }
            });

    private final TorrentTagsList.Listener tagsListener = new TorrentTagsList.Listener() {
        @Override
        public void onAddTagClick() {
            disposables.add(viewModel.getTags()
                    .subscribeOn(Schedulers.io())
                    .flatMap((tags) -> Observable.fromIterable(tags)
                            .map((tag) -> tag.id)
                            .toList()
                    )
                    .observeOn(AndroidSchedulers.mainThread())
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
            disposables.add(viewModel.removeTag(info)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            );
        }
    };

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(binding.tagsList::submit)
        );
    }

    private void showChooseDirDialog() {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(null,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.DIR_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        chooseDir.launch(i);
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAlertDialog();
        subscribeTags();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_EDIT_NAME_DIALOG) || editNameDialog == null)
                        return;
                    switch (event.type) {
                        case DIALOG_SHOWN:
                            initEditNameDialog();
                            break;
                        case POSITIVE_BUTTON_CLICKED:
                            Dialog dialog = editNameDialog.getDialog();
                            if (dialog != null) {
                                TextInputEditText editText = dialog.findViewById(R.id.text_input_dialog);
                                TextInputLayout layoutEditText = dialog.findViewById(R.id.layout_text_input_dialog);
                                Editable e = editText.getText();
                                if (checkNameField(e, layoutEditText)) {
                                    viewModel.mutableParams.setName(e.toString());
                                    editNameDialog.dismiss();
                                }
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            editNameDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
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

    private boolean checkNameField(Editable s, TextInputLayout layoutEditText) {
        if (TextUtils.isEmpty(s)) {
            layoutEditText.setErrorEnabled(true);
            layoutEditText.setError(getString(R.string.error_field_required));
            layoutEditText.requestFocus();

            return false;

        } else {
            layoutEditText.setErrorEnabled(false);
            layoutEditText.setError(null);

            return true;
        }
    }

    private void showEditNameDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_EDIT_NAME_DIALOG) == null) {
            editNameDialog = BaseAlertDialog.newInstance(
                    getString(R.string.edit_torrent_name),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            editNameDialog.show(fm, TAG_EDIT_NAME_DIALOG);
        }
    }

    private void initEditNameDialog() {
        Dialog dialog = editNameDialog.getDialog();
        if (dialog == null)
            return;

        TextInputEditText editText = dialog.findViewById(R.id.text_input_dialog);
        TextInputLayout layoutEditText = dialog.findViewById(R.id.layout_text_input_dialog);

        if (!TextUtils.isEmpty(editText.getText()))
            return;

        String name = viewModel.mutableParams.getName();
        if (name != null)
            editText.setText(name);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkNameField(s, layoutEditText);
            }
        });
    }
}