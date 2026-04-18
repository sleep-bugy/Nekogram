package tw.nekomimi.nekogram.helpers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;

import java.util.concurrent.TimeUnit;

public class SleepTimerHelper {

    private static final String PREFS_NAME = "nekoconfig";
    private static final String KEY_SLEEP_TIMER_END = "sleepTimerEndTime";
    private static final int REQUEST_CODE = 2104;

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static PendingIntent getPendingIntent() {
        Intent intent = new Intent(ApplicationLoader.applicationContext, Receiver.class);
        return PendingIntent.getBroadcast(ApplicationLoader.applicationContext, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static boolean isActive() {
        return getEndTime() > System.currentTimeMillis();
    }

    public static long getEndTime() {
        return getPreferences().getLong(KEY_SLEEP_TIMER_END, 0);
    }

    public static void schedule(int seconds) {
        if (seconds <= 0) {
            cancel();
            return;
        }

        long triggerAtMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        getPreferences().edit().putLong(KEY_SLEEP_TIMER_END, triggerAtMillis).apply();

        AlarmManager alarmManager = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent();
        alarmManager.cancel(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static void cancel() {
        getPreferences().edit().remove(KEY_SLEEP_TIMER_END).apply();
        AlarmManager alarmManager = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getPendingIntent());
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                MediaController mediaController = MediaController.getInstance();
                MessageObject playingMessage = mediaController.getPlayingMessageObject();
                if (playingMessage != null && !mediaController.isMessagePaused()) {
                    mediaController.pauseMessage(playingMessage);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                cancel();
            }
        }
    }
}
