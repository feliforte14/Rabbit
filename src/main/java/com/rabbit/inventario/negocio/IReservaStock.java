package com.rabbit.inventario.negocio;

/**
 * CONTRATO DE RESERVA del componente ServicioDeInventario.
 *
 * Esta es la interfaz que hace que el componente sea @Stateful: sus
 * operaciones NO son autocontenidas. Forman una conversacion con estado
 * que se extiende a lo largo de varias llamadas:
 *
 *     reservarStock()  ->  [el cliente hace otras cosas]  ->  confirmarReserva()
 *                                                         o   liberarReserva()
 *                                                         o   extenderReserva()
 *
 * Entre esas llamadas el componente recuerda cual es la reserva en curso
 * (el campo reservaActual de InventarioService). Ninguna de las
 * operaciones de cierre recibe el ID de la reserva por parametro:
 * operan sobre la reserva que esta conversacion dejo abierta. Ese es
 * exactamente el estado conversacional que justifica el @Stateful, y lo
 * que lo diferencia de un componente stateless como ServicioDeComercios,
 * donde cada operacion recibe todo lo que necesita.
 *
 * Contabilidad del stock mientras la reserva esta vigente: la cantidad
 * queda comprometida (cantidadReservada del item sube) pero NO descontada
 * (cantidadDisponible no cambia). Recien al confirmar sale del inventario.
 *
 * Solo se admite UNA reserva vigente por conversacion.
 *
 * EXCEPCION A LA REGLA DEL "SIN ID POR PARAMETRO": registrarDevolucion()
 * si recibe el ID. No es parte de la conversacion reservar->confirmar: es
 * una correccion contable fuera de banda (un pedido ya confirmado que
 * despues se cancela), que puede pedir cualquier cliente sobre cualquier
 * reserva, sin haberla abierto el mismo.
 *
 * Implementada por {@link InventarioService}.
 */

import com.rabbit.inventario.dto.ReservaStockDTO;
import jakarta.ejb.Local;

@Local
public interface IReservaStock {

    /**
     * Abre una reserva sobre el stock de un item y la deja como reserva en
     * curso de esta conversacion.
     *
     * Valida contra IConsultaComercios que el comercio exista y este
     * activo: un comercio dado de baja no puede comprometer stock.
     *
     * Valida ademas que el item le PERTENEZCA: el deposito es de Rabbit,
     * pero la mercaderia adentro es del comercio que la consigno, y solo
     * el puede reservarla (ver ItemInventario.idComercio).
     *
     * @param idComercio comercio que pide la reserva
     * @param idItem item de stock a comprometer
     * @param cantidad unidades a reservar
     * @return la reserva creada, ya vigente
     * @throws ValidacionException si el comercio no esta activo, el item no
     *         existe, el item es de otro comercio o no tiene dueño
     *         asignado, la cantidad no es positiva, no hay stock libre
     *         suficiente, o esta conversacion ya tiene una reserva vigente
     */
    ReservaStockDTO reservarStock(Long idComercio, Long idItem, int cantidad);

    /**
     * Confirma la reserva en curso: el stock se descuenta definitivamente
     * del deposito y la reserva pasa a CONFIRMADA.
     *
     * @throws ValidacionException si no hay reserva en curso o si vencio
     *         antes de confirmarse
     */
    void confirmarReserva();

    /**
     * Libera la reserva en curso sin consumir stock: la cantidad vuelve a
     * estar libre y la reserva pasa a LIBERADA.
     *
     * @throws ValidacionException si no hay reserva en curso
     */
    void liberarReserva();

    /**
     * Empuja el vencimiento de la reserva en curso, para darle mas tiempo
     * al cliente sin perder el stock comprometido.
     *
     * @throws ValidacionException si no hay reserva en curso o si ya vencio
     */
    void extenderReserva();

    /**
     * @return la reserva en curso de esta conversacion, o null si no hay
     */
    ReservaStockDTO obtenerReservaActual();

    /**
     * @return true si esta conversacion tiene una reserva abierta y sin vencer
     */
    boolean hayReservaVigente();

    /**
     * Revierte una reserva CONFIRMADA: devuelve su cantidad al stock
     * disponible del item y la deja en estado DEVUELTA. La usa
     * ServicioDePedidos cuando se cancela un pedido cuyo stock ya se habia
     * descontado.
     *
     * A diferencia de las operaciones de cierre, recibe el ID: no opera
     * sobre "la reserva de esta conversacion" sino sobre una puntual,
     * identificada desde afuera.
     *
     * @param idReserva reserva a revertir
     * @throws ValidacionException si la reserva no existe o no esta CONFIRMADA
     *         (si ya se libero, vencio o devolvio, su cantidad ya volvio al
     *         stock y devolverla otra vez lo dejaria inflado)
     */
    void registrarDevolucion(Long idReserva);
}
