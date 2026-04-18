package tw.nekomimi.nekogram.helpers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.URLUtil;

import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.LaunchActivity;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class RepoUpdateHelper {
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final Gson GSON = new Gson();

    private static volatile RepoUpdateHelper instance;

    private final OkHttpClient client = new OkHttpClient();
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long finishedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            Long activeDownloadId;
            synchronized (RepoUpdateHelper.this) {
                activeDownloadId = downloadId;
            }
            if (activeDownloadId == null || activeDownloadId != finishedDownloadId) {
                return;
            }
            Utilities.globalQueue.postRunnable(() -> handleDownloadFinished(finishedDownloadId));
        }
    };

    private boolean receiverRegistered;
    private BetaUpdate update;
    private String lastError;
    private Long downloadId;
    private long downloadedSize;
    private long totalSize;

    public static RepoUpdateHelper getInstance() {
        if (instance == null) {
            synchronized (RepoUpdateHelper.class) {
                if (instance == null) {
                    instance = new RepoUpdateHelper();
                }
            }
        }
        return instance;
    }

    public boolean isEnabled() {
        return !TextUtils.isEmpty(BuildConfig.CUSTOM_UPDATE_MANIFEST_URL);
    }

    public synchronized BetaUpdate getUpdate() {
        return update;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    public synchronized boolean isDownloadingUpdate() {
        return downloadId != null;
    }

    public synchronized float getDownloadingUpdateProgress() {
        if (downloadId == null || totalSize <= 0) {
            return 0.0f;
        }
        queryDownloadState(downloadId);
        return totalSize > 0 ? Math.min(1.0f, downloadedSize / (float) totalSize) : 0.0f;
    }

    public synchronized File getDownloadedUpdateFile() {
        if (update == null || TextUtils.isEmpty(update.url)) {
            return null;
        }
        File apk = resolveApkFile(update);
        return apk.exists() ? apk : null;
    }

    public void checkUpdate(boolean force, Runnable whenDone) {
        if (!isEnabled()) {
            synchronized (this) {
                update = null;
                lastError = null;
            }
            if (whenDone != null) {
                AndroidUtilities.runOnUIThread(whenDone);
            }
            return;
        }
        if (!force && Math.abs(System.currentTimeMillis() - SharedConfig.lastUpdateCheckTime) < MessagesController.getInstance(0).updateCheckDelay * 1000L) {
            if (whenDone != null) {
                AndroidUtilities.runOnUIThread(whenDone);
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            BetaUpdate nextUpdate = null;
            String nextError = null;
            try {
                Request request = new Request.Builder()
                        .url(BuildConfig.CUSTOM_UPDATE_MANIFEST_URL)
                        .header("Cache-Control", "no-cache")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException("HTTP " + response.code());
                    }
                    var body = response.body();
                    if (body == null) {
                        throw new IllegalStateException("Empty response body");
                    }
                    UpdateManifest manifest = GSON.fromJson(body.string(), UpdateManifest.class);
                    if (manifest != null && manifest.versionCode != null && manifest.versionCode > BuildConfig.VERSION_CODE && !TextUtils.isEmpty(manifest.url)) {
                        nextUpdate = manifest.toUpdate();
                    }
                }
            } catch (Exception e) {
                nextError = e.getLocalizedMessage();
                FileLog.e(e);
            }
            synchronized (RepoUpdateHelper.this) {
                SharedConfig.lastUpdateCheckTime = System.currentTimeMillis();
                SharedConfig.saveConfig();
                if (nextError == null) {
                    update = nextUpdate;
                }
                lastError = nextError;
            }
            if (whenDone != null) {
                AndroidUtilities.runOnUIThread(whenDone);
            }
        });
    }

    public boolean showUpdatePopup(Context context, BetaUpdate update, int account) {
        if (context == null || update == null) {
            return false;
        }
        TLRPC.TL_help_appUpdate appUpdate = new TLRPC.TL_help_appUpdate();
        appUpdate.version = update.version;
        appUpdate.can_not_skip = update.canNotSkip;
        if (!TextUtils.isEmpty(update.url)) {
            appUpdate.url = update.url;
            appUpdate.flags |= 4;
        }
        if (!TextUtils.isEmpty(update.changelog)) {
            appUpdate.text = update.changelog;
        }
        try {
            new UpdateAppAlertDialog(context, appUpdate, account).show();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public void downloadUpdate() {
        BetaUpdate currentUpdate;
        synchronized (this) {
            currentUpdate = update;
        }
        if (currentUpdate == null || TextUtils.isEmpty(currentUpdate.url)) {
            return;
        }
        File apk = resolveApkFile(currentUpdate);
        if (apk.exists()) {
            var launchActivity = LaunchActivity.instance;
            if (launchActivity != null) {
                ApkInstaller.installUpdate(launchActivity, apk);
            }
            return;
        }
        ensureReceiverRegistered();
        try {
            DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                throw new IllegalStateException("DownloadManager unavailable");
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(currentUpdate.url));
            request.setTitle(LocaleController.getString(R.string.UpdateNekogram));
            request.setDescription(currentUpdate.version);
            request.setMimeType(APK_MIME_TYPE);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(ApplicationLoader.applicationContext, Environment.DIRECTORY_DOWNLOADS, apk.getName());
            long nextDownloadId = downloadManager.enqueue(request);
            synchronized (this) {
                downloadId = nextDownloadId;
                downloadedSize = 0L;
                totalSize = currentUpdate.size != null ? currentUpdate.size : 0L;
                lastError = null;
            }
        } catch (Exception e) {
            synchronized (this) {
                lastError = e.getLocalizedMessage();
            }
            FileLog.e(e);
            showDownloadFailure(e.getLocalizedMessage());
        }
    }

    public void cancelDownloadingUpdate() {
        Long activeDownloadId;
        synchronized (this) {
            activeDownloadId = downloadId;
            downloadId = null;
            downloadedSize = 0L;
            totalSize = 0L;
        }
        if (activeDownloadId == null) {
            return;
        }
        try {
            DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.remove(activeDownloadId);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private synchronized void queryDownloadState(long activeDownloadId) {
        DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(activeDownloadId);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                downloadedSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void handleDownloadFinished(long finishedDownloadId) {
        boolean success = false;
        String failure = null;
        DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor cursor = null;
        try {
            if (downloadManager == null) {
                throw new IllegalStateException("DownloadManager unavailable");
            }
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(finishedDownloadId);
            cursor = downloadManager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IllegalStateException("Download not found");
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                BetaUpdate currentUpdate;
                synchronized (this) {
                    currentUpdate = update;
                }
                File apk = currentUpdate == null ? null : resolveApkFile(currentUpdate);
                success = apk != null && apk.exists();
                if (!success) {
                    failure = "Downloaded APK not found";
                }
            } else {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                failure = "Download failed (" + reason + ")";
            }
        } catch (Exception e) {
            failure = e.getLocalizedMessage();
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            synchronized (this) {
                downloadId = null;
                downloadedSize = 0L;
                totalSize = 0L;
                if (!success) {
                    lastError = failure;
                }
            }
        }
        final boolean downloadSuccess = success;
        final String downloadFailure = failure;
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            if (downloadSuccess) {
                showDownloadReady();
            } else if (downloadFailure != null) {
                showDownloadFailure(downloadFailure);
            }
        });
    }

    private void showDownloadReady() {
        LaunchActivity launchActivity = LaunchActivity.instance;
        File apk = getDownloadedUpdateFile();
        if (launchActivity == null || apk == null) {
            return;
        }
        launchActivity.showBulletin(factory -> factory.createSimpleBulletin(
                R.raw.chats_infotip,
                LocaleController.getString(R.string.UpdateNekogram),
                LocaleController.getString(R.string.Open),
                () -> ApkInstaller.installUpdate(launchActivity, apk)
        ));
    }

    private void showDownloadFailure(String reason) {
        if (reason == null) {
            return;
        }
        LaunchActivity launchActivity = LaunchActivity.instance;
        if (launchActivity == null) {
            return;
        }
        launchActivity.showBulletin(factory -> factory.createErrorBulletin(LocaleController.getString(R.string.ErrorOccurred) + "\n" + reason));
    }

    private void ensureReceiverRegistered() {
        synchronized (this) {
            if (receiverRegistered) {
                return;
            }
            receiverRegistered = true;
        }
        ContextCompat.registerReceiver(
                ApplicationLoader.applicationContext,
                downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private File resolveApkFile(BetaUpdate update) {
        File downloadsDir = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) {
            downloadsDir = ApplicationLoader.getFilesDirFixed("updates");
        }
        if (downloadsDir == null) {
            downloadsDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "updates");
            downloadsDir.mkdirs();
        }
        return new File(downloadsDir, resolveFileName(update));
    }

    private String resolveFileName(BetaUpdate update) {
        if (!TextUtils.isEmpty(update.fileName)) {
            return update.fileName;
        }
        String guessed = URLUtil.guessFileName(update.url, null, APK_MIME_TYPE);
        if (!TextUtils.isEmpty(guessed)) {
            return guessed;
        }
        return "Nekogram-" + update.version + "-" + update.versionCode + ".apk";
    }

    private static class UpdateManifest {
        @SerializedName("version")
        @Expose
        String version;

        @SerializedName("version_code")
        @Expose
        Integer versionCode;

        @SerializedName("url")
        @Expose
        String url;

        @SerializedName("changelog")
        @Expose
        String changelog;

        @SerializedName("file_name")
        @Expose
        String fileName;

        @SerializedName("sha256")
        @Expose
        String sha256;

        @SerializedName("size")
        @Expose
        Long size;

        @SerializedName("can_not_skip")
        @Expose
        Boolean canNotSkip;

        private BetaUpdate toUpdate() {
            return new BetaUpdate(
                    version,
                    versionCode,
                    changelog,
                    url,
                    fileName,
                    sha256,
                    size,
                    Boolean.TRUE.equals(canNotSkip)
            );
        }
    }
}
