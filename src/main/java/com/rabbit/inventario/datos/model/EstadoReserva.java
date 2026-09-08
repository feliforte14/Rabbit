package com.rabbit.inventario.datos.model;

/**
 * Estados por los que pasa una reserva de stock.
 *
 *                    reservarStock()
 *                          |
 *                          v
 *                      [VIGENTE] <---- extenderReserva()
 *              ____________|____________
 *             |            |            |
 *      confirmarReserva  liberar    vence el plazo
 *             |            |            |
 *             v            v            v
 *       [CONFIRMADA]  [LIBERADA]   [EXPIRADA]
 *             |
 *      registrarDevolucion()   (el pedido que la confirmo se cancelo)
 *             |
 *             v
 *        [DEVUELTA]
 *
 * VIGENTE es el unico estado no terminal. Los demas no vuelven atras: para
 * comprometer stock otra vez hay que crear una reserva nueva. DEVUELTA es
 * el unico que se alcanza desde otro estado final (CONFIRMADA), y solo por
 * esa via: es la contracara contable de la confirmacion, no un paso normal
 * del flujo.
 *
 * OJO AL AGREGAR UN VALOR NUEVO
 * La columna "estado" es @Enumerated(EnumType.STRING) y Hibernate le genero
 * un CHECK constraint en Postgres al CREAR la tabla, con los valores que el
 * enum tenia en ese momento. hbm2ddl.auto=update NO actualiza constraints
 * existentes — solo agrega tablas y columnas que falten. Un valor nuevo
 * compila y corre, pero el UPDATE rebota contra la base:
 *
 *     ERROR: new row for relation "reservas_stock" violates check
 *     constraint "reservas_stock_estado_check"
 *
 * Hay que correr el DDL a mano. Para DEVUELTA fue:
 *
 *     ALTER TABLE reservas_stock DROP CONSTRAINT reservas_stock_estado_check;
 *     ALTER TABLE reservas_stock ADD CONSTRAINT reservas_stock_estado_check
 *       CHECK (estado IN ('VIGENTE','CONFIRMADA','LIBERADA','EXPIRADA','DEVUELTA'));
 *
 * Mismo cuidado con EstadoPedido y Rol, que tienen su propio CHECK.
 * Es el caso concreto de por que en produccion irian migraciones explicitas
 * (Flyway/Liquibase) en vez de hbm2ddl.auto=update — ver persistence.xml.
 */
public enum EstadoReserva {

    /** Stock comprometido pero todavia no descontado. Tiene fecha de vencimiento. */
    VIGENTE,

    /** El cliente confirmo: el stock se descuenta definitivamente del deposito. */
    CONFIRMADA,

    /** El cliente desistio antes de vencer: el stock vuelve a estar libre. */
    LIBERADA,

    /** Se vencio el plazo sin confirmar. La libera el barredor automatico. */
    EXPIRADA,

    /**
     * La reserva estaba CONFIRMADA (stock ya descontado) pero el pedido que
     * la origino se cancelo: la cantidad se devolvio al stock disponible.
     * Ver InventarioService.registrarDevolucion.
     */
    DEVUELTA
}
