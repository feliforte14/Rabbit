package com.rabbit.notificaciones.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.notificaciones.datos.model.AvisoDestinatario;
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

    public List<Notificacion> listarDeComercio(Long idComercio, int cantidad) {
        return em.createQuery("SELECT n FROM Notificacion n WHERE n.idComercio = :idComercio ORDER BY n.id DESC",
                        Notificacion.class)
                .setParameter("idComercio", idComercio)
                .setMaxResults(cantidad)
                .getResultList();
    }

    // Borra los avisos de un comercio que se elimina (ver ComercioEliminado),
    // también los que recibieron sus clientes (ADR-040).
    public int eliminarNotificacionesDeComercio(Long idComercio) {
        int aDestinatarios = em.createQuery("DELETE FROM AvisoDestinatario a WHERE a.idComercio = :id")
                .setParameter("id", idComercio).executeUpdate();
        return aDestinatarios + em.createQuery("DELETE FROM Notificacion n WHERE n.idComercio = :id")
                .setParameter("id", idComercio).executeUpdate();
    }

    // --- Avisos a quien recibe el pedido (ADR-040) ---

    public AvisoDestinatario guardarAvisoDestinatario(AvisoDestinatario aviso) {
        em.persist(aviso);
        return aviso;
    }

    // La fechaCambio más nueva ya avisada al destinatario de ese pedido (null si ninguna).
    public LocalDateTime ultimaFechaCambioAvisadaAlDestinatario(Long idPedido) {
        return em.createQuery(
                        "SELECT MAX(a.fechaCambio) FROM AvisoDestinatario a WHERE a.idPedido = :idPedido", LocalDateTime.class)
                .setParameter("idPedido", idPedido)
                .getSingleResult();
    }

    public List<AvisoDestinatario> listarAvisosDestinatarioRecientes(int cantidad) {
        return em.createQuery("SELECT a FROM AvisoDestinatario a ORDER BY a.id DESC", AvisoDestinatario.class)
                .setMaxResults(cantidad)
                .getResultList();
    }
}
