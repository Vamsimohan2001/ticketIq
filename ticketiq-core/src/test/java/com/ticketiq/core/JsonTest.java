package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesNestedStructures() {
        Map<String, Object> m = Json.parseObject(
                "{\"a\": 1, \"b\": [true, null, 2.5, \"x\"], \"c\": {\"d\": \"e\"}}");
        assertEquals(1L, m.get("a"));
        List<Object> b = Json.asList(m.get("b"));
        assertEquals(Boolean.TRUE, b.get(0));
        assertEquals(null, b.get(1));
        assertEquals(2.5, (Double) b.get(2), 0.0001);
        assertEquals("e", Json.string(Json.asMap(m.get("c")), "d"));
    }

    @Test
    void handlesEscapesAndUnicode() {
        Map<String, Object> m = Json.parseObject("{\"t\": \"line1\\nline2 \\\"q\\\" \\u00e9\"}");
        assertEquals("line1\nline2 \"q\" \u00e9", m.get("t"));
    }

    @Test
    void writesAndReadsBack() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Tick\"et\n");
        m.put("count", 3L);
        m.put("ratio", 0.5);
        m.put("tags", List.of("a", "b"));
        m.put("none", null);
        String json = Json.write(m);
        assertEquals(m, Json.parseObject(json));
    }

    @Test
    void extractsObjectFromChattyModelOutput() {
        String raw = "Sure! Here you go:\n```json\n{\"category\": \"BUG\"}\n```\nHope that helps.";
        assertEquals("BUG", Json.string(Json.parseObject(Json.extractObject(raw)), "category"));
    }

    @Test
    void rejectsBrokenJson() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1, 2"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{} trailing"));
        assertThrows(Json.JsonException.class, () -> Json.parse("\"open"));
    }

    @Test
    void numberHelperAcceptsNumbersAndNumericStrings() {
        Map<String, Object> m = Json.parseObject("{\"a\": 0.7, \"b\": \"0.9\", \"c\": \"nope\"}");
        assertEquals(0.7, Json.number(m, "a"), 0.0001);
        assertEquals(0.9, Json.number(m, "b"), 0.0001);
        assertEquals(null, Json.number(m, "c"));
        assertTrue(Json.write(0.5).startsWith("0.5"));
    }
}
