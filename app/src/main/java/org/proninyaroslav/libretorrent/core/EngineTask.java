/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.util.Log;

/*
 * Encapsulates TorrentEngine object in stream and provides an interface for management.
 */

public class EngineTask implements Runnable
{
    @SuppressWarnings("unused")
    private static final String TAG = EngineTask.class.getSimpleName();

    private static final int SYNC_TIME = 1000; /* ms */

    private Context context;
    private TorrentEngineCallback callback;
    private TorrentEngine engine;
    private boolean isCancelled = false;

    public EngineTask(Context context, TorrentEngineCallback callback)
    {
        this.context = context;
        this.callback = callback;
        try {
            engine = new TorrentEngine(this.context, this.callback);

        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            if (callback != null) {
                callback.onEngineInterrupted();
            }
        }
    }

    @Override
    public void run()
    {
        try {
            engine.start();

            /* Looping task work */
            while(!isCancelled) {
                Thread.sleep(SYNC_TIME);
            }

        } catch (InterruptedException e) {
            /* Ignore */
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));

            if (callback != null) {
                callback.onEngineInterrupted();
            }
        }
    }

    public void cancel()
    {
        isCancelled = true;
        callback = null;
        engine.stop();
        engine = null;
    }

    public TorrentEngine getEngine()
    {
        return engine;
    }
}
