/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class ClipboardDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = ClipboardDialog.class.getSimpleName();

    private AppCompatActivity activity;
    private ArrayAdapter<CharSequence> adapter;
    private SharedViewModel viewModel;

    public static class SharedViewModel extends androidx.lifecycle.ViewModel
    {
        private final PublishSubject<Item> selectedItemSubject = PublishSubject.create();

        public Observable<Item> observeSelectedItem()
        {
            return selectedItemSubject;
        }

        public void sendSelectedItem(Item item)
        {
            selectedItemSubject.onNext(item);
        }
    }

    public class Item
    {
        public String dialogTag;
        public String str;

        public Item(String dialogTag, String str)
        {
            this.dialogTag = dialogTag;
            this.str = str;
        }
    }

    public static ClipboardDialog newInstance()
    {
        ClipboardDialog frag = new ClipboardDialog();

        Bundle args = new Bundle();
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(getActivity()).get(SharedViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(R.string.clipboard)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        List<CharSequence> clipboardText = Utils.getClipboardText(activity.getApplicationContext());
        adapter = new ArrayAdapter<>(activity, R.layout.item_clipboard_list);
        adapter.addAll(clipboardText);

        dialogBuilder.setAdapter(adapter, (dialog, which) -> {
            CharSequence item = adapter.getItem(which);
            if (item != null)
                viewModel.sendSelectedItem(new Item(getTag(), item.toString()));
        });

        return dialogBuilder.create();
    }
}
