package com.expense.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.YearMonth;

/** Creates the shared Gson configuration used by the API and local JSON files. */
public final class JsonUtil {
    private JsonUtil() {
    }

    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(YearMonth.class, new YearMonthAdapter())
                .setPrettyPrinting()
                .create();
    }

    /** Keeps transaction dates readable as YYYY-MM-DD strings. */
    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonPrimitive serialize(LocalDate date, Type type,
                                       com.google.gson.JsonSerializationContext context) {
            return new JsonPrimitive(date.toString());
        }

        @Override
        public LocalDate deserialize(com.google.gson.JsonElement json, Type type,
                                     com.google.gson.JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString());
        }
    }

    /** Keeps budget months readable as YYYY-MM strings. */
    private static class YearMonthAdapter implements JsonSerializer<YearMonth>, JsonDeserializer<YearMonth> {
        @Override
        public JsonPrimitive serialize(YearMonth month, Type type,
                                       com.google.gson.JsonSerializationContext context) {
            return new JsonPrimitive(month.toString());
        }

        @Override
        public YearMonth deserialize(com.google.gson.JsonElement json, Type type,
                                     com.google.gson.JsonDeserializationContext context) {
            return YearMonth.parse(json.getAsString());
        }
    }
}
