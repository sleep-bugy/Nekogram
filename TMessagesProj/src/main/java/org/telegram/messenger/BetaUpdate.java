package org.telegram.messenger;

import androidx.annotation.Nullable;

public class BetaUpdate {

    public final String version;
    public final int versionCode;

    @Nullable
    public final String changelog;

    @Nullable
    public final String url;

    @Nullable
    public final String fileName;

    @Nullable
    public final String sha256;

    @Nullable
    public final Long size;

    public final boolean canNotSkip;

    public BetaUpdate(String version, int versionCode, String changelog) {
        this(version, versionCode, changelog, null, null, null, null, false);
    }

    public BetaUpdate(String version, int versionCode, @Nullable String changelog, @Nullable String url, @Nullable String fileName, @Nullable String sha256, @Nullable Long size, boolean canNotSkip) {
        this.version = version;
        this.versionCode = versionCode;
        this.changelog = changelog;
        this.url = url;
        this.fileName = fileName;
        this.sha256 = sha256;
        this.size = size;
        this.canNotSkip = canNotSkip;
    }

    public boolean higherThan(BetaUpdate update) {
        return update == null || SharedConfig.versionBiggerOrEqual(version, update.version) && versionCode > update.versionCode;
    }

}
