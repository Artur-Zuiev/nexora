package com.nexora.os.export;

import java.util.Arrays;

/** RFC 4180 quoting plus spreadsheet formula-injection mitigation. */
public final class CsvExport {
    private CsvExport() {}

    public static String row(Object... values) {
        return Arrays.stream(values)
                .map(CsvExport::cell)
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "\r\n";
    }

    static String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        // Excel/LibreOffice may interpret formula prefixes even after whitespace.
        // A literal apostrophe makes potentially executable cell text inert.
        String prefix = text.stripLeading();
        if (!prefix.isEmpty() && "=+-@".indexOf(prefix.charAt(0)) >= 0) {
            text = "'" + text;
        }
        // Tabs and embedded line breaks at the start can also hide formulas.
        // Strip all control characters except CR/LF, which remain quoted.
        text = text.replace("\t", " ");
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
