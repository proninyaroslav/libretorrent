/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.Collection;
import java.util.List;

public class PeerListAdapter extends SelectableAdapter<PeerListAdapter.ViewHolder>
{
    @SuppressWarnings("unused")
    private static final String TAG = TrackerListAdapter.class.getSimpleName();

    private Context context;
    private ViewHolder.ClickListener clickListener;
    private int rowLayout;
    private List<PeerStateParcel> items;

    public PeerListAdapter(List<PeerStateParcel> items, Context context,
                              int rowLayout, ViewHolder.ClickListener clickListener)
    {
        this.context = context;
        this.rowLayout = rowLayout;
        this.items = items;
        this.clickListener = clickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(rowLayout, parent, false);

        return new ViewHolder(v, clickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        PeerStateParcel state = items.get(position);
        holder.state = state;

        holder.ip.setText(state.ip);

        holder.progress.setProgress(state.progress);

        String portTemplate = context.getString(R.string.peer_port);
        holder.port.setText(
                String.format(portTemplate, state.port));

        String relevanceTemplate = context.getString(R.string.peer_relevance);
        holder.relevance.setText(
                String.format(relevanceTemplate, state.relevance));

        String connectionTemplate = context.getString(R.string.peer_connection_type);
        String connectionType = "";
        switch (state.connectionType) {
            case PeerStateParcel.ConnectionType.BITTORRENT:
                connectionType = context.getString(R.string.peer_connection_type_bittorrent);
                break;
            case PeerStateParcel.ConnectionType.WEB:
                connectionType = context.getString(R.string.peer_connection_type_web);
                break;
            case PeerStateParcel.ConnectionType.UTP:
                connectionType = context.getString(R.string.peer_connection_type_utp);
                break;
        }
        holder.connection.setText(
                String.format(connectionTemplate, connectionType));

        String speedTemplate = context.getString(R.string.download_upload_speed_template);
        String downSpeed = Formatter.formatFileSize(context, state.downSpeed);
        String upSpeed = Formatter.formatFileSize(context, state.upSpeed);
        holder.upDownSpeed.setText(
                String.format(speedTemplate, downSpeed, upSpeed));

        String clientTemplate = context.getString(R.string.peer_client);
        holder.client.setText(
                String.format(clientTemplate, state.client));

        String downloadUploadTemplate = context.getString(R.string.peer_total_download_upload);
        String upload = Formatter.formatFileSize(context, state.totalUpload);
        String download = Formatter.formatFileSize(context, state.totalDownload);
        holder.totalDownloadUpload.setText(
                String.format(downloadUploadTemplate, download, upload));
    }

    public synchronized void addItems(Collection<PeerStateParcel> states)
    {
        items.addAll(states);
        notifyItemRangeInserted(0, states.size());
    }

    public void clearAll()
    {
        int size = items.size();
        if (size > 0) {
            items.clear();

            notifyItemRangeRemoved(0, size);
        }
    }

    public synchronized void updateItems(Collection<PeerStateParcel> states)
    {
        items.clear();
        items.addAll(states);

        notifyDataSetChanged();
    }

    public boolean isEmpty()
    {
        return items.isEmpty();
    }

    @Override
    public int getItemCount()
    {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnLongClickListener
    {
        Context context;
        private TextView ip;
        private ProgressBar progress;
        private TextView port;
        private TextView relevance;
        private TextView connection;
        private TextView upDownSpeed;
        private TextView client;
        private TextView totalDownloadUpload;
        private ClickListener listener;
        private PeerStateParcel state;

        public ViewHolder(View itemView, ClickListener listener)
        {
            super(itemView);

            this.context = itemView.getContext();
            this.listener = listener;
            itemView.setOnLongClickListener(this);

            ip = (TextView) itemView.findViewById(R.id.peer_ip);
            progress = (ProgressBar) itemView.findViewById(R.id.peer_progress);
            Utils.colorizeProgressBar(context, progress);
            port = (TextView) itemView.findViewById(R.id.peer_port);
            relevance = (TextView) itemView.findViewById(R.id.peer_relevance);
            connection = (TextView) itemView.findViewById(R.id.peer_connection_type);
            upDownSpeed = (TextView) itemView.findViewById(R.id.peer_up_down_speed);
            client = (TextView) itemView.findViewById(R.id.peer_client);
            totalDownloadUpload = (TextView) itemView.findViewById(R.id.peer_total_download_upload);
        }

        @Override
        public boolean onLongClick(View view)
        {
            int position = getAdapterPosition();

            if (listener != null && position >= 0) {
                listener.onItemLongClicked(position, state);

                return true;
            }

            return false;
        }

        public interface ClickListener
        {
            boolean onItemLongClicked(int position, PeerStateParcel state);
        }
    }
}
