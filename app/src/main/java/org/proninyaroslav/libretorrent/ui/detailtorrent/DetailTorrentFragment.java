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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentDetailTorrentBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportDialog;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DetailTorrentFragment extends Fragment {
    private static final String TAG = DetailTorrentFragment.class.getSimpleName();

    private static final String TAG_TORRENT_ID = "torrent_id";
    private static final String TAG_CURRENT_FRAG_POS = "current_frag_pos";
    private static final String TAG_ADD_TRACKERS_DIALOG = "add_trackers_dialog";
    private static final String TAG_DELETE_TORRENT_DIALOG = "delete_torrent_dialog";
    private static final String TAG_ERR_REPORT_DIALOG = "err_report_dialog";
    private static final String TAG_SPEED_LIMIT_DIALOG = "speed_limit_dialog";
    private static final String TAG_MAGNET_INCLUDE_PRIOR_DIALOG = "include_prior_dialog";

    private AppCompatActivity activity;
    private FragmentDetailTorrentBinding binding;
    private DetailPagerAdapter adapter;
    private String torrentId;
    private int currentFragPos = DetailPagerAdapter.INFO_FRAG_POS;
    private boolean downloadingMetadata = false;
    private DetailTorrentViewModel viewModel;
    private MsgDetailTorrentViewModel msgViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private ErrorReportDialog errReportDialog;
    private BaseAlertDialog deleteTorrentDialog, addTrackersDialog, speedLimitDialog;

    public static DetailTorrentFragment newInstance(@NonNull String id) {
        DetailTorrentFragment fragment = new DetailTorrentFragment();
        fragment.setTorrentId(id);
        fragment.setArguments(new Bundle());

        return fragment;
    }

    public String getTorrentId() {
        return torrentId;
    }

    public void setTorrentId(String id) {
        torrentId = id;
    }

    public void showFiles() {
        currentFragPos = DetailPagerAdapter.FILES_FRAG_POS;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
            activity.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish(new Intent(), FragmentCallback.ResultCode.BACK);
                }
            });
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail_torrent, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(DetailTorrentViewModel.class);
        /* Remove previous data if fragment changed */
        if (Utils.isTwoPane(activity))
            viewModel.clearData();
        viewModel.setTorrentId(torrentId);
        msgViewModel = provider.get(MsgDetailTorrentViewModel.class);

        if (Utils.isTwoPane(activity)) {
            binding.appbar.toolbar.inflateMenu(R.menu.detail_torrent);
            binding.appbar.toolbar.setNavigationIcon(ContextCompat.getDrawable(activity.getApplicationContext(),
                    R.drawable.ic_arrow_back_white_24dp));
            binding.appbar.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        } else {
            binding.appbar.toolbar.setTitle(R.string.details);
            activity.setSupportActionBar(binding.appbar.toolbar);
            if (activity.getSupportActionBar() != null)
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.appbar.toolbar.setNavigationOnClickListener((v) ->
                finish(new Intent(), FragmentCallback.ResultCode.BACK));

        adapter = new DetailPagerAdapter(this);
        binding.fragmentViewpager.setAdapter(adapter);
        binding.fragmentViewpager.registerOnPageChangeCallback(viewPagerListener);
        new TabLayoutMediator(binding.appbar.tabLayout, binding.fragmentViewpager,
                (tab, position) -> {
                    switch (position) {
                        case DetailPagerAdapter.INFO_FRAG_POS:
                            tab.setText(R.string.torrent_info);
                            break;
                        case DetailPagerAdapter.STATE_FRAG_POS:
                            tab.setText(R.string.torrent_state);
                            break;
                        case DetailPagerAdapter.FILES_FRAG_POS:
                            tab.setText(R.string.torrent_files);
                            break;
                        case DetailPagerAdapter.TRACKERS_FRAG_POS:
                            tab.setText(R.string.torrent_trackers);
                            break;
                        case DetailPagerAdapter.PEERS_FRAG_POS:
                            tab.setText(R.string.torrent_peers);
                            break;
                        case DetailPagerAdapter.PIECES_FRAG_POS:
                            tab.setText(R.string.torrent_pieces);
                            break;
                    }
                }
        ).attach();
        binding.fragmentViewpager.setCurrentItem(currentFragPos);

        FragmentManager fm = getChildFragmentManager();
        deleteTorrentDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG);
        errReportDialog = (ErrorReportDialog) fm.findFragmentByTag(TAG_ERR_REPORT_DIALOG);
        addTrackersDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_ADD_TRACKERS_DIALOG);
        speedLimitDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_SPEED_LIMIT_DIALOG);

        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        activity.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.detail_torrent, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == android.R.id.home) {
                    finish(new Intent(), FragmentCallback.ResultCode.BACK);
                } else if (itemId == R.id.pause_resume_torrent_menu) {
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
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeTorrentInfo();
        subscribeAlertDialog();
        subscribeMsgViewModel();
        subscribeFreeSpaceError();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(TAG_TORRENT_ID, torrentId);
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
                            finish(new Intent(), FragmentCallback.ResultCode.CANCEL);
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

    private void subscribeMsgViewModel() {
        disposables.add(msgViewModel.observeFragmentInActionMode()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((inActionMode) -> setTabLayoutColor(
                        Utils.getAttributeColor(
                                activity,
                                inActionMode ? R.attr.actionModeBackground : R.attr.toolbarColor
                        )
                )));
    }

    private void subscribeFreeSpaceError() {
        disposables.add(viewModel.observeFreeSpaceError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(__ -> showFreeSpaceErrorToast()));
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null)
                        return;

                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENT_DIALOG) && deleteTorrentDialog != null) {
                                deleteTorrent();
                                deleteTorrentDialog.dismiss();
                            } else if (event.dialogTag.equals(TAG_MAGNET_INCLUDE_PRIOR_DIALOG)) {
                                shareMagnet(true);
                            } else if (event.dialogTag.equals(TAG_ERR_REPORT_DIALOG) && errReportDialog != null) {
                                Dialog dialog = errReportDialog.getDialog();
                                if (dialog != null) {
                                    TextInputEditText editText = dialog.findViewById(R.id.comment);
                                    Editable e = editText.getText();
                                    String comment = (e == null ? null : e.toString());

                                    Utils.reportError(viewModel.errorReport, comment);
                                    errReportDialog.dismiss();
                                }
                            } else if (event.dialogTag.equals(TAG_ADD_TRACKERS_DIALOG) && addTrackersDialog != null) {
                                addTrackers(false);
                            } else if (event.dialogTag.equals(TAG_SPEED_LIMIT_DIALOG) && speedLimitDialog != null) {
                                setSpeedLimit();
                                speedLimitDialog.dismiss();
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENT_DIALOG) && deleteTorrentDialog != null) {
                                deleteTorrentDialog.dismiss();
                            } else if (event.dialogTag.equals(TAG_MAGNET_INCLUDE_PRIOR_DIALOG)) {
                                shareMagnet(false);
                            } else if (event.dialogTag.equals(TAG_ERR_REPORT_DIALOG) && errReportDialog != null) {
                                errReportDialog.dismiss();
                            } else if (event.dialogTag.equals(TAG_ADD_TRACKERS_DIALOG) && addTrackersDialog != null) {
                                addTrackers(true);
                            } else if (event.dialogTag.equals(TAG_SPEED_LIMIT_DIALOG) && speedLimitDialog != null) {
                                speedLimitDialog.dismiss();
                            }
                            break;
                        case NEUTRAL_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_ADD_TRACKERS_DIALOG) && addTrackersDialog != null)
                                addTrackersDialog.dismiss();
                            break;
                        case DIALOG_SHOWN:
                            if (event.dialogTag.equals(TAG_ADD_TRACKERS_DIALOG) && addTrackersDialog != null)
                                initAddTrackersDialog();
                            else if (event.dialogTag.equals(TAG_SPEED_LIMIT_DIALOG) && speedLimitDialog != null)
                                initSpeedLimitDialog();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void handleTorrentInfo(Pair<Torrent, TorrentInfo> info) {
        Torrent torrent = info.first;
        TorrentInfo ti = info.second;
        TorrentInfo oldTi = viewModel.info.getTorrentInfo();
        boolean alreadyPaused = oldTi != null && oldTi.stateCode == TorrentStateCode.PAUSED;

        viewModel.updateInfo(torrent, ti);

        if (ti == null || torrent == null)
            return;

        if (downloadingMetadata && ti.stateCode != TorrentStateCode.DOWNLOADING_METADATA)
            activity.invalidateOptionsMenu();
        downloadingMetadata = torrent.downloadingMetadata;

        if (ti.stateCode == TorrentStateCode.PAUSED || alreadyPaused) {
            /* Redraw pause/resume menu */
            if (Utils.isTwoPane(activity))
                prepareOptionsMenu(binding.appbar.toolbar.getMenu());
            else
                activity.invalidateOptionsMenu();
        }
    }

    private void setTabLayoutColor(int color) {
        if (Utils.isTwoPane(activity))
            return;

        binding.appbar.tabLayout.setBackgroundColor(color);
    }

    ViewPager2.OnPageChangeCallback viewPagerListener = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            currentFragPos = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    };

    private void showFreeSpaceErrorToast() {
        Snackbar.make(binding.coordinatorLayout,
                        R.string.error_free_space,
                        Snackbar.LENGTH_LONG)
                .show();
    }

    private void prepareOptionsMenu(Menu menu) {
        MenuItem pauseResume = menu.findItem(R.id.pause_resume_torrent_menu);
        Torrent torrent = viewModel.info.getTorrent();
        TorrentInfo ti = viewModel.info.getTorrentInfo();
        if (torrent == null || ti == null || ti.stateCode != TorrentStateCode.PAUSED) {
            pauseResume.setTitle(R.string.pause_torrent);
            if (!Utils.isTwoPane(activity))
                pauseResume.setIcon(R.drawable.ic_pause_white_24dp);

        } else {
            pauseResume.setTitle(R.string.resume_torrent);
            if (!Utils.isTwoPane(activity))
                pauseResume.setIcon(R.drawable.ic_play_arrow_white_24dp);
        }

        MenuItem saveTorrentFile = menu.findItem(R.id.save_torrent_file_menu);
        if (torrent != null && torrent.downloadingMetadata)
            saveTorrentFile.setVisible(false);
        else
            saveTorrentFile.setVisible(true);
    }

    private void deleteTorrentDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
            deleteTorrentDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    getString(R.string.delete_selected_torrent),
                    R.layout.dialog_delete_torrent,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            deleteTorrentDialog.show(fm, TAG_DELETE_TORRENT_DIALOG);
        }
    }

    private void shareMagnetDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_MAGNET_INCLUDE_PRIOR_DIALOG) == null) {
            BaseAlertDialog shareMagnetDialog = BaseAlertDialog.newInstance(
                    getString(R.string.share_magnet),
                    null,
                    R.layout.dialog_magnet_include_prior,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            shareMagnetDialog.show(fm, TAG_MAGNET_INCLUDE_PRIOR_DIALOG);
        }
    }

    private void addTrackersDialog() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_ADD_TRACKERS_DIALOG) == null) {
            addTrackersDialog = BaseAlertDialog.newInstance(
                    getString(R.string.add_trackers),
                    getString(R.string.dialog_add_trackers),
                    R.layout.dialog_multiline_text_input,
                    getString(R.string.add),
                    getString(R.string.replace),
                    getString(R.string.cancel),
                    false);

            addTrackersDialog.show(fm, TAG_ADD_TRACKERS_DIALOG);
        }
    }

    private void torrentSaveChooseDialog() {
        Intent i = new Intent(activity, FileManagerFragment.class);
        FileManagerConfig config = new FileManagerConfig(null,
                null,
                FileManagerConfig.Mode.SAVE_FILE);
        config.fileName = viewModel.mutableParams.getName();
        config.mimeType = Utils.MIME_TORRENT;

        i.putExtra(FileManagerFragment.TAG_CONFIG, config);
        saveTorrentFileChoose.launch(i);
    }

    private void saveErrorTorrentFileDialog(Exception e) {
        if (!isAdded())
            return;

        viewModel.errorReport = e;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_ERR_REPORT_DIALOG) == null) {
            errReportDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_save_torrent_file),
                    (e != null ? Log.getStackTraceString(e) : null));

            errReportDialog.show(fm, TAG_ERR_REPORT_DIALOG);
        }
    }

    private void speedLimitDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) == null) {
            speedLimitDialog = BaseAlertDialog.newInstance(
                    getString(R.string.speed_limit_title),
                    getString(R.string.torrent_speed_limit_dialog),
                    R.layout.dialog_speed_limit,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            speedLimitDialog.show(fm, TAG_SPEED_LIMIT_DIALOG);
        }
    }

    private void deleteTorrent() {
        Dialog dialog = deleteTorrentDialog.getDialog();
        if (dialog == null)
            return;

        CheckBox withFiles = dialog.findViewById(R.id.delete_with_downloaded_files);
        viewModel.deleteTorrent(withFiles.isChecked());
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

    private void initAddTrackersDialog() {
        Dialog dialog = addTrackersDialog.getDialog();
        if (dialog == null)
            return;

        final TextInputEditText field = dialog.findViewById(R.id.multiline_text_input_dialog);
        final TextInputLayout fieldLayout = dialog.findViewById(R.id.layout_multiline_text_input_dialog);

        /* Dismiss error label if user has changed the text */
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fieldLayout.setErrorEnabled(false);
                fieldLayout.setError(null);

                /* Clear selection of invalid url */
                Spannable text = field.getText();
                if (text != null) {
                    ForegroundColorSpan[] errorSpans = text.getSpans(0, text.length(),
                            ForegroundColorSpan.class);
                    for (ForegroundColorSpan span : errorSpans)
                        text.removeSpan(span);
                }
            }
        });
    }

    private boolean checkAddTrackersField(List<String> strings,
                                          TextInputLayout layout,
                                          TextInputEditText field) {
        if (strings == null || layout == null)
            return false;

        if (strings.isEmpty()) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        boolean valid = true;
        int curLineStartIndex = 0;
        for (String s : strings) {
            if (!Utils.isValidTrackerUrl(s) && field.getText() != null) {
                TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{R.attr.colorError});
                /* Select invalid url */
                field.getText().setSpan(new ForegroundColorSpan(a.getColor(0, 0)),
                        curLineStartIndex,
                        curLineStartIndex + s.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                a.recycle();

                valid = false;
            }
            /* Considering newline char */
            curLineStartIndex += s.length() + 1;
        }

        if (valid) {
            layout.setErrorEnabled(false);
            layout.setError(null);
        } else {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_invalid_link));
            layout.requestFocus();
        }

        return valid;
    }

    private void addTrackers(boolean replace) {
        Dialog dialog = addTrackersDialog.getDialog();
        if (dialog == null)
            return;

        TextInputEditText field = dialog.findViewById(R.id.multiline_text_input_dialog);
        TextInputLayout fieldLayout = dialog.findViewById(R.id.layout_multiline_text_input_dialog);

        Editable editable = field.getText();
        if (editable == null)
            return;

        String text = editable.toString();

        List<String> urls = Observable.fromArray(text.split(Utils.getLineSeparator()))
                .filter((s) -> !s.isEmpty())
                .toList()
                .blockingGet();

        if (checkAddTrackersField(urls, fieldLayout, field))
            addTrackersDialog.dismiss();
        else
            return;

        if (checkAddTrackersField(urls, fieldLayout, field))
            addTrackersDialog.dismiss();
        else
            return;

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.decode = false;
        List<String> normalizedUrls = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (TextUtils.isEmpty(url))
                continue;
            try {
                normalizedUrls.add(NormalizeUrl.normalize(url, options));

            } catch (NormalizeUrlException e) {
                /* Ignore */
            }
        }

        if (replace)
            viewModel.replaceTrackers(normalizedUrls);
        else
            viewModel.addTrackers(normalizedUrls);
    }

    private void initSpeedLimitDialog() {
        Dialog dialog = speedLimitDialog.getDialog();
        if (dialog == null)
            return;

        TextInputEditText upload = dialog.findViewById(R.id.upload_limit);
        TextInputEditText download = dialog.findViewById(R.id.download_limit);

        int minSpeedLimit = 0;
        InputFilter[] filter = new InputFilter[]{InputFilterRange.UNSIGNED_INT};

        upload.setFilters(filter);

        int uploadSpeedLimit = viewModel.getUploadSpeedLimit();
        int downloadSpeedLimit = viewModel.getDownloadSpeedLimit();

        if (TextUtils.isEmpty(upload.getText()))
            upload.setText((uploadSpeedLimit != -1 ?
                    Integer.toString(uploadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));

        download.setFilters(filter);
        if (TextUtils.isEmpty(download.getText()))
            download.setText((downloadSpeedLimit != -1 ?
                    Integer.toString(downloadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));
    }

    private void setSpeedLimit() {
        Dialog dialog = speedLimitDialog.getDialog();
        if (dialog == null)
            return;

        TextInputEditText upload = dialog.findViewById(R.id.upload_limit);
        TextInputEditText download = dialog.findViewById(R.id.download_limit);

        Editable uploadEditable = upload.getText();
        Editable downloadEditable = download.getText();
        if (TextUtils.isEmpty(uploadEditable) || TextUtils.isEmpty(uploadEditable))
            return;

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
        viewModel.setSpeedLimit(uploadSpeedLimit, downloadSpeedLimit);
    }

    final ActivityResultLauncher<Intent> saveTorrentFileChoose = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != Activity.RESULT_OK)
                    return;

                if (data == null || data.getData() == null) {
                    saveErrorTorrentFileDialog(null);
                    return;
                }

                Uri path = data.getData();
                try {
                    viewModel.copyTorrentFile(path);

                    Snackbar.make(binding.coordinatorLayout,
                                    getString(R.string.save_torrent_file_successfully),
                                    Snackbar.LENGTH_SHORT)
                            .show();

                } catch (IOException | UnknownUriException e) {
                    saveErrorTorrentFileDialog(e);
                }
            }
    );

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        if (activity instanceof FragmentCallback)
            ((FragmentCallback) activity).onFragmentFinished(this, intent, code);
    }
}
