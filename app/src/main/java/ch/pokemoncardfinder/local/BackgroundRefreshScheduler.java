package ch.pokemoncardfinder.local;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Keeps the four-hour refresh scheduled across app closure and device restarts. */
public final class BackgroundRefreshScheduler {
    private static final String WORK_NAME = "migros-background-stock-refresh";
    private static final String COOP_WORK_NAME = "coop-background-stock-refresh";

    private BackgroundRefreshScheduler() {}


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
                BackgroundCoopWorker.class, 4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(COOP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
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
                BackgroundMigrosWorker.class, 4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
