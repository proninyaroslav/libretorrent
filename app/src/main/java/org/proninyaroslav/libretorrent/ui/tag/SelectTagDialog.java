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

package org.proninyaroslav.libretorrent.ui.tag;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogSelectTagBinding;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class SelectTagDialog extends DialogFragment implements TagsAdapter.OnClickListener {
    public static final String KEY_RESULT_TAG = "tag";

    private AppCompatActivity activity;
    private SelectTagViewModel viewModel;
    private TagsAdapter adapter;
    private DialogSelectTagBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private String requestKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
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

        var args = SelectTagDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(SelectTagViewModel.class);
        if (getArguments() != null) {
            viewModel.setExcludeTagsId(args.getExcludeTagsId());
        }

        binding = DialogSelectTagBinding.inflate(getLayoutInflater(), null, false);
        adapter = new TagsAdapter(this);

        initLayoutView();

        var alert = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_label_24px)
                .setTitle(R.string.select_tag)
                .setPositiveButton(R.string.new_tag, null)
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dismiss()))
                .setView(binding.getRoot())
                .create();

        alert.setOnShowListener((dialog) -> {
            var positiveButton = alert.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener((v) -> {
                var action = SelectTagDialogDirections.actionAddTagDialog();
                NavHostFragment.findNavController(this).navigate(action);
            });
        });

        return alert;
    }

    private void initLayoutView() {
        binding.tagsList.setEmptyView(binding.emptyListView);
        binding.tagsList.setLayoutManager(new LinearLayoutManager(activity));
        binding.tagsList.setAdapter(adapter);
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
        var bundle = new Bundle();
        bundle.putParcelable(KEY_RESULT_TAG, item.info);
        getParentFragmentManager().setFragmentResult(requestKey, bundle);

        dismiss();
    }
}
