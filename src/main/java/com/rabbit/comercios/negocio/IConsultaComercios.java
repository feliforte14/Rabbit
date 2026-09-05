package com.rabbit.comercios.negocio;

/**
 * CONTRATO DE SOLO LECTURA del componente ServicioDeComercios.
 *
 * Esta es una de las dos interfaces de negocio que expone el componente
 * (la otra es IRegistroComercios). La separacion no es cosmetica: responde
 * al principio de segregacion de interfaces.
 *
 * Los demas componentes del sistema (ServicioDeInventario, ServicioDePedidos,
 * ServicioDePagosYCobranzas, ServicioDeNotificaciones,
 * ServicioDeUsuariosYSeguridad) solo necesitan CONSULTAR datos de un comercio
 * — tipicamente preguntar "este comercio existe y esta habilitado para operar?".
 * Al depender unicamente de esta interfaz, esos componentes:
 *
 *   - No obtienen por dependencia la capacidad de modificar o dar de baja
 *     un comercio. Si manana ServicioDePedidos tuviera un bug, no podria
 *     borrar comercios ni por accidente: el metodo directamente no existe
 *     en el tipo que tiene inyectado.
 *   - No se acoplan a la implementacion (ComercioService) sino al contrato.
 *     Se puede cambiar por completo como esta hecho el componente por dentro
 *     sin tocar una linea de sus consumidores.
 *
 * @Local la marca como interfaz de negocio local: los consumidores viven en
 * el mismo contenedor (WildFly), asi que la invocacion es una llamada Java
 * directa, sin serializacion ni red de por medio.
 *
 * Implementada por {@link ComercioService}.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.SucursalDTO;
import jakarta.ejb.Local;
import java.util.List;

@Local
public interface IConsultaComercios {

    /**
     * Devuelve los datos de un comercio puntual.
     *
     * @param idComercio identificador del comercio
     * @return el comercio como DTO (nunca la entidad JPA)
     * @throws ValidacionException si el comercio no existe
     */
    ComercioDTO obtenerComercio(Long idComercio);

    /**
     * Devuelve el padron completo de comercios, activos e inactivos.
     *
     * @return todos los comercios registrados, como DTOs
     */
    List<ComercioDTO> listarTodos();

    /**
     * Devuelve solo las sucursales ACTIVAS de un comercio — la vista
     * operativa, la que interesa a otros componentes que necesitan saber
     * donde puede operar el comercio hoy.
     *
     * @param idComercio identificador del comercio
     * @return sucursales con activa = true
     */
    List<SucursalDTO> listarSucursales(Long idComercio);

    /**
     * Devuelve TODAS las sucursales de un comercio, activas e inactivas —
     * la vista de administracion, que necesita mostrar tambien las dadas
     * de baja para poder reactivarlas.
     *
     * @param idComercio identificador del comercio
     * @return todas las sucursales del comercio
     * @throws ValidacionException si el comercio no existe
     */
    List<SucursalDTO> listarSucursalesDeComercio(Long idComercio);

    /**
     * Indica si un comercio existe y esta habilitado para operar.
     *
     * Es la operacion mas usada por el resto del sistema: antes de reservar
     * stock, crear un pedido o procesar un cobro, el componente de turno
     * pregunta por aca si el comercio esta activo.
     *
     * A diferencia del resto de los metodos, no lanza excepcion si el
     * comercio no existe: devuelve false. El que pregunta quiere una
     * respuesta si/no, no manejar un error.
     *
     * @param idComercio identificador del comercio
     * @return true si el comercio existe y esta activo
     */
    boolean validarComercioActivo(Long idComercio);
}
