package com.rabbit.pruebas;

import java.lang.reflect.Field;

/**
 * Las entidades JPA no tienen setId: el ID lo asigna la base. En los tests unitarios no hay base,
 * así que se lo ponemos por reflexión para poder armar entidades "ya guardadas".
 */
public final class Ids {

    private Ids() {}

    public static <T> T conId(T entidad, Long id) {
        Class<?> clase = entidad.getClass();
        while (clase != null) {
            try {
                Field campo = clase.getDeclaredField("id");
                campo.setAccessible(true);
                campo.set(entidad, id);
                return entidad;
            } catch (NoSuchFieldException e) {
                clase = clase.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException(entidad.getClass() + " no tiene un campo id");
    }
}
