package com.rabbit.seguridad.datos.model;

/**
 * Roles del sistema. Mapean 1 a 1 con los "groups" que WildFly resuelve
 * contra ApplicationRealm al autenticar (ver LoginBean y
 * ApplicationRealmSync) — son los mismos nombres que se usan en
 * @RolesAllowed sobre las operaciones sensibles (ver
 * ComercioService.eliminarComercio).
 */
public enum Rol {
    ADMINISTRADOR,
    OPERADOR
}
