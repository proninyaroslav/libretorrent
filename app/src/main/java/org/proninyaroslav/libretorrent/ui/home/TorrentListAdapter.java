/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.home;

import android.content.res.ColorStateList;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ItemTorrentListBinding;
import org.proninyaroslav.libretorrent.ui.Selectable;

import java.util.concurrent.atomic.AtomicReference;

public class TorrentListAdapter extends ListAdapter<TorrentListItem, TorrentListAdapter.ViewHolder>
        implements Selectable<TorrentListItem> {
    private final ClickListener listener;
    private SelectionTracker<TorrentListItem> selectionTracker;
    private final AtomicReference<TorrentListItem> curOpenItem = new AtomicReference<>();
    private boolean onBind = false;

    public TorrentListAdapter(ClickListener listener) {
        super(diffCallback);

        this.listener = listener;
    }

    public void setSelectionTracker(SelectionTracker<TorrentListItem> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        var binding = ItemTorrentListBinding.inflate(inflater, parent, false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        onBind = true;

        var item = getItem(position);

        if (selectionTracker != null) {
            holder.setSelected(selectionTracker.isSelected(item));
        }

        boolean isOpened = false;
        TorrentListItem currTorrent = curOpenItem.get();
        if (currTorrent != null) {
            isOpened = item.torrentId.equals(currTorrent.torrentId);
        }

        holder.bind(item, isOpened, listener);

        onBind = false;
    }

    @Override
    public TorrentListItem getItemKey(int position) {
        if (position < 0 || position >= getCurrentList().size()) {
            return null;
        }

        return getItem(position);
    }

    @Override
    public int getItemPosition(TorrentListItem key) {
        return getCurrentList().indexOf(key);
    }

    public void markAsOpen(@Nullable String torrentId) {
        var prevItem = curOpenItem.getAndSet(
                torrentId == null ? null : new TorrentListItem(torrentId)
        );
        if (onBind) {
            return;
        }
        int position = getItemPosition(prevItem);
        if (position >= 0) {
            notifyItemChanged(position);
        }

        var item = curOpenItem.get();
        if (item != null) {
            position = getItemPosition(item);
            if (position >= 0) {
                notifyItemChanged(position);
            }
        }
    }

    interface ViewHolderWithDetails {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements ViewHolderWithDetails {
        private final ItemTorrentListBinding binding;
        private AnimatedVectorDrawable currAnim;
        ColorStateList pauseButtonBackground;
        ColorStateList cardBackground;
        int progressTrackColor;
        /* For selection support */
        private TorrentListItem selectionKey;
        private boolean isSelected;

        ViewHolder(ItemTorrentListBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
            var context = itemView.getContext();
            pauseButtonBackground = binding.pauseButton.getBackgroundTintList();
            cardBackground = binding.card.getCardBackgroundColor();
            progressTrackColor = binding.progress.getTrackColor();
        }

        void bind(TorrentListItem item, boolean isOpened, ClickListener listener) {
            var context = itemView.getContext();
            selectionKey = item;

            binding.card.setChecked(isSelected);
            binding.card.setOnClickListener((v) -> {
                /* Skip selecting and deselecting */
                if (isSelected) {
                    return;
                }

                if (listener != null) {
                    listener.onItemClicked(item);
                }
            });

            var pauseButton = (MaterialButton) binding.pauseButton;
            pauseButton.setChecked(item.stateCode != TorrentStateCode.PAUSED);
            pauseButton.setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onItemPauseClicked(item);
                }
            });

            binding.name.setText(item.name);

            if (item.progress == 100
                    || item.stateCode == TorrentStateCode.UNKNOWN
                    || item.progress == 0 && item.stateCode == TorrentStateCode.STOPPED) {
                binding.progress.setVisibility(View.GONE);
            } else {
                binding.progress.setVisibility(View.VISIBLE);
            }

            if (item.stateCode == TorrentStateCode.DOWNLOADING_METADATA) {
                binding.progress.setIndeterminate(true);
            } else {
                binding.progress.setIndeterminate(false);
                binding.progress.setProgress(item.progress);
            }

            String statusString = switch (item.stateCode) {
                case DOWNLOADING -> context.getString(R.string.torrent_status_downloading);
                case SEEDING -> context.getString(R.string.torrent_status_seeding);
                case PAUSED -> context.getString(R.string.torrent_status_paused);
                case STOPPED -> context.getString(R.string.torrent_status_stopped);
                case FINISHED -> context.getString(R.string.torrent_status_finished);
                case CHECKING -> context.getString(R.string.torrent_status_checking);
                case DOWNLOADING_METADATA ->
                        context.getString(R.string.torrent_status_downloading_metadata);
                default -> "";
            };
            binding.status.setText(statusString);

            String totalBytes = Formatter.formatFileSize(context, item.totalBytes);
            String receivedBytes;
            String ETA;
            if (item.ETA >= TorrentInfo.MAX_ETA) {
                ETA = "• " + Utils.INFINITY_SYMBOL;
            } else if (item.ETA == 0) {
                ETA = "";
            } else {
                ETA = "• " + DateUtils.formatElapsedTime(item.ETA);
            }

            if (item.progress == 100) {
                receivedBytes = totalBytes;
            } else {
                receivedBytes = Formatter.formatFileSize(context, item.receivedBytes);
            }

            binding.downloadCounter.setText(context.getString(R.string.download_counter_ETA_template, receivedBytes, totalBytes,
                    (item.totalBytes == 0 ? 0 : item.progress), ETA));

            String downloadSpeed = Formatter.formatFileSize(context, item.downloadSpeed);
            String uploadSpeed = Formatter.formatFileSize(context, item.uploadSpeed);
            binding.downloadUploadSpeed.setText(context.getString(R.string.download_upload_speed_template, downloadSpeed, uploadSpeed));

            binding.peers.setText(context.getString(R.string.peers_template, item.peers, item.totalPeers));

            if (item.error != null) {
                binding.errorContainer.setVisibility(View.VISIBLE);
                binding.error.setText(context.getString(R.string.error_template, item.error));
            } else {
                binding.errorContainer.setVisibility(View.GONE);
            }

            applyOpenItemStyle(isOpened);
        }

        private void applyOpenItemStyle(boolean isOpened) {
            if (isOpened) {
                binding.card.setCardBackgroundColor(MaterialColors.getColor(binding.getRoot(), R.attr.colorPrimaryContainer));
                var colorSurface = MaterialColors.getColor(binding.getRoot(), R.attr.colorSurface);
                binding.pauseButton.setBackgroundTintList(ColorStateList.valueOf(colorSurface));
                binding.progress.setTrackColor(colorSurface);
            } else {
                binding.card.setCardBackgroundColor(cardBackground);
                binding.pauseButton.setBackgroundTintList(pauseButtonBackground);
                binding.progress.setTrackColor(progressTrackColor);
            }
        }

        private void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails() {
            return new ItemDetails(selectionKey, getBindingAdapterPosition());
        }
    }

    public interface ClickListener {
        void onItemClicked(@NonNull TorrentListItem item);

        void onItemPauseClicked(@NonNull TorrentListItem item);
    }

    public static final DiffUtil.ItemCallback<TorrentListItem> diffCallback = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areContentsTheSame(@NonNull TorrentListItem oldItem,
                                          @NonNull TorrentListItem newItem) {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull TorrentListItem oldItem,
                                       @NonNull TorrentListItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<TorrentListItem> {
        private final Selectable<TorrentListItem> selectable;

        KeyProvider(Selectable<TorrentListItem> selectable) {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public TorrentListItem getKey(int position) {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull TorrentListItem key) {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<TorrentListItem> {
        private final TorrentListItem selectionKey;
        private final int adapterPosition;

        ItemDetails(TorrentListItem selectionKey, int adapterPosition) {
            this.selectionKey = selectionKey;
            this.adapterPosition = adapterPosition;
        }

        @Nullable
        @Override
        public TorrentListItem getSelectionKey() {
            return selectionKey;
        }

        @Override
        public int getPosition() {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<TorrentListItem> {
        private final RecyclerView recyclerView;

        ItemLookup(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<TorrentListItem> getItemDetails(@NonNull MotionEvent e) {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ViewHolderWithDetails v) {
                    return v.getItemDetails();
                }
            }

            return null;
        }
    }
}
