package com.rabbit.comercios.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 *
 * ALCANCE ACTUAL: la entidad Producto (ver Sección 1.7 del documento
 * técnico) queda modelada y persistible, pensada para cuando
 * ServicioDeIntegracionERP importe el catálogo real de cada comercio.
 * Por ahora no tiene una pantalla propia — es la base de datos sobre la
 * que se construiría esa importación, no una funcionalidad entregada en
 * esta instancia.
 */

import com.rabbit.comercios.datos.model.Producto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ProductoRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Producto guardar(Producto producto) {
        em.persist(producto);
        return producto;
    }

    public Producto actualizar(Producto producto) {
        return em.merge(producto);
    }

    public Producto buscarPorId(Long id) {
        return em.find(Producto.class, id);
    }

    public List<Producto> listarPorComercio(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM Producto p WHERE p.comercio.id = :idComercio ORDER BY p.nombre",
                Producto.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    public Producto buscarPorCodigoExternoERP(Long idComercio, String codigoExternoERP) {
        return em.createQuery(
                "SELECT p FROM Producto p WHERE p.comercio.id = :idComercio AND p.codigoExternoERP = :codigo",
                Producto.class)
                .setParameter("idComercio", idComercio)
                .setParameter("codigo", codigoExternoERP)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
