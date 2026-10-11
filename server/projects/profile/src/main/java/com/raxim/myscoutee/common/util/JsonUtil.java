package com.raxim.myscoutee.common.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class JsonUtil {
    public static final String ISO_DATE_STRING = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public static <T> T jsonToObject(String jsonString, Class<T> targetClass, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(jsonString, targetClass);
        } catch (JacksonException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T clone(T t, ObjectMapper objectMapper) {
        try {
            String jsonString = objectMapper.writeValueAsString(t);
            return objectMapper.readValue(jsonString, new TypeReference<T>() {
                @Override
                public Type getType() {
                    return new ParameterizedType() {
                        @Override
                        public Type[] getActualTypeArguments() {
                            return new Type[] {};
                        }

                        @Override
                        public Type getRawType() {
                            return t.getClass();
                        }

                        @Override
                        public Type getOwnerType() {
                            return null;
                        }
                    };
                }
            });
        } catch (JacksonException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String toJson(Object t, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(t);
        } catch (JacksonException e) {
            e.printStackTrace();
            return null;
        }
    }
}
