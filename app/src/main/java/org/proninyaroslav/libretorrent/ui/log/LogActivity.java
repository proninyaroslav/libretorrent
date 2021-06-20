/*
 * Copyright (C) 2020-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.library.baseAdapters.BR;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.ActivityLogBinding;
import org.proninyaroslav.libretorrent.ui.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

import io.reactivex.disposables.CompositeDisposable;

public class LogActivity extends AppCompatActivity
    implements LogAdapter.ClickListener
{
    private static final String TAG_AUTO_SCROLL = "auto_scroll";
    private static final String TAG_SCROLL_POSITION = "scroll_position";
    private static final String TAG_FILTER_DIALOG = "filter_dialog";

    private ActivityLogBinding binding;
    private LogViewModel viewModel;
    private Toolbar toolbar;
    private LinearLayoutManager layoutManager;
    private LogAdapter adapter;
    private Handler handler;
    private boolean ignoreScrollEvent;
    private boolean autoScroll = true;
    private int scrollPosition = 0;
    private CompositeDisposable disposables = new CompositeDisposable();
    private SessionLogFilterDialog filterDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();
        filterDialog = (SessionLogFilterDialog) fm.findFragmentByTag(TAG_FILTER_DIALOG);

        if (savedInstanceState != null) {
            autoScroll = savedInstanceState.getBoolean(TAG_AUTO_SCROLL);
            scrollPosition = savedInstanceState.getInt(TAG_SCROLL_POSITION);
        }

        handler = new Handler();

        viewModel = new ViewModelProvider(this).get(LogViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_log);
        binding.setViewModel(viewModel);

        toolbar = binding.rootLayout.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.log_journal);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener((v) -> finish());
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        updateToolbarSubtitle();

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.logList.setLayoutManager(layoutManager);
        binding.logList.setEmptyView(binding.emptyViewLog);
        TypedArray a = obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });
        binding.logList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        a.recycle();

        /* Disable animation, so as not to blink while updating the list */
        binding.logList.setItemAnimator(null);
        binding.logList.setLayoutAnimation(null);

        adapter = new LogAdapter(this);
        binding.logList.setAdapter(adapter);

        viewModel.observeLog().observe(this, (entries) ->
            adapter.submitList(entries, handleUpdateAdapter));

        binding.logList.addOnScrollListener(scrollCallback);

        binding.fabUp.setOnClickListener((v) -> {
            viewModel.pauseLog();

            hideFabUp();
            autoScroll = false;
            layoutManager.scrollToPositionWithOffset(0, 0);

            resumeLog();
        });

        binding.fabDown.setOnClickListener((v) -> {
            viewModel.pauseLog();

            hideFabUp();
            ignoreScrollEvent = true;
            autoScroll = true;
            layoutManager.scrollToPosition(adapter.getItemCount() - 1);

            resumeLog();
        });

        hideFabUp();
        hideFabDown();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        handler.removeCallbacksAndMessages(null);
        resumeLog();
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposables.clear();
        viewModel.mutableParams.removeOnPropertyChangedCallback(paramsCallback);
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        viewModel.mutableParams.addOnPropertyChangedCallback(paramsCallback);
    }

    private final androidx.databinding.Observable.OnPropertyChangedCallback paramsCallback =
            new androidx.databinding.Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId)
                {
                    if (propertyId == BR.logging)
                        invalidateOptionsMenu();
                }
            };

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putBoolean(TAG_AUTO_SCROLL, autoScroll);
        outState.putInt(TAG_SCROLL_POSITION, scrollPosition);

        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        menu.setGroupVisible(R.id.log_menu, viewModel.mutableParams.isLogging());

        MenuItem pauseResume = menu.findItem(R.id.pause_resume_log_menu);
        if (viewModel.logPausedManually()) {
            pauseResume.setIcon(R.drawable.ic_play_arrow_white_24dp);
            pauseResume.setTitle(R.string.resume_torrent);

        } else {
            pauseResume.setIcon(R.drawable.ic_pause_white_24dp);
            pauseResume.setTitle(R.string.pause_torrent);
        }

        MenuItem record = menu.findItem(R.id.record_log_menu);
        if (viewModel.logRecording()) {
            record.setIcon(R.drawable.ic_stop_white_24dp);
            record.setTitle(R.string.journal_stop_recording);

        } else {
            record.setIcon(R.drawable.ic_record_white_24dp);
            record.setTitle(R.string.journal_start_recording);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.pause_resume_log_menu) {
            pauseResumeLog();
        } else if (itemId == R.id.record_log_menu) {
            toggleRecord();
        } else if (itemId == R.id.save_log_menu) {
            saveLogPathChooseDialog();
        } else if (itemId == R.id.filter_log_menu) {
            showFilterDialog();
        } else if (itemId == R.id.log_settings_menu) {
            showLogSettings();
        }

        return true;
    }

    private void showFilterDialog()
    {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_FILTER_DIALOG) == null) {
            filterDialog = SessionLogFilterDialog.newInstance();
            filterDialog.show(fm, TAG_FILTER_DIALOG);
        }
    }

    private void showLogSettings()
    {
        startActivity(new Intent(this, LogSettingsActivity.class));
    }

    private void pauseResumeLog()
    {
        boolean newPausedState = !viewModel.logPausedManually();
        if (newPausedState)
            viewModel.pauseLogManually();
        else
            viewModel.resumeLogManually();

        invalidateOptionsMenu();
    }

    private void resumeLog()
    {
        if (!viewModel.logPausedManually())
            viewModel.resumeLog();
    }

    private void toggleRecord()
    {
        boolean recording = !viewModel.logRecording();

        if (recording && viewModel.logPausedManually())
            return;

        if (recording) {
            Snackbar.make(binding.coordinatorLayout,
                    getString(R.string.journal_started_recording),
                    Snackbar.LENGTH_SHORT)
                    .show();

            viewModel.startLogRecording();

            invalidateOptionsMenu();

        } else {
            viewModel.pauseLog();
            saveLogPathChooseDialog();
        }
    }

    private void saveLogPathChooseDialog()
    {
        Intent i = new Intent(this, FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(
                null,
                getString(R.string.pref_journal_save_log_to),
                FileManagerConfig.SAVE_FILE_MODE);
        config.fileName = viewModel.getSaveLogFileName();
        config.mimeType = Utils.MIME_TEXT_PLAIN;

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        saveLogPathChoose.launch(i);
    }

    final ActivityResultLauncher<Intent> saveLogPathChoose = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    Uri filePath = data.getData();
                    if (filePath == null)
                        return;

                    viewModel.saveLog(filePath);

                } else {
                    viewModel.stopLogRecording();
                    resumeLog();
                }

                invalidateOptionsMenu();
            }
    );

    RecyclerView.OnScrollListener scrollCallback = new RecyclerView.OnScrollListener() {

        int lastDy = 0;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
        {
            super.onScrolled(recyclerView, dx, dy);

            if (dy > 0 && lastDy <= 0) {
                hideFabUp();
                showFabDown();

            } else if (dy < 0 && lastDy >= 0) {
                showFabUp();
                hideFabDown();
            }
            lastDy = dy;
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState)
        {
            super.onScrollStateChanged(recyclerView, newState);

            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                autoScroll = false;
                if (lastDy > 0) {
                    hideFabUp();
                    showFabDown();

                } else if (lastDy < 0) {
                    showFabUp();
                    hideFabDown();
                }

            } else {
                int firstPos = -1;
                if (!autoScroll && newState == RecyclerView.SCROLL_STATE_IDLE)
                    firstPos = layoutManager.findFirstCompletelyVisibleItemPosition();

                int pos = layoutManager.findLastCompletelyVisibleItemPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    autoScroll = false;
                    return;
                }

                if (ignoreScrollEvent) {
                    if (pos == adapter.getItemCount())
                        ignoreScrollEvent = false;
                    return;
                }

                if (pos == 0)
                    hideFabUp();

                if (firstPos == RecyclerView.NO_POSITION)
                    firstPos = layoutManager.findFirstCompletelyVisibleItemPosition();

                scrollPosition = firstPos;
                autoScroll = pos == adapter.getItemCount() - 1;

                if (autoScroll) {
                    hideFabUp();
                    hideFabDown();
                }
            }
        }
    };

    private void showFabUp()
    {
        handler.removeCallbacks(hideFabUpRunnable);
        binding.fabUp.show();
        handler.postDelayed(hideFabUpRunnable, 2000);
    }

    private void hideFabUp()
    {
        handler.removeCallbacks(hideFabUpRunnable);
        binding.fabUp.hide();
    }

    private void showFabDown()
    {
        handler.removeCallbacks(hideFabDownRunnable);
        binding.fabDown.show();
        handler.postDelayed(hideFabDownRunnable, 2000);
    }

    private void hideFabDown()
    {
        handler.removeCallbacks(hideFabDownRunnable);
        binding.fabDown.hide();
    }

    private final Runnable hideFabUpRunnable = () -> binding.fabUp.hide();

    private final Runnable hideFabDownRunnable = () -> binding.fabDown.hide();

    private final Runnable handleUpdateAdapter = () -> {
        scrollLogList();
        updateToolbarSubtitle();
    };

    private void updateToolbarSubtitle()
    {
        int numEntries = viewModel.getLogEntriesCount();
        if (numEntries > 1)
            toolbar.setSubtitle(Integer.toString(numEntries));
        else
            toolbar.setSubtitle(null);
    }

    private void scrollLogList()
    {
        if (autoScroll)
            layoutManager.scrollToPosition(adapter.getItemCount() - 1);
        else
            layoutManager.scrollToPositionWithOffset(scrollPosition, 0);
    }

    @Override
    public void onItemClicked(@NonNull LogEntry entry)
    {
        if (viewModel.copyLogEntryToClipboard(entry)) {
            Snackbar.make(binding.coordinatorLayout,
                    R.string.text_copied_to_clipboard,
                    Snackbar.LENGTH_SHORT)
                    .show();
        }
    }
}
