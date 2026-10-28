package chasm;

import java.io.InputStream;
import java.util.Hashtable;

/**
 * Открытый формат текста: загружает все строки из res/open/texts/strings.txt внутри JAR.
 * Если файл отсутствует или ключ не найден - возвращает дефолтное значение (хардкод).
 * Пользователь может редактировать JAR как ZIP, поменять strings.txt и пересобрать через ./build.sh
 */
public final class TextTable {
    private static Hashtable table = null;
    private static boolean loaded = false;

    private TextTable() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        table = new Hashtable();
        InputStream is = null;
        try {
            // В JAR путь будет /open/texts/strings.txt (res/open -> open/)
            is = TextTable.class.getResourceAsStream("/open/texts/strings.txt");
            if (is == null) is = TextTable.class.getResourceAsStream("open/texts/strings.txt");
            if (is == null) is = TextTable.class.getResourceAsStream("/res/open/texts/strings.txt");
            if (is == null) return;
            StringBuffer sb = new StringBuffer();
            int b;
            while ((b = is.read()) != -1) {
                sb.append((char)(b & 0xFF));
            }
            String content = sb.toString();
            int pos = 0;
            while (pos < content.length()) {
                int nl = content.indexOf('\n', pos);
                if (nl < 0) nl = content.length();
                String line = content.substring(pos, nl).trim();
                pos = nl + 1;
                if (line.length() == 0 || line.charAt(0) == '#' || line.charAt(0) == ';') continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1);
                // Keep trailing spaces for prefixes like "MAP: "
                // Trim only leading, keep value as is except \r
                if (v.length() > 0 && v.charAt(v.length()-1) == '\r') v = v.substring(0, v.length()-1);
                table.put(k, v);
            }
        } catch (Exception e) {
            // ignore, fallback to hardcode
        } finally {
            if (is != null) try { is.close(); } catch (Exception ex) {}
        }
    }

    public static String get(String key, String def) {
        ensureLoaded();
        if (table == null) return def;
        Object v = table.get(key);
        return v != null ? (String)v : def;
    }

    // Удобные геттеры для часто используемых ключей
    public static String levelName(int idx, String def) {
        return get("LEVEL_" + (idx+1) + "_NAME", def);
    }
    public static String monsterName(int idx, String def) {
        return get("MONSTER_" + idx, def);
    }
    public static String weaponName(int idx, String def) {
        return get("WEAPON_" + idx, def);
    }
}
