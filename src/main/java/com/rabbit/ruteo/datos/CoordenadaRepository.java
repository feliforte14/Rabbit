package com.rabbit.ruteo.datos;

/** DAO del componente Ruteo: la caché de coordenadas de direcciones (ADR-046). */

import com.rabbit.ruteo.datos.model.CoordenadaDireccion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class CoordenadaRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    /** Las que ya se consultaron, encontradas o no. */
    public List<CoordenadaDireccion> buscarPorClaves(Collection<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT c FROM CoordenadaDireccion c WHERE c.clave IN :claves", CoordenadaDireccion.class)
                .setParameter("claves", claves)
                .getResultList();
    }

    /** Guarda la consulta, o la actualiza si ya existía (un reintento de una no encontrada). */
    public void guardar(CoordenadaDireccion nueva) {
        List<CoordenadaDireccion> existentes = buscarPorClaves(List.of(nueva.getClave()));
        if (existentes.isEmpty()) {
            em.persist(nueva);
            return;
        }
        CoordenadaDireccion existente = existentes.get(0);
        existente.setEncontrada(nueva.isEncontrada());
        existente.setLatitud(nueva.getLatitud());
        existente.setLongitud(nueva.getLongitud());
        existente.setFechaConsulta(nueva.getFechaConsulta());
    }
}
