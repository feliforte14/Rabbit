package com.rabbit.ruteo.datos.model;

/** Quién reparte en una zona. */
public enum CoberturaZona {
    /** Repartidores propios de Rabbit (con un transportista de respaldo opcional). */
    PROPIA,
    /** Un transportista externo: los pedidos de la zona se le derivan siempre. */
    TRANSPORTISTA
}
