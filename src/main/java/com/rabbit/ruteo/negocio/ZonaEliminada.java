package com.rabbit.ruteo.negocio;

/**
 * Evento CDI sincrónico: "esta zona se elimina ahora". Lo dispara
 * ZonaService.eliminarZona antes de borrarla, dentro de la misma
 * transacción: Repartidores deja sin zona fija a los que repartían ahí
 * (ver ZonaDeRepartidores). Ruteo no depende de Repartidores para esto,
 * mismo patrón que ComercioEliminado.
 */
public class ZonaEliminada {

    private final Long idZona;

    public ZonaEliminada(Long idZona) {
        this.idZona = idZona;
    }

    public Long getIdZona() { return idZona; }
}
