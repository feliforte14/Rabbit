package com.rabbit.inventario.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 *
 * Usa la misma unidad de persistencia "comerciosPU" que ComercioRepository:
 * hay un solo datasource/PU para toda la app (ver persistence.xml), el
 * nombre quedó de cuando existía un único componente.
 */

import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.ItemInventario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class InventarioRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Deposito guardarDeposito(Deposito deposito) {
        em.persist(deposito);
        return deposito;
    }

    public Deposito buscarDepositoPorId(Long id) {
        return em.find(Deposito.class, id);
    }

    public List<Deposito> listarDepositos() {
        return em.createQuery("SELECT d FROM Deposito d", Deposito.class).getResultList();
    }

    public ItemInventario guardarItem(ItemInventario item) {
        em.persist(item);
        return item;
    }

    public ItemInventario actualizarItem(ItemInventario item) {
        return em.merge(item);
    }

    public ItemInventario buscarItemPorId(Long id) {
        return em.find(ItemInventario.class, id);
    }

    public List<ItemInventario> listarItemsPorDeposito(Long idDeposito) {
        return em.createQuery(
                "SELECT i FROM ItemInventario i WHERE i.deposito.id = :idDeposito ORDER BY i.id",
                ItemInventario.class)
                .setParameter("idDeposito", idDeposito)
                .getResultList();
    }

    /**
     * Indica si ya existe otro ítem con ese producto en el mismo depósito
     * (evita cargar el mismo producto dos veces como filas separadas).
     */
    public boolean existeProductoEnDeposito(String producto, Long idDeposito, Long idExcluir) {
        String jpql = "SELECT COUNT(i) FROM ItemInventario i WHERE i.producto = :producto AND i.deposito.id = :idDeposito"
                + (idExcluir != null ? " AND i.id <> :idExcluir" : "");
        var query = em.createQuery(jpql, Long.class)
                .setParameter("producto", producto)
                .setParameter("idDeposito", idDeposito);
        if (idExcluir != null) {
            query.setParameter("idExcluir", idExcluir);
        }
        return query.getSingleResult() > 0;
    }

    /**
     * Depósitos que tienen stock libre (disponible - reservada > 0) de un
     * producto puntual — usado por IConsultaStock.listarDepositosConStock.
     */
    public List<Deposito> listarDepositosConStock(String producto) {
        return em.createQuery(
                "SELECT DISTINCT i.deposito FROM ItemInventario i "
                        + "WHERE i.producto = :producto AND (i.cantidadDisponible - i.cantidadReservada) > 0",
                Deposito.class)
                .setParameter("producto", producto)
                .getResultList();
    }
}
