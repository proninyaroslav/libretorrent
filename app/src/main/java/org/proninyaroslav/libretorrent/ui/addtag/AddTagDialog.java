/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addtag;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.jaredrummler.android.colorpicker.ColorPickerDialog;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.databinding.DialogAddTagBinding;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AddTagDialog extends DialogFragment {
    private static final String TAG = AddTagDialog.class.getSimpleName();

    private static final String TAG_INIT_INFO = "init_info";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private AddTagViewModel viewModel;
    private DialogAddTagBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static AddTagDialog newInstance(@Nullable TagInfo initInfo) {
        AddTagDialog frag = new AddTagDialog();

        Bundle args = new Bundle();
        args.putParcelable(TAG_INIT_INFO, initInfo);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Back button handle
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    onBackPressed();
                }
                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(AddTagViewModel.class);
        TagInfo initInfo = getArguments().getParcelable(TAG_INIT_INFO);
        if (initInfo != null) {
            viewModel.setInitValues(initInfo);
            getArguments().putParcelable(TAG_INIT_INFO, null);
        } else if (viewModel.state.getColor() == -1) {
            viewModel.setRandomColor();
        }

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_tag, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();
        initAlertDialog(binding.getRoot());

        return alert;
    }

    private void initLayoutView() {
        binding.name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.nameLayout.setErrorEnabled(false);
                binding.nameLayout.setError(null);
            }
        });
        binding.color.setOnClickListener((v) -> ColorPickerDialog.newBuilder()
                .setColor(viewModel.state.getColor())
                .show(activity)
        );
    }

    private boolean checkName() {
        if (TextUtils.isEmpty(viewModel.state.getName())) {
            binding.nameLayout.setErrorEnabled(true);
            binding.nameLayout.setError(getString(R.string.error_empty_name));
            binding.nameLayout.requestFocus();
            return false;
        }

        binding.nameLayout.setErrorEnabled(false);
        binding.nameLayout.setError(null);
        return true;
    }

    private void initAlertDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(viewModel.state.getExistsTagId() == null ?
                        R.string.add_tag :
                        R.string.edit_tag)
                .setPositiveButton(viewModel.state.getExistsTagId() == null ?
                        R.string.add :
                        R.string.apply, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener((v) -> addTag());

            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelButton.setOnClickListener(
                    (v) -> finish(new Intent(), FragmentCallback.ResultCode.CANCEL)
            );
        });
    }

    private void addTag() {
        if (!checkName()) {
            return;
        }
        disposables.add(viewModel.saveTag()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> finish(new Intent(), FragmentCallback.ResultCode.OK),
                        (e) -> {
                            if (e instanceof AddTagViewModel.TagAlreadyExistsException) {
                                Toast.makeText(
                                        activity,
                                        R.string.tag_already_exists,
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                Log.e(TAG, Log.getStackTraceString(e));
                                Toast.makeText(
                                        activity,
                                        R.string.add_tag_failed,
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
        );
    }

    public void onBackPressed() {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        alert.dismiss();
        ((FragmentCallback) activity).onFragmentFinished(this, intent, code);
    }
}
