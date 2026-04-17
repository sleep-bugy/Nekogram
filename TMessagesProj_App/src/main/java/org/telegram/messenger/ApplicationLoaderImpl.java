package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import java.io.File;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateLayout;

import tw.nekomimi.nekogram.Extra;
import tw.nekomimi.nekogram.helpers.RepoUpdateHelper;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    protected boolean isStandalone() {
        return Extra.isDirectApp();
    }

    @Override
    public boolean showUpdateAppPopup(Context context, TLRPC.TL_help_appUpdate update, int account) {
        try {
            (new UpdateAppAlertDialog(context, update, account)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        return RepoUpdateHelper.getInstance().showUpdatePopup(context, update, account);
    }

    @Override
    public boolean isCustomUpdate() {
        return RepoUpdateHelper.getInstance().isEnabled();
    }

    @Override
    public void downloadUpdate() {
        RepoUpdateHelper.getInstance().downloadUpdate();
    }

    @Override
    public void cancelDownloadingUpdate() {
        RepoUpdateHelper.getInstance().cancelDownloadingUpdate();
    }

    @Override
    public boolean isDownloadingUpdate() {
        return RepoUpdateHelper.getInstance().isDownloadingUpdate();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return RepoUpdateHelper.getInstance().getDownloadingUpdateProgress();
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        RepoUpdateHelper.getInstance().checkUpdate(force, whenDone);
    }

    @Override
    public BetaUpdate getUpdate() {
        return RepoUpdateHelper.getInstance().getUpdate();
    }

    @Override
    public String getUpdateCheckError() {
        return RepoUpdateHelper.getInstance().getLastError();
    }

    @Override
    public File getDownloadedUpdateFile() {
        return RepoUpdateHelper.getInstance().getDownloadedUpdateFile();
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return new UpdateLayout(activity, sideMenuContainer);
    }
}
