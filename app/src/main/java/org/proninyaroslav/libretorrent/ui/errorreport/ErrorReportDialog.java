/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.ui.errorreport;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.acra.ReportField;
import org.acra.dialog.CrashReportDialogHelper;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogErrorBinding;

import java.io.IOException;

public class ErrorReportDialog extends DialogFragment {
    @Nullable
    private CrashReportDialogHelper helper;
    @Nullable
    private ErrorReportActivity errorReportActivity;

    public static ErrorReportDialog newInstance(
            @NonNull String message,
            @Nullable Throwable exception
    ) {
        var dialog = new ErrorReportDialog();
        dialog.setArguments(
                new ErrorReportDialogArgs.Builder(message)
                        .setException(exception)
                        .build()
                        .toBundle()
        );

        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof ErrorReportActivity a) {
            errorReportActivity = a;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        if (requireActivity() instanceof ErrorReportActivity) {
            errorReportActivity = (ErrorReportActivity) requireActivity();
        }

        try {
            if (errorReportActivity != null) {
                helper = new CrashReportDialogHelper(errorReportActivity, errorReportActivity.getIntent());
            }
        } catch (IllegalArgumentException e) {
            helper = null;
        }

        var args = ErrorReportDialogArgs.fromBundle(getArguments());
        var exception = args.getException();
        var stackTrace = exception == null ? getStackTrace(helper) : null;

        var inflater = getLayoutInflater();
        DialogErrorBinding binding = DataBindingUtil.inflate(inflater, R.layout.dialog_error, null, false);
        binding.setStackTrace(exception == null ? stackTrace : Log.getStackTraceString(exception));

        initLayoutView(binding);

        return new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_error_24px)
                .setTitle(R.string.error)
                .setMessage(args.getMessage())
                .setView(binding.getRoot())
                .setPositiveButton(R.string.report, (dialog, which) -> {
                    var e = binding.comment.getText();
                    String comment = e == null ? null : e.toString();
                    if (helper != null) {
                        helper.sendCrash(comment, null);
                    } else {
                        Utils.reportError(exception, comment);
                    }
                    dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                .create();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (helper != null) {
            helper.cancelReports();
            if (errorReportActivity != null) {
                errorReportActivity.finish();
            }
        }
    }

    private void initLayoutView(DialogErrorBinding binding) {
        binding.expansionHeader.setOnClickListener((v) -> {
            binding.expandableLayout.toggle();
            binding.expansionHeader.toggleExpand();
        });
    }

    @Nullable
    private String getStackTrace(@Nullable CrashReportDialogHelper helper) {
        try {
            return helper == null ? null : helper.getReportData().getString(ReportField.STACK_TRACE);
        } catch (IOException e) {
            return null;
        }
    }
}
