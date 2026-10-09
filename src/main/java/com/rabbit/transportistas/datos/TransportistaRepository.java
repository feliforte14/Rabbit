package com.rabbit.transportistas.datos;

/**
 * DAO del componente Transportistas: transportistas y envíos. Única clase
 * del componente que toca el EntityManager.
 */

import com.rabbit.transportistas.datos.model.Envio;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.datos.model.Transportista;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class TransportistaRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    // --- Transportistas

    public Transportista guardar(Transportista transportista) {
        em.persist(transportista);
        return transportista;
    }

    public Transportista buscarPorId(Long id) {
        return em.find(Transportista.class, id);
    }

    public Transportista actualizar(Transportista transportista) {
        return em.merge(transportista);
    }

    public List<Transportista> listarTodos() {
        return em.createQuery("SELECT t FROM Transportista t ORDER BY t.nombre", Transportista.class).getResultList();
    }

    // --- Envíos

    public Envio guardarEnvio(Envio envio) {
        em.persist(envio);
        return envio;
    }

    public Envio actualizarEnvio(Envio envio) {
        return em.merge(envio);
    }

    public Envio buscarEnvioPorId(Long idEnvio) {
        return em.find(Envio.class, idEnvio);
    }

    // Bloqueo pesimista: el seguimiento y una cancelación pueden tocar el
    // mismo envío a la vez. Si el envío ya estaba cargado en esta
    // transacción, find con bloqueo solo toma el lock y devuelve esa copia
    // (con el estado de antes del bloqueo): se lo saca del contexto y se
    // lo vuelve a leer, ya bloqueado, así el chequeo de estado que sigue
    // es sobre lo último (mismo esquema que
    // PedidoRepository.buscarPedidoParaActualizar).
    public Envio buscarEnvioParaActualizar(Long idEnvio) {
        em.flush();
        Envio cargado = em.find(Envio.class, idEnvio);
        if (cargado == null) {
            return null;
        }
        em.detach(cargado);
        return em.find(Envio.class, idEnvio, LockModeType.PESSIMISTIC_WRITE);
    }

    /** El envío de un pedido, bloqueado y releído; null si no hay. */
    public Envio buscarEnvioDePedidoParaActualizar(Long idPedido) {
        Envio envio = buscarEnvioDePedido(idPedido);
        return envio == null ? null : buscarEnvioParaActualizar(envio.getId());
    }

    /** El envío de un transportista con ese código de seguimiento, sin bloquear; null si no hay. */
    public Envio buscarEnvioPorCodigo(Long idTransportista, String codigoSeguimiento) {
        return em.createQuery("SELECT e FROM Envio e WHERE e.transportista.id = :t AND e.codigoSeguimiento = :c", Envio.class)
                .setParameter("t", idTransportista)
                .setParameter("c", codigoSeguimiento)
                .getResultStream().findFirst().orElse(null);
    }

    public Envio buscarEnvioDePedido(Long idPedido) {
        return em.createQuery("SELECT e FROM Envio e WHERE e.idPedido = :id", Envio.class)
                .setParameter("id", idPedido)
                .getResultStream().findFirst().orElse(null);
    }

    public List<Envio> listarEnvios() {
        return em.createQuery("SELECT e FROM Envio e ORDER BY e.fechaSolicitud DESC", Envio.class).getResultList();
    }

    public List<Envio> listarEnviosActivos() {
        return em.createQuery("SELECT e FROM Envio e WHERE e.estado IN :estados ORDER BY e.fechaSolicitud", Envio.class)
                .setParameter("estados", List.of(EstadoEnvio.SOLICITADO, EstadoEnvio.EN_TRANSITO))
                .getResultList();
    }

    public List<Envio> listarEnviosDeComercio(Long idComercio) {
        return em.createQuery("SELECT e FROM Envio e WHERE e.idComercio = :id ORDER BY e.fechaSolicitud DESC", Envio.class)
                .setParameter("id", idComercio)
                .getResultList();
    }

    public long contarEnviosActivos(Long idTransportista) {
        return em.createQuery("SELECT COUNT(e) FROM Envio e WHERE e.transportista.id = :id AND e.estado IN :estados", Long.class)
                .setParameter("id", idTransportista)
                .setParameter("estados", List.of(EstadoEnvio.SOLICITADO, EstadoEnvio.EN_TRANSITO))
                .getSingleResult();
    }
}
