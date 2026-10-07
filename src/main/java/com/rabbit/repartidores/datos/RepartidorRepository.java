package com.rabbit.repartidores.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.repartidores.datos.model.EstadoRepartidor;
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
     * El primer repartidor DISPONIBLE, bloqueado (SELECT ... FOR UPDATE)
     * hasta que termine la transacción. Sin el bloqueo, dos pedidos
     * confirmados a la vez podrían leer al mismo repartidor como libre y
     * quedar los dos asignados a él. null si no hay ninguno.
     */
    public Repartidor tomarPrimeroDisponible() {
        List<Repartidor> libres = em.createQuery(
                        "SELECT r FROM Repartidor r WHERE r.estado = :estado ORDER BY r.id", Repartidor.class)
                .setParameter("estado", EstadoRepartidor.DISPONIBLE)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
        return libres.isEmpty() ? null : libres.get(0);
    }

    // Primero uno DISPONIBLE de la zona preferida; si no hay, cualquiera
    // DISPONIBLE. Mismo bloqueo pesimista que tomarPrimeroDisponible.
    public Repartidor tomarPrimeroDisponible(Long idZonaPreferida) {
        if (idZonaPreferida == null) {
            return tomarPrimeroDisponible();
        }
        List<Repartidor> libres = em.createQuery(
                        "SELECT r FROM Repartidor r WHERE r.estado = :estado "
                                + "ORDER BY CASE WHEN r.idZona = :zona THEN 0 ELSE 1 END, r.id", Repartidor.class)
                .setParameter("estado", EstadoRepartidor.DISPONIBLE)
                .setParameter("zona", idZonaPreferida)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
        return libres.isEmpty() ? null : libres.get(0);
    }

    public List<Repartidor> listarTodos() {
        return em.createQuery("SELECT r FROM Repartidor r ORDER BY r.id", Repartidor.class).getResultList();
    }

    public Repartidor actualizar(Repartidor repartidor) {
        return em.merge(repartidor);
    }
}
