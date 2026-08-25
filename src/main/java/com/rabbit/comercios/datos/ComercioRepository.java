package com.rabbit.comercios.datos;

/**
 * CAPA DE DATOS (Patrón DAO - Data Access Object, sobre JPA)
 *
 * Esta carpeta contiene las clases que se comunican directamente con la base de datos.
 * Su única responsabilidad es persistir, buscar, actualizar y eliminar datos.
 * NO contiene lógica de negocio — eso le pertenece a la capa de Negocio.
 *
 * ComercioRepository usa JPA a través del EntityManager para operar sobre
 * las entidades Comercio y Sucursal sin escribir SQL manual.
 *
 * Al aislar el acceso a datos acá, si mañana cambiamos de H2 a PostgreSQL
 * o de JPA a otra tecnología, solo se toca esta capa.
 */

import com.rabbit.comercios.datos.model.Comercio;
import com.rabbit.comercios.datos.model.Sucursal;
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

    // Lista todos los comercios registrados — usada por la vista de listado
    public List<Comercio> listarTodos() {
        return em.createQuery("SELECT c FROM Comercio c", Comercio.class).getResultList();
    }

    // Indica si ya existe otro comercio con ese CUIT (excluyendo idAExcluir, útil al actualizar)
    public boolean existeCuit(String cuit, Long idAExcluir) {
        String jpql = "SELECT COUNT(c) FROM Comercio c WHERE c.cuit = :cuit"
                + (idAExcluir != null ? " AND c.id <> :idAExcluir" : "");
        var query = em.createQuery(jpql, Long.class).setParameter("cuit", cuit);
        if (idAExcluir != null) {
            query.setParameter("idAExcluir", idAExcluir);
        }
        return query.getSingleResult() > 0;
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
