package com.rabbit.comercios.negocio;

/**
 * CONTRATO DE ESCRITURA del componente ServicioDeComercios.
 *
 * Agrupa las operaciones que modifican el padron: alta, actualizacion de
 * datos fiscales, baja logica, reactivacion y eliminacion fisica, tanto de
 * comercios como de sus sucursales.
 *
 * Es la contracara de {@link IConsultaComercios}. Quien necesita solo leer
 * depende de aquella; quien necesita administrar el padron depende de esta.
 * Hoy el unico consumidor de esta interfaz es la capa de Presentacion
 * (ComercioBean y SucursalBean), que es la que le da al usuario las
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
import com.rabbit.comercios.dto.DatosSucursalDTO;
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
     * Arrastra la baja de todas sus sucursales activas — una sucursal no
     * puede quedar operativa si su comercio no lo esta.
     *
     * @param idComercio ID del comercio a dar de baja
     * @throws ValidacionException si el comercio no existe
     */
    void darDeBajaComercio(Long idComercio);

    /**
     * Reactiva un comercio dado de baja. No reactiva automaticamente sus
     * sucursales: cada una se reactiva por separado, decision explicita.
     *
     * @param idComercio ID del comercio a reactivar
     * @throws ValidacionException si el comercio no existe
     */
    void reactivarComercio(Long idComercio);

    /**
     * Elimina fisicamente el comercio de la base, junto con todas sus
     * sucursales (borrado en cascada). Solo se permite sobre un comercio
     * ya dado de baja — la baja logica previa funciona como confirmacion
     * de que la perdida de datos es intencional.
     *
     * Es la operacion mas sensible del componente: candidata natural a
     * llevar seguridad declarativa por rol.
     *
     * @param idComercio ID del comercio a eliminar
     * @throws ValidacionException si el comercio no existe o sigue activo
     */
    void eliminarComercio(Long idComercio);

    /**
     * Da de alta una sucursal sobre un comercio existente y activo.
     *
     * @param idComercio ID del comercio dueño de la sucursal
     * @param datos datos de la sucursal a registrar
     * @return el ID asignado por la base a la nueva sucursal
     * @throws ValidacionException si el comercio no existe, esta dado de
     *         baja, o los datos de la sucursal son invalidos
     */
    Long registrarSucursal(Long idComercio, DatosSucursalDTO datos);

    /**
     * Baja logica de una sucursal: sigue en la base pero deja de operar.
     *
     * @param idSucursal ID de la sucursal a dar de baja
     * @throws ValidacionException si la sucursal no existe
     */
    void darDeBajaSucursal(Long idSucursal);

    /**
     * Reactiva una sucursal dada de baja. No se permite si el comercio
     * dueño sigue inactivo: primero hay que reactivar el comercio.
     *
     * @param idSucursal ID de la sucursal a reactivar
     * @throws ValidacionException si la sucursal no existe o su comercio
     *         esta dado de baja
     */
    void reactivarSucursal(Long idSucursal);
}
