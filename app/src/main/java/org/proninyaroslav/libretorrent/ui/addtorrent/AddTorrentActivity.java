/*
 * Copyright (C) 2016-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.Observable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import org.proninyaroslav.libretorrent.BR;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FetchLinkException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.NoFilesSelectedException;
import org.proninyaroslav.libretorrent.core.exception.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ActivityAddTorrentBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.PermissionDeniedDialog;
import org.proninyaroslav.libretorrent.ui.PermissionManager;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportDialog;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/*
 * The dialog for adding torrent. The parent window.
 */

public class AddTorrentActivity extends AppCompatActivity
{
    private static final String TAG = AddTorrentActivity.class.getSimpleName();

    public static final String TAG_URI = "uri";

    private static final String TAG_ERR_REPORT_DIALOG = "io_err_report_dialog";
    private static final String TAG_DECODE_EXCEPT_DIALOG = "decode_except_dialog";
    private static final String TAG_FETCH_EXCEPT_DIALOG = "fetch_except_dialog";
    private static final String TAG_OUT_OF_MEMORY_DIALOG = "out_of_memory_dialog";
    private static final String TAG_ILLEGAL_ARGUMENT_DIALOG = "illegal_argument_dialog";
    private static final String TAG_ADD_ERROR_DIALOG = "add_error_dialog";
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";

    private ActivityAddTorrentBinding binding;
    private AddTorrentViewModel viewModel;
    private AddTorrentPagerAdapter adapter;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private ErrorReportDialog errReportDialog;
    private CompositeDisposable disposable = new CompositeDisposable();
    private boolean showAddButton;
    private PermissionDeniedDialog permDeniedDialog;
    private SharedPreferences localPref;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_torrent);
        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(AddTorrentViewModel.class);
        permissionManager = new PermissionManager(this, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                var fm = getSupportFragmentManager();
                if (!isGranted && shouldRequestStoragePermission) {
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
                }
            }

            @Override
            public void onNotificationResult(boolean isGranted, boolean shouldRequestNotificationPermission) {
                permissionManager.setDoNotAskNotifications(!isGranted);
                if (isGranted) {
                    viewModel.restartForegroundNotification();
                }
            }
        });

        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);
        errReportDialog = (ErrorReportDialog)getSupportFragmentManager().findFragmentByTag(TAG_ERR_REPORT_DIALOG);
        localPref = PreferenceManager.getDefaultSharedPreferences(this);
        fillMutableParams();

        if (!permissionManager.checkPermissions() && permDeniedDialog == null) {
            permissionManager.requestPermissions();
        }
        Utils.showManageAllFilesWarningDialog(getApplicationContext(), getSupportFragmentManager());

        initLayout();
        observeDecodeState();
    }

    private void fillMutableParams() {
        viewModel.mutableParams.setSequentialDownload(
                localPref.getBoolean(
                        getString(R.string.add_torrent_sequential_download),
                        false
                )
        );
        viewModel.mutableParams.setStartAfterAdd(
                localPref.getBoolean(
                        getString(R.string.add_torrent_start_after_add),
                        true
                )
        );
        viewModel.mutableParams.setIgnoreFreeSpace(
                localPref.getBoolean(
                        getString(R.string.add_torrent_ignore_free_space),
                        false
                )
        );
        viewModel.mutableParams.setFirstLastPiecePriority(
                localPref.getBoolean(
                        getString(R.string.add_torrent_download_first_last_pieces),
                        false
                )
        );
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        unsubscribeParamsChanged();
        disposable.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
        subscribeParamsChanged();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposable.add(d);
    }

    private void subscribeParamsChanged() {
        viewModel.mutableParams.addOnPropertyChangedCallback(onMutableParamsChanged);
    }

    private void unsubscribeParamsChanged() {
        viewModel.mutableParams.removeOnPropertyChangedCallback(onMutableParamsChanged);
    }

    private final Observable.OnPropertyChangedCallback onMutableParamsChanged =
            new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            if (propertyId == BR.sequentialDownload) {
                localPref.edit()
                        .putBoolean(
                                getString(R.string.add_torrent_sequential_download),
                                viewModel.mutableParams.isSequentialDownload()
                        )
                        .apply();
            } else if (propertyId == BR.startAfterAdd) {
                localPref.edit()
                        .putBoolean(
                                getString(R.string.add_torrent_start_after_add),
                                viewModel.mutableParams.isStartAfterAdd()
                        )
                        .apply();
            } else if (propertyId == BR.ignoreFreeSpace) {
                localPref.edit()
                        .putBoolean(
                                getString(R.string.add_torrent_ignore_free_space),
                                viewModel.mutableParams.isIgnoreFreeSpace()
                        )
                        .apply();
            } else if (propertyId == BR.firstLastPiecePriority) {
                localPref.edit()
                        .putBoolean(
                                getString(R.string.add_torrent_download_first_last_pieces),
                                viewModel.mutableParams.isFirstLastPiecePriority()
                        )
                        .apply();
            }
        }
    };

    private void handleAlertDialogEvent(BaseAlertDialog.Event event) {
        if (event.dialogTag == null) {
            return;
        }
        if (event.dialogTag.equals(TAG_ERR_REPORT_DIALOG)) {
            switch (event.type) {
                case POSITIVE_BUTTON_CLICKED:
                    if (errReportDialog != null) {
                        Dialog dialog = errReportDialog.getDialog();
                        if (dialog != null) {
                            TextInputEditText editText = dialog.findViewById(R.id.comment);
                            Editable e = editText.getText();
                            String comment = (e == null ? null : e.toString());

                            Utils.reportError(viewModel.errorReport, comment);
                            errReportDialog.dismiss();
                        }
                    }
                    finish();
                    break;
                case NEGATIVE_BUTTON_CLICKED:
                    if (errReportDialog != null) {
                        errReportDialog.dismiss();
                    }
                    finish();
                    break;
            }
        } else if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
            if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                permDeniedDialog.dismiss();
            }
            if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                permissionManager.requestPermissions();
            }
            if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                permissionManager.setDoNotAskStorage(true);
            }
        }
    }

    private void initLayout()
    {
        binding.toolbar.setTitle(R.string.add_torrent_title);
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        /* Disable elevation for portrait mode */
        if (!Utils.isTwoPane(this)) {
            binding.toolbar.setElevation(0);
        }

        adapter = new AddTorrentPagerAdapter(this);
        binding.viewpager.setAdapter(adapter);
        binding.viewpager.setOffscreenPageLimit(AddTorrentPagerAdapter.NUM_FRAGMENTS);
        new TabLayoutMediator(binding.tabLayout, binding.viewpager,
                (tab, position) -> {
                    switch (position) {
                        case AddTorrentPagerAdapter.INFO_FRAG_POS:
                            tab.setText(R.string.torrent_info);
                            break;
                        case AddTorrentPagerAdapter.FILES_FRAG_POS:
                            tab.setText(R.string.torrent_files);
                            break;
                    }
                }
        ).attach();
    }

    private void observeDecodeState()
    {
        viewModel.getDecodeState().observe(this, (state) -> {
            switch (state.status) {
                case UNKNOWN:
                    Uri uri = getUri();
                    if (uri != null)
                        viewModel.startDecode(uri);
                    break;
                case DECODE_TORRENT_FILE:
                case FETCHING_HTTP:
                case FETCHING_MAGNET:
                    onStartDecode(state.status == AddTorrentViewModel.Status.DECODE_TORRENT_FILE);
                    break;
                case FETCHING_HTTP_COMPLETED:
                case DECODE_TORRENT_COMPLETED:
                case FETCHING_MAGNET_COMPLETED:
                case ERROR:
                    onStopDecode(state.error);
                    break;
            }
        });
    }

    private void onStartDecode(boolean isTorrentFile)
    {
        binding.progress.setVisibility(View.VISIBLE);
        showAddButton = !isTorrentFile;
        invalidateOptionsMenu();
    }

    private void onStopDecode(Throwable e)
    {
        binding.progress.setVisibility(View.GONE);

        if (e != null) {
            handleDecodeException(e);
            return;
        }

        viewModel.makeFileTree();
        showAddButton = true;
        invalidateOptionsMenu();
    }

    private Uri getUri()
    {
        Intent i = getIntent();
        /* Implicit intent with path to torrent file, http or magnet link */
        if (i.getData() != null)
            return i.getData();
        else if (!TextUtils.isEmpty(i.getStringExtra(Intent.EXTRA_TEXT)))
            return Uri.parse(i.getStringExtra(Intent.EXTRA_TEXT));
        else
            return i.getParcelableExtra(TAG_URI);
    }

    private void addTorrent()
    {
        String name = viewModel.mutableParams.getName();
        if (TextUtils.isEmpty(name)) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.error_empty_name,
                    Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        try {
            if (viewModel.addTorrent())
                finish();

        } catch (Exception e) {
            if (e instanceof TorrentAlreadyExistsException) {
                Toast.makeText(getApplication(),
                        R.string.torrent_exist,
                        Toast.LENGTH_SHORT)
                        .show();
                finish();

            } else {
                handleAddException(e);
            }
        }
    }

    private void handleAddException(Throwable e)
    {
        if (e instanceof NoFilesSelectedException) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.error_no_files_selected,
                    Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        if (e instanceof FreeSpaceException) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.error_free_space,
                    Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        Log.e(TAG, Log.getStackTraceString(e));
        if (e instanceof FileNotFoundException) {
            showAddErrorDialog(getApplication().getString(R.string.error_file_not_found_add_torrent), null);
        } else if (e instanceof IOException) {
            showAddErrorDialog(getApplication().getString(R.string.error_io_add_torrent), null);
        } else {
            showAddErrorDialog(getApplication().getString(R.string.error_add_torrent), e);
        }
    }

    private void showAddErrorDialog(String message, Throwable e)
    {
        FragmentManager fm = getSupportFragmentManager();
        if (e != null) {
            viewModel.errorReport = e;
            if (fm.findFragmentByTag(TAG_ERR_REPORT_DIALOG) == null) {
                errReportDialog = ErrorReportDialog.newInstance(
                        getString(R.string.error),
                        message,
                        Log.getStackTraceString(e)
                );

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errReportDialog, TAG_ERR_REPORT_DIALOG);
                ft.commitAllowingStateLoss();
            }
        } else if (fm.findFragmentByTag(TAG_ADD_ERROR_DIALOG) == null) {
            BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    message,
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    false
                );

            FragmentTransaction ft = fm.beginTransaction();
            ft.add(errDialog, TAG_ADD_ERROR_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    public void handleDecodeException(Throwable e)
    {
        if (e == null)
            return;

        Log.e(TAG, Log.getStackTraceString(e));
        FragmentManager fm = getSupportFragmentManager();

        if (e instanceof DecodeException) {
            if (fm.findFragmentByTag(TAG_DECODE_EXCEPT_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_decode_torrent),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        false);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_DECODE_EXCEPT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof FetchLinkException) {
            if (fm.findFragmentByTag(TAG_FETCH_EXCEPT_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_fetch_link),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        false);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_FETCH_EXCEPT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof IllegalArgumentException) {
            if (fm.findFragmentByTag(TAG_ILLEGAL_ARGUMENT_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_invalid_link_or_path),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        false);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_ILLEGAL_ARGUMENT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof IOException) {
            viewModel.errorReport = e;
            if (fm.findFragmentByTag(TAG_ERR_REPORT_DIALOG) == null) {
                errReportDialog = ErrorReportDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_io_torrent),
                        Log.getStackTraceString(e));

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errReportDialog, TAG_ERR_REPORT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof OutOfMemoryError) {
            if (fm.findFragmentByTag(TAG_OUT_OF_MEMORY_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.file_is_too_large_error),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        false);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_OUT_OF_MEMORY_DIALOG);
                ft.commitAllowingStateLoss();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.add_torrent, menu);

        MenuItem add = menu.findItem(R.id.add_torrent_dialog_add_menu);
        if (add != null)
            add.setVisible(showAddButton);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.add_torrent_dialog_add_menu) {
            addTorrent();
        }

        return true;
    }

    @Override
    public void finish()
    {
        viewModel.finish();

        super.finish();
    }

    @Override
    public void onBackPressed()
    {
        finish();
    }
}
