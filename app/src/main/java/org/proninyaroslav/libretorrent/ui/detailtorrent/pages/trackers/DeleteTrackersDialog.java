package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.trackers;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;

public class DeleteTrackersDialog extends DialogFragment {
    public enum Result {
        DELETE,
        CANCEL,
    }

    public static final String KEY_RESULT_VALUE = "value";

    private String requestKey;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = DeleteTrackersDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_delete_24px)
                .setTitle(args.getTrackersCount() > 1 ?
                        R.string.delete_selected_trackers :
                        R.string.delete_selected_tracker)
                .setPositiveButton(R.string.ok, this::onClick)
                .setNegativeButton(R.string.cancel, this::onClick);

        return builder.create();
    }

    private void onClick(DialogInterface dialog, int which) {
        var bundle = new Bundle();
        Result resultValue = null;
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE -> resultValue = Result.DELETE;
            case DialogInterface.BUTTON_NEGATIVE -> resultValue = Result.CANCEL;
        }
        bundle.putSerializable(KEY_RESULT_VALUE, resultValue);
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        dismiss();
    }
}
