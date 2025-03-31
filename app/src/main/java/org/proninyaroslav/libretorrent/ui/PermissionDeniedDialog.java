/*
 * Copyright (C) 2021-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_POSITIVE;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;

public class PermissionDeniedDialog extends DialogFragment {
    public enum Result {
        RETRY,
        DENIED
    }

    public static final String KEY_RESULT_VALUE = "value";

    private AppCompatActivity activity;
    private String requestKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        var args = PermissionDeniedDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        var message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                R.string.perm_denied_warning_android_r :
                R.string.perm_denied_warning;

        var builder = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_folder_24px)
                .setTitle(R.string.perm_denied_title)
                .setMessage(message)
                .setPositiveButton(R.string.yes, this::onClick)
                .setNegativeButton(R.string.no, this::onClick);

        return builder.create();
    }

    private void onClick(DialogInterface dialog, int which) {
        var bundle = new Bundle();
        switch (which) {
            case BUTTON_POSITIVE -> bundle.putSerializable(KEY_RESULT_VALUE, Result.DENIED);
            case BUTTON_NEGATIVE -> bundle.putSerializable(KEY_RESULT_VALUE, Result.RETRY);
        }
        activity.getSupportFragmentManager().setFragmentResult(requestKey, bundle);
        dismiss();
    }
}
