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
 * quien llama. Por eso, si en PedidoService.confirmarPedido el cobro se
 * rechaza DESPUÉS de asignar el repartidor, el rollback lo devuelve solo a
 * DISPONIBLE — nadie tiene que "desasignarlo" a mano.
 */

import com.rabbit.repartidores.datos.RepartidorRepository;
import com.rabbit.repartidores.datos.model.EstadoRepartidor;
import com.rabbit.repartidores.datos.model.Repartidor;
import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque asignar y liberar corren dentro de
// transacciones de Pedidos que también dispara un REPARTIDOR; dar de alta
// repartidores es solo del personal de Rabbit.
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
public class RepartidorService implements IAsignacionRepartidores, IGestionRepartidores {

    private static final Logger LOG = Logger.getLogger(RepartidorService.class.getName());

    @Inject
    private RepartidorRepository repository;

    // ===============================================================
    // IAsignacionRepartidores
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Long asignarRepartidor(Long idPedido) {
        return asignarRepartidor(idPedido, null);
    }

    // Prefiere un repartidor de la zona del pedido (ver RuteoService); si no
    // hay ninguno libre ahí, toma cualquiera.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Long asignarRepartidor(Long idPedido, Long idZonaPreferida) {
        Repartidor repartidor = repository.tomarPrimeroDisponible(idZonaPreferida);
        if (repartidor == null) {
            throw new ValidacionException("No hay repartidores disponibles para el pedido " + idPedido);
        }
        repartidor.setEstado(EstadoRepartidor.OCUPADO);
        repartidor.setIdPedidoActual(idPedido);
        repository.actualizar(repartidor);
        LOG.info("[Repartidores] Repartidor " + repartidor.getId() + " asignado al pedido " + idPedido);
        return repartidor.getId();
    }

    // Idempotente: liberar a alguien que ya está DISPONIBLE no hace nada.
    // Un idRepartidor null (pedido confirmado antes de existir este
    // componente) también se ignora.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void liberarRepartidor(Long idRepartidor) {
        if (idRepartidor == null) {
            return;
        }
        Repartidor repartidor = repository.buscarParaActualizar(idRepartidor);
        if (repartidor == null || repartidor.getEstado() == EstadoRepartidor.DISPONIBLE) {
            return;
        }
        repartidor.setEstado(EstadoRepartidor.DISPONIBLE);
        repartidor.setIdPedidoActual(null);
        repository.actualizar(repartidor);
        LOG.info("[Repartidores] Repartidor " + idRepartidor + " liberado");
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
        repartidor.setEstado(EstadoRepartidor.DISPONIBLE);
        return repository.guardar(repartidor).getId();
    }

    @Override
    public List<RepartidorDTO> listarTodos() {
        return repository.listarTodos().stream().map(RepartidorDTO::desde).collect(Collectors.toList());
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
        return repartidor != null ? RepartidorDTO.desde(repartidor) : null;
    }
}
