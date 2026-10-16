package com.rabbit.repartidores.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDeRepartidores (EJB @Stateless).
 *
 * Implementa las dos caras del componente:
 *   - IAsignacionRepartidores: lo que usa PedidoService al confirmar,
 *     entregar o cancelar un pedido.
 *   - IGestionRepartidores: alta y listado para repartidores.xhtml.
 *
 * Todas las escrituras corren con REQUIRED: se suman a la transacción de
 * quien llama. Por eso, si en PedidoService.confirmarPedido algo falla
 * DESPUÉS de asignar el repartidor, el rollback deshace la asignación
 * (estado y CargaAsignada) — nadie tiene que "desasignarlo" a mano.
 *
 * Capacidad (ADR-032): un repartidor con capacidad cargada lleva varios
 * pedidos mientras le entren; sin capacidad, uno a la vez (ver
 * CapacidadRepartidor).
 */

import com.rabbit.repartidores.datos.RepartidorRepository;
import com.rabbit.repartidores.datos.model.CargaAsignada;
import com.rabbit.repartidores.datos.model.EstadoRepartidor;
import com.rabbit.repartidores.datos.model.Repartidor;
import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque asignar y liberar corren dentro de
// transacciones de Pedidos que también dispara un REPARTIDOR; dar de alta
// repartidores es solo del personal de Rabbit, y eliminarlos, solo del
// administrador.
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
@PermitAll
public class RepartidorService implements IAsignacionRepartidores, IGestionRepartidores {

    private static final Logger LOG = Logger.getLogger(RepartidorService.class.getName());

    // Un camión mediano de reparto urbano.
    private static final BigDecimal CAPACIDAD_MAXIMA_KG = new BigDecimal("10000");
    private static final int CAPACIDAD_MAXIMA_BULTOS = 1000;

    // Una ubicación más vieja que esto ya no se muestra: el celular dejó de
    // mandarla (pantalla cerrada, sin señal).
    private static final int MINUTOS_UBICACION_VIGENTE = 15;

    @Inject
    private RepartidorRepository repository;

    // Quién es el repartidor que llama (registrarUbicacion).
    @Inject
    private IContextoUsuario contextoUsuario;

    // Antes de eliminar, pregunta si algo todavía necesita al repartidor y
    // después avisa que se elimina (ver eliminarRepartidor).
    @Inject
    private Event<EliminacionDeRepartidor> eliminacionDeRepartidor;

    @Inject
    private Event<RepartidorEliminado> repartidorEliminado;

    // ===============================================================
    // IAsignacionRepartidores
    // ===============================================================

    // Recorre los repartidores (los de la zona del pedido primero) y se lo
    // da al primero al que le entra (ADR-032, ADR-037). Los candidatos
    // quedan bloqueados hasta el fin de la transacción: dos confirmaciones
    // a la vez no pueden cargar de más al mismo repartidor.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Long asignarRepartidor(Long idPedido, Long idZonaPreferida, BigDecimal pesoKg, int bultos,
                                  Long idRepartidorExcluido) {
        String corte = motivoDelCorte();
        if (corte != null) {
            throw new SinLugarException(corte);
        }
        List<Repartidor> candidatos = repository.bloquearCandidatos(idZonaPreferida);
        List<CargaAsignada> cargas = repository.listarCargas();
        List<RepartidorDTO> conCarga = candidatos.stream().map(r -> RepartidorDTO.desde(r, cargas)).collect(Collectors.toList());
        RepartidorDTO elegido = AsignacionPorCapacidad.elegir(conCarga, idRepartidorExcluido, pesoKg, bultos);
        if (elegido == null) {
            throw new SinLugarException(sinLugar(conCarga, idRepartidorExcluido, pesoKg, bultos));
        }
        Repartidor repartidor = candidatos.stream().filter(r -> r.getId().equals(elegido.getId())).findFirst().orElseThrow();
        CargaAsignada carga = new CargaAsignada();
        carga.setIdRepartidor(repartidor.getId());
        carga.setIdPedido(idPedido);
        carga.setPesoKg(pesoKg);
        carga.setBultos(bultos);
        carga.setFechaAsignacion(LocalDateTime.now());
        repository.guardarCarga(carga);
        repartidor.setEstado(EstadoRepartidor.OCUPADO);
        repartidor.setIdPedidoActual(idPedido);
        repository.actualizar(repartidor);
        LOG.info("[Repartidores] Repartidor " + repartidor.getId() + " asignado al pedido " + idPedido
                + " (" + (pesoKg != null ? pesoKg + " kg, " : "") + bultos + " bulto(s))");
        return repartidor.getId();
    }

    // Sin bloqueo: es solo para decidir si vale la pena cobrar e intentar
    // la asignación. Si entre esta consulta y asignarRepartidor otro se
    // queda con el lugar, asignarRepartidor igual lanza SinLugarException.
    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public String motivoParaEsperar(BigDecimal pesoKg, int bultos, Long idRepartidorExcluido) {
        String corte = motivoDelCorte();
        if (corte != null) {
            return corte;
        }
        List<RepartidorDTO> todos = listarTodos();
        if (AsignacionPorCapacidad.elegir(todos, idRepartidorExcluido, pesoKg, bultos) != null) {
            return null;
        }
        return sinLugar(todos, idRepartidorExcluido, pesoKg, bultos);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean antesDelCorte() {
        return motivoDelCorte() == null;
    }

    private static String motivoDelCorte() {
        LocalTime corte = HoraDeCorte.configurada();
        if (HoraDeCorte.permiteAsignar(corte, LocalTime.now())) {
            return null;
        }
        return "Pasó la hora de corte (" + corte + "): se asigna mañana";
    }

    // Por qué no le entra a nadie, corto: se muestra en la tabla de Pedidos.
    // Si el pedido fue devuelto, el que lo devolvió no cuenta, y se aclara
    // (si no, "nadie tiene lugar" confunde cuando a él sí le sobra).
    private static String sinLugar(List<RepartidorDTO> repartidores, Long idExcluido, BigDecimal pesoKg, int bultos) {
        if (repartidores.isEmpty()) {
            return "No hay repartidores cargados";
        }
        String carga = (pesoKg != null ? com.rabbit.infraestructura.Formatos.kilos(pesoKg) + " y " : "")
                + bultos + (bultos == 1 ? " bulto" : " bultos");
        if (idExcluido == null) {
            return "Ningún repartidor tiene lugar para " + carga;
        }
        boolean aQuienLoDevolvioLeEntra = repartidores.stream()
                .anyMatch(r -> r.getId().equals(idExcluido) && r.entra(pesoKg, bultos));
        if (aQuienLoDevolvioLeEntra) {
            return "Se espera a otro repartidor con lugar para " + carga + ": no vuelve al que lo devolvió";
        }
        return "Ningún otro repartidor tiene lugar para " + carga + " (no vuelve al que lo devolvió)";
    }

    // Idempotente: liberar un pedido que ya no lleva no hace nada. Un
    // idRepartidor null (pedido confirmado antes de existir este
    // componente) también se ignora.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void liberarRepartidor(Long idRepartidor, Long idPedido) {
        if (idRepartidor == null) {
            return;
        }
        Repartidor repartidor = repository.buscarParaActualizar(idRepartidor);
        if (repartidor == null) {
            return;
        }
        boolean teniaCarga = repository.borrarCargaDePedido(idPedido);
        boolean asignadoAntesDeLasCargas = !teniaCarga && idPedido.equals(repartidor.getIdPedidoActual());
        if (!teniaCarga && !asignadoAntesDeLasCargas) {
            return;
        }
        List<CargaAsignada> quedan = repository.listarCargasDe(idRepartidor);
        if (quedan.isEmpty()) {
            repartidor.setEstado(EstadoRepartidor.DISPONIBLE);
            repartidor.setIdPedidoActual(null);
        } else {
            repartidor.setIdPedidoActual(quedan.get(quedan.size() - 1).getIdPedido());
        }
        repository.actualizar(repartidor);
        LOG.info("[Repartidores] Repartidor " + idRepartidor + " deja el pedido " + idPedido
                + (quedan.isEmpty() ? ": queda libre" : ": le quedan " + quedan.size()));
    }

    // ===============================================================
    // IGestionRepartidores
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long registrarRepartidor(DatosRepartidorDTO datos) {
        if (datos.nombre == null || datos.nombre.isBlank()) {
            throw new ValidacionException("El nombre del repartidor es obligatorio");
        }
        Repartidor repartidor = new Repartidor();
        repartidor.setNombre(datos.nombre.trim());
        repartidor.setTelefono(datos.telefono);
        repartidor.setIdZona(datos.idZona);
        validarCapacidad(datos.capacidadKg, datos.capacidadBultos);
        repartidor.setCapacidadKg(datos.capacidadKg);
        repartidor.setCapacidadBultos(datos.capacidadBultos);
        repartidor.setEstado(EstadoRepartidor.DISPONIBLE);
        return repository.guardar(repartidor).getId();
    }

    // Con lo que lleva cada uno: el tablero y el despacho por zona lo usan
    // para saber a quién le queda lugar.
    @Override
    public List<RepartidorDTO> listarTodos() {
        List<CargaAsignada> cargas = repository.listarCargas();
        return repository.listarTodos().stream()
                .map(r -> RepartidorDTO.desde(r, cargas))
                .collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void actualizarCapacidad(Long idRepartidor, BigDecimal capacidadKg, Integer capacidadBultos) {
        Repartidor repartidor = idRepartidor != null ? repository.buscarPorId(idRepartidor) : null;
        if (repartidor == null) {
            throw new ValidacionException("Repartidor no encontrado: " + idRepartidor);
        }
        validarCapacidad(capacidadKg, capacidadBultos);
        repartidor.setCapacidadKg(capacidadKg);
        repartidor.setCapacidadBultos(capacidadBultos);
        repository.actualizar(repartidor);
    }

    // ===============================================================
    // Ubicación (ADR-035)
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("REPARTIDOR")
    public void registrarUbicacion(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new ValidacionException("Ubicación inválida");
        }
        Long idRepartidor = contextoUsuario.idRepartidorActual();
        Repartidor repartidor = repository.buscarPorId(idRepartidor);
        // Fuera de servicio no se registra: la ubicación es para seguir
        // entregas, no a la persona.
        if (repartidor == null || repartidor.getEstado() != EstadoRepartidor.OCUPADO) {
            throw new ValidacionException("La ubicación solo se comparte mientras tenés entregas en curso");
        }
        repartidor.setLatitud(latitud);
        repartidor.setLongitud(longitud);
        repartidor.setFechaUbicacion(LocalDateTime.now());
        repository.actualizar(repartidor);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<UbicacionDTO> listarUbicacionesRecientes() {
        List<CargaAsignada> cargas = repository.listarCargas();
        return repository.listarTodos().stream()
                .filter(r -> r.getEstado() == EstadoRepartidor.OCUPADO && esReciente(r))
                .map(r -> ubicacion(r, RepartidorDTO.desde(r, cargas).getPedidosEnCurso()))
                .collect(Collectors.toList());
    }

    // Sin rol: la usa la página pública de seguimiento. Solo devuelve algo
    // si ese repartidor tiene ESE pedido en curso, así el código de un
    // pedido viejo no sirve para seguir al repartidor en otro reparto.
    @Override
    public UbicacionDTO ubicacionParaPedido(Long idRepartidor, Long idPedido) {
        Repartidor repartidor = idRepartidor != null ? repository.buscarPorId(idRepartidor) : null;
        if (repartidor == null || !esReciente(repartidor)) {
            return null;
        }
        boolean loLleva = repository.listarCargasDe(idRepartidor).stream()
                .anyMatch(c -> c.getIdPedido().equals(idPedido))
                || idPedido.equals(repartidor.getIdPedidoActual());
        return loLleva ? ubicacion(repartidor, 0) : null;
    }

    private static boolean esReciente(Repartidor r) {
        return r.getLatitud() != null && r.getFechaUbicacion() != null
                && r.getFechaUbicacion().isAfter(LocalDateTime.now().minusMinutes(MINUTOS_UBICACION_VIGENTE));
    }

    private static UbicacionDTO ubicacion(Repartidor r, int pedidosEnCurso) {
        UbicacionDTO dto = new UbicacionDTO();
        dto.idRepartidor = r.getId();
        dto.nombre = r.getNombre();
        dto.latitud = r.getLatitud();
        dto.longitud = r.getLongitud();
        dto.fecha = r.getFechaUbicacion();
        dto.pedidosEnCurso = pedidosEnCurso;
        return dto;
    }

    // Opcionales; si vienen, positivos y razonables para un vehículo de reparto.
    private void validarCapacidad(BigDecimal capacidadKg, Integer capacidadBultos) {
        if (capacidadKg != null && (capacidadKg.signum() <= 0 || capacidadKg.compareTo(CAPACIDAD_MAXIMA_KG) > 0)) {
            throw new ValidacionException("La capacidad en kilos tiene que ser mayor a 0 y de hasta "
                    + CAPACIDAD_MAXIMA_KG + " kg");
        }
        if (capacidadBultos != null && (capacidadBultos <= 0 || capacidadBultos > CAPACIDAD_MAXIMA_BULTOS)) {
            throw new ValidacionException("La capacidad en bultos tiene que ser de 1 a " + CAPACIDAD_MAXIMA_BULTOS);
        }
    }

    // Mismo esquema que ComercioService.eliminarComercio: cada componente
    // puede impedirlo y, si nadie lo impide, suelta lo suyo antes del
    // borrado, en la misma transacción.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void eliminarRepartidor(Long idRepartidor) {
        Repartidor repartidor = idRepartidor != null ? repository.buscarParaActualizar(idRepartidor) : null;
        if (repartidor == null) {
            throw new ValidacionException("Repartidor no encontrado: " + idRepartidor);
        }
        if (repartidor.getEstado() == EstadoRepartidor.OCUPADO || !repository.listarCargasDe(idRepartidor).isEmpty()) {
            throw new ValidacionException("No se puede eliminar a " + repartidor.getNombre()
                    + ": tiene entregas en curso. Esperá a que las termine.");
        }
        EliminacionDeRepartidor evento = new EliminacionDeRepartidor(idRepartidor);
        eliminacionDeRepartidor.fire(evento);
        if (!evento.getImpedimentos().isEmpty()) {
            throw new ValidacionException("No se puede eliminar a " + repartidor.getNombre() + ": tiene "
                    + String.join(", ", evento.getImpedimentos()) + ".");
        }
        repartidorEliminado.fire(new RepartidorEliminado(idRepartidor));
        repository.eliminar(repartidor);
        LOG.info("[Repartidores] Repartidor " + idRepartidor + " eliminado");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void asignarZona(Long idRepartidor, Long idZona) {
        Repartidor repartidor = idRepartidor != null ? repository.buscarPorId(idRepartidor) : null;
        if (repartidor == null) {
            throw new ValidacionException("Repartidor no encontrado: " + idRepartidor);
        }
        repartidor.setIdZona(idZona);
        repository.actualizar(repartidor);
    }

    @Override
    public RepartidorDTO obtenerRepartidor(Long idRepartidor) {
        Repartidor repartidor = idRepartidor != null ? repository.buscarPorId(idRepartidor) : null;
        return repartidor != null ? RepartidorDTO.desde(repartidor, repository.listarCargasDe(idRepartidor)) : null;
    }
}
