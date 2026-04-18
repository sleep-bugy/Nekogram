package tw.nekomimi.nekogram.helpers;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashMap;
import java.util.List;

public class AccountOrderHelper {

    private static final String PREFS_NAME = "nekoconfig";
    private static final String KEY_ACCOUNT_ORDER = "accountOrder";

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    private static HashMap<Integer, Integer> getOrderMap() {
        var orderMap = new HashMap<Integer, Integer>();
        var value = getPreferences().getString(KEY_ACCOUNT_ORDER, "");
        if (value == null || value.isEmpty()) {
            return orderMap;
        }
        var parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            try {
                orderMap.put(Integer.parseInt(parts[i]), i);
            } catch (Exception ignore) {
            }
        }
        return orderMap;
    }

    public static void sortAccountNumbers(List<Integer> accountNumbers) {
        var orderMap = getOrderMap();
        accountNumbers.sort((o1, o2) -> {
            int p1 = orderMap.getOrDefault(o1, Integer.MAX_VALUE);
            int p2 = orderMap.getOrDefault(o2, Integer.MAX_VALUE);
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            return Long.compare(l1, l2);
        });
    }

    public static void saveOrder(List<Integer> accountNumbers) {
        var builder = new StringBuilder();
        for (int i = 0; i < accountNumbers.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(accountNumbers.get(i));
        }
        getPreferences().edit().putString(KEY_ACCOUNT_ORDER, builder.toString()).apply();
    }

    public static Integer getFirstAvailableAccount() {
        for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return null;
    }

    public static void showLimitReached(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        var builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.AddAccount));
        builder.setMessage(LocaleController.formatString(R.string.AccountLimitReachedNeko, UserConfig.MAX_ACCOUNT_COUNT));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create());
    }
}
