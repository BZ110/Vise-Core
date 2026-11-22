package org.albi.core.data;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Preserves numeric types when reading JSON.
 *
 * Prevents Gson from converting:
 *   5   → 5.0 (Double)
 *   10  → Double
 *
 * Keeps:
 *   INT / LONG / DOUBLE correctly inferred.
 */
public class TypedNumberAdapter extends TypeAdapter<Object> {

    @Override
    public void write(JsonWriter out, Object value) throws IOException {

        if (value == null) {
            out.nullValue();
            return;
        }

        if (value instanceof Number n) {
            out.value(n);
            return;
        }

        if (value instanceof Boolean b) {
            out.value(b);
            return;
        }

        if (value instanceof String s) {
            out.value(s);
            return;
        }

        // fallback
        out.value(value.toString());
    }

    @Override
    public Object read(JsonReader in) throws IOException {

        JsonToken token = in.peek();

        return switch (token) {

            case NUMBER -> {
                String numStr = in.nextString();

                // Integer
                if (!numStr.contains(".") && !numStr.contains("e") && !numStr.contains("E")) {
                    try {
                        yield Integer.parseInt(numStr);
                    } catch (NumberFormatException ex) {
                        yield Long.parseLong(numStr);
                    }
                }

                // Decimal
                yield Double.parseDouble(numStr);
            }

            case BOOLEAN -> in.nextBoolean();

            case STRING -> in.nextString();

            case NULL -> {
                in.nextNull();
                yield null;
            }

            default -> {
                in.skipValue();
                yield null;
            }
        };
    }
}
