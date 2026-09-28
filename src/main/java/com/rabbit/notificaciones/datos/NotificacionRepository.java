package com.rabbit.notificaciones.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.notificaciones.datos.model.Notificacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class NotificacionRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Notificacion guardar(Notificacion notificacion) {
        em.persist(notificacion);
        return notificacion;
    }

    // La fechaCambio más nueva ya avisada para ese pedido (null si ninguna).
    public LocalDateTime ultimaFechaCambio(Long idPedido) {
        return em.createQuery(
                        "SELECT MAX(n.fechaCambio) FROM Notificacion n WHERE n.idPedido = :idPedido", LocalDateTime.class)
                .setParameter("idPedido", idPedido)
                .getSingleResult();
    }

    public List<Notificacion> listarRecientes(int cantidad) {
        return em.createQuery("SELECT n FROM Notificacion n ORDER BY n.id DESC", Notificacion.class)
                .setMaxResults(cantidad)
                .getResultList();
    }
}
