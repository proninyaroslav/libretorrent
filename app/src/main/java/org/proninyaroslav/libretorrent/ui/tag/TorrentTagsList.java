package org.proninyaroslav.libretorrent.ui.tag;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;

import java.util.List;

public class TorrentTagsList extends ChipGroup {
    private Listener listener;
    private List<TagInfo> tags;
    private AddTagButton addTagButton;

    public TorrentTagsList(Context context) {
        super(context);

        init(context);
    }

    public TorrentTagsList(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    void init(Context context) {
        this.addTagButton = buildAddTagButton(context);
        addView(addTagButton);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<TagInfo> tags) {
        removeAllViews();
        this.tags = tags;

        for (TagInfo tag : tags) {
            addView(buildChip(getContext(), tag));
        }
        addView(addTagButton);
    }

    private Chip buildChip(Context context, TagInfo info) {
        Chip chip = new Chip(context);
        chip.setText(info.name);
        chip.setChipIconSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                18,
                getResources().getDisplayMetrics()
        ));
        chip.setChipIconResource(R.drawable.torrent_tag_color_background);
        chip.setChipIconTint(new ColorStateList(
                new int[][]{new int[]{}},
                new int[]{info.color})
        );
        chip.setCloseIconVisible(true);
        chip.setClickable(false);
        chip.setOnCloseIconClickListener((v) -> {
            if (listener != null) {
                listener.onTagRemoved(info);
            }
        });

        return chip;
    }

    private AddTagButton buildAddTagButton(Context context) {
        AddTagButton button = new AddTagButton(context);
        button.setOnClickListener((v) -> {
            if (listener != null) {
                listener.onAddTagClick();
            }
        });

        return button;
    }

    public interface Listener {
        void onAddTagClick();

        void onTagRemoved(@NonNull TagInfo info);
    }
}
