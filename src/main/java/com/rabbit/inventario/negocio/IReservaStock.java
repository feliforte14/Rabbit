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
     * @param idComercio comercio que pide la reserva
     * @param idItem item de stock a comprometer
     * @param cantidad unidades a reservar
     * @return la reserva creada, ya vigente
     * @throws ValidacionException si el comercio no esta activo, el item no
     *         existe, la cantidad no es positiva, no hay stock libre
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
}
