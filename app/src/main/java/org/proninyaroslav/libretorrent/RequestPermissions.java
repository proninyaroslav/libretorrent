/*
 * Copyright (C) 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;

public class RequestPermissions extends AppCompatActivity
        implements
        BaseAlertDialog.OnClickListener,
        BaseAlertDialog.OnDialogShowListener
{
    @SuppressWarnings("unused")
    private static final String TAG = RequestPermissions.class.getSimpleName();

    private static final String TAG_PERM_DIALOG = "perm_dialog";
    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private boolean permDialogIsShow = false;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        if (savedInstanceState != null) {
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);
        }

        /* Prevents duplication permission dialog */
        if (!permDialogIsShow) {
            permDialogIsShow = true;
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setResult(RESULT_OK);
                finish();
                overridePendingTransition(0, 0);

            } else if (getFragmentManager().findFragmentByTag(TAG_PERM_DIALOG) == null) {
                BaseAlertDialog permDialog = BaseAlertDialog.newInstance(
                        getString(R.string.perm_denied_title),
                        getString(R.string.perm_denied_warning),
                        0,
                        getString(R.string.yes),
                        getString(R.string.no),
                        null,
                        this);

                permDialog.show(getFragmentManager(), TAG_PERM_DIALOG);
            }
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        setResult(RESULT_OK);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        ActivityCompat.requestPermissions(RequestPermissions.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }

    @Override
    public void onShow(AlertDialog dialog)
    {
        dialog.setCanceledOnTouchOutside(false);
    }
}
