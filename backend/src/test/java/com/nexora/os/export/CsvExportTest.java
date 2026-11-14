package com.nexora.os.export;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CsvExportTest {
    @Test
    void quotesCommasNewlinesAndQuotes() {
        assertEquals("\"hello, world\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\n",
                CsvExport.row("hello, world", "say \"hi\"", "line1\nline2"));
    }

    @Test
    void neutralizesFormulasEvenAfterWhitespace() {
        for (String value : new String[] {"=1+1", "+SUM(A1)", "-1+2", "@cmd", "  =2+2", "\t=HYPERLINK(1)"}) {
            String cell = CsvExport.cell(value);
            assertTrue(cell.startsWith("\"'"), "Unsafe CSV cell: " + value);
        }
        assertEquals("\"normal text\"", CsvExport.cell("normal text"));
        assertEquals("\"\"", CsvExport.cell(null));
    }
}
