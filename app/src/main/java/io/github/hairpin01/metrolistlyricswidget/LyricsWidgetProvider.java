package io.github.hairpin01.metrolistlyricswidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class LyricsWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetState.update(context, manager, appWidgetIds);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) WidgetState.clearLineState(context, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (Constants.ACTION_UPDATE.equals(action)) {
            WidgetState.save(context, intent);
            WidgetState.updateAll(context);
        } else if (Constants.ACTION_CLEAR.equals(action)) {
            WidgetState.clear(context);
            WidgetState.updateAll(context);
        }
    }
}