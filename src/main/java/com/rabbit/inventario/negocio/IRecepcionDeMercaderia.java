package com.rabbit.inventario.negocio;

import com.rabbit.inventario.dto.DatosOrdenDeRecepcionDTO;
import com.rabbit.inventario.dto.LineaRecibidaDTO;
import com.rabbit.inventario.dto.OrdenDeRecepcionDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * Órdenes de recepción (ADR-058): el comercio anuncia qué mercadería manda a un depósito de Rabbit y el
 * personal del depósito registra lo que llegó, con las diferencias. Al registrarla se suma al stock
 * SOLO lo que llegó.
 */
@Local
public interface IRecepcionDeMercaderia {

    /**
     * El comercio anuncia la mercadería que va a mandar (queda ANUNCIADA).
     *
     * @return el ID de la orden
     * @throws ValidacionException si el depósito no existe, no hay productos o los datos no sirven
     */
    Long anunciar(DatosOrdenDeRecepcionDTO datos);

    /** Las órdenes del comercio que llama, de la más nueva a la más vieja. */
    List<OrdenDeRecepcionDTO> listarDelComercioActual();

    /**
     * Cancela una orden ANUNCIADA: el comercio la suya, o el personal cualquiera (por ejemplo, si no llegó nada).
     *
     * @throws ValidacionException si no existe, no es del comercio que llama o ya no está ANUNCIADA
     */
    void cancelar(Long idOrden);

    /** Todas las órdenes, para el personal del depósito. */
    List<OrdenDeRecepcionDTO> listarTodas();

    OrdenDeRecepcionDTO obtener(Long idOrden);

    /**
     * El personal registra lo que llegó de cada línea. La orden queda RECIBIDA (no faltó nada) o PARCIAL
     * (faltó algo en alguna línea) y se suma al stock del comercio, en ese depósito, lo que llegó.
     * Se registra una sola vez.
     *
     * @throws ValidacionException si la orden no está ANUNCIADA, falta informar una línea, hay valores fuera de
     *         rango o no llegó nada
     */
    OrdenDeRecepcionDTO registrarRecepcion(Long idOrden, List<LineaRecibidaDTO> recibidas);

    /** Cuántas órdenes están ANUNCIADAS esperando la mercadería (para el inicio del personal). */
    long contarAnunciadas();
}
