/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.acra.dialog.BaseCrashReportDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;

public class ErrorReportActivity extends BaseCrashReportDialog
        implements
        ErrorReportAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_ERROR_DIALOG = "error_dialog";

    @Override
    protected void init(@Nullable Bundle savedInstanceState)
    {
        super.init(savedInstanceState);

        if (getFragmentManager().findFragmentByTag(TAG_ERROR_DIALOG) == null) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.app_error_occurred),
                    Log.getStackTraceString(getException()),
                    this);

            errDialog.show(getFragmentManager(), TAG_ERROR_DIALOG);
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        String comment = "";
        if (v != null) {
            EditText editText = (EditText)v.findViewById(R.id.comment);
            if (editText != null) {
                comment = editText.getText().toString();
            }
        }

        sendCrash(comment, "");

        finish();
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        cancelReports();

        finish();
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }
}
