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
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogMultilineTextInputBinding;
import org.proninyaroslav.libretorrent.databinding.DialogSpeedLimitBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;

public class SpeedLimitDialog extends DialogFragment {
    public record Speed(int download, int upload) implements Parcelable {
        public Speed(Parcel source) {
            this(source.readInt(), source.readInt());
        }

        public static final Parcelable.Creator<Speed> CREATOR = new Parcelable.Creator<>() {
            @Override
            public Speed createFromParcel(Parcel source) {
                return new Speed(source);
            }

            @Override
            public Speed[] newArray(int size) {
                return new Speed[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(download);
            dest.writeInt(upload);
        }
    }

    public static final String KEY_RESULT_SPEED = "speed";

    private AppCompatActivity activity;
    private DialogSpeedLimitBinding binding;
    private String requestKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        var args = SpeedLimitDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        binding = DialogSpeedLimitBinding.inflate(getLayoutInflater(), null, false);
        var builder = new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_speed_24px)
                .setTitle(R.string.speed_limit_title)
                .setMessage(R.string.torrent_speed_limit_dialog)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, ((dialog, which) -> onResult()))
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dismiss()));

        var minSpeedLimit = 0;
        var filter = new InputFilter[]{InputFilterRange.UNSIGNED_INT};
        var initSpeed = args.getInitSpeedLimit();

        binding.uploadLimit.setFilters(filter);
        if (TextUtils.isEmpty(binding.uploadLimit.getText())) {
            binding.uploadLimit.setText(
                    initSpeed.upload != -1
                            ? Integer.toString(initSpeed.upload / 1024)
                            : Integer.toString(minSpeedLimit)
            );
        }

        binding.downloadLimit.setFilters(filter);
        if (TextUtils.isEmpty(binding.downloadLimit.getText())) {
            binding.downloadLimit.setText(
                    initSpeed.download != -1
                            ? Integer.toString(initSpeed.download / 1024)
                            : Integer.toString(minSpeedLimit)
            );
        }

        return builder.create();
    }

    private void onResult() {
        var uploadEditable = binding.uploadLimit.getText();
        var downloadEditable = binding.downloadLimit.getText();
        if (TextUtils.isEmpty(uploadEditable) || TextUtils.isEmpty(downloadEditable)) {
            return;
        }

        int uploadSpeedLimit;
        try {
            uploadSpeedLimit = Integer.parseInt(uploadEditable.toString()) * 1024;
        } catch (NumberFormatException e) {
            uploadSpeedLimit = 0;
        }
        int downloadSpeedLimit;
        try {
            downloadSpeedLimit = Integer.parseInt(downloadEditable.toString()) * 1024;
        } catch (NumberFormatException e) {
            downloadSpeedLimit = 0;
        }

        var bundle = new Bundle();
        bundle.putParcelable(KEY_RESULT_SPEED, new Speed(downloadSpeedLimit, uploadSpeedLimit));
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        dismiss();
    }
}
