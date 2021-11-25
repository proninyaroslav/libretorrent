/*
 * Copyright (C) 2018-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonSyntaxException;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.storage.FeedRepositoryImpl;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.detailtorrent.BlankFragment;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportDialog;
import org.proninyaroslav.libretorrent.ui.feeditems.FeedItemsActivity;
import org.proninyaroslav.libretorrent.ui.feeditems.FeedItemsFragment;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FeedActivity extends AppCompatActivity implements FragmentCallback
{
    private static final String TAG = FeedActivity.class.getSimpleName();

    private static final String TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG = "backup_feeds_error_report_dialog";
    private static final String TAG_RESTORE_FEEDS_ERROR_DIALOG = "restore_feeds_error_dialog";
    private static final String TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG = "restore_feeds_error_report_dialog";

    /* Android data binding doesn't work with layout aliases */
    private Toolbar toolbar;

    private FeedViewModel viewModel;
    private MsgFeedViewModel msgViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private ErrorReportDialog backupFeedsReportDialog, restoreFeedsReportDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(FeedViewModel.class);
        msgViewModel = provider.get(MsgFeedViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        setContentView(R.layout.activity_feed);
        initLayout();

        FragmentManager fm = getSupportFragmentManager();
        backupFeedsReportDialog = (ErrorReportDialog)fm.findFragmentByTag(TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG);
        restoreFeedsReportDialog = (ErrorReportDialog)fm.findFragmentByTag(TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG);
    }

    private void initLayout()
    {
        showBlankFragment();
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.feed);
        toolbar.inflateMenu(R.menu.feed);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener((v) -> finish());
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
        subscribeMsgViewModel();
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null)
                        return;

                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG) &&
                                    backupFeedsReportDialog != null) {
                                sendErrorReport(backupFeedsReportDialog.getDialog());
                                backupFeedsReportDialog.dismiss();

                            } else if (event.dialogTag.equals(TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG) &&
                                    restoreFeedsReportDialog!= null) {
                                sendErrorReport(restoreFeedsReportDialog.getDialog());
                                restoreFeedsReportDialog.dismiss();
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG) &&
                                    backupFeedsReportDialog!= null) {
                                backupFeedsReportDialog.dismiss();

                            } else if (event.dialogTag.equals(TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG) &&
                                    restoreFeedsReportDialog!= null) {
                                restoreFeedsReportDialog.dismiss();
                            }
                    }
                });
        disposables.add(d);
    }

    private void subscribeMsgViewModel()
    {
        disposables.add(msgViewModel.observeFeedItemsOpened()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showFeedItems));

        disposables.add(msgViewModel.observeFeedItemsClosed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> showBlankFragment()));

        disposables.add(msgViewModel.observeFeedsDeleted()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((ids) -> {
                    FeedItemsFragment f = getCurrentFeedItemsFragment();
                    if (f != null && ids.contains(f.getFeedId()))
                        showBlankFragment();
                }));
    }

    private void showBlankFragment()
    {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_feed_channel));
            fm.beginTransaction()
                    .replace(R.id.feed_items_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    private void showFeedItems(long feedId)
    {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            FeedItemsFragment items = FeedItemsFragment.newInstance(feedId);
            Fragment fragment = fm.findFragmentById(R.id.feed_items_fragmentContainer);

            if (fragment instanceof FeedItemsFragment) {
                long oldId = ((FeedItemsFragment)fragment).getFeedId();
                if (feedId == oldId)
                    return;
            }
            fm.beginTransaction()
                    .replace(R.id.feed_items_fragmentContainer, items)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

        } else {
            Intent i = new Intent(this, FeedItemsActivity.class);
            i.putExtra(FeedItemsActivity.TAG_FEED_ID, feedId);
            startActivity(i);
        }
    }

    private FeedItemsFragment getCurrentFeedItemsFragment()
    {
        if (!Utils.isTwoPane(this))
            return null;

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.feed_items_fragmentContainer);

        return (fragment instanceof FeedItemsFragment ? (FeedItemsFragment)fragment : null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        toolbar.inflateMenu(R.menu.feed);
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_feed_channel_menu) {
            viewModel.refreshAllFeeds();
        } else if (itemId == R.id.backup_feed_channels_menu) {
            backupFeedsChooseDialog();
        } else if (itemId == R.id.restore_feed_channels_backup_menu) {
            restoreFeedsChooseDialog();
        }

        return true;
    }

    private void backupFeedsChooseDialog()
    {
        Intent i = new Intent(this, FileManagerDialog.class);
        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(getApplicationContext());
        FileManagerConfig config = new FileManagerConfig(fs.getUserDirPath(),
                null,
                FileManagerConfig.SAVE_FILE_MODE);
        config.fileName = "Feeds-" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())
                .format(new Date(System.currentTimeMillis())) +
                "." + FeedRepositoryImpl.SERIALIZE_FILE_FORMAT;
        config.mimeType = FeedRepositoryImpl.SERIALIZE_MIME_TYPE;

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        backupFeedsChoose.launch(i);
    }

    private void restoreFeedsChooseDialog()
    {
        Intent i = new Intent(this, FileManagerDialog.class);
        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(getApplicationContext());
        FileManagerConfig config = new FileManagerConfig(fs.getUserDirPath(),
                getString(R.string.feeds_backup_selection_dialog_title),
                FileManagerConfig.FILE_CHOOSER_MODE);
        config.highlightFileTypes = new ArrayList<>();
        config.highlightFileTypes.add(FeedRepositoryImpl.SERIALIZE_FILE_FORMAT);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        restoreFeedsBackupChoose.launch(i);
    }

    private void backupFeedsErrorDialog(Throwable e)
    {
        viewModel.errorReport = e;
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG) == null) {
            backupFeedsReportDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_backup_feeds),
                    (e != null ? Log.getStackTraceString(e) : null));

            backupFeedsReportDialog.show(fm, TAG_BACKUP_FEEDS_ERROR_REPORT_DIALOG);
        }
    }

    private void restoreFeedsBackupErrorDialog(Throwable e)
    {
        FragmentManager fm = getSupportFragmentManager();
        if (e instanceof JsonSyntaxException) {
            if (fm.findFragmentByTag(TAG_RESTORE_FEEDS_ERROR_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_import_invalid_format),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        true);
                errDialog.show(fm, TAG_RESTORE_FEEDS_ERROR_DIALOG);
            }
        } else {
            viewModel.errorReport = e;
            if (fm.findFragmentByTag(TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG) == null) {
                restoreFeedsReportDialog = ErrorReportDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_restore_feeds_backup),
                        (e != null ? Log.getStackTraceString(e) : null));

                restoreFeedsReportDialog.show(fm, TAG_RESTORE_FEEDS_ERROR_REPORT_DIALOG);
            }
        }
    }

    private void sendErrorReport(Dialog errDialog)
    {
        if (errDialog == null)
            return;

        TextInputEditText editText = errDialog.findViewById(R.id.comment);
        Editable e = editText.getText();
        String comment = (e == null ? null : e.toString());

        Utils.reportError(viewModel.errorReport, comment);
    }

    private void backupFeeds(Uri file)
    {
        disposables.add(Completable.fromCallable(() -> {
            viewModel.saveFeedsSync(file);
            return true;
        }).subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(() -> Toast.makeText(this,
                  R.string.backup_feeds_successfully,
                  Toast.LENGTH_SHORT)
                  .show(), this::restoreFeedsBackupErrorDialog));
    }

    private void restoreFeedsBackup(Uri file)
    {
        disposables.add(Observable.fromCallable(() -> viewModel.restoreFeedsSync(file))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((feedIdList) -> {
                        Toast.makeText(this,
                                R.string.restore_feeds_backup_successfully,
                                Toast.LENGTH_SHORT)
                                .show();
                        viewModel.refreshFeeds(feedIdList);
                    }, this::backupFeedsErrorDialog));
    }

    final ActivityResultLauncher<Intent> backupFeedsChoose = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != Activity.RESULT_OK)
                    return;

                if (data == null || data.getData() == null) {
                    backupFeedsErrorDialog(null);
                    return;
                }

                backupFeeds(data.getData());
            }
    );

    final ActivityResultLauncher<Intent> restoreFeedsBackupChoose = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != Activity.RESULT_OK)
                    return;

                if (data == null || data.getData() == null) {
                    restoreFeedsBackupErrorDialog(null);
                    return;
                }

                restoreFeedsBackup(data.getData());
            }
    );

    @Override
    public void onFragmentFinished(@NonNull Fragment f, Intent intent,
                                   @NonNull ResultCode code)
    {
        if (f instanceof FeedItemsFragment && Utils.isTwoPane(this))
            msgViewModel.feedItemsClosed();
    }
}
