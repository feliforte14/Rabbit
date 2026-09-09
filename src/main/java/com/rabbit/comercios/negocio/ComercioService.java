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
 * ComercioService implementa las dos interfaces de negocio del componente
 * ServicioDeComercios:
 *   - IRegistroComercios: alta, modificación, baja (escritura)
 *   - IConsultaComercios: consultas de solo lectura
 *
 * Son interfaces Java explícitas (no un comentario ni una convención): son
 * EL contrato del componente. Los consumidores inyectan la interfaz que
 * necesitan, no esta clase — así dependen de QUÉ se puede pedir y no de
 * CÓMO está implementado. Un componente que solo lee (por ejemplo
 * ServicioDeInventario, que necesita saber si un comercio está activo antes
 * de reservar stock) inyecta IConsultaComercios y con eso no obtiene, ni
 * por accidente, la capacidad de dar de baja o eliminar un comercio.
 */

import com.rabbit.comercios.dto.*;
import com.rabbit.comercios.datos.model.Comercio;
import com.rabbit.comercios.datos.model.PuntoPicking;
import com.rabbit.comercios.datos.ComercioRepository;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

// SEGURIDAD DECLARATIVA (Jakarta Security / Jakarta Authorization):
// @DeclareRoles documenta qué roles existen para este componente (ver
// también SecurityConfig, que los declara a nivel de toda la app).
//
// @PermitAll a nivel de clase: este WildFly tiene
// default-missing-method-permissions-deny-access=true (standalone.xml,
// subsystem ejb3) — apenas un bean usa CUALQUIER anotación de seguridad,
// todo método sin permiso explícito queda denegado a todo el mundo. Hay
// que decir explícitamente "esto es público" para no romper el resto del
// componente.
//
// @RolesAllowed en eliminarComercio, en el método puntual, es la única
// restricción real: el contenedor rechaza la llamada con
// EJBAccessException si el caller no autenticó con el rol ADMINISTRADOR.
// El rol lo resuelve WildFly contra ApplicationRealm (ver LoginBean para
// el porqué de usar el realm nativo del servidor en vez de un
// IdentityStore propio). Es la operación más sensible del componente —
// borra físicamente datos sin vuelta atrás.
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
@Stateless
public class ComercioService implements IRegistroComercios, IConsultaComercios {

    @Inject
    private ComercioRepository repository;

    // IRegistroComercios

    /**
     * Crea un comercio nuevo. Valida los datos de negocio (ver métodos
     * validar*) antes de tocar la BD; si alguna validación falla, no se
     * persiste nada — la excepción interrumpe el método antes del guardar.
     *
     * @param datos datos ingresados en el formulario de alta
     * @return el ID asignado por la BD al nuevo comercio
     * @throws ValidacionException si algún dato es inválido o el CUIT ya existe
     */
    @Override
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

    /**
     * Actualiza solo los campos fiscales de un comercio existente (razón
     * social, CUIT, email, teléfono) sin tocar el nombre comercial.
     *
     * @param idComercio ID del comercio a actualizar
     * @param datos nuevos datos fiscales
     * @throws ValidacionException si el comercio no existe o los datos son inválidos
     */
    @Override
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

    // Nombre comercial obligatorio, sin más restricción de formato.
    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidacionException("El nombre del comercio es obligatorio");
        }
    }

    // Razón social obligatoria, sin más restricción de formato.
    private void validarRazonSocial(String razonSocial) {
        if (razonSocial == null || razonSocial.isBlank()) {
            throw new ValidacionException("La razón social es obligatoria");
        }
    }

    // Tres chequeos en cadena: obligatorio, formato (con o sin guiones) y
    // unicidad. idComercioActual se excluye de la unicidad para poder
    // reusar este mismo método al actualizar un comercio existente (si no
    // se excluyera, el propio comercio siempre "chocaría" con su CUIT).
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

    // Email opcional: solo se valida el formato si se cargó alguno.
    private void validarEmail(String email) {
        if (email != null && !email.isBlank() && !PATRON_EMAIL.matcher(email.trim()).matches()) {
            throw new ValidacionException("El email tiene un formato inválido");
        }
    }

    // Baja lógica: el comercio sigue en la BD pero activo=false.
    // Arrastra la baja a sus puntos de picking — uno no puede quedar
    // activo si el comercio dueño no lo está.
    @Override
    @Transactional
    public void darDeBajaComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(false);
        repository.actualizar(comercio);

        for (PuntoPicking puntoPicking : repository.listarPuntosPickingActivos(idComercio)) {
            puntoPicking.setActiva(false);
            repository.actualizarPuntoPicking(puntoPicking);
        }
    }

    // Reactiva un comercio dado de baja previamente — vuelve a activo=true
    @Override
    @Transactional
    public void reactivarComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(true);
        repository.actualizar(comercio);
    }

    // --- Puntos de picking (Comercios es dueño de sus puntos de picking) ---

    // Da de alta un punto de picking nuevo sobre un comercio existente
    @Override
    @Transactional
    public Long registrarPuntoPicking(Long idComercio, DatosPuntoPickingDTO datos) {
        Comercio comercio = obtenerOFallar(idComercio);
        if (!comercio.isActivo()) {
            throw new ValidacionException("No se pueden agregar puntos de picking a un comercio dado de baja");
        }
        validarNombrePuntoPicking(datos.nombre);
        validarDireccionPuntoPicking(datos.direccion);

        PuntoPicking puntoPicking = new PuntoPicking();
        puntoPicking.setNombre(datos.nombre.trim());
        puntoPicking.setDireccion(datos.direccion.trim());
        puntoPicking.setActiva(true);
        puntoPicking.setComercio(comercio);
        return repository.guardarPuntoPicking(puntoPicking).getId();
    }

    // Baja lógica de un punto de picking — sigue en la BD pero activa=false
    @Override
    @Transactional
    public void darDeBajaPuntoPicking(Long idPuntoPicking) {
        PuntoPicking puntoPicking = obtenerPuntoPickingOFallar(idPuntoPicking);
        puntoPicking.setActiva(false);
        repository.actualizarPuntoPicking(puntoPicking);
    }

    // Reactiva un punto de picking dado de baja previamente. No tiene
    // sentido si el comercio dueño sigue de baja — primero hay que
    // reactivar el comercio.
    @Override
    @Transactional
    public void reactivarPuntoPicking(Long idPuntoPicking) {
        PuntoPicking puntoPicking = obtenerPuntoPickingOFallar(idPuntoPicking);
        if (!puntoPicking.getComercio().isActivo()) {
            throw new ValidacionException(
                    "No se puede reactivar el punto de picking porque el comercio está dado de baja. Reactive el comercio primero.");
        }
        puntoPicking.setActiva(true);
        repository.actualizarPuntoPicking(puntoPicking);
    }

    // Devuelve todos los puntos de picking de un comercio (activos e inactivos) — pantalla de administración
    @Override
    public List<PuntoPickingDTO> listarPuntosPickingDeComercio(Long idComercio) {
        obtenerOFallar(idComercio);
        return repository.listarPuntosPickingDeComercio(idComercio)
                .stream()
                .map(PuntoPickingDTO::desde)
                .collect(Collectors.toList());
    }

    // Nombre obligatorio, sin más restricción de formato.
    private void validarNombrePuntoPicking(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidacionException("El nombre del punto de picking es obligatorio");
        }
    }

    // Dirección obligatoria, sin más restricción de formato.
    private void validarDireccionPuntoPicking(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            throw new ValidacionException("La dirección del punto de picking es obligatoria");
        }
    }

    // Lanza excepción si el punto de picking no existe — evita repetir
    // este chequeo en cada método que opera sobre uno puntual.
    private PuntoPicking obtenerPuntoPickingOFallar(Long id) {
        PuntoPicking puntoPicking = repository.buscarPuntoPickingPorId(id);
        if (puntoPicking == null) {
            throw new ValidacionException("Punto de picking no encontrado: " + id);
        }
        return puntoPicking;
    }

    // IConsultaComercios

    // Devuelve el comercio como DTO (nunca expone la entidad directamente)
    @Override
    public ComercioDTO obtenerComercio(Long idComercio) {
        return ComercioDTO.desde(obtenerOFallar(idComercio));
    }

    // Devuelve todos los comercios como DTO — usado por la vista de listado (JSF)
    @Override
    public List<ComercioDTO> listarTodos() {
        return repository.listarTodos()
                .stream()
                .map(ComercioDTO::desde)
                .collect(Collectors.toList());
    }

    // Devuelve solo los puntos de picking activos del comercio
    @Override
    public List<PuntoPickingDTO> listarPuntosPicking(Long idComercio) {
        return repository.listarPuntosPickingActivos(idComercio)
                .stream()
                .map(PuntoPickingDTO::desde)
                .collect(Collectors.toList());
    }

    // Usado por otros servicios para verificar si el comercio puede operar
    @Override
    public boolean validarComercioActivo(Long idComercio) {
        Comercio comercio = repository.buscarPorId(idComercio);
        return comercio != null && comercio.isActivo();
    }

    // Eliminación física — borra el registro de la BD permanentemente,
    // junto con todos sus puntos de picking (cascade). Para evitar
    // pérdidas de datos accidentales, solo se permite si el comercio ya
    // fue dado de baja (activo=false) previamente.
    @Override
    @Transactional
    @RolesAllowed("ADMINISTRADOR")
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
