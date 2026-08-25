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
        validarNombre(datos.nombre);
        validarRazonSocial(datos.razonSocial);
        validarCuit(datos.cuit, null);
        validarEmail(datos.email);

        Comercio comercio = new Comercio();
        comercio.setNombre(datos.nombre.trim());
        comercio.setRazonSocial(datos.razonSocial.trim());
        comercio.setCuit(datos.cuit.trim());
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        comercio.setActivo(true);
        return repository.guardar(comercio).getId();
    }

    // Actualiza solo los campos fiscales sin tocar el nombre
    @Transactional
    public void actualizarDatosFiscales(Long idComercio, DatosFiscalesDTO datos) {
        Comercio comercio = obtenerOFallar(idComercio);
        validarRazonSocial(datos.razonSocial);
        validarCuit(datos.cuit, idComercio);
        validarEmail(datos.email);

        comercio.setRazonSocial(datos.razonSocial.trim());
        comercio.setCuit(datos.cuit.trim());
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        repository.actualizar(comercio);
    }

    // --- Validaciones de negocio ---
    // Se centralizan acá (no en el Bean ni en la vista) porque son reglas del
    // dominio: deben cumplirse sin importar desde dónde se invoque el Service.

    private static final java.util.regex.Pattern PATRON_CUIT =
            java.util.regex.Pattern.compile("^\\d{2}-?\\d{8}-?\\d$");
    private static final java.util.regex.Pattern PATRON_EMAIL =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidacionException("El nombre del comercio es obligatorio");
        }
    }

    private void validarRazonSocial(String razonSocial) {
        if (razonSocial == null || razonSocial.isBlank()) {
            throw new ValidacionException("La razón social es obligatoria");
        }
    }

    private void validarCuit(String cuit, Long idComercioActual) {
        if (cuit == null || cuit.isBlank()) {
            throw new ValidacionException("El CUIT es obligatorio");
        }
        if (!PATRON_CUIT.matcher(cuit.trim()).matches()) {
            throw new ValidacionException("El CUIT debe tener el formato XX-XXXXXXXX-X (11 dígitos)");
        }
        if (repository.existeCuit(cuit.trim(), idComercioActual)) {
            throw new ValidacionException("Ya existe un comercio registrado con el CUIT " + cuit);
        }
    }

    private void validarEmail(String email) {
        if (email != null && !email.isBlank() && !PATRON_EMAIL.matcher(email.trim()).matches()) {
            throw new ValidacionException("El email tiene un formato inválido");
        }
    }

    // Baja lógica: el comercio sigue en la BD pero activo=false
    @Transactional
    public void darDeBajaComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(false);
        repository.actualizar(comercio);
    }

    // Reactiva un comercio dado de baja previamente — vuelve a activo=true
    @Transactional
    public void reactivarComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(true);
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

    // Eliminación física — borra el registro de la BD permanentemente,
    // junto con todas sus sucursales (cascade). Para evitar pérdidas de
    // datos accidentales, solo se permite si el comercio ya fue dado de
    // baja (activo=false) previamente.
    @Transactional
    public void eliminarComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        if (comercio.isActivo()) {
            throw new ValidacionException(
                    "No se puede eliminar un comercio activo. Debe darse de baja primero.");
        }
        repository.eliminar(comercio);
    }

    // Lanza excepción si el comercio no existe — evita repetir este chequeo en cada método
    private Comercio obtenerOFallar(Long id) {
        Comercio comercio = repository.buscarPorId(id);
        if (comercio == null) {
            throw new ValidacionException("Comercio no encontrado: " + id);
        }
        return comercio;
    }
}
