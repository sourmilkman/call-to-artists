package uk.co.ateliermulliner.calltoartists;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Home-screen countdown: next deadline with days remaining, plus the one after. */
public class DeadlineWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(context));
    }

    static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, DeadlineWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = build(context);
        for (int id : ids) mgr.updateAppWidget(id, views);
    }

    private static RemoteViews build(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_deadline);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);

        String json = context.getSharedPreferences(CtaNativePlugin.WIDGET_PREFS, Context.MODE_PRIVATE)
            .getString(CtaNativePlugin.WIDGET_KEY, "[]");

        LocalDate today = LocalDate.now();
        JSONObject first = null;
        JSONObject second = null;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                LocalDate d = LocalDate.parse(o.optString("date"));
                if (d.isBefore(today)) continue;
                if (first == null) first = o;
                else { second = o; break; }
            }
        } catch (Exception ignored) { }

        if (first == null) {
            v.setTextViewText(R.id.widget_days, "—");
            v.setTextViewText(R.id.widget_unit, "");
            v.setTextViewText(R.id.widget_title, "No upcoming deadlines");
            v.setTextViewText(R.id.widget_meta, "Track a call in the app");
            v.setTextViewText(R.id.widget_next, "");
            return v;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);
        LocalDate d1 = LocalDate.parse(first.optString("date"));
        long days = ChronoUnit.DAYS.between(today, d1);
        v.setTextViewText(R.id.widget_days, days == 0 ? "Today" : String.valueOf(days));
        v.setTextViewText(R.id.widget_unit, days == 0 ? "" : (days == 1 ? "day" : "days"));
        v.setTextViewText(R.id.widget_title, first.optString("title"));
        String time = first.optString("time", "");
        v.setTextViewText(R.id.widget_meta,
            first.optString("label") + " · " + d1.format(fmt) + (time.isEmpty() ? "" : " " + time));

        if (second != null) {
            try {
                LocalDate d2 = LocalDate.parse(second.optString("date"));
                long days2 = ChronoUnit.DAYS.between(today, d2);
                v.setTextViewText(R.id.widget_next, "Then: " + second.optString("title") + " · " + days2 + "d");
            } catch (Exception e) {
                v.setTextViewText(R.id.widget_next, "");
            }
        } else {
            v.setTextViewText(R.id.widget_next, "");
        }
        return v;
    }
}
