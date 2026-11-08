package com.rabbit.pedidos.datos;

/**
 * CAPA DE DATOS — bandeja de salida de los cambios de estado (ver
 * EventoEstadoPedido). Única clase que toca el EntityManager para esa tabla.
 */

import com.rabbit.pedidos.datos.model.EventoEstadoPedido;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EventoEstadoRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public void guardar(EventoEstadoPedido evento) {
        em.persist(evento);
    }

    /** Los pendientes más viejos primero, para publicarlos en el orden en que pasaron. */
    public List<EventoEstadoPedido> listarPendientes(int maximo) {
        return em.createQuery(
                        "SELECT e FROM EventoEstadoPedido e WHERE e.publicadoEn IS NULL ORDER BY e.id",
                        EventoEstadoPedido.class)
                .setMaxResults(maximo)
                .getResultList();
    }

    public long contarPendientes() {
        return em.createQuery(
                        "SELECT COUNT(e) FROM EventoEstadoPedido e WHERE e.publicadoEn IS NULL", Long.class)
                .getSingleResult();
    }

    public void marcarPublicado(Long id, LocalDateTime cuando) {
        em.createQuery("UPDATE EventoEstadoPedido e SET e.publicadoEn = :cuando WHERE e.id = :id")
                .setParameter("cuando", cuando)
                .setParameter("id", id)
                .executeUpdate();
    }

    public void sumarIntentoFallido(Long id) {
        em.createQuery("UPDATE EventoEstadoPedido e SET e.intentosFallidos = e.intentosFallidos + 1 WHERE e.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    /** Borra los ya publicados antes de la fecha (la bandeja no crece sin límite). */
    public int borrarPublicadosAnterioresA(LocalDateTime limite) {
        return em.createQuery("DELETE FROM EventoEstadoPedido e WHERE e.publicadoEn IS NOT NULL AND e.publicadoEn < :limite")
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
