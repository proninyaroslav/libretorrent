/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogMultilineTextInputBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;

public class AddTrackersDialog extends DialogFragment {
    public static final String KEY_RESULT_URL_LIST = "url_list";
    public static final String KEY_RESULT_REPLACE = "replace";

    private AppCompatActivity activity;
    private DialogMultilineTextInputBinding binding;
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
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        var args = AddTrackersDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        binding = DialogMultilineTextInputBinding.inflate(getLayoutInflater(), null, false);
        var builder = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_add_24px)
                .setTitle(R.string.add_trackers)
                .setMessage(R.string.dialog_add_trackers)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.replace, null)
                .setNeutralButton(R.string.cancel, null);

        var alert = builder.create();
        alert.setOnShowListener(dialog -> {
            var positiveButton = alert.getButton(DialogInterface.BUTTON_POSITIVE);
            var negativeButton = alert.getButton(DialogInterface.BUTTON_NEGATIVE);
            var neutralButton = alert.getButton(DialogInterface.BUTTON_NEUTRAL);
            positiveButton.setOnClickListener((v) -> onClick(dialog, DialogInterface.BUTTON_POSITIVE));
            negativeButton.setOnClickListener((v) -> onClick(dialog, DialogInterface.BUTTON_NEGATIVE));
            neutralButton.setOnClickListener((v) -> onClick(dialog, DialogInterface.BUTTON_NEUTRAL));
        });

        binding.multilineTextInputDialog.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutMultilineTextInputDialog.setErrorEnabled(false);
                binding.layoutMultilineTextInputDialog.setError(null);

                /* Clear selection of invalid url */
                Spannable text = binding.multilineTextInputDialog.getText();
                if (text != null) {
                    var errorSpans = text.getSpans(
                            0, text.length(), ForegroundColorSpan.class);
                    for (var span : errorSpans) {
                        text.removeSpan(span);
                    }
                }
            }
        });

        return alert;
    }

    private void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE -> {
                var editable = binding.multilineTextInputDialog.getText();
                if (editable == null) {
                    return;
                }
                var text = editable.toString();
                var urls = Observable.fromArray(text.split(Utils.getLineSeparator()))
                        .filter((s) -> !s.isEmpty())
                        .toList()
                        .blockingGet();

                if (!checkAddTrackersField(urls) || !checkAddTrackersField(urls)) {
                    return;
                }

                var options = new NormalizeUrl.Options();
                options.decode = false;
                var normalizedUrls = new ArrayList<String>(urls.size());
                for (var url : urls) {
                    if (TextUtils.isEmpty(url)) {
                        continue;
                    }
                    try {
                        normalizedUrls.add(NormalizeUrl.normalize(url, options));
                    } catch (NormalizeUrlException e) {
                        /* Ignore */
                    }
                }

                var bundle = new Bundle();
                bundle.putStringArrayList(KEY_RESULT_URL_LIST, normalizedUrls);
                bundle.putBoolean(KEY_RESULT_REPLACE, which == DialogInterface.BUTTON_NEGATIVE);
                getParentFragmentManager().setFragmentResult(requestKey, bundle);
                dismiss();
            }
            case DialogInterface.BUTTON_NEUTRAL -> dismiss();
        }
    }

    private boolean checkAddTrackersField(List<String> strings) {
        if (strings == null)
            return false;

        if (strings.isEmpty()) {
            binding.layoutMultilineTextInputDialog.setErrorEnabled(true);
            binding.layoutMultilineTextInputDialog.setError(getString(R.string.error_empty_link));
            binding.layoutMultilineTextInputDialog.requestFocus();

            return false;
        }

        var valid = true;
        var curLineStartIndex = 0;
        for (var s : strings) {
            if (!Utils.isValidTrackerUrl(s) && binding.multilineTextInputDialog.getText() != null) {
                /* Select invalid url */
                binding.multilineTextInputDialog.getText()
                        .setSpan(new ForegroundColorSpan(
                                        MaterialColors.getColor(binding.multilineTextInputDialog, R.attr.colorError)),
                                curLineStartIndex,
                                curLineStartIndex + s.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                valid = false;
            }
            /* Considering newline char */
            curLineStartIndex += s.length() + 1;
        }

        if (valid) {
            binding.layoutMultilineTextInputDialog.setErrorEnabled(false);
            binding.layoutMultilineTextInputDialog.setError(null);
        } else {
            binding.layoutMultilineTextInputDialog.setErrorEnabled(true);
            binding.layoutMultilineTextInputDialog.setError(getString(R.string.error_invalid_link));
            binding.layoutMultilineTextInputDialog.requestFocus();
        }

        return valid;
    }
}
