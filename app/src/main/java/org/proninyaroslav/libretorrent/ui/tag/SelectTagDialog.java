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

package org.proninyaroslav.libretorrent.ui.tag;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogSelectTagBinding;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.addtag.AddTagActivity;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class SelectTagDialog extends DialogFragment implements TagsAdapter.OnClickListener {
    private static final String TAG = SelectTagDialog.class.getSimpleName();

    private static final String TAG_EXCLUDE_TAGS_ID = "exclude_tags_id";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private SelectTagViewModel viewModel;
    private TagsAdapter adapter;
    private DialogSelectTagBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static SelectTagDialog newInstance(@Nullable long[] excludeTagsId) {
        SelectTagDialog frag = new SelectTagDialog();

        Bundle args = new Bundle();
        args.putLongArray(TAG_EXCLUDE_TAGS_ID, excludeTagsId);
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
    public void onStart() {
        super.onStart();

        subscribeTags();
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
        viewModel = provider.get(SelectTagViewModel.class);
        if (getArguments() != null) {
            viewModel.setExcludeTagsId(getArguments()
                    .getLongArray(TAG_EXCLUDE_TAGS_ID));
        }

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_select_tag, null, false);

        adapter = new TagsAdapter(this);

        initLayoutView();
        initAlertDialog(binding.getRoot());

        return alert;
    }

    private void initLayoutView() {
        binding.tagsList.setEmptyView(binding.emptyListView);
        binding.tagsList.setLayoutManager(new LinearLayoutManager(activity));
        binding.tagsList.setAdapter(adapter);
    }

    private void initAlertDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.select_tag)
                .setPositiveButton(R.string.new_tag, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button newTagButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            newTagButton.setOnClickListener(
                    (v) -> startActivity(new Intent(activity, AddTagActivity.class))
            );
            cancelButton.setOnClickListener(
                    (v) -> finish(new Intent(), FragmentCallback.ResultCode.CANCEL)
            );
        });
    }

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle((list) -> Flowable.fromIterable(list)
                                .filter(viewModel::filterExcludeTags)
                                .map(TagItem::new)
                                .toList()
                        )
                        .subscribe(adapter::submitList)
                );
    }

    @Override
    public void onTagClicked(@NonNull TagItem item) {
        Intent i = new Intent();
        i.putExtra(SelectTagActivity.TAG_RESULT_SELECTED_TAG, item.info);

        finish(i, FragmentCallback.ResultCode.OK);
    }

    public void onBackPressed() {

        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        alert.dismiss();
        ((FragmentCallback) activity).onFragmentFinished(this, intent, code);
    }
}
