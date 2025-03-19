/*
 * https://stackoverflow.com/a/74112704
 */

package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialEditTextPreferenceDialog extends EditTextPreferenceDialogFragmentCompat {
    private int whichButtonClicked = 0;
    private boolean onDialogClosedWasCalledFromOnDismiss = false;

    @NonNull
    public static MaterialEditTextPreferenceDialog newInstance(String key) {
        var fragment = new MaterialEditTextPreferenceDialog();
        var b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);

        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        var context = getContext();
        whichButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        var preference = getPreference();
        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(preference.getDialogTitle())
                .setIcon(preference.getDialogIcon())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this);

        var contentView = context != null ? onCreateDialogView(context) : null;
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(preference.getDialogMessage());
        }
        onPrepareDialogBuilder(builder);

        return builder.create();
    }

    public void onClick(@NonNull DialogInterface dialog, int which) {
        whichButtonClicked = which;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        onDialogClosedWasCalledFromOnDismiss = true;
        super.onDismiss(dialog);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (onDialogClosedWasCalledFromOnDismiss) {
            onDialogClosedWasCalledFromOnDismiss = false;
            super.onDialogClosed(whichButtonClicked == DialogInterface.BUTTON_POSITIVE);
        } else {
            super.onDialogClosed(positiveResult);
        }
    }
}

