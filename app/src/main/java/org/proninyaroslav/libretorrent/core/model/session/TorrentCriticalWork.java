/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

class TorrentCriticalWork
{
    public enum Type
    {
        APPLYING_PARAMS,
        MOVING,
        SAVE_RESUME
    }

    public class State
    {
        public boolean applyingParams;
        public boolean moving;
        public boolean saveResume;
        public long changeTime;

        public State(boolean applyingParams, boolean moving, boolean saveResume, long changeTime)
        {
            this.applyingParams = applyingParams;
            this.moving = moving;
            this.saveResume = saveResume;
            this.changeTime = changeTime;
        }

        public boolean isDuringChange()
        {
            return applyingParams || moving || saveResume;
        }
    }

    private ExecutorService exec = Executors.newFixedThreadPool(2);
    private boolean applyingParams;
    private boolean moving;
    private int saveResume;
    private BehaviorSubject<State> stateChangedEvent =
            BehaviorSubject.createDefault(new State(false, false, false, System.currentTimeMillis()));

    public boolean isApplyingParams()
    {
        return applyingParams;
    }

    public void setApplyingParams(boolean applyingParams)
    {
        this.applyingParams = applyingParams;
        emitChangedEvent();
    }

    public boolean isMoving()
    {
        return moving;
    }

    public void setMoving(boolean moving)
    {
        this.moving = moving;
        emitChangedEvent();
    }

    public boolean isSaveResume()
    {
        return saveResume > 0;
    }

    public void setSaveResume(boolean saveResume)
    {
        if (saveResume)
            ++this.saveResume;
        else
            --this.saveResume;
        emitChangedEvent();
    }

    public Observable<State> observeStateChanging()
    {
        return stateChangedEvent;
    }

    private void emitChangedEvent()
    {
        exec.submit(() -> {
            stateChangedEvent.onNext(
                    new State(applyingParams, moving, saveResume > 0, System.currentTimeMillis()));
        });
    }
}
