package ch.pokemoncardfinder.local;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Schedules background refreshes and enforces the user's local refresh window. */
public final class BackgroundRefreshScheduler {
    private static final String WORK_NAME = "migros-background-stock-refresh";
    private static final String COOP_WORK_NAME = "coop-background-stock-refresh";
    private static final TimeZone USER_TIME_ZONE = TimeZone.getTimeZone("Europe/Zurich");

    private BackgroundRefreshScheduler() {}

    public static boolean isAllowedRefreshTime() {
        Calendar now = Calendar.getInstance(USER_TIME_ZONE);
        return now.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                && now.get(Calendar.HOUR_OF_DAY) >= 8
                && now.get(Calendar.HOUR_OF_DAY) < 21;
    }

    /** Used by the foreground timer to wait until the next allowed morning. */
    public static long millisUntilNextAllowedWindow() {
        Calendar now = Calendar.getInstance(USER_TIME_ZONE);
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                && now.get(Calendar.HOUR_OF_DAY) >= 8
                && now.get(Calendar.HOUR_OF_DAY) < 21) return 0L;

        Calendar next = (Calendar) now.clone();
        if (next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                || next.get(Calendar.HOUR_OF_DAY) >= 21) {
            next.add(Calendar.DAY_OF_MONTH, 1);
            while (next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                next.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        return Math.max(0L, next.getTimeInMillis() - now.getTimeInMillis());
    }

    public static void syncCoop(Context context, boolean enabled) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!enabled) {
            manager.cancelUniqueWork(COOP_WORK_NAME);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundCoopWorker.class, 90, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(COOP_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void sync(Context context, boolean enabled) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundMigrosWorker.class, 90, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
