package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageFilterHelper;

public class NekoMessageFilterSettingsActivity extends BaseNekoSettingsActivity {

    private final int enableRow = rowId++;
    private final int keywordsRow = rowId++;
    private final int exactMatchRow = rowId++;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(enableRow, LocaleController.getString(R.string.MessageFiltersEnabled)).setChecked(NekoConfig.enableMessageFilter));
        items.add(UItem.asButton(keywordsRow, LocaleController.getString(R.string.MessageFiltersKeywords), getKeywordsValue()));
        items.add(UItem.asCheck(exactMatchRow, LocaleController.getString(R.string.MessageFiltersExactMatch), LocaleController.getString(R.string.MessageFiltersExactMatchDesc)).setChecked(NekoConfig.messageFilterExactMatch));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MessageFiltersAbout)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id == enableRow) {
            NekoConfig.toggleEnableMessageFilter();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.enableMessageFilter);
            }
        } else if (id == exactMatchRow) {
            NekoConfig.toggleMessageFilterExactMatch();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.messageFilterExactMatch);
            }
        } else if (id == keywordsRow) {
            showKeywordsDialog(item, position);
        }
    }

    private String getKeywordsValue() {
        if (MessageFilterHelper.getFilterCount() == 0) {
            return LocaleController.getString(R.string.MessageFiltersKeywordsEmpty);
        }
        return LocaleController.formatString(R.string.MessageFiltersRules, MessageFilterHelper.getFilterCount());
    }

    private void showKeywordsDialog(UItem item, int position) {
        var context = getParentActivity();
        if (context == null) {
            return;
        }
        var builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.MessageFiltersKeywords));
        builder.setCustomViewOffset(0);

        var container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        var editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(132), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setText(NekoConfig.messageFilterKeywords);
        editText.setHintText(LocaleController.getString(R.string.MessageFiltersKeywordsHint));
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setSingleLine(false);
        editText.setMinLines(4);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)
        );
        editText.setBackground(null);
        editText.setPadding(0, 0, 0, 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 24, 0, 24, 0));

        builder.setView(container);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        var dialog = builder.create();
        showDialog(dialog);
        var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> {
                var value = editText.getText() == null ? "" : editText.getText().toString().replace("\r\n", "\n").trim();
                if (value.length() > 4000) {
                    AndroidUtilities.shakeViewSpring(editText, -6);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    return;
                }
                NekoConfig.setMessageFilterKeywords(value);
                if (!value.isEmpty() && !NekoConfig.enableMessageFilter) {
                    NekoConfig.toggleEnableMessageFilter();
                    notifyItemChanged(enableRow, PARTIAL);
                }
                item.textValue = getKeywordsValue();
                listView.adapter.notifyItemChanged(position, PARTIAL);
                dialog.dismiss();
            });
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.MessageFiltersNeko);
    }

    @Override
    protected String getKey() {
        return "messageFilters";
    }
}
