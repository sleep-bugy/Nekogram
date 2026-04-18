package tw.nekomimi.nekogram.helpers;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;

public class MessageFilterHelper {

    public static ArrayList<TLRPC.MessageEntity> checkBlockedEntities(MessageObject messageObject, ArrayList<TLRPC.MessageEntity> original) {
        if (messageObject.shouldBlockMessage() && messageObject.messageOwner.message != null) {
            ArrayList<TLRPC.MessageEntity> entities = original == null ? new ArrayList<>() : new ArrayList<>(original);
            var spoiler = new TLRPC.TL_messageEntitySpoiler();
            spoiler.offset = 0;
            spoiler.length = messageObject.messageOwner.message.length();
            entities.add(spoiler);
            var quote = new TLRPC.TL_messageEntityBlockquote();
            quote.offset = 0;
            quote.length = messageObject.messageOwner.message.length();
            quote.collapsed = true;
            entities.add(quote);
            return entities;
        } else {
            return original;
        }
    }

    public static ArrayList<TLRPC.MessageEntity> checkBlockedEntities(MessageObject messageObject) {
        return checkBlockedEntities(messageObject, messageObject.messageOwner.entities);
    }

    public static boolean shouldBlockMessage(MessageObject message) {
        if (message.messageOwner == null || message.storyItem != null) {
            return false;
        }
        if (shouldHideBlockedMessage(message)) {
            return true;
        }
        return matchesCustomFilters(message);
    }

    public static int getFilterCount() {
        return getFilters().size();
    }

    private static boolean shouldHideBlockedMessage(MessageObject message) {
        if (!NekoConfig.ignoreBlocked) {
            return false;
        }
        if (isUserBlocked(message.currentAccount, message.getFromChatId())) {
            return true;
        }
        if (message.messageOwner.fwd_from == null || message.messageOwner.fwd_from.from_id == null) {
            return false;
        }
        return isUserBlocked(message.currentAccount, MessageObject.getPeerId(message.messageOwner.fwd_from.from_id));
    }

    private static boolean matchesCustomFilters(MessageObject message) {
        if (!NekoConfig.enableMessageFilter) {
            return false;
        }
        var text = message.messageOwner.message;
        if (text == null || text.isEmpty()) {
            return false;
        }
        var normalizedText = text.toLowerCase(Locale.ROOT);
        for (var filter : getFilters()) {
            var normalizedFilter = filter.toLowerCase(Locale.ROOT);
            if (NekoConfig.messageFilterExactMatch ? containsWholePhrase(normalizedText, normalizedFilter) : normalizedText.contains(normalizedFilter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWholePhrase(String text, String phrase) {
        int index = -1;
        while ((index = text.indexOf(phrase, index + 1)) >= 0) {
            if (isBoundary(text, index - 1) && isBoundary(text, index + phrase.length())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundary(String text, int index) {
        return index < 0 || index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
    }

    public static ArrayList<String> getFilters() {
        var filters = new ArrayList<String>();
        if (NekoConfig.messageFilterKeywords == null || NekoConfig.messageFilterKeywords.isBlank()) {
            return filters;
        }
        var lines = NekoConfig.messageFilterKeywords.split("\\r?\\n");
        for (var line : lines) {
            var trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                filters.add(trimmed);
            }
        }
        return filters;
    }

    private static boolean isUserBlocked(int currentAccount, long id) {
        var messagesController = MessagesController.getInstance(currentAccount);
        var userFull = messagesController.getUserFull(id);
        return (userFull != null && userFull.blocked) || messagesController.blockePeers.indexOfKey(id) >= 0;
    }

}
