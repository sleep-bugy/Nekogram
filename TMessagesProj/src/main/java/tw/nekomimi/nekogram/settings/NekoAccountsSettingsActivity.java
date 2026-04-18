package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.helpers.AccountOrderHelper;

public class NekoAccountsSettingsActivity extends BaseNekoSettingsActivity {

    private final int accountsStartRow = 100;
    private final ArrayList<Integer> accounts = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        reloadAccounts();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        var fragmentView = super.createView(context);
        listView.listenReorder((section, items) -> {
            accounts.clear();
            for (int i = 0; i < items.size(); i++) {
                accounts.add(items.get(i).intValue);
            }
            AccountOrderHelper.saveOrder(accounts);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        });
        listView.allowReorder(accounts.size() > 1);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadAccounts();
        listView.allowReorder(accounts.size() > 1);
        listView.adapter.update(true);
    }

    private void reloadAccounts() {
        accounts.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        AccountOrderHelper.sortAccountNumbers(accounts);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.SettingsAccounts)));
        if (adapter != null) {
            adapter.reorderSectionStart();
        }
        for (int i = 0; i < accounts.size(); i++) {
            items.add(AccountOrderCellFactory.of(accountsStartRow + accounts.get(i), accounts.get(i)));
        }
        if (adapter != null) {
            adapter.reorderSectionEnd();
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.AccountOrderHintNeko)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= accountsStartRow && getParentActivity() != null) {
            var account = item.intValue;
            if (UserConfig.selectedAccount != account && org.telegram.ui.LaunchActivity.instance != null) {
                org.telegram.ui.LaunchActivity.instance.switchToAccount(account, true);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.AccountOrderNeko);
    }

    @Override
    protected String getKey() {
        return "accounts";
    }

    private static class AccountOrderCellFactory extends UItem.UItemFactory<AccountCell> {
        static {
            setup(new AccountOrderCellFactory());
        }

        @Override
        public AccountCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new AccountCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((AccountCell) view).setAccount(item.intValue, false, divider, listView.isReorderAllowed());
        }

        public static UItem of(int id, int account) {
            var item = UItem.ofFactory(AccountOrderCellFactory.class);
            item.id = id;
            item.intValue = account;
            return item;
        }
    }
}
