package com.rabbit.repartidores.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.repartidores.datos.model.CargaAsignada;
import com.rabbit.repartidores.datos.model.Repartidor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class RepartidorRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Repartidor guardar(Repartidor repartidor) {
        em.persist(repartidor);
        return repartidor;
    }

    public Repartidor buscarPorId(Long id) {
        return em.find(Repartidor.class, id);
    }

    public Repartidor buscarParaActualizar(Long id) {
        return em.find(Repartidor.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Todos los repartidores, bloqueados (SELECT ... FOR UPDATE) hasta que
     * termine la transacción, con los de la zona preferida primero. La
     * asignación elige entre ellos el primero al que le entra el pedido
     * (ADR-032): con el bloqueo, dos pedidos confirmados a la vez no
     * pueden llenar de más al mismo repartidor. Son pocas filas.
     */
    public List<Repartidor> bloquearCandidatos(Long idZonaPreferida) {
        return em.createQuery(
                        "SELECT r FROM Repartidor r "
                                + "ORDER BY CASE WHEN r.idZona = :zona THEN 0 ELSE 1 END, r.id", Repartidor.class)
                .setParameter("zona", idZonaPreferida != null ? idZonaPreferida : -1L)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    public List<CargaAsignada> listarCargas() {
        return em.createQuery("SELECT c FROM CargaAsignada c ORDER BY c.fechaAsignacion, c.id", CargaAsignada.class)
                .getResultList();
    }

    public List<CargaAsignada> listarCargasDe(Long idRepartidor) {
        return em.createQuery("SELECT c FROM CargaAsignada c WHERE c.idRepartidor = :r "
                        + "ORDER BY c.fechaAsignacion, c.id", CargaAsignada.class)
                .setParameter("r", idRepartidor)
                .getResultList();
    }

    public void guardarCarga(CargaAsignada carga) {
        em.persist(carga);
    }

    /** Borra la carga del pedido; false si no había (pedido asignado antes de las cargas). */
    public boolean borrarCargaDePedido(Long idPedido) {
        return em.createQuery("DELETE FROM CargaAsignada c WHERE c.idPedido = :p")
                .setParameter("p", idPedido)
                .executeUpdate() > 0;
    }

    public List<Repartidor> listarTodos() {
        return em.createQuery("SELECT r FROM Repartidor r ORDER BY r.id", Repartidor.class).getResultList();
    }

    public Repartidor actualizar(Repartidor repartidor) {
        return em.merge(repartidor);
    }
}
