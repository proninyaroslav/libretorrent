/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.createtorrent;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentCreateTorrentBinding;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CreateTorrentFragment extends Fragment {
    private static final String TAG = CreateTorrentFragment.class.getSimpleName();

    private static final String KEY_FILE_MANAGER_DIALOG_REQUEST = TAG + "_file_manager_dialog";

    private AppCompatActivity activity;
    private CreateTorrentViewModel viewModel;
    private FragmentCreateTorrentBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setOpenFileManagerDialogListener();
    }

    private void setOpenFileManagerDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_FILE_MANAGER_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        String message = "";
                        switch (resultValue.config().showMode) {
                            case FILE_CHOOSER -> message = getString(R.string.unable_to_open_file);
                            case DIR_CHOOSER -> message = getString(R.string.unable_to_open_folder);
                            case SAVE_FILE -> message = getString(R.string.unable_to_create_file);
                        }
                        Snackbar.make(activity, binding.coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
                    } else {
                        if (resultValue.config().showMode == FileManagerConfig.Mode.SAVE_FILE) {
                            viewModel.mutableParams.setSavePath(uri);
                            buildTorrent();
                        } else {
                            binding.layoutFileOrDirPath.setErrorEnabled(false);
                            binding.layoutFileOrDirPath.setError(null);
                            viewModel.mutableParams.getSeedPath().set(uri);
                        }
                    }
                }
        );
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        var provider = new ViewModelProvider(this);
        viewModel = provider.get(CreateTorrentViewModel.class);

        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.fragment_create_torrent, null, false);
        binding.setViewModel(viewModel);

        Utils.applyWindowInsets(
                binding.nestedScrollView,
                WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT,
                WindowInsetsCompat.Type.ime()
        );
        Utils.applyWindowInsets(binding.bottomBar, WindowInsetsSide.ALL, WindowInsetsCompat.Type.ime());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        initLayoutView();
    }

    private void initLayoutView() {
        /* Dismiss error label if user has changed the text */
        binding.trackerUrls.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutTrackerUrls.setErrorEnabled(false);
                binding.layoutTrackerUrls.setError(null);
            }
        });

        binding.webSeedUrls.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutWebSeedUrls.setErrorEnabled(false);
                binding.layoutWebSeedUrls.setError(null);
            }
        });

        var piecesSizeAdapter = binding.pieceSize.getAdapter();
        var currentSize = piecesSizeAdapter.getItem(
                viewModel.mutableParams.getPieceSizeIndex()
        ).toString();
        binding.pieceSize.setText(currentSize, false);
        binding.pieceSize.setOnItemClickListener((parent, view, position, id) ->
                viewModel.setPiecesSizeIndex(position));

        var torrentVersionAdapter = binding.torrentVersion.getAdapter();
        var currentVersion = torrentVersionAdapter.getItem(
                viewModel.mutableParams.getTorrentVersionIndex()
        ).toString();
        binding.torrentVersion.setText(currentVersion, false);
        binding.torrentVersion.setOnItemClickListener((parent, view, position, id) ->
                viewModel.setTorrentVersionIndex(position));

        binding.fileChooserButton.setOnClickListener((v) -> {
            var config = new FileManagerConfig(
                    null,
                    null,
                    FileManagerConfig.Mode.FILE_CHOOSER);
            var action = CreateTorrentFragmentDirections
                    .actionFileManagerDialog(config, KEY_FILE_MANAGER_DIALOG_REQUEST);
            NavHostFragment.findNavController(this).navigate(action);
        });

        binding.folderChooserButton.setOnClickListener((v) -> {
            var config = new FileManagerConfig(
                    null,
                    null,
                    FileManagerConfig.Mode.DIR_CHOOSER);
            var action = CreateTorrentFragmentDirections
                    .actionFileManagerDialog(config, KEY_FILE_MANAGER_DIALOG_REQUEST);
            NavHostFragment.findNavController(this).navigate(action);
        });

        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());

        viewModel.getState().observe(getViewLifecycleOwner(), (state) -> {
            if (state.status == CreateTorrentViewModel.BuildState.Status.BUILDING) {
                Snackbar.make(
                        activity,
                        binding.coordinatorLayout,
                        getString(R.string.creating_torrent_progress),
                        Snackbar.LENGTH_LONG
                ).show();
                binding.createButton.setEnabled(false);
            } else {
                binding.createButton.setEnabled(true);
            }

            if (state.status == CreateTorrentViewModel.BuildState.Status.FINISHED) {
                handleFinish();
            } else if (state.status == CreateTorrentViewModel.BuildState.Status.ERROR) {
                handleBuildError(state.err);
            }
        });

        binding.createButton.setOnClickListener((v) -> {
            if (TextUtils.isEmpty(viewModel.mutableParams.getSeedPathName()) || "null".equals(viewModel.mutableParams.getSeedPathName())) {
                binding.layoutFileOrDirPath.setErrorEnabled(true);
                binding.layoutFileOrDirPath.setError(getText(R.string.error_please_select_file_or_folder_for_seeding_first));
            } else {
                choosePathToSaveDialog();
            }
        });
    }

    private void choosePathToSaveDialog() {
        var config = new FileManagerConfig(
                null,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.Mode.SAVE_FILE);
        config.mimeType = Utils.MIME_TORRENT;
        var action = CreateTorrentFragmentDirections
                .actionFileManagerDialog(config, KEY_FILE_MANAGER_DIALOG_REQUEST);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void buildTorrent() {
        binding.layoutTrackerUrls.setErrorEnabled(false);
        binding.layoutTrackerUrls.setError(null);
        binding.layoutWebSeedUrls.setErrorEnabled(false);
        binding.layoutWebSeedUrls.setError(null);

        viewModel.buildTorrent();
    }

    private void handleBuildError(Throwable err) {
        if (err == null) {
            return;
        }

        Log.e(TAG, Log.getStackTraceString(err));

        if (err instanceof CreateTorrentViewModel.InvalidTrackerException) {
            binding.layoutTrackerUrls.setErrorEnabled(true);
            binding.layoutTrackerUrls.setError(getString(R.string.invalid_url,
                    ((CreateTorrentViewModel.InvalidTrackerException) err).url));
            binding.layoutTrackerUrls.requestFocus();

        } else if (err instanceof CreateTorrentViewModel.InvalidWebSeedException) {
            binding.layoutWebSeedUrls.setErrorEnabled(true);
            binding.layoutWebSeedUrls.setError(getString(R.string.invalid_url,
                    ((CreateTorrentViewModel.InvalidWebSeedException) err).url));
            binding.layoutWebSeedUrls.requestFocus();

        } else if (err instanceof FileNotFoundException) {
            fileOrFolderNotFoundDialog((FileNotFoundException) err);

        } else if (err instanceof IOException) {
            if (err.getMessage() != null && err.getMessage().contains("content total size can't be 0")) {
                Snackbar.make(
                        activity,
                        binding.coordinatorLayout,
                        getString(R.string.folder_is_empty),
                        Snackbar.LENGTH_SHORT
                ).show();
            } else {
                errorReportDialog(err);
            }
        } else {
            errorReportDialog(err);
        }
    }

    private void handleFinish() {
        Uri savePath = viewModel.mutableParams.getSavePath();
        if (savePath != null) {
            Toast.makeText(activity.getApplicationContext(),
                            getString(R.string.torrent_saved_to, savePath.getPath()),
                            Toast.LENGTH_SHORT)
                    .show();
        }
        if (viewModel.mutableParams.isStartSeeding()) {
            try {
                disposables.add(viewModel.downloadTorrent()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> activity.getOnBackPressedDispatcher().onBackPressed(),
                                this::handleBuildError)
                );
            } catch (UnknownUriException e) {
                handleBuildError(e);
            }
        } else {
            activity.getOnBackPressedDispatcher().onBackPressed();
        }
    }

    private void fileOrFolderNotFoundDialog(FileNotFoundException e) {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_error_24px)
                .setTitle(R.string.error)
                .setMessage(e.getMessage())
                .setPositiveButton(R.string.ok, ((dialog, which) -> dialog.dismiss()))
                .show();
    }

    private void errorReportDialog(Throwable e) {
        var action = CreateTorrentFragmentDirections.actionErrorReportDialog(
                getString(R.string.error_create_torrent) + ": " + e.getMessage()
        );
        NavHostFragment.findNavController(this).navigate(action);
    }
}