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

package org.proninyaroslav.libretorrent.ui.main.drawer;

import java.util.ArrayList;
import java.util.List;

/*
 * Expandable group of clickable items (radio button-like behavior).
 */

public class DrawerGroup
{
    public static final long DEFAULT_SELECTED_ID = 0;

    public long id;
    public String name;
    public List<DrawerGroupItem> items = new ArrayList<>();
    private long selectedItemId = DEFAULT_SELECTED_ID;
    private boolean defaultExpandState;

    public DrawerGroup(long id, String name, boolean defaultExpandState)
    {
        this.id = id;
        this.name = name;
        this.defaultExpandState = defaultExpandState;
    }

    public void selectItem(long itemId)
    {
        selectedItemId = itemId;
    }

    public boolean isItemSelected(long itemId)
    {
        return itemId == selectedItemId;
    }

    public long getSelectedItemId()
    {
        return selectedItemId;
    }

    /*
     * true - expanded; false - collapsed
     */

    public boolean getDefaultExpandState()
    {
        return defaultExpandState;
    }
}
