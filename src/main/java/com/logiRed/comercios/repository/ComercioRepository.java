package com.logiRed.comercios.repository;

import com.logiRed.comercios.model.Comercio;
import com.logiRed.comercios.model.Sucursal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ComercioRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Comercio guardar(Comercio comercio) {
        em.persist(comercio);
        return comercio;
    }

    public Comercio actualizar(Comercio comercio) {
        return em.merge(comercio);
    }

    public Comercio buscarPorId(Long id) {
        return em.find(Comercio.class, id);
    }

    public void eliminar(Comercio comercio) {
        em.remove(em.contains(comercio) ? comercio : em.merge(comercio));
    }

    public List<Sucursal> listarSucursalesActivas(Long idComercio) {
        return em.createQuery(
                "SELECT s FROM Sucursal s WHERE s.comercio.id = :idComercio AND s.activa = true",
                Sucursal.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }
}
