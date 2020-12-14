/*
 * Copyright (C) 2017, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class RequestPermissions extends AppCompatActivity
{
    private static final String TAG = RequestPermissions.class.getSimpleName();

    private static final String TAG_PERM_DIALOG = "perm_dialog";
    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private boolean permDialogIsShow = false;
    private BaseAlertDialog permDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposable = new CompositeDisposable();

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        dialogViewModel = new ViewModelProvider(this).get(BaseAlertDialog.SharedViewModel.class);
        permDialog = (BaseAlertDialog)getSupportFragmentManager().findFragmentByTag(TAG_PERM_DIALOG);

        if (savedInstanceState != null)
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);

        /* Prevents duplication permission dialog */
        if (!permDialogIsShow) {
            permDialogIsShow = true;
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
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
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_PERM_DIALOG) || permDialog == null)
                        return;
                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            permDialog.dismiss();
                            setResult(RESULT_OK);
                            finish();
                            overridePendingTransition(0, 0);
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            permDialog.dismiss();
                            ActivityCompat.requestPermissions(RequestPermissions.this,
                                    PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                            break;
                        case DIALOG_SHOWN:
                            if (permDialog.getDialog() != null)
                                permDialog.getDialog().setCanceledOnTouchOutside(false);
                            break;
                    }
                });
        disposable.add(d);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setResult(RESULT_OK);
                finish();
                overridePendingTransition(0, 0);

            } else if (getSupportFragmentManager().findFragmentByTag(TAG_PERM_DIALOG) == null) {
                permDialog = BaseAlertDialog.newInstance(
                        getString(R.string.perm_denied_title),
                        getString(R.string.perm_denied_warning),
                        0,
                        getString(R.string.yes),
                        getString(R.string.no),
                        null,
                        false);

                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(permDialog, TAG_PERM_DIALOG);
                ft.commitAllowingStateLoss();
            }
        }
    }
}
