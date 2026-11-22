package org.albi.mods;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Reflect {

    private Reflect() {
    }

    public static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing runtime class: " + name, e);
        }
    }

    public static Method resolveMethod(Class<?> type, String[] names, Class<?>... params) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (String name : names) {
                try {
                    Method method = cursor.getMethod(name, params);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method method = cursor.getDeclaredMethod(name, params);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    public static Field resolveField(Class<?> type, String... names) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (String name : names) {
                try {
                    Field field = cursor.getField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    public static Constructor<?> resolveConstructor(Class<?> type, Class<?>... params) {
        try {
            Constructor<?> constructor = type.getConstructor(params);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(params);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object receiver, Method method, Object... args) {
        if (method == null) {
            throw new IllegalStateException("Method not resolved");
        }
        try {
            return (T) method.invoke(receiver, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new RuntimeException("Invoke failed: " + method + " cause=" + cause, cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Invoke failed: " + method, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object receiver, Field field) {
        if (field == null) {
            throw new IllegalStateException("Field not resolved");
        }
        try {
            return (T) field.get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Field get failed: " + field, ex);
        }
    }

    public static void set(Object receiver, Field field, Object value) {
        if (field == null) {
            throw new IllegalStateException("Field not resolved");
        }
        try {
            field.set(receiver, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Field set failed: " + field, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Constructor<?> constructor, Object... args) {
        if (constructor == null) {
            throw new IllegalStateException("Constructor not resolved");
        }
        try {
            return (T) constructor.newInstance(args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new RuntimeException("Constructor failed: " + constructor + " cause=" + cause, cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Constructor failed: " + constructor, ex);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }
}
