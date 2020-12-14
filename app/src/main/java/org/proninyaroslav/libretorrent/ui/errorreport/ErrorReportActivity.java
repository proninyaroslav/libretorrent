/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.errorreport;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.acra.dialog.CrashReportDialogHelper;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;

import java.io.IOException;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class ErrorReportActivity extends AppCompatActivity
{
    private static final String TAG = ErrorReportActivity.class.getSimpleName();

    private static final String TAG_ERROR_DIALOG = "error_dialog";
    private ErrorReportDialog errDialog;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private CrashReportDialogHelper helper;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        helper = new CrashReportDialogHelper(this, getIntent());
        dialogViewModel = new ViewModelProvider(this).get(BaseAlertDialog.SharedViewModel.class);
        errDialog = (ErrorReportDialog)getSupportFragmentManager().findFragmentByTag(TAG_ERROR_DIALOG);

        if (errDialog == null) {
            errDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.app_error_occurred),
                    getStackTrace());

            errDialog.show(getSupportFragmentManager(), TAG_ERROR_DIALOG);
        }
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents().subscribe(this::handleAlertDialogEvent);
        disposables.add(d);
    }

    private void handleAlertDialogEvent(BaseAlertDialog.Event event)
    {
        if (event.dialogTag == null || !event.dialogTag.equals(TAG_ERROR_DIALOG) || errDialog == null)
            return;
        switch (event.type) {
            case POSITIVE_BUTTON_CLICKED:
                Dialog dialog = errDialog.getDialog();
                if (dialog != null) {
                    TextInputEditText editText = dialog.findViewById(R.id.comment);
                    Editable e = editText.getText();
                    String comment = (e == null ? null : e.toString());

                    helper.sendCrash(comment, null);
                    finish();
                }
                break;
            case NEGATIVE_BUTTON_CLICKED:
                helper.cancelReports();
                finish();
                break;
        }
    }

    @Override
    public void finish()
    {
        if (errDialog != null)
            errDialog.dismiss();

        super.finish();
    }

    private String getStackTrace()
    {
        Intent i = getIntent();
        if (i == null)
            return null;

        CrashReportData crashReportData;
        try {
            crashReportData = helper.getReportData();

        } catch (IOException e) {
            return null;
        }

        return crashReportData.getString(ReportField.STACK_TRACE);
    }
}
