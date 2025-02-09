/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.List;

public class ClipboardDialog extends DialogFragment {
    private static final String TAG = ClipboardDialog.class.getSimpleName();
    public static final String KEY_RESULT = TAG + "_result";
    public static final String KEY_CLIPBOARD_ITEM = "clipboard_item";

    private AppCompatActivity activity;
    private ArrayAdapter<CharSequence> adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        var dialogBuilder = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_content_copy_24px)
                .setTitle(R.string.clipboard)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        List<CharSequence> clipboardText = Utils.getClipboardText(activity.getApplicationContext());
        adapter = new ArrayAdapter<>(activity, R.layout.item_clipboard_list);
        adapter.addAll(clipboardText);

        dialogBuilder.setAdapter(adapter, (dialog, which) -> {
            CharSequence item = adapter.getItem(which);
            if (item != null) {
                var bundle = new Bundle();
                bundle.putString(KEY_CLIPBOARD_ITEM, item.toString());
                getParentFragmentManager().setFragmentResult(KEY_RESULT, bundle);
                dialog.dismiss();
            }
        });

        return dialogBuilder.create();
    }
}
