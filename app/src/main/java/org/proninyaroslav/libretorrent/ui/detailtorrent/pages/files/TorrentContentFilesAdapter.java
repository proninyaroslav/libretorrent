/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.files;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.model.filetree.TorrentContentFileTree;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ItemTorrentContentFileBinding;
import org.proninyaroslav.libretorrent.ui.Selectable;

import java.util.Locale;

public class TorrentContentFilesAdapter extends ListAdapter<TorrentContentFileItem, TorrentContentFilesAdapter.ViewHolder>
    implements Selectable<TorrentContentFileItem>
{
    private static final String TAG = TorrentContentFilesAdapter.class.getSimpleName();

    private ClickListener listener;
    private SelectionTracker<TorrentContentFileItem> selectionTracker;

    public TorrentContentFilesAdapter(ClickListener listener)
    {
        super(diffCallback);

        this.listener = listener;
    }

    public void setSelectionTracker(SelectionTracker<TorrentContentFileItem> selectionTracker)
    {
        this.selectionTracker = selectionTracker;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTorrentContentFileBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.item_torrent_content_file,
                parent,
                false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
        TorrentContentFileItem item = getItem(position);
        if (selectionTracker != null)
            holder.setSelected(selectionTracker.isSelected(item));

        holder.bind(item, listener);
    }

    @Override
    public TorrentContentFileItem getItemKey(int position)
    {
        if (position < 0 || position >= getCurrentList().size())
            return null;

        return getItem(position);
    }

    @Override
    public int getItemPosition(TorrentContentFileItem key)
    {
        return getCurrentList().indexOf(key);
    }

    private static final DiffUtil.ItemCallback<TorrentContentFileItem> diffCallback = new DiffUtil.ItemCallback<TorrentContentFileItem>()
    {
        @Override
        public boolean areContentsTheSame(@NonNull TorrentContentFileItem oldItem,
                                          @NonNull TorrentContentFileItem newItem)
        {
            return oldItem.equalsContent(newItem);
        }

        @Override
        public boolean areItemsTheSame(@NonNull TorrentContentFileItem oldItem,
                                       @NonNull TorrentContentFileItem newItem)
        {
            return oldItem.equals(newItem);
        }
    };

    interface ViewHolderWithDetails
    {
        ItemDetails getItemDetails();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
        implements ViewHolderWithDetails
    {
        private ItemTorrentContentFileBinding binding;
        /* For selection support */
        private TorrentContentFileItem selectionKey;
        private boolean isSelected;

        public ViewHolder(ItemTorrentContentFileBinding binding)
        {
            super(binding.getRoot());

            this.binding = binding;
            Utils.colorizeProgressBar(itemView.getContext(), binding.progress);
        }

        void bind(TorrentContentFileItem item, ClickListener listener)
        {
            Context context = itemView.getContext();

            selectionKey = item;

            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] {
                    R.attr.selectableColor,
                    R.attr.defaultRectRipple
            });
            Drawable d;
            if (isSelected)
                d = a.getDrawable(0);
            else
                d = a.getDrawable(1);
            if (d != null)
                Utils.setBackground(itemView, d);
            a.recycle();

            itemView.setOnClickListener((v) -> {
                if (isSelected)
                    return;

                if (listener != null)
                    listener.onItemClicked(item);
            });
            binding.priority.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemCheckedChanged(item, binding.priority.isChecked());
            });

            binding.name.setText(item.name);

            boolean isParentDir = item.name.equals(TorrentContentFileTree.PARENT_DIR);

            if (item.isFile) {
                binding.icon.setImageResource(R.drawable.ic_file_grey600_24dp);
                binding.icon.setContentDescription(context.getString(R.string.file));
            } else {
                if (isParentDir) {
                    binding.icon.setImageResource(R.drawable.ic_arrow_up_bold_grey600_24dp);
                    binding.icon.setContentDescription(context.getString(R.string.parent_folder));
                } else {
                    binding.icon.setImageResource(R.drawable.ic_folder_grey600_24dp);
                    binding.icon.setContentDescription(context.getString(R.string.folder));
                }
            }

            if (isParentDir) {
                binding.priority.setVisibility(View.GONE);
                binding.status.setVisibility(View.GONE);
                binding.progress.setVisibility(View.GONE);

            } else {
                binding.priority.setVisibility(View.VISIBLE);
                binding.status.setVisibility(View.VISIBLE);
                binding.progress.setVisibility(View.VISIBLE);

                long totalBytes = item.size;
                long receivedBytes = item.receivedBytes;
                int progress = (receivedBytes == totalBytes ? 100 : (int)((receivedBytes * 100.0f) / totalBytes));

                String total = Formatter.formatFileSize(context, item.size);
                String received = Formatter.formatFileSize(context, receivedBytes);

                String priority = "";
                switch (item.priority.getType()) {
                    case NORMAL:
                        priority = context.getString(R.string.file_priority_normal);
                        break;
                    case IGNORE:
                        priority = context.getString(R.string.file_priority_low);
                        break;
                    case MIXED:
                        priority = context.getString(R.string.file_priority_mixed);
                        break;
                    case HIGH:
                        priority = context.getString(R.string.file_priority_high);
                        break;
                }
                double avail = item.availability;
                String availability;
                if (avail < 0)
                    availability = context.getString(R.string.not_available);
                else
                    availability =  String.format(Locale.getDefault(), "%.1f%%", (avail >= 1 ? 100 : avail * 100));

                binding.priority.setChecked(item.priority.getType() != FilePriority.Type.IGNORE);
                binding.progress.setProgress(progress);

                binding.status.setText(context.getString(R.string.file_downloading_status_template, priority,
                        received, total, progress, availability));
            }
        }

        private void setSelected(boolean isSelected)
        {
            this.isSelected = isSelected;
        }

        @Override
        public ItemDetails getItemDetails()
        {
            return new ItemDetails(selectionKey, getBindingAdapterPosition());
        }
    }

    public interface ClickListener
    {
        void onItemClicked(@NonNull TorrentContentFileItem item);

        void onItemCheckedChanged(@NonNull TorrentContentFileItem item, boolean selected);
    }

    /*
     * Selection support stuff
     */

    public static final class KeyProvider extends ItemKeyProvider<TorrentContentFileItem>
    {
        private Selectable<TorrentContentFileItem> selectable;

        KeyProvider(Selectable<TorrentContentFileItem> selectable)
        {
            super(SCOPE_MAPPED);

            this.selectable = selectable;
        }

        @Nullable
        @Override
        public TorrentContentFileItem getKey(int position)
        {
            return selectable.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull TorrentContentFileItem key)
        {
            return selectable.getItemPosition(key);
        }
    }

    public static final class ItemDetails extends ItemDetailsLookup.ItemDetails<TorrentContentFileItem>
    {
        private TorrentContentFileItem selectionKey;
        private int adapterPosition;

        ItemDetails(TorrentContentFileItem selectionKey, int adapterPosition)
        {
            this.selectionKey = selectionKey;
            this.adapterPosition = adapterPosition;
        }

        @Nullable
        @Override
        public TorrentContentFileItem getSelectionKey()
        {
            return selectionKey;
        }

        @Override
        public int getPosition()
        {
            return adapterPosition;
        }
    }

    public static class ItemLookup extends ItemDetailsLookup<TorrentContentFileItem>
    {
        private final RecyclerView recyclerView;

        ItemLookup(RecyclerView recyclerView)
        {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<TorrentContentFileItem> getItemDetails(@NonNull MotionEvent e)
        {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ViewHolder)
                    return ((ViewHolder)viewHolder).getItemDetails();
            }

            return null;
        }
    }
}