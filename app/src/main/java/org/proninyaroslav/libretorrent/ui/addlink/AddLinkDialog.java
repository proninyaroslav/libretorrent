/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addlink;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogAddLinkBinding;
import org.proninyaroslav.libretorrent.ui.ClipboardDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.addtorrent.AddTorrentActivity;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class AddLinkDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = AddLinkDialog.class.getSimpleName();

    private static final String TAG_CLIPBOARD_DIALOG = "clipboard_dialog";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private DialogAddLinkBinding binding;
    private AddLinkViewModel viewModel;
    private ClipboardDialog clipboardDialog;
    private ClipboardDialog.SharedViewModel clipboardViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static AddLinkDialog newInstance()
    {
        AddLinkDialog frag = new AddLinkDialog();

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
    public void onResume()
    {
        super.onResume();

        /* Back button handle */
        getDialog().setOnKeyListener((DialogInterface dialog, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                } else {
                    onBackPressed();
                    return true;
                }
            } else {
                return false;
            }
        });
    }

    @Override
    public void onStop()
    {
        super.onStop();

        unsubscribeClipboardManager();
        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeClipboardManager();
        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = clipboardViewModel.observeSelectedItem().subscribe((item) -> {
            if (TAG_CLIPBOARD_DIALOG.equals(item.dialogTag))
                handleUrlClipItem(item.str);
        });
        disposables.add(d);
    }

    private void handleUrlClipItem(String item)
    {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.link.set(item);
    }

    private void subscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(clipListener);
    }

    private void unsubscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.removePrimaryClipChangedListener(clipListener);
    }

    private ClipboardManager.OnPrimaryClipChangedListener clipListener = this::switchClipboardButton;

    private void switchClipboardButton()
    {
        ClipData clip = Utils.getClipData(activity.getApplicationContext());
        viewModel.showClipboardButton.set(clip != null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(activity).get(AddLinkViewModel.class);
        clipboardViewModel = ViewModelProviders.of(activity).get(ClipboardDialog.SharedViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        FragmentManager fm = getFragmentManager();
        if (fm != null)
            clipboardDialog = (ClipboardDialog)fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG);

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_link, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();

        return alert;
    }

    private void initLayoutView()
    {
        /* Dismiss error label if user has changed the text */
        binding.link.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutLink.setErrorEnabled(false);
                binding.layoutLink.setError(null);
            }
        });

        binding.clipboardButton.setOnClickListener((v) -> showClipboardDialog());
        switchClipboardButton();
        viewModel.initLinkFromClipboard();

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_add_link_title)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener((v) -> addLink());

            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelButton.setOnClickListener((v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
        });
    }

    private void showClipboardDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG) == null) {
            clipboardDialog = ClipboardDialog.newInstance();
            clipboardDialog.show(fm, TAG_CLIPBOARD_DIALOG);
        }
    }

    private void addLink()
    {
        String s = viewModel.link.get();
        if (TextUtils.isEmpty(s))
            return;
        if (!checkUrlField())
            return;

        try {
            s = viewModel.normalizeUrl(s);

        } catch (NormalizeUrlException e) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(String.format(getString(R.string.invalid_url), e.getMessage()));
            binding.layoutLink.requestFocus();

            return;
        }

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, Uri.parse(s));
        startActivity(i);

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private boolean checkUrlField()
    {
        if (TextUtils.isEmpty(binding.link.getText())) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(getString(R.string.error_empty_link));
            binding.layoutLink.requestFocus();

            return false;
        }

        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);

        return true;
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        alert.dismiss();
        ((FragmentCallback)activity).onFragmentFinished(this, intent, code);
    }
}
