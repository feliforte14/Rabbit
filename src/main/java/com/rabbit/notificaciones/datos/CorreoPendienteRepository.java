package com.rabbit.notificaciones.datos;

/**
 * CAPA DE DATOS — bandeja de salida de los mails de aviso (ver CorreoPendiente). Única clase que toca el
 * EntityManager para esa tabla.
 */

import com.rabbit.notificaciones.datos.model.CorreoPendiente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class CorreoPendienteRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public void guardar(CorreoPendiente correo) {
        em.persist(correo);
    }

    /** Los pendientes más viejos primero, para mandarlos en el orden en que pasaron. */
    public List<CorreoPendiente> listarPendientes(int maximo) {
        return em.createQuery("SELECT c FROM CorreoPendiente c WHERE c.estado = :estado ORDER BY c.id",
                        CorreoPendiente.class)
                .setParameter("estado", CorreoPendiente.PENDIENTE)
                .setMaxResults(maximo)
                .getResultList();
    }

    public void marcarEnviado(Long id, LocalDateTime cuando) {
        em.createQuery("UPDATE CorreoPendiente c SET c.estado = :estado, c.resueltoEn = :cuando WHERE c.id = :id")
                .setParameter("estado", CorreoPendiente.ENVIADO)
                .setParameter("cuando", cuando)
                .setParameter("id", id)
                .executeUpdate();
    }

    public void sumarIntentoFallido(Long id) {
        em.createQuery("UPDATE CorreoPendiente c SET c.intentosFallidos = c.intentosFallidos + 1 WHERE c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    public void descartar(Long id, LocalDateTime cuando, String motivo) {
        em.createQuery("UPDATE CorreoPendiente c SET c.estado = :estado, c.resueltoEn = :cuando, c.motivo = :motivo "
                        + "WHERE c.id = :id")
                .setParameter("estado", CorreoPendiente.DESCARTADO)
                .setParameter("cuando", cuando)
                .setParameter("motivo", motivo)
                .setParameter("id", id)
                .executeUpdate();
    }

    /** Descarta los pendientes creados antes del límite: un aviso que llega tarde confunde más de lo que sirve. */
    public int descartarPendientesCreadosAntesDe(LocalDateTime limite, LocalDateTime cuando, String motivo) {
        return em.createQuery("UPDATE CorreoPendiente c SET c.estado = :nuevo, c.resueltoEn = :cuando, c.motivo = :motivo "
                        + "WHERE c.estado = :pendiente AND c.creadoEn < :limite")
                .setParameter("nuevo", CorreoPendiente.DESCARTADO)
                .setParameter("cuando", cuando)
                .setParameter("motivo", motivo)
                .setParameter("pendiente", CorreoPendiente.PENDIENTE)
                .setParameter("limite", limite)
                .executeUpdate();
    }

    /** Borra los ya resueltos en ese estado antes del límite (la tabla no crece sin límite ni guarda datos de más). */
    public int borrarResueltosAntesDe(String estado, LocalDateTime limite) {
        return em.createQuery("DELETE FROM CorreoPendiente c WHERE c.estado = :estado AND c.resueltoEn < :limite")
                .setParameter("estado", estado)
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
