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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.MutableCreationExtras;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsBinding;
import org.proninyaroslav.libretorrent.ui.DeleteTorrentDialog;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.ui.home.NavBarFragment;
import org.proninyaroslav.libretorrent.ui.home.NavBarFragmentDirections;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class TorrentDetailsFragment extends Fragment {
    private static final String TAG = TorrentDetailsFragment.class.getSimpleName();

    private static final String TAG_CURRENT_FRAG_POS = "current_frag_pos";
    private static final String KEY_ADD_TRACKERS_DIALOG_REQUEST = TAG + "_add_trackers_dialog";
    private static final String KEY_DELETE_TORRENT_DIALOG_REQUEST = TAG + "_delete_torrent_dialog";
    private static final String KEY_SPEED_LIMIT_DIALOG_REQUEST = TAG + "_speed_limit_dialog";
    private static final String KEY_SAVE_TORRENT_CHOOSE_DIALOG_REQUEST = TAG + "_save_torrent_choose_dialog";

    private MainActivity activity;
    private FragmentTorrentDetailsBinding binding;
    private String torrentId;
    private int currentFragPos = DetailPagerAdapter.Page.INFO.position;
    private boolean downloadingMetadata = false;
    private TorrentDetailsViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDeleteTorrentDialogListener();
        setAddTrackersDialogListener();
        setSpeedLimitDialogListener();
        var navBarFragment = activity.findNavBarFragment(this);
        if (navBarFragment != null) {
            setSaveTorrentChooseDialogListener(navBarFragment);
        }
    }

    private void setDeleteTorrentDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_DELETE_TORRENT_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var resultValue = (DeleteTorrentDialog.Result) result
                            .getSerializable(DeleteTorrentDialog.KEY_RESULT_VALUE);
                    if (resultValue == null) {
                        return;
                    }
                    switch (resultValue) {
                        case DELETE -> viewModel.deleteTorrent(false);
                        case DELETE_WITH_FILES -> viewModel.deleteTorrent(true);
                        case CANCEL -> {
                        }
                    }
                });
    }

    private void setAddTrackersDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_ADD_TRACKERS_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var urls = result.getStringArrayList(AddTrackersDialog.KEY_RESULT_URL_LIST);
                    var replace = result.getBoolean(AddTrackersDialog.KEY_RESULT_REPLACE);
                    if (urls == null) {
                        return;
                    }
                    if (replace) {
                        viewModel.replaceTrackers(urls);
                    } else {
                        viewModel.addTrackers(urls);
                    }
                });
    }

    private void setSpeedLimitDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_SPEED_LIMIT_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    SpeedLimitDialog.Speed speed = result.getParcelable(SpeedLimitDialog.KEY_RESULT_SPEED);
                    if (speed == null) {
                        return;
                    }
                    viewModel.setSpeedLimit(speed.upload(), speed.download());
                });
    }

    private void setSaveTorrentChooseDialogListener(NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                KEY_SAVE_TORRENT_CHOOSE_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.SAVE_FILE) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        saveErrorTorrentFileDialog(null);
                    } else {
                        try {
                            viewModel.copyTorrentFile(uri);
                            Snackbar.make(binding.coordinatorLayout,
                                            getString(R.string.save_torrent_file_successfully),
                                            Snackbar.LENGTH_SHORT)
                                    .show();

                        } catch (IOException | UnknownUriException e) {
                            saveErrorTorrentFileDialog(e);
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTorrentDetailsBinding.inflate(inflater, container, false);

        if (Utils.isTwoPane(activity)) {
            binding.appBar.setNavigationIcon(null);
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) getActivity();
        }

        var args = TorrentDetailsFragmentArgs.fromBundle(getArguments());
        torrentId = args.getTorrentId();

        var extras = new MutableCreationExtras();
        extras.set(
                ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY,
                activity.getApplication()
        );
        extras.set(TorrentDetailsViewModel.KEY_TORRENT_ID, torrentId);
        viewModel = new ViewModelProvider(
                getViewModelStore(),
                ViewModelProvider.Factory.from(TorrentDetailsViewModel.initializer),
                extras
        ).get(TorrentDetailsViewModel.class);

        DetailPagerAdapter adapter = new DetailPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.registerOnPageChangeCallback(viewPagerListener);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    var page = DetailPagerAdapter.Page.fromPosition(position);
                    switch (page) {
                        case INFO -> tab.setText(R.string.torrent_info);
                        case STATE -> tab.setText(R.string.torrent_state);
                        case FILES -> tab.setText(R.string.torrent_files);
                        case TRACKERS -> tab.setText(R.string.torrent_trackers);
                        case PEERS -> tab.setText(R.string.torrent_peers);
                        case PIECES -> tab.setText(R.string.torrent_pieces);
                    }
                }
        ).attach();
        binding.viewPager.setCurrentItem(currentFragPos);

        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());
        binding.appBar.setOnMenuItemClickListener((item) -> {
            int itemId = item.getItemId();
            if (itemId == R.id.pause_resume_torrent_menu) {
                viewModel.pauseResumeTorrent();
            } else if (itemId == R.id.delete_torrent_menu) {
                deleteTorrentDialog();
            } else if (itemId == R.id.force_recheck_torrent_menu) {
                viewModel.forceRecheckTorrent();
            } else if (itemId == R.id.force_announce_torrent_menu) {
                viewModel.forceAnnounceTorrent();
            } else if (itemId == R.id.share_magnet_menu) {
                shareMagnetDialog();
            } else if (itemId == R.id.save_torrent_file_menu) {
                torrentSaveChooseDialog();
            } else if (itemId == R.id.add_trackers_menu) {
                addTrackersDialog();
            } else if (itemId == R.id.torrent_speed_limit) {
                speedLimitDialog();
            }

            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeTorrentInfo();
        subscribeFreeSpaceError();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(TAG_CURRENT_FRAG_POS, currentFragPos);

        super.onSaveInstanceState(outState);
    }

    private void subscribeTorrentInfo() {
        if (torrentId == null)
            return;

        disposables.add(viewModel.observeTorrentMetaInfo()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((metaInfo) -> viewModel.updateInfo(metaInfo),
                        (Throwable e) -> {
                            Log.e(TAG, "Getting meta info error: " + Log.getStackTraceString(e));
                            Snackbar.make(
                                    activity,
                                    binding.coordinatorLayout,
                                    getString(R.string.error_fetch_link),
                                    Snackbar.LENGTH_LONG
                            ).show();
                        }));

        disposables.add(viewModel.observeTorrentInfoPair()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleTorrentInfo,
                        (Throwable e) -> Log.e(TAG, "Getting info error: " + Log.getStackTraceString(e))));

        disposables.add(viewModel.observeAdvancedTorrentInfo()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((info) -> viewModel.updateInfo(info),
                        (Throwable e) -> Log.e(TAG, "Getting advanced info error: " + Log.getStackTraceString(e))));
    }

    private void subscribeFreeSpaceError() {
        disposables.add(viewModel.observeFreeSpaceError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(__ -> showFreeSpaceErrorToast()));
    }

    private void handleTorrentInfo(Pair<Torrent, TorrentInfo> info) {
        Torrent torrent = info.first;
        TorrentInfo ti = info.second;
        TorrentInfo oldTi = viewModel.info.getTorrentInfo();
        boolean alreadyPaused = oldTi != null && oldTi.stateCode == TorrentStateCode.PAUSED;

        viewModel.updateInfo(torrent, ti);

        if (ti == null || torrent == null) {
            return;
        }

        if (downloadingMetadata && ti.stateCode != TorrentStateCode.DOWNLOADING_METADATA) {
            binding.appBar.invalidateMenu();
        }
        downloadingMetadata = torrent.downloadingMetadata;

        if (ti.stateCode == TorrentStateCode.PAUSED || alreadyPaused) {
            /* Redraw pause/resume menu */
            if (Utils.isTwoPane(activity)) {
                prepareMenu(binding.appBar.getMenu());
            } else {
                binding.appBar.invalidateMenu();
            }
        }
    }

    private final ViewPager2.OnPageChangeCallback viewPagerListener = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            currentFragPos = position;
        }
    };

    private void showFreeSpaceErrorToast() {
        Snackbar.make(binding.coordinatorLayout,
                        R.string.error_free_space,
                        Snackbar.LENGTH_LONG)
                .show();
    }

    private void prepareMenu(Menu menu) {
        MenuItem pauseResume = menu.findItem(R.id.pause_resume_torrent_menu);
        Torrent torrent = viewModel.info.getTorrent();
        TorrentInfo ti = viewModel.info.getTorrentInfo();
        if (torrent == null || ti == null || ti.stateCode != TorrentStateCode.PAUSED) {
            pauseResume.setTitle(R.string.pause_torrent);
            pauseResume.setIcon(R.drawable.ic_pause_24px);
        } else {
            pauseResume.setTitle(R.string.resume_torrent);
            pauseResume.setIcon(R.drawable.ic_play_arrow_24px);
        }

        MenuItem saveTorrentFile = menu.findItem(R.id.save_torrent_file_menu);
        saveTorrentFile.setVisible(torrent == null || !torrent.downloadingMetadata);
    }

    private void deleteTorrentDialog() {
        if (!isAdded()) {
            return;
        }

        var action = TorrentDetailsFragmentDirections
                .actionDeleteTorrentDetailsDialog(1, KEY_DELETE_TORRENT_DIALOG_REQUEST);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void shareMagnetDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_magnet_24px)
                .setTitle(R.string.share_magnet)
                .setView(R.layout.dialog_magnet_include_prior)
                .setPositiveButton(R.string.yes, (dialog, which) -> shareMagnet(true))
                .setNegativeButton(R.string.no, ((dialog, which) -> shareMagnet(false)))
                .show();
    }

    private void addTrackersDialog() {
        if (!isAdded()) {
            return;
        }

        var action = TorrentDetailsFragmentDirections
                .actionAddTrackersDialog(KEY_ADD_TRACKERS_DIALOG_REQUEST);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void torrentSaveChooseDialog() {
        var config = new FileManagerConfig(null,
                null,
                FileManagerConfig.Mode.SAVE_FILE);
        config.fileName = viewModel.mutableParams.getName();
        config.mimeType = Utils.MIME_TORRENT;

        var action = NavBarFragmentDirections
                .actionSaveFileChooseDialog(config, KEY_SAVE_TORRENT_CHOOSE_DIALOG_REQUEST);
        activity.getRootNavController().navigate(action);
    }

    private void saveErrorTorrentFileDialog(Exception e) {
        if (!isAdded()) {
            return;
        }

        var action = TorrentDetailsFragmentDirections.actionErrorReportDialog(
                getString(R.string.error_save_torrent_file)
        );
        action.setException(e);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void speedLimitDialog() {
        if (!isAdded()) {
            return;
        }

        var speed = new SpeedLimitDialog.Speed(viewModel.getDownloadSpeedLimit(), viewModel.getUploadSpeedLimit());
        var action = TorrentDetailsFragmentDirections.actionSpeedLimitDialog(
                KEY_SPEED_LIMIT_DIALOG_REQUEST,
                speed
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void shareMagnet(boolean includePriorities) {
        String magnet = viewModel.makeMagnet(includePriorities);
        if (magnet != null) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "magnet");
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, magnet);
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
        }
    }
}
