package io.github.hairpin01.metrolistlyricswidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

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
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                           int appWidgetId, Bundle newOptions) {
        // Resize changes the available size, so the layout must be re-evaluated.
        WidgetState.update(context, manager, new int[]{appWidgetId});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (Constants.ACTION_UPDATE.equals(action)) {
            // Network work launched directly by a receiver can be frozen/killed as soon
            // as onReceive returns. Keep this delivery alive until the new artwork has
            // finished and RemoteViews has received its follow-up update.
            final PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            boolean waitingForArtwork = false;
            try {
                WidgetState.save(app, intent);
                final String trackId = intent.getStringExtra(Constants.EXTRA_TRACK_ID);
                final String artwork = intent.getStringExtra(Constants.EXTRA_ARTWORK);
                waitingForArtwork = ArtworkLoader.ensureAsync(app, trackId, artwork, new Runnable() {
                    @Override public void run() {
                        try {
                            WidgetState.updateAll(app);
                        } finally {
                            pending.finish();
                        }
                    }
                });
                WidgetState.updateAll(app);
            } finally {
                if (!waitingForArtwork) pending.finish();
            }
        } else if (Constants.ACTION_CLEAR.equals(action)) {
            WidgetState.clear(context);
            WidgetState.updateAll(context);
        }
    }
}