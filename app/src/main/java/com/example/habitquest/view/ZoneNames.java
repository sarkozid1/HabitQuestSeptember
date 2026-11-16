package com.example.habitquest.view;

import android.content.Context;

import com.example.habitquest.R;
import com.example.habitquest.model.WorldZone;

/** A zónák megjelenített (fordítható) neve – a WorldZone belső neve angol azonosító. */
public final class ZoneNames {

    private ZoneNames() { }

    public static String of(Context ctx, WorldZone zone) {
        if (zone == WorldZone.MOUNTAIN) return ctx.getString(R.string.zone_mountain);
        if (zone == WorldZone.CITY) return ctx.getString(R.string.zone_city);
        return ctx.getString(R.string.zone_forest);
    }
}
