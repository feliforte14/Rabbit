package com.logiRed.comercios.repository;

/**
 * CAPA REPOSITORY (Patrón DAO - Data Access Object)
 *
 * Esta carpeta contiene las clases que se comunican directamente con la base de datos.
 * Su única responsabilidad es persistir, buscar, actualizar y eliminar datos.
 * NO contiene lógica de negocio — eso le pertenece al Service.
 *
 * ComercioRepository usa JPA a través del EntityManager para operar sobre
 * las entidades Comercio y Sucursal sin escribir SQL manual.
 *
 * Al aislar el acceso a datos acá, si mañana cambiamos de H2 a PostgreSQL
 * o de JPA a otra tecnología, solo se toca esta capa.
 */

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

    // Persiste un comercio nuevo en la BD
    public Comercio guardar(Comercio comercio) {
        em.persist(comercio);
        return comercio;
    }

    // Actualiza un comercio existente en la BD
    public Comercio actualizar(Comercio comercio) {
        return em.merge(comercio);
    }

    // Busca un comercio por su ID — devuelve null si no existe
    public Comercio buscarPorId(Long id) {
        return em.find(Comercio.class, id);
    }

    // Elimina físicamente un comercio de la BD
    public void eliminar(Comercio comercio) {
        em.remove(em.contains(comercio) ? comercio : em.merge(comercio));
    }

    // Lista solo las sucursales activas de un comercio usando JPQL
    public List<Sucursal> listarSucursalesActivas(Long idComercio) {
        return em.createQuery(
                "SELECT s FROM Sucursal s WHERE s.comercio.id = :idComercio AND s.activa = true",
                Sucursal.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }
}
