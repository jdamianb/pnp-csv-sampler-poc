package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON serialization/deserialization of {@link PnpImportFormatConfig}.
 */
class PnpImportFormatConfigSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripFullConfig() throws Exception {
        var original = new PnpImportFormatConfig(
                1, 0.85, ";", "\"", "UTF-8",
                List.of(0, 1, 2, 10),
                3, 4, 9,
                ",",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                        "partNumber", new ColumnMapping(ColumnSource.HEADER_NAME, "PartNo"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X-Pos"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y-Pos"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle"),
                        "side", new ColumnMapping(ColumnSource.HEADER_NAME, "MountSide")
                ),
                Map.of("x", "mm", "y", "mm", "angle", "deg"),
                Map.of("side", Map.of("T", "Top", "B", "Bottom")),
                List.of()
        );

        var json = mapper.writeValueAsString(original);
        var parsed = mapper.readValue(json, PnpImportFormatConfig.class);

        assertEquals(1, parsed.schemaVersion());
        assertEquals(0.85, parsed.confidence(), 0.001);
        assertEquals(";", parsed.delimiter());
        assertEquals(",", parsed.decimalSeparator());
        assertEquals(3, parsed.headerRowIndex());
        assertEquals(4, parsed.dataStartRowIndex());
        assertEquals(9, parsed.dataEndRowIndex());
        assertEquals(6, parsed.columns().size());
        assertEquals(ColumnSource.HEADER_NAME, parsed.columns().get("x").source());
        assertEquals("X-Pos", parsed.columns().get("x").value());
        assertEquals("Top", parsed.valueMappings().get("side").get("T"));
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    void deserializeColumnIndexSource() throws Exception {
        var json = """
                {
                  "schemaVersion": 1,
                  "confidence": 0.5,
                  "delimiter": ",",
                  "quoteChar": "",
                  "encoding": "UTF-8",
                  "linesToIgnore": [],
                  "dataStartRowIndex": 0,
                  "decimalSeparator": ".",
                  "columns": {
                    "reference": { "source": "COLUMN_INDEX", "value": "0" },
                    "x": { "source": "COLUMN_INDEX", "value": "1" },
                    "y": { "source": "COLUMN_INDEX", "value": "2" },
                    "angle": { "source": "COLUMN_INDEX", "value": "3" }
                  },
                  "units": {},
                  "valueMappings": {},
                  "warnings": []
                }
                """;
        var config = mapper.readValue(json, PnpImportFormatConfig.class);

        assertEquals(ColumnSource.COLUMN_INDEX, config.columns().get("x").source());
        assertEquals("1", config.columns().get("x").value());
        assertEquals("0", config.columns().get("reference").value());
    }

    @Test
    void deserializeMinimalConfig() throws Exception {
        var json = """
                {
                  "schemaVersion": 1,
                  "confidence": 0.5,
                  "delimiter": ",",
                  "quoteChar": "",
                  "encoding": "UTF-8",
                  "linesToIgnore": [],
                  "dataStartRowIndex": 0,
                  "decimalSeparator": ".",
                  "columns": {
                    "reference": { "source": "HEADER_NAME", "value": "R" },
                    "x": { "source": "HEADER_NAME", "value": "X" },
                    "y": { "source": "HEADER_NAME", "value": "Y" },
                    "angle": { "source": "HEADER_NAME", "value": "A" }
                  },
                  "units": {},
                  "valueMappings": {},
                  "warnings": []
                }
                """;
        var config = mapper.readValue(json, PnpImportFormatConfig.class);

        assertEquals(1, config.schemaVersion());
        assertEquals(",", config.delimiter());
        assertEquals(4, config.columns().size());
    }
}
