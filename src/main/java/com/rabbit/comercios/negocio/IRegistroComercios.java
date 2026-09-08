package com.rabbit.comercios.negocio;

/**
 * CONTRATO DE ESCRITURA del componente ServicioDeComercios.
 *
 * Agrupa las operaciones que modifican el padron: alta, actualizacion de
 * datos fiscales, baja logica, reactivacion y eliminacion fisica, tanto de
 * comercios como de sus puntos de picking.
 *
 * Es la contracara de {@link IConsultaComercios}. Quien necesita solo leer
 * depende de aquella; quien necesita administrar el padron depende de esta.
 * Hoy el unico consumidor de esta interfaz es la capa de Presentacion
 * (ComercioBean y PuntoPickingBean), que es la que le da al usuario las
 * pantallas de administracion.
 *
 * Todas las operaciones de escritura son transaccionales y validan las
 * reglas del dominio antes de tocar la base. Si una regla no se cumple,
 * lanzan ValidacionException — anotada con @ApplicationException(rollback
 * = true), de modo que el contenedor revierte la transaccion en curso y
 * la base queda como estaba.
 *
 * @Local la marca como interfaz de negocio local (misma JVM, sin red).
 *
 * Implementada por {@link ComercioService}.
 */

import com.rabbit.comercios.dto.DatosComercioDTO;
import com.rabbit.comercios.dto.DatosFiscalesDTO;
import com.rabbit.comercios.dto.DatosPuntoPickingDTO;
import jakarta.ejb.Local;

@Local
public interface IRegistroComercios {

    /**
     * Da de alta un comercio nuevo, ya en estado activo.
     *
     * @param datos datos ingresados en el formulario de alta
     * @return el ID asignado por la base al nuevo comercio
     * @throws ValidacionException si algun dato es invalido o el CUIT ya existe
     */
    Long registrarComercio(DatosComercioDTO datos);

    /**
     * Actualiza los campos fiscales de un comercio (razon social, CUIT,
     * email, telefono) sin tocar el nombre comercial.
     *
     * @param idComercio ID del comercio a actualizar
     * @param datos nuevos datos fiscales
     * @throws ValidacionException si el comercio no existe o los datos son invalidos
     */
    void actualizarDatosFiscales(Long idComercio, DatosFiscalesDTO datos);

    /**
     * Baja logica del comercio: sigue en la base pero deja de operar.
     * Arrastra la baja de todos sus puntos de picking activos — un punto
     * de picking no puede quedar operativo si su comercio no lo esta.
     *
     * @param idComercio ID del comercio a dar de baja
     * @throws ValidacionException si el comercio no existe
     */
    void darDeBajaComercio(Long idComercio);

    /**
     * Reactiva un comercio dado de baja. No reactiva automaticamente sus
     * puntos de picking: cada uno se reactiva por separado, decision explicita.
     *
     * @param idComercio ID del comercio a reactivar
     * @throws ValidacionException si el comercio no existe
     */
    void reactivarComercio(Long idComercio);

    /**
     * Elimina fisicamente el comercio de la base, junto con todos sus
     * puntos de picking (borrado en cascada). Solo se permite sobre un
     * comercio ya dado de baja — la baja logica previa funciona como
     * confirmacion de que la perdida de datos es intencional.
     *
     * Es la operacion mas sensible del componente: candidata natural a
     * llevar seguridad declarativa por rol.
     *
     * @param idComercio ID del comercio a eliminar
     * @throws ValidacionException si el comercio no existe o sigue activo
     */
    void eliminarComercio(Long idComercio);

    /**
     * Da de alta un punto de picking sobre un comercio existente y activo.
     *
     * @param idComercio ID del comercio dueño del punto de picking
     * @param datos datos del punto de picking a registrar
     * @return el ID asignado por la base al nuevo punto de picking
     * @throws ValidacionException si el comercio no existe, esta dado de
     *         baja, o los datos son invalidos
     */
    Long registrarPuntoPicking(Long idComercio, DatosPuntoPickingDTO datos);

    /**
     * Baja logica de un punto de picking: sigue en la base pero deja de operar.
     *
     * @param idPuntoPicking ID del punto de picking a dar de baja
     * @throws ValidacionException si el punto de picking no existe
     */
    void darDeBajaPuntoPicking(Long idPuntoPicking);

    /**
     * Reactiva un punto de picking dado de baja. No se permite si el
     * comercio dueño sigue inactivo: primero hay que reactivar el comercio.
     *
     * @param idPuntoPicking ID del punto de picking a reactivar
     * @throws ValidacionException si el punto de picking no existe o su
     *         comercio esta dado de baja
     */
    void reactivarPuntoPicking(Long idPuntoPicking);
}
