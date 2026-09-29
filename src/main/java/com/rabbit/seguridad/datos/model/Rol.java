package com.rabbit.seguridad.datos.model;

/**
 * Roles del sistema. Mapean 1 a 1 con los "groups" que WildFly resuelve
 * contra ApplicationRealm al autenticar (ver LoginBean y
 * ApplicationRealmSync) — son los mismos nombres que se usan en
 * @RolesAllowed sobre las operaciones sensibles (ver
 * ComercioService.eliminarComercio).
 */
public enum Rol {
    /** Personal de Rabbit con permisos totales. */
    ADMINISTRADOR,
    /** Personal de Rabbit que opera comercios, depósitos, pedidos y repartidores. */
    OPERADOR,
    /** Un comercio: ve solo sus pedidos, su stock y sus puntos de picking. */
    COMERCIO,
    /** Un repartidor: ve sus entregas y marca retiro y entrega. */
    REPARTIDOR
}
