package org.albi.core.gui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTemplatesTest {
    @Test
    void replacementsKeepLiteralCharactersAndAreNotParsedAsTemplates() {
        assertEquals("$5\\back {2} / second / $5\\back {2} / {3}",
                TextTemplates.format("{1} / {2} / {1} / {3}", "$5\\back {2}", "second"));
    }

    @Test
    void namedTokensCanBeReorderedAndUnknownTokensAreRetained() {
        assertEquals("2/4 Alex {unknown}", TextTemplates.format("{page}/{pages} {viewer} {unknown}",
                Map.of("viewer", "Alex", "page", 2, "pages", 4)));
    }
}
