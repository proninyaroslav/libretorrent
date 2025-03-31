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

package org.proninyaroslav.libretorrent.ui.filemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.Observable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentFileManagerBinding;
import org.proninyaroslav.libretorrent.MainActivity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

/*
 * The simple dialog for navigation and select directory.
 *
 * Returns Uri that represent filesystem path (file:// scheme).
 *
 * For different show modes returns the following values:
 *  - FILE_CHOOSER_MODE: Uri of the selected file
 *  - DIR_CHOOSER_MODE: Uri of the selected folder
 *  - SAVE_FILE_MODE: Uri of the created file
 */

public class FileManagerFragment extends Fragment implements FileManagerAdapter.ViewHolder.ClickListener {
    private static final String TAG = FileManagerFragment.class.getSimpleName();

    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String TAG_SPINNER_POS = "spinner_pos";
    private static final String KEY_GO_TO_FOLDER_DIALOG_REQUEST = TAG + "_go_to_folder_dialog";
    private static final String KEY_INPUT_NAME_DIALOG_REQUEST = TAG + "_input_name_folder";

    // TODO
    public static final String TAG_CONFIG = "config";

    public static final String KEY_RESULT = "result";

    private MainActivity activity;
    private FragmentFileManagerBinding binding;
    private GridLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable filesListState;
    private FileManagerAdapter adapter;

    private FileManagerViewModel viewModel;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private SharedPreferences pref;
    private final MediaReceiver mediaReceiver = new MediaReceiver(this);
    /*
     * Prevent call onItemSelected after set OnItemSelectedListener,
     * see http://stackoverflow.com/questions/21747917/undesired-onitemselected-calls/21751327#21751327
     */
    private int storageMenuPos = -1;
    private String requestKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity a) {
            activity = a;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var fm = getParentFragmentManager();
        fm.setFragmentResultListener(
                KEY_INPUT_NAME_DIALOG_REQUEST,
                this,
                (key, result) -> {
                    var name = result.getString(InputNameDialog.KEY_RESULT_NAME);
                    if (!viewModel.createDirectory(name)) {
                        Snackbar.make(
                                binding.coordinatorLayout,
                                R.string.error_dialog_new_folder,
                                Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        try {
                            viewModel.openDirectory(name);
                        } catch (SecurityException e) {
                            permissionDeniedToast();
                        } catch (IOException e) {
                            Log.e(TAG, Log.getStackTraceString(e));
                            showSendErrorDialog(e);
                        }
                    }
                }
        );
        fm.setFragmentResultListener(
                KEY_GO_TO_FOLDER_DIALOG_REQUEST,
                this,
                (key, result) -> {
                    var path = result.getString(GoToFolderDialog.KEY_RESULT_PATH);
                    goToDirectory(path);
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_file_manager, container, false);
        Utils.applyWindowInsets(binding.bottomBar, WindowInsetsSide.ALL, WindowInsetsCompat.Type.ime());
        Utils.applyWindowInsets(
                binding.swipeContainer,
                WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT | WindowInsetsSide.BOTTOM,
                WindowInsetsCompat.Type.ime()
        );
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(FileManagerFragment.this).navigateUp();
            }
        });

        var args = FileManagerFragmentArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();
        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(activity.getApplicationContext());
        pref = PreferenceManager.getDefaultSharedPreferences(activity);

        String startDir = pref.getString(getString(R.string.pref_key_filemanager_last_dir), fs.getDefaultDownloadPath());
        FileManagerViewModelFactory factory = new FileManagerViewModelFactory(
                activity.getApplication(),
                args.getConfig(),
                startDir
        );
        viewModel = new ViewModelProvider(this, factory).get(FileManagerViewModel.class);
        binding.setViewModel(viewModel);

        String title = viewModel.config.title;
        if (TextUtils.isEmpty(title)) {
            switch (viewModel.config.showMode) {
                case DIR_CHOOSER:
                    binding.appBar.setTitle(R.string.dir_chooser_title);
                    break;
                case FILE_CHOOSER:
                    binding.appBar.setTitle(R.string.file_chooser_title);
                    break;
                case SAVE_FILE:
                    binding.appBar.setTitle(R.string.save_file);
                    break;
            }
        } else {
            binding.appBar.setTitle(title);
        }

        binding.appBar.setOnMenuItemClickListener(this::onMenuItemClickListener);
        binding.appBar.setNavigationOnClickListener((v) ->
                NavHostFragment.findNavController(this).navigateUp());

        var okButton = (MaterialButton) binding.okButton;
        if (viewModel.config.showMode == FileManagerConfig.Mode.DIR_CHOOSER) {
            okButton.setIconResource(R.drawable.ic_check_24px);
        } else {
            okButton.setIconResource(R.drawable.ic_save_24px);
        }
        okButton.setOnClickListener((v) -> {
            saveCurDirectoryPath();
            if (viewModel.config.showMode == FileManagerConfig.Mode.SAVE_FILE) {
                createFile(false);
            } else {
                returnDirectoryUri();
            }
        });

        if (savedInstanceState == null)
            binding.fileName.setText(viewModel.config.fileName);
        binding.fileName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutFileName.setErrorEnabled(false);
                binding.layoutFileName.setError(null);
            }
        });

        layoutManager = (GridLayoutManager) binding.fileList.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return adapter.getItemViewType(position) == FileManagerAdapter.VIEW_TYPE_PARENT_DIR
                            ? layoutManager.getSpanCount()
                            : 1;
                }
            });
        }
        binding.fileList.setLayoutManager(layoutManager);
        binding.fileList.setItemAnimator(new DefaultItemAnimator());

        adapter = new FileManagerAdapter(viewModel.config.highlightFileTypes, this);
        binding.fileList.setAdapter(adapter);

        binding.swipeContainer.setOnRefreshListener(this::refreshDir);

        var storageItems = viewModel.getStorageList();
        if (savedInstanceState != null) {
            storageMenuPos = savedInstanceState.getInt(TAG_SPINNER_POS);
        } else {
            storageMenuPos = getStorageMenuPos(storageItems);
        }

        ((MaterialAutoCompleteTextView) binding.storageMenuTextView)
                .setSimpleItems(buildSpinnerMenuItems(storageItems));
        binding.storageMenuTextView.setText(viewModel.curDir.get(), false);
        binding.storageMenuTextView.setTag(storageMenuPos);
        binding.storageMenuTextView.setListSelection(storageMenuPos);
        binding.storageMenuTextView.setOnItemClickListener((parent, v, position, id) -> {
            var item = viewModel.getStorageById(position);
            if (item == null) {
                return;
            }
            binding.storageMenuTextView.setText(item.path(), false);
            if (((Integer) binding.storageMenuTextView.getTag()) != position) {
                storageMenuPos = position;
                binding.storageMenuTextView.setTag(storageMenuPos);
                try {
                    binding.storageMenuTextView.setText(item.path(), false);
                    if (viewModel.isDirectoryExists(item.path())) {
                        viewModel.jumpToDirectory(item.path());
                    } else {
                        viewModel.jumpToDirectory(viewModel.startDir);
                    }

                } catch (SecurityException e) {
                    permissionDeniedToast();
                }
            }
        });

        registerMediaReceiver();
    }

    private void showInputNameDialog() {
        var action = FileManagerFragmentDirections
                .actionOpenInputNameDialog(KEY_INPUT_NAME_DIALOG_REQUEST);
        NavHostFragment.findNavController(this).navigate(action);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        unregisterMediaReceiver();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposable.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
        viewModel.curDir.addOnPropertyChangedCallback(currentDirCallback);
    }

    private void registerMediaReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        activity.registerReceiver(mediaReceiver, filter);
    }

    private void unregisterMediaReceiver() {
        try {
            activity.unregisterReceiver(mediaReceiver);
        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
    }

    private void subscribeAdapter() {
        disposable.add(viewModel.childNodes
                .doOnNext((childList) -> {
                    if (binding.swipeContainer.isRefreshing())
                        binding.swipeContainer.setRefreshing(false);
                })
                .subscribe(adapter::submitList));
    }

    private final Observable.OnPropertyChangedCallback currentDirCallback =
            new Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(Observable sender, int propertyId) {
                    binding.storageMenuTextView.setText(viewModel.curDir.get(), false);
                }
            };

    private void showSendErrorDialog(Exception e) {
        var action = FileManagerFragmentDirections
                .actionErrorReportDialog(getString(R.string.error_open_dir))
                .setException(e);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void permissionDeniedToast() {
        Snackbar.make(binding.coordinatorLayout,
                        R.string.permission_denied,
                        Snackbar.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            filesListState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_LIST_FILES_STATE, filesListState);
        }
        outState.putInt(TAG_SPINNER_POS, storageMenuPos);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            filesListState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (filesListState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(filesListState);
        }
    }

    @Override
    public void onItemClicked(FileManagerNode item) {
        if (item.getName().equals(FileManagerNode.PARENT_DIR)) {
            try {
                viewModel.upToParentDirectory();

            } catch (SecurityException e) {
                permissionDeniedToast();
            }
            return;
        }

        if (item.getType() == FileManagerNode.Type.DIR) {
            try {
                viewModel.openDirectory(item.getName());

            } catch (SecurityException e) {
                permissionDeniedToast();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                showSendErrorDialog(e);
            }

        } else if (item.getType() == FileManagerNode.Type.FILE &&
                viewModel.config.showMode == FileManagerConfig.Mode.FILE_CHOOSER) {
            saveCurDirectoryPath();
            returnFileUri(item.getName());
        }
    }

    private void refreshDir() {
        binding.swipeContainer.setRefreshing(true);
        viewModel.refreshCurDirectory();
    }

    private void saveCurDirectoryPath() {
        String path = viewModel.curDir.get();
        if (path == null)
            return;

        String keyFileManagerLastDir = getString(R.string.pref_key_filemanager_last_dir);
        if (!pref.getString(keyFileManagerLastDir, "").equals(path))
            pref.edit().putString(keyFileManagerLastDir, path).apply();
    }

    private boolean onMenuItemClickListener(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.home_menu) {
            openHomeDirectory();
        } else if (itemId == R.id.create_menu) {
            showInputNameDialog();
        } else if (itemId == R.id.go_to_folder_menu) {
            goToDirectoryDialog();
        }

        return true;
    }

    private void openHomeDirectory() {
        String path = SystemFacadeHelper.getFileSystemFacade(activity.getApplicationContext())
                .getUserDirPath();
        goToDirectory(path);
    }

    private void goToDirectory(String path) {
        if (!TextUtils.isEmpty(path)) {
            try {
                viewModel.jumpToDirectory(path);

            } catch (SecurityException e) {
                permissionDeniedToast();
            }
        } else {
            Snackbar.make(
                    binding.coordinatorLayout,
                    R.string.error_open_dir,
                    Snackbar.LENGTH_SHORT
            ).show();
        }
    }

    private void goToDirectoryDialog() {
        var action = FileManagerFragmentDirections
                .actionGoToFolderDialog(KEY_GO_TO_FOLDER_DIALOG_REQUEST);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void returnDirectoryUri() {
        var bundle = new Bundle();
        try {
            bundle.putParcelable(
                    KEY_RESULT,
                    new Result(viewModel.getCurDirectoryUri(), viewModel.config)
            );
        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void createFile(boolean replace) {
        if (!checkFileNameField()) {
            return;
        }

        Editable editable = binding.fileName.getText();
        String fileName = (editable == null ? null : editable.toString());
        if (!replace && viewModel.fileExists(fileName)) {
            new MaterialAlertDialogBuilder(activity)
                    .setIcon(R.drawable.ic_file_24px)
                    .setTitle(R.string.replace_file)
                    .setMessage(R.string.error_file_exists)
                    .setPositiveButton(R.string.replace, (dialog, which) -> {
                        createFile(true);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        var bundle = new Bundle();
        try {
            bundle.putParcelable(
                    KEY_RESULT,
                    new Result(viewModel.createFile(fileName), viewModel.config)
            );
        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void returnFileUri(String fileName) {
        var bundle = new Bundle();
        try {
            bundle.putParcelable(
                    KEY_RESULT,
                    new Result(viewModel.getFileUri(fileName), viewModel.config)
            );
        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private boolean checkFileNameField() {
        if (TextUtils.isEmpty(binding.fileName.getText())) {
            binding.layoutFileName.setErrorEnabled(true);
            binding.layoutFileName.setError(getString(R.string.file_name_is_empty));
            binding.layoutFileName.requestFocus();

            return false;
        }

        binding.layoutFileName.setErrorEnabled(false);
        binding.layoutFileName.setError(null);

        return true;
    }

    final synchronized void reloadSpinner() {
        var storageList = viewModel.regenerateStorageList();
        storageMenuPos = getStorageMenuPos(storageList);
        binding.storageMenuTextView.setSelection(storageMenuPos);

        ((MaterialAutoCompleteTextView) binding.storageMenuTextView)
                .setSimpleItems(buildSpinnerMenuItems(storageList));

        viewModel.refreshCurDirectory();
    }

    private int getStorageMenuPos(List<FileManagerViewModel.StorageItem> items) {
        for (int i = 0; i < items.size(); i++) {
            String curDir = viewModel.curDir.get();
            if (curDir != null && curDir.startsWith(items.get(i).path())) {
                return i;
            }
        }

        return -1;
    }

    private String[] buildSpinnerMenuItems(List<FileManagerViewModel.StorageItem> items) {
        return items.stream().map((item) -> {
            var nameAndSizeTemplate = activity.getString(R.string.storage_name_and_size);
            var nameAndSize = String.format(nameAndSizeTemplate, item.name(),
                    Formatter.formatFileSize(activity, item.size()));
            return nameAndSize + "\n" + item.path();
        }).toArray(String[]::new);
    }

    /**
     * The receiver for mount and eject actions of removable storage.
     */

    private static class MediaReceiver extends BroadcastReceiver {
        WeakReference<FileManagerFragment> dialog;

        MediaReceiver(FileManagerFragment dialog) {
            this.dialog = new WeakReference<>(dialog);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (dialog.get() != null)
                dialog.get().reloadSpinner();
        }
    }

    public record Result(@Nullable Uri uri, FileManagerConfig config) implements Parcelable {
        public Result(Parcel source) {
            this(source.readParcelable(Uri.class.getClassLoader()),
                    source.readParcelable(FileManagerConfig.class.getClassLoader()));
        }

        public static final Creator<Result> CREATOR =
                new Creator<>() {
                    @Override
                    public Result createFromParcel(Parcel source) {
                        return new Result(source);
                    }

                    @Override
                    public Result[] newArray(int size) {
                        return new Result[size];
                    }
                };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(uri, flags);
            dest.writeParcelable(config, flags);
        }
    }
}