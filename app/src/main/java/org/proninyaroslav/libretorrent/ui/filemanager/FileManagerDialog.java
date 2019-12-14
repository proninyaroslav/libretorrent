/*
 * Copyright (C) 2016, 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ActivityFilemanagerDialogBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportDialog;

import java.io.IOException;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/*
 * The simple dialog for navigation and select directory.
 *
 * Returns Uri that represent filesystem path (file:// scheme) or
 * Storage Access Framework (SAF) path (content:// scheme) if a system file manager was open.
 *
 * For different show modes returns the following values:
 *  - FILE_CHOOSER_MODE: Uri of the selected file (filesystem or SAF)
 *  - DIR_CHOOSER_MODE: Uri of the selected folder
 *                      (filesystem or SAF; SAF requires Android API >= 21)
 *  - SAVE_FILE_MODE: Uri of the created file (filesystem or SAF)
 */

public class FileManagerDialog extends AppCompatActivity
        implements FileManagerAdapter.ViewHolder.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = FileManagerDialog.class.getSimpleName();

    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String TAG_INPUT_NAME_DIALOG = "input_name_dialog";
    private static final String TAG_ERR_CREATE_DIR = "err_create_dir";
    private static final String TAG_ERROR_OPEN_DIR_DIALOG = "error_open_dir_dialog";
    private static final String TAG_REPLACE_FILE_DIALOG = "replace_file_dialog";
    private static final String TAG_ERROR_REPORT_DIALOG = "error_report_dialog";
    private static final int SAF_CREATE_FILE_REQUEST_CODE = 1;
    private static final int SAF_OPEN_FILE_REQUEST_CODE = 2;
    private static final int SAF_OPEN_FILE_TREE_REQUEST_CODE = 3;

    public static final String TAG_CONFIG = "config";

    private ActivityFilemanagerDialogBinding binding;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable filesListState;
    private FileManagerAdapter adapter;

    private FileManagerViewModel viewModel;
    private BaseAlertDialog inputNameDialog;
    private ErrorReportDialog errorReportDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposable = new CompositeDisposable();
    private SharedPreferences pref;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (!intent.hasExtra(TAG_CONFIG)) {
            Log.e(TAG, "To work need to set intent with FileManagerConfig in startActivity()");

            finish();
        }

        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(getApplicationContext());
        pref = PreferenceManager.getDefaultSharedPreferences(this);

        String startDir = pref.getString(getString(R.string.pref_key_filemanager_last_dir), fs.getDefaultDownloadPath());
        FileManagerViewModelFactory factory = new FileManagerViewModelFactory(this.getApplicationContext(),
                intent.getParcelableExtra(TAG_CONFIG), startDir);
        viewModel = ViewModelProviders.of(this, factory).get(FileManagerViewModel.class);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_filemanager_dialog);
        binding.setEnableSystemManagerButton(!viewModel.config.disableSystemFileManager &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
        binding.setViewModel(viewModel);

        FragmentManager fm = getSupportFragmentManager();
        inputNameDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_INPUT_NAME_DIALOG);
        errorReportDialog = (ErrorReportDialog)fm.findFragmentByTag(TAG_ERROR_REPORT_DIALOG);
        dialogViewModel = ViewModelProviders.of(this).get(BaseAlertDialog.SharedViewModel.class);

        String title = viewModel.config.title;
        if (TextUtils.isEmpty(title)) {
            switch (viewModel.config.showMode) {
                case FileManagerConfig.DIR_CHOOSER_MODE:
                    binding.toolbar.setTitle(R.string.dir_chooser_title);
                    break;
                case FileManagerConfig.FILE_CHOOSER_MODE:
                    binding.toolbar.setTitle(R.string.file_chooser_title);
                    break;
                case FileManagerConfig.SAVE_FILE_MODE:
                    binding.toolbar.setTitle(R.string.save_file);
                    break;
            }

        } else {
            binding.toolbar.setTitle(title);
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.addFab.setOnClickListener((v) -> showInputNameDialog());
        binding.openSystemFilemanagerFab.setOnClickListener((v) -> showSAFDialog());

        if (savedInstanceState == null)
            binding.fileName.setText(viewModel.config.fileName);
        binding.fileName.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutFileName.setErrorEnabled(false);
                binding.layoutFileName.setError(null);
            }
        });

        layoutManager = new LinearLayoutManager(this);
        binding.fileList.setLayoutManager(layoutManager);
        binding.fileList.setItemAnimator(new DefaultItemAnimator());

        adapter = new FileManagerAdapter(viewModel.config.highlightFileTypes, this);
        binding.fileList.setAdapter(adapter);

        binding.swipeContainer.setOnRefreshListener(this::refreshDir);
    }

    private void showInputNameDialog()
    {
        if (getSupportFragmentManager().findFragmentByTag(TAG_INPUT_NAME_DIALOG) == null) {
            inputNameDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_new_folder_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            inputNameDialog.show(getSupportFragmentManager(), TAG_INPUT_NAME_DIALOG);
        }
    }

    private void showSAFDialog()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return;

        String mimeType = viewModel.config.mimeType;
        Intent i;
        int requestCode;

        switch (viewModel.config.showMode) {
            case FileManagerConfig.SAVE_FILE_MODE:
                if (!checkFileNameField())
                    return;

                i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mimeType == null ? "application/octet-stream" : mimeType);
                i.putExtra(Intent.EXTRA_TITLE, binding.fileName.getText().toString());
                requestCode = SAF_CREATE_FILE_REQUEST_CODE;
                break;
            case FileManagerConfig.DIR_CHOOSER_MODE:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    Snackbar.make(binding.coordinatorLayout,
                            R.string.device_does_not_support_this_feature,
                            Snackbar.LENGTH_SHORT)
                            .show();
                    return;
                }
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                requestCode = SAF_OPEN_FILE_TREE_REQUEST_CODE;
                break;
            case FileManagerConfig.FILE_CHOOSER_MODE:
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mimeType == null ? "*/*" : mimeType);
                requestCode = SAF_OPEN_FILE_REQUEST_CODE;
                break;
            default:
                return;
        }

        i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(i, requestCode);

        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.system_file_manager_not_found,
                    Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposable.clear();
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
        subscribeAdapter();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposable.add(d);
    }

    private void subscribeAdapter()
    {
        disposable.add(viewModel.childNodes
                .doOnNext((childList) -> {
                    if (binding.swipeContainer.isRefreshing())
                        binding.swipeContainer.setRefreshing(false);
                })
                .subscribe(adapter::submitList));
    }

    private void handleAlertDialogEvent(BaseAlertDialog.Event event)
    {
        switch (event.type) {
            case POSITIVE_BUTTON_CLICKED:
                if (event.dialogTag.equals(TAG_INPUT_NAME_DIALOG) && inputNameDialog != null) {
                    Dialog dialog = inputNameDialog.getDialog();
                    if (dialog != null) {
                        EditText nameField = dialog.findViewById(R.id.text_input_dialog);
                        String name = nameField.getText().toString();

                        if (!viewModel.createDirectory(name)) {
                            showCreateFolderErrDialog();
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
                    inputNameDialog.dismiss();

                } else if (event.dialogTag.equals(TAG_REPLACE_FILE_DIALOG)) {
                    createFile(true);
                } else if (event.dialogTag.equals(TAG_ERROR_REPORT_DIALOG) && errorReportDialog != null) {
                    Dialog dialog = errorReportDialog.getDialog();
                    if (dialog != null) {
                        TextInputEditText editText = dialog.findViewById(R.id.comment);
                        Editable e = editText.getText();
                        String comment = (e == null ? null : e.toString());

                        Utils.reportError(viewModel.errorReport, comment);
                        errorReportDialog.dismiss();
                    }
                }
                break;
            case NEGATIVE_BUTTON_CLICKED:
                if (event.dialogTag.equals(TAG_INPUT_NAME_DIALOG) && inputNameDialog != null)
                    inputNameDialog.dismiss();
                else if (event.dialogTag.equals(TAG_ERROR_REPORT_DIALOG) && errorReportDialog != null)
                    errorReportDialog.dismiss();
                break;
        }
    }

    private void showSendErrorDialog(Exception e)
    {
        viewModel.errorReport = e;
        if (getSupportFragmentManager().findFragmentByTag(TAG_ERROR_REPORT_DIALOG) == null) {
            errorReportDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_open_dir),
                    Log.getStackTraceString(e));

            errorReportDialog.show(getSupportFragmentManager(), TAG_ERROR_REPORT_DIALOG);
        }
    }

    private void permissionDeniedToast()
    {
        Snackbar.make(binding.coordinatorLayout,
                R.string.permission_denied,
                Snackbar.LENGTH_SHORT)
                .show();
    }

    private void showCreateFolderErrDialog()
    {
        if (getSupportFragmentManager().findFragmentByTag(TAG_ERR_CREATE_DIR) == null) {
            BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_dialog_new_folder),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            errDialog.show(getSupportFragmentManager(), TAG_ERR_CREATE_DIR);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        filesListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_FILES_STATE, filesListState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState != null)
            filesListState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (filesListState != null)
            layoutManager.onRestoreInstanceState(filesListState);
    }

    @Override
    public void onItemClicked(FileManagerNode item)
    {
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
            }  catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                showSendErrorDialog(e);
            }

        } else if (item.getType() == FileManagerNode.Type.FILE &&
                viewModel.config.showMode == FileManagerConfig.FILE_CHOOSER_MODE) {
            saveCurDirectoryPath();
            returnFileUri(item.getName());
        }
    }

    private void refreshDir()
    {
        binding.swipeContainer.setRefreshing(true);
        viewModel.refreshCurDirectory();
    }

    private void saveCurDirectoryPath()
    {
        String path = viewModel.curDir.get();
        if (path == null)
            return;

        String keyFileManagerLastDir = getString(R.string.pref_key_filemanager_last_dir);
        if (!pref.getString(keyFileManagerLastDir, "").equals(path))
            pref.edit().putString(keyFileManagerLastDir, path).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.filemanager, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);

        if (viewModel.config.showMode == FileManagerConfig.FILE_CHOOSER_MODE)
            menu.findItem(R.id.filemanager_ok_menu).setVisible(false);

        return true;
    }

    @Override
    public void onBackPressed()
    {
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.filemanager_home_menu:
                openHomeDirectory();
                break;
            case R.id.filemanager_ok_menu:
                saveCurDirectoryPath();
                if (viewModel.config.showMode == FileManagerConfig.SAVE_FILE_MODE)
                    createFile(false);
                else
                    returnDirectoryUri();
                break;
        }

        return true;
    }

    private void openHomeDirectory()
    {
        String path = SystemFacadeHelper.getFileSystemFacade(getApplicationContext())
                .getUserDirPath();
        if (!TextUtils.isEmpty(path)) {
            try {
                viewModel.jumpToDirectory(path);

            } catch (SecurityException e) {
                permissionDeniedToast();
            }
        } else {
            if (getSupportFragmentManager().findFragmentByTag(TAG_ERROR_OPEN_DIR_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_open_dir),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        true);

                errDialog.show(getSupportFragmentManager(), TAG_ERROR_OPEN_DIR_DIALOG);
            }
        }
    }

    private void returnDirectoryUri()
    {
        Intent i = new Intent();
        try {
            i.setData(viewModel.getCurDirectoryUri());

        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        setResult(RESULT_OK, i);
        finish();
    }

    private void createFile(boolean replace)
    {
        if (!checkFileNameField())
            return;

        Editable editable = binding.fileName.getText();
        String fileName = (editable == null ? null : editable.toString());
        if (!replace && viewModel.fileExists(fileName)) {
            showReplaceFileDialog();
            return;
        }

        Intent i = new Intent();
        try {
            i.setData(viewModel.createFile(fileName));

        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        setResult(RESULT_OK, i);
        finish();
    }

    private void returnFileUri(String fileName)
    {
        Intent i = new Intent();
        try {
            i.setData(viewModel.getFileUri(fileName));

        } catch (SecurityException e) {
            permissionDeniedToast();
            return;
        }
        setResult(RESULT_OK, i);
        finish();
    }

    private void showReplaceFileDialog()
    {
        if (getSupportFragmentManager().findFragmentByTag(TAG_REPLACE_FILE_DIALOG) == null) {
            BaseAlertDialog replaceFileDialog = BaseAlertDialog.newInstance(
                    getString(R.string.replace_file),
                    getString(R.string.error_file_exists),
                    0,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            replaceFileDialog.show(getSupportFragmentManager(), TAG_REPLACE_FILE_DIALOG);
        }
    }

    private boolean checkFileNameField()
    {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null)
            return;

        switch (requestCode) {
            case SAF_OPEN_FILE_TREE_REQUEST_CODE:
            case SAF_CREATE_FILE_REQUEST_CODE:
            case SAF_OPEN_FILE_REQUEST_CODE:
                viewModel.takeSafPermissions(data);

                Intent i = new Intent();
                i.setData(data.getData());
                setResult(RESULT_OK, i);
                finish();
                break;
        }
    }
}