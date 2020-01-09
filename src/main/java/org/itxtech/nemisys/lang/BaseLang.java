package org.itxtech.nemisys.lang;

import org.itxtech.nemisys.event.TextContainer;
import org.itxtech.nemisys.event.TranslationContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BaseLang {

    protected Map<String, String> lang = new HashMap<>();
    protected Map<String, String> fallbackLang = new HashMap<>();


    public BaseLang(String lang) {
        this(lang, null);
    }

    public BaseLang(String lang, String path) {
        this(lang, path, "eng");
    }

    public BaseLang(String lang, String path, String fallback) {
    }

    public String getName() {
        return "English";
    }

    public String getLang() {
        return "English";
    }

    public String translateString(String str) {
        return this.translateString(str, new String[]{}, null);
    }

    public String translateString(String str, String param) {
        return this.translateString(str, new String[]{param});
    }

    public String translateString(String str, String[] params) {
        return this.translateString(str, params, null);
    }

    public String translateString(String str, String param, String onlyPrefix) {
        return this.translateString(str, new String[]{param}, onlyPrefix);
    }

    public String translateString(String str, String[] params, String onlyPrefix) {
        String baseText = this.get(str);
        baseText = this.parseTranslation((baseText != null && (onlyPrefix == null || str.indexOf(onlyPrefix) == 0)) ? baseText : str, onlyPrefix);
        for (int i = 0; i < params.length; i++) {
            baseText = baseText.replace("{%" + i + '}', this.parseTranslation(params[i]));
        }

        return baseText;
    }

    public String translate(TextContainer c) {
        String baseText = this.parseTranslation(c.getText());
        if (c instanceof TranslationContainer) {
            baseText = this.internalGet(c.getText());
            baseText = this.parseTranslation(baseText != null ? baseText : c.getText());
            for (int i = 0; i < ((TranslationContainer) c).getParameters().length; i++) {
                baseText = baseText.replace("{%" + i + '}', this.parseTranslation(((TranslationContainer) c).getParameters()[i]));
            }
        }
        return baseText;
    }

    public String internalGet(String id) {
        if (this.lang.containsKey(id)) {
            return this.lang.get(id);
        } else if (this.fallbackLang.containsKey(id)) {
            return this.fallbackLang.get(id);
        }
        return null;
    }

    public String get(String id) {
        if (this.lang.containsKey(id)) {
            return this.lang.get(id);
        } else if (this.fallbackLang.containsKey(id)) {
            return this.fallbackLang.get(id);
        }
        return id;
    }

    protected String parseTranslation(String text) {
        return this.parseTranslation(text, null);
    }

    protected String parseTranslation(String text, String onlyPrefix) {
        if (text == null) return "";
        StringBuilder newString = new StringBuilder();

        StringBuilder replaceString = null;

        int len = text.length();

        for (int i = 0; i < len; ++i) {
            char c = text.charAt(i);
            if (replaceString != null) {
                if (((int) c >= 0x30 && (int) c <= 0x39)
                        || ((int) c >= 0x41 && (int) c <= 0x5a)
                        || ((int) c >= 0x61 && (int) c <= 0x7a)
                        || c == '.' || c == '-') {
                    replaceString.append(c);
                } else {
                    String t = this.internalGet(replaceString.substring(1));
                    if (t != null && (onlyPrefix == null || replaceString.indexOf(onlyPrefix) == 1)) {
                        newString.append(t);
                    } else {
                        newString.append(replaceString);
                    }
                    replaceString = null;
                    if (c == '%') {
                        replaceString = new StringBuilder(String.valueOf(c));
                    } else {
                        newString.append(c);
                    }
                }
            } else if (c == '%') {
                replaceString = new StringBuilder(String.valueOf(c));
            } else {
                newString.append(c);
            }
        }

        if (replaceString != null) {
            String t = this.internalGet(replaceString.substring(1));
            if (t != null && (onlyPrefix == null || replaceString.indexOf(onlyPrefix) == 1)) {
                newString.append(t);
            } else {
                newString.append(replaceString);
            }
        }
        return newString.toString();
    }
}
