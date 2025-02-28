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

package org.proninyaroslav.libretorrent.ui.addtag;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogTagBinding;
import org.proninyaroslav.libretorrent.ui.colorpicker.ColorPickerDialog;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class TagDialog extends DialogFragment {
    private static final String TAG = TagDialog.class.getSimpleName();

    private static final String KEY_COLOR_PICKER_DIALOG_REQUEST = TAG + "_color_picker_dialog";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private AddTagViewModel viewModel;
    private DialogTagBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
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

        var args = TagDialogArgs.fromBundle(getArguments());
        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(AddTagViewModel.class);

        getParentFragmentManager().setFragmentResultListener(
                KEY_COLOR_PICKER_DIALOG_REQUEST,
                this,
                (key, bundle) -> viewModel.state.setColor(bundle.getInt(ColorPickerDialog.KEY_RESULT_COLOR))
        );

        var initInfo = args.getTag();
        if (initInfo != null && !viewModel.hasInitValues()) {
            viewModel.setInitValues(initInfo);
        } else if (viewModel.state.getColor() == -1) {
            viewModel.setRandomColor();
        }

        var inflater = getLayoutInflater();
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_tag, null, false);
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
        binding.color.setOnClickListener((v) -> {
            var action = TagDialogDirections
                    .actionColorPickerDialog(KEY_COLOR_PICKER_DIALOG_REQUEST)
                    .setColor(viewModel.state.getColor());
            NavHostFragment.findNavController(this).navigate(action);
        });
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
        var builder = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_label_24px)
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
            cancelButton.setOnClickListener((v) -> dismiss());
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
                        this::dismiss,
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
}
