/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

@SuppressLint("RestrictedApi")
public class CustomPreferenceGroupAdapter extends PreferenceGroupAdapter {
    public CustomPreferenceGroupAdapter(@NonNull PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);

        if (getItem(position) instanceof PreferenceCategory) {
            stylizePreferenceCategory(holder);
        } else {
            stylizePreference(holder);
        }
    }

    private void stylizePreferenceCategory(@NonNull PreferenceViewHolder holder) {
        TextView title = holder.itemView.findViewById(android.R.id.title);

        if (title != null) {
            title.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
            title.setTextColor(MaterialColors.getColor(title, R.attr.colorPrimary));
        }
    }

    private void stylizePreference(@NonNull PreferenceViewHolder holder) {
        var context = holder.itemView.getContext();
        TextView title = holder.itemView.findViewById(android.R.id.title);
        TextView summary = holder.itemView.findViewById(android.R.id.summary);

        var params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        params.rightMargin = Utils.dpToPx(context, 4);
        params.leftMargin = params.rightMargin;

        setPreferenceDrawable(context, holder);

        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        if (title != null) {
            title.setTextAppearance(R.style.TextAppearance_Material3_BodyLarge);
            title.setLineSpacing(Utils.dpToPx(context, 8), 1);
        }
        if (summary != null) {
            summary.setPadding(0, Utils.dpToPx(context, 8), 0, 0);
            summary.setTextAppearance(R.style.App_Components_ListItem_SupportingText);
        }
    }

    private void setPreferenceDrawable(@NonNull Context context, @NonNull PreferenceViewHolder holder) {
        try (var a = ThemeEnforcement.obtainStyledAttributes(
                context, null, R.styleable.MaterialCardView, R.attr.materialCardViewStyle, R.style.App_Components_ListItem)) {
            @SuppressLint("PrivateResource")
            var rippleColor = MaterialResources.getColorStateList(context, a, R.styleable.MaterialCardView_rippleColor);
            if (rippleColor == null) {
                rippleColor = ColorStateList.valueOf(
                        MaterialColors.getColor(holder.itemView, R.attr.colorControlHighlight));
            }
            var foregroundShapeDrawable = new MaterialShapeDrawable();
            @SuppressLint("PrivateResource")
            var shape = ShapeAppearanceModel.builder(
                            context,
                            a.getResourceId(R.styleable.MaterialCardView_shapeAppearance, 0),
                            a.getResourceId(R.styleable.MaterialCardView_shapeAppearanceOverlay, 0))
                    .build();
            foregroundShapeDrawable.setShapeAppearanceModel(shape);
            var foreground = new RippleDrawable(rippleColor, null, foregroundShapeDrawable);
            holder.itemView.setForeground(foreground);
            holder.itemView.setBackgroundColor(
                    ResourcesCompat.getColor(context.getResources(), android.R.color.transparent, null)
            );
        }
    }
}
