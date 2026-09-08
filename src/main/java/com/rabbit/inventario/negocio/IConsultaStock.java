package com.rabbit.inventario.negocio;

/**
 * CONTRATO DE CONSULTA del componente ServicioDeInventario.
 *
 * Agrupa las operaciones de solo lectura sobre depositos y stock, mas el
 * alta de depositos e items (que es administracion del inventario, no
 * parte de la conversacion de reserva).
 *
 * Es la contracara de {@link IReservaStock}. La separacion importa: un
 * componente que solo necesita saber si hay stock de un producto —por
 * ejemplo ServicioDeRuteo, para elegir desde que deposito despachar—
 * depende de esta interfaz y con eso NO obtiene la capacidad de
 * comprometer stock ni de confirmar reservas.
 *
 * @Local: los consumidores viven en el mismo contenedor, la invocacion es
 * una llamada Java directa.
 *
 * Implementada por {@link InventarioService}.
 */

import com.rabbit.inventario.dto.DatosDepositoDTO;
import com.rabbit.inventario.dto.DatosItemInventarioDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import jakarta.ejb.Local;
import java.util.List;

@Local
public interface IConsultaStock {

    /**
     * Da de alta un deposito nuevo.
     *
     * @param datos nombre y direccion del deposito
     * @return el ID asignado por la base
     * @throws ValidacionException si nombre o direccion estan vacios
     */
    Long registrarDeposito(DatosDepositoDTO datos);

    /**
     * @param idDeposito identificador del deposito
     * @return el deposito como DTO
     * @throws ValidacionException si no existe
     */
    DepositoDTO obtenerDeposito(Long idDeposito);

    /**
     * @return todos los depositos registrados
     */
    List<DepositoDTO> listarDepositos();

    /**
     * Registra una consignacion: un comercio deja stock de un producto en
     * un deposito de Rabbit. Un mismo comercio no puede cargar dos veces
     * el mismo producto en el mismo deposito, pero dos comercios distintos
     * si pueden tener el mismo producto ahi — son consignaciones separadas.
     *
     * @param idDeposito deposito donde se carga el stock
     * @param datos producto, cantidad y comercio dueño
     * @return el ID del item creado
     * @throws ValidacionException si el deposito no existe, el producto
     *         esta vacio, la cantidad es negativa, falta el comercio o
     *         esta dado de baja, o ese comercio ya cargo ese producto en
     *         ese deposito
     */
    Long registrarItem(Long idDeposito, DatosItemInventarioDTO datos);

    /**
     * TODO el stock de un deposito, de todos los comercios. Es la vista de
     * operador de Rabbit: quien administra el galpon ve todo lo que hay
     * adentro, sin importar de quien sea. Para las pantallas donde se
     * opera EN NOMBRE DE un comercio, usar
     * {@link #listarItemsPorComercioYDeposito}.
     *
     * @param idDeposito deposito a consultar
     * @return los items de stock de ese deposito
     * @throws ValidacionException si el deposito no existe
     */
    List<ItemInventarioDTO> listarItemsPorDeposito(Long idDeposito);

    /**
     * Solo el stock de UN comercio dentro de un deposito. Es la vista que
     * corresponde cuando se opera en nombre de ese comercio (reservar
     * stock, simular un pedido): ofrecer stock ajeno seria ofrecer algo
     * que reservarStock va a rechazar despues.
     *
     * @param idComercio comercio dueño de la mercaderia consignada
     * @param idDeposito deposito a consultar
     * @return los items de ese comercio en ese deposito; lista vacia si
     *         falta alguno de los dos parametros
     * @throws ValidacionException si el deposito no existe
     */
    List<ItemInventarioDTO> listarItemsPorComercioYDeposito(Long idComercio, Long idDeposito);

    /**
     * Cantidad libre para comprometer: lo disponible menos lo que ya esta
     * reservado por holds vigentes.
     *
     * @param idItem item de stock a consultar
     * @return cantidad que todavia se puede reservar
     * @throws ValidacionException si el item no existe
     */
    int consultarDisponibilidad(Long idItem);

    /**
     * Depositos que tienen stock libre de un producto puntual.
     *
     * @param producto nombre del producto
     * @return depositos con cantidad libre mayor a cero
     */
    List<DepositoDTO> listarDepositosConStock(String producto);
}
