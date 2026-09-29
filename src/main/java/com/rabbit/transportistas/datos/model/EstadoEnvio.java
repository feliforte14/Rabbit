package com.rabbit.transportistas.datos.model;

/** Estado de un envío derivado a un transportista, en el modelo de Rabbit. */
public enum EstadoEnvio {
    SOLICITADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO;

    /** Todavía puede cambiar: el seguimiento lo sigue consultando. */
    public boolean isActivo() {
        return this == SOLICITADO || this == EN_TRANSITO;
    }
}
