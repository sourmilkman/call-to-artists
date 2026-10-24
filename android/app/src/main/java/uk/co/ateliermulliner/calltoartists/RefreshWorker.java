package uk.co.ateliermulliner.calltoartists;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Daily: redraw the countdown widget and check Artquest for new listings.
 * Notifies only when listings appear that weren't there last time.
 */
public class RefreshWorker extends Worker {

    static final String WORK_NAME = "cta-daily-refresh";
    static final String PREFS = "cta_refresh";
    static final String SEEN_KEY = "aq_seen";
    static final String CHANNEL = "cta_new_calls";
    static final String ARTQUEST_URL = "https://artquest.org.uk/opportunities/";
    static final Pattern ENTRY = Pattern.compile("([^<>]{3,200}?)\\s*\\(Deadline:\\s*(\\d{2}/\\d{2}/\\d{4})\\)");

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context context) {
        Constraints c = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(RefreshWorker.class, 24, TimeUnit.HOURS)
            .setConstraints(c)
            .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        DeadlineWidget.updateAll(ctx);

        String html;
        try {
            html = fetch(ARTQUEST_URL);
        } catch (Exception e) {
            return Result.retry();
        }

        Set<String> found = new HashSet<>();
        Matcher m = ENTRY.matcher(html);
        while (m.find()) {
            String title = m.group(1).trim().replaceAll("\\s+", " ");
            found.add(title + "|" + m.group(2));
        }
        if (found.isEmpty()) return Result.success(); // page layout changed; app will flag it

        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prev = p.getString(SEEN_KEY, null);
        Set<String> seen = new HashSet<>();
        if (prev != null) {
            try {
                JSONArray a = new JSONArray(prev);
                for (int i = 0; i < a.length(); i++) seen.add(a.getString(i));
            } catch (Exception ignored) { }
        }

        int fresh = 0;
        for (String k : found) if (!seen.contains(k)) fresh++;

        p.edit().putString(SEEN_KEY, new JSONArray(found).toString()).apply();

        if (prev != null && fresh > 0) notifyNew(ctx, fresh);
        return Result.success();
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", "CallToArtists/personal (Android)");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    private static void notifyNew(Context ctx, int count) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "New open calls", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cta)
            .setContentTitle(count == 1 ? "1 new open call on Artquest" : count + " new open calls on Artquest")
            .setContentText("Tap to review and add deadlines")
            .setContentIntent(pi)
            .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(ctx).notify(4201, b.build());
        } catch (SecurityException ignored) { }
    }
}
