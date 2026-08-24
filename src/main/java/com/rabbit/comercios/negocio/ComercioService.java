package com.rabbit.comercios.negocio;

/**
 * CAPA DE NEGOCIO (EJB Stateless)
 *
 * Esta carpeta contiene la lógica de negocio del sistema.
 * El Service orquesta las operaciones: valida, transforma datos y delega
 * el acceso a la BD a la capa de Datos — nunca toca JPA directamente.
 *
 * @Stateless significa que el servidor de aplicaciones (WildFly) administra
 * un pool de instancias de esta clase. Cada request toma una instancia libre,
 * la usa y la devuelve — sin estado entre llamadas. Esto escala bien.
 *
 * @Transactional garantiza que cada operación de escritura sea atómica:
 * si algo falla a mitad, la BD vuelve al estado anterior (rollback automático).
 *
 * ComercioService implementa dos interfaces conceptuales del TPO:
 *   - IRegistroComercios: alta, modificación, baja
 *   - IConsultaComercios: consultas de solo lectura
 */

import com.rabbit.comercios.dto.*;
import com.rabbit.comercios.datos.model.Comercio;
import com.rabbit.comercios.datos.ComercioRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class ComercioService {

    @Inject
    private ComercioRepository repository;

    // IRegistroComercios

    // Crea un comercio nuevo y devuelve su ID asignado por la BD
    @Transactional
    public Long registrarComercio(DatosComercioDTO datos) {
        Comercio comercio = new Comercio();
        comercio.setNombre(datos.nombre);
        comercio.setRazonSocial(datos.razonSocial);
        comercio.setCuit(datos.cuit);
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        comercio.setActivo(true);
        return repository.guardar(comercio).getId();
    }

    // Actualiza solo los campos fiscales sin tocar el nombre
    @Transactional
    public void actualizarDatosFiscales(Long idComercio, DatosFiscalesDTO datos) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setRazonSocial(datos.razonSocial);
        comercio.setCuit(datos.cuit);
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        repository.actualizar(comercio);
    }

    // Baja lógica: el comercio sigue en la BD pero activo=false
    @Transactional
    public void darDeBajaComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(false);
        repository.actualizar(comercio);
    }

    // IConsultaComercios

    // Devuelve el comercio como DTO (nunca expone la entidad directamente)
    public ComercioDTO obtenerComercio(Long idComercio) {
        return ComercioDTO.desde(obtenerOFallar(idComercio));
    }

    // Devuelve todos los comercios como DTO — usado por la vista de listado (JSF)
    public List<ComercioDTO> listarTodos() {
        return repository.listarTodos()
                .stream()
                .map(ComercioDTO::desde)
                .collect(Collectors.toList());
    }

    // Devuelve solo las sucursales activas del comercio
    public List<SucursalDTO> listarSucursales(Long idComercio) {
        return repository.listarSucursalesActivas(idComercio)
                .stream()
                .map(SucursalDTO::desde)
                .collect(Collectors.toList());
    }

    // Usado por otros servicios para verificar si el comercio puede operar
    public boolean validarComercioActivo(Long idComercio) {
        Comercio comercio = repository.buscarPorId(idComercio);
        return comercio != null && comercio.isActivo();
    }

    // Eliminación física — borra el registro de la BD permanentemente
    @Transactional
    public void eliminarComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        repository.eliminar(comercio);
    }

    // Lanza excepción si el comercio no existe — evita repetir este chequeo en cada método
    private Comercio obtenerOFallar(Long id) {
        Comercio comercio = repository.buscarPorId(id);
        if (comercio == null) {
            throw new IllegalArgumentException("Comercio no encontrado: " + id);
        }
        return comercio;
    }
}
