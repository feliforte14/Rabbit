package com.rabbit.ruteo.datos;

/** DAO del componente Ruteo: las zonas de reparto. */

import com.rabbit.ruteo.datos.model.Zona;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ZonaRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Zona guardar(Zona zona) {
        em.persist(zona);
        return zona;
    }

    public Zona actualizar(Zona zona) {
        return em.merge(zona);
    }

    public Zona buscarPorId(Long id) {
        return em.find(Zona.class, id);
    }

    public List<Zona> listarTodas() {
        return em.createQuery("SELECT z FROM Zona z ORDER BY z.codigoPostalDesde", Zona.class).getResultList();
    }

    // Zonas activas cuyo rango se superpone con [desde, hasta]; excluye una
    // (la que se está reactivando) si se indica.
    public List<Zona> listarSuperpuestas(int desde, int hasta, Long excluida) {
        return em.createQuery("SELECT z FROM Zona z WHERE z.activa = true AND z.codigoPostalDesde <= :hasta "
                        + "AND z.codigoPostalHasta >= :desde AND z.id <> :excluida", Zona.class)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .setParameter("excluida", excluida != null ? excluida : -1L)
                .getResultList();
    }
}
