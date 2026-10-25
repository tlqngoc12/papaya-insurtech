package com.papaya.fraud.io;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared Jackson configuration: snake_case property names (matching the output schema), ISO dates, and a pretty
 * printer that always writes {@code \n}, two-space indentation, {@code "key": value} and empty arrays as {@code []}.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Jackson's default indenter uses the system line separator and writes empty arrays as "[ ]". */
    static final class LfPrettyPrinter extends DefaultPrettyPrinter {

        LfPrettyPrinter() {
            super(DefaultPrettyPrinter.DEFAULT_SEPARATORS
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
            DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
            indentObjectsWith(indenter);
            indentArraysWith(indenter);
        }

        LfPrettyPrinter(LfPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new LfPrettyPrinter(this);
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
            if (nrOfValues == 0) {
                if (!_arrayIndenter.isInline()) {
                    --_nesting;
                }
                g.writeRaw(']');
                return;
            }
            super.writeEndArray(g, nrOfValues);
        }
    }

    private static final ObjectWriter PRETTY = MAPPER.writer(new LfPrettyPrinter());

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** UTF-8 bytes (no BOM) of the value, pretty printed with {@code \n}, ending with a newline. */
    public static byte[] prettyBytes(Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PRETTY.writeValue(bytes, value);
            bytes.write("\n".getBytes(StandardCharsets.UTF_8));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
