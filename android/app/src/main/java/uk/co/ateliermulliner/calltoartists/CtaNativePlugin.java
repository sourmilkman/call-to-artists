package uk.co.ateliermulliner.calltoartists;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

/**
 * Native bridge for Call to Artists:
 * calendar read/write, home-screen widget data, share-to-app, file export, daily refresh.
 */
@CapacitorPlugin(
    name = "CtaNative",
    permissions = {
        @Permission(alias = "calendar", strings = { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR })
    }
)
public class CtaNativePlugin extends Plugin {

    static final String WIDGET_PREFS = "cta_widget";
    static final String WIDGET_KEY = "items";

    private String pendingShareText = null;
    private String pendingShareSubject = null;

    @Override
    public void load() {
        if (getActivity() != null) readShareIntent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        readShareIntent(intent);
        if (pendingShareText != null) {
            getBridge().triggerWindowJSEvent("ctaShare");
        }
    }

    private void readShareIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            pendingShareText = intent.getStringExtra(Intent.EXTRA_TEXT);
            pendingShareSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            // Consume so a rotation / resume doesn't re-deliver it.
            intent.setAction(Intent.ACTION_MAIN);
        }
    }

    // ---------- Share-to-app ----------

    @PluginMethod
    public void consumeShare(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("text", pendingShareText);
        ret.put("subject", pendingShareSubject);
        pendingShareText = null;
        pendingShareSubject = null;
        call.resolve(ret);
    }

    // ---------- Calendar ----------

    private boolean hasCalendar() {
        return getPermissionState("calendar") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void listCalendars(PluginCall call) {
        if (!hasCalendar()) { requestPermissionForAlias("calendar", call, "calendarPermsCallback"); return; }
        doListCalendars(call);
    }

    @PluginMethod
    public void addEvent(PluginCall call) {
        if (!hasCalendar()) { requestPermissionForAlias("calendar", call, "calendarPermsCallback"); return; }
        doAddEvent(call);
    }

    @PluginMethod
    public void deleteEvent(PluginCall call) {
        if (!hasCalendar()) { requestPermissionForAlias("calendar", call, "calendarPermsCallback"); return; }
        doDeleteEvent(call);
    }

    @PermissionCallback
    private void calendarPermsCallback(PluginCall call) {
        if (!hasCalendar()) { call.reject("Calendar permission was not granted"); return; }
        switch (call.getMethodName()) {
            case "listCalendars": doListCalendars(call); break;
            case "addEvent": doAddEvent(call); break;
            case "deleteEvent": doDeleteEvent(call); break;
            default: call.reject("Unknown method");
        }
    }

    private void doListCalendars(PluginCall call) {
        String[] proj = {
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.IS_PRIMARY, Calendars.VISIBLE, Calendars.CALENDAR_COLOR
        };
        JSArray list = new JSArray();
        ContentResolver cr = getContext().getContentResolver();
        try (Cursor c = cr.query(Calendars.CONTENT_URI, proj,
                Calendars.CALENDAR_ACCESS_LEVEL + ">=?",
                new String[] { String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR) }, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    JSObject o = new JSObject();
                    o.put("id", String.valueOf(c.getLong(0)));
                    o.put("name", c.getString(1));
                    o.put("account", c.getString(2));
                    o.put("accountType", c.getString(3));
                    o.put("primary", c.getInt(4) == 1);
                    o.put("visible", c.getInt(5) == 1);
                    o.put("color", String.format("#%06X", 0xFFFFFF & c.getInt(6)));
                    list.put(o);
                }
            }
        } catch (Exception e) {
            call.reject("Could not read calendars: " + e.getMessage());
            return;
        }
        JSObject ret = new JSObject();
        ret.put("calendars", list);
        call.resolve(ret);
    }

    /**
     * Options: calendarId (string), title, description, location,
     * start "YYYY-MM-DD", end "YYYY-MM-DD" (inclusive, optional),
     * time "HH:MM" (optional; timed event in tz, default Europe/London), durationMin,
     * reminders: [minutes before].
     */
    private void doAddEvent(PluginCall call) {
        String calIdStr = call.getString("calendarId");
        String title = call.getString("title", "Deadline");
        String start = call.getString("start");
        if (calIdStr == null || start == null) { call.reject("calendarId and start are required"); return; }

        long calId;
        LocalDate startDate;
        try {
            calId = Long.parseLong(calIdStr);
            startDate = LocalDate.parse(start);
        } catch (Exception e) {
            call.reject("Bad calendarId or start date");
            return;
        }

        String end = call.getString("end");
        String time = call.getString("time");
        int duration = call.getInt("durationMin", 30);

        ContentValues v = new ContentValues();
        v.put(Events.CALENDAR_ID, calId);
        v.put(Events.TITLE, title);
        v.put(Events.DESCRIPTION, call.getString("description", ""));
        v.put(Events.EVENT_LOCATION, call.getString("location", ""));

        try {
            if (time != null && !time.isEmpty()) {
                String tz = call.getString("tz", "");
                ZoneId zone = ZoneId.of(tz == null || tz.isEmpty() ? "Europe/London" : tz);
                String[] hm = time.split(":");
                LocalDateTime ldt = startDate.atTime(Integer.parseInt(hm[0]), Integer.parseInt(hm[1]));
                long startMs = ldt.atZone(zone).toInstant().toEpochMilli();
                v.put(Events.DTSTART, startMs);
                v.put(Events.DTEND, startMs + duration * 60_000L);
                v.put(Events.EVENT_TIMEZONE, zone.getId());
                v.put(Events.ALL_DAY, 0);
            } else {
                // All-day events must be stored at UTC midnight, end exclusive.
                LocalDate endDate = (end != null && !end.isEmpty()) ? LocalDate.parse(end) : startDate;
                long startMs = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long endMs = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                v.put(Events.DTSTART, startMs);
                v.put(Events.DTEND, endMs);
                v.put(Events.EVENT_TIMEZONE, TimeZone.getTimeZone("UTC").getID());
                v.put(Events.ALL_DAY, 1);
            }
        } catch (Exception e) {
            call.reject("Bad date/time: " + e.getMessage());
            return;
        }

        JSArray reminders = call.getArray("reminders", new JSArray());
        v.put(Events.HAS_ALARM, reminders.length() > 0 ? 1 : 0);

        ContentResolver cr = getContext().getContentResolver();
        try {
            Uri uri = cr.insert(Events.CONTENT_URI, v);
            if (uri == null) { call.reject("Calendar insert failed"); return; }
            long eventId = Long.parseLong(uri.getLastPathSegment());
            List<Object> mins = reminders.toList();
            for (Object m : mins) {
                if (!(m instanceof Number)) continue;
                ContentValues r = new ContentValues();
                r.put(Reminders.EVENT_ID, eventId);
                r.put(Reminders.MINUTES, ((Number) m).intValue());
                r.put(Reminders.METHOD, Reminders.METHOD_ALERT);
                cr.insert(Reminders.CONTENT_URI, r);
            }
            JSObject ret = new JSObject();
            ret.put("eventId", String.valueOf(eventId));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Calendar write failed: " + e.getMessage());
        }
    }

    private void doDeleteEvent(PluginCall call) {
        String id = call.getString("eventId");
        if (id == null) { call.reject("eventId required"); return; }
        try {
            Uri uri = android.content.ContentUris.withAppendedId(Events.CONTENT_URI, Long.parseLong(id));
            int rows = getContext().getContentResolver().delete(uri, null, null);
            JSObject ret = new JSObject();
            ret.put("deleted", rows > 0);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Delete failed: " + e.getMessage());
        }
    }

    // ---------- Widget ----------

    /** json: JSON array string of {title,label,date:"YYYY-MM-DD",time?} sorted soonest first. */
    @PluginMethod
    public void setWidgetData(PluginCall call) {
        String json = call.getString("json", "[]");
        Context ctx = getContext();
        ctx.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit().putString(WIDGET_KEY, json).apply();
        DeadlineWidget.updateAll(ctx);
        call.resolve();
    }

    @PluginMethod
    public void pinWidget(PluginCall call) {
        Context ctx = getContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        JSObject ret = new JSObject();
        if (mgr.isRequestPinAppWidgetSupported()) {
            mgr.requestPinAppWidget(new ComponentName(ctx, DeadlineWidget.class), null, null);
            ret.put("requested", true);
        } else {
            ret.put("requested", false);
        }
        call.resolve(ret);
    }

    // ---------- Background refresh ----------

    @PluginMethod
    public void scheduleRefresh(PluginCall call) {
        RefreshWorker.schedule(getContext());
        call.resolve();
    }

    // ---------- Export ----------

    @PluginMethod
    public void shareFile(PluginCall call) {
        String name = call.getString("filename", "call-to-artists-backup.json");
        String content = call.getString("content", "");
        String mime = call.getString("mime", "application/json");
        try {
            File dir = new File(getContext().getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) { call.reject("Could not create export folder"); return; }
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, "Save or send backup");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("Export failed: " + e.getMessage());
        }
    }
}
