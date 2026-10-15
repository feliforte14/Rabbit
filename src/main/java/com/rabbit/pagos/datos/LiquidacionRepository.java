package com.rabbit.pagos.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — liquidaciones mensuales
 * (ADR-034). Ver ComercioRepository para la explicación del patrón.
 */

import com.rabbit.pagos.datos.model.EstadoLiquidacion;
import com.rabbit.pagos.datos.model.Liquidacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class LiquidacionRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public void guardar(Liquidacion liquidacion) {
        em.persist(liquidacion);
    }

    public Liquidacion buscarPorId(Long id) {
        return em.find(Liquidacion.class, id);
    }

    /** Bloqueada hasta el fin de la transacción: dos cobros de la misma no se cruzan. */
    public Liquidacion buscarParaActualizar(Long id) {
        return em.find(Liquidacion.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public boolean existe(Long idComercio, String periodo) {
        return em.createQuery("SELECT COUNT(l) FROM Liquidacion l WHERE l.idComercio = :c AND l.periodo = :p", Long.class)
                .setParameter("c", idComercio)
                .setParameter("p", periodo)
                .getSingleResult() > 0;
    }

    public List<Liquidacion> listarDelPeriodo(String periodo) {
        return em.createQuery("SELECT l FROM Liquidacion l WHERE l.periodo = :p ORDER BY l.idComercio", Liquidacion.class)
                .setParameter("p", periodo)
                .getResultList();
    }

    public List<Liquidacion> listarDeComercio(Long idComercio) {
        return em.createQuery("SELECT l FROM Liquidacion l WHERE l.idComercio = :c ORDER BY l.periodo DESC", Liquidacion.class)
                .setParameter("c", idComercio)
                .getResultList();
    }

    public List<String> listarPeriodos() {
        return em.createQuery("SELECT DISTINCT l.periodo FROM Liquidacion l ORDER BY l.periodo DESC", String.class)
                .getResultList();
    }

    public List<Long> listarIdsPendientes() {
        return em.createQuery("SELECT l.id FROM Liquidacion l WHERE l.estado = :e ORDER BY l.id", Long.class)
                .setParameter("e", EstadoLiquidacion.PENDIENTE)
                .getResultList();
    }

    public int eliminarDeComercio(Long idComercio) {
        return em.createQuery("DELETE FROM Liquidacion l WHERE l.idComercio = :c")
                .setParameter("c", idComercio)
                .executeUpdate();
    }
}
