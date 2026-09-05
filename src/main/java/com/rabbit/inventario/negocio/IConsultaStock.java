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
     * Carga stock de un producto en un deposito. Un mismo producto no
     * puede cargarse dos veces como filas separadas en el mismo deposito.
     *
     * @param idDeposito deposito donde se carga el stock
     * @param datos producto y cantidad
     * @return el ID del item creado
     * @throws ValidacionException si el deposito no existe, el producto
     *         esta vacio, la cantidad es negativa o el producto ya estaba
     *         cargado en ese deposito
     */
    Long registrarItem(Long idDeposito, DatosItemInventarioDTO datos);

    /**
     * @param idDeposito deposito a consultar
     * @return los items de stock de ese deposito
     * @throws ValidacionException si el deposito no existe
     */
    List<ItemInventarioDTO> listarItemsPorDeposito(Long idDeposito);

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
