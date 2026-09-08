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
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.ReservaStock;
import com.rabbit.inventario.dto.FiltroHistorialDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    /**
     * Fuerza el envio a la base de lo que este pendiente en el contexto de
     * persistencia, sin esperar al commit.
     *
     * Lo necesita reservarStock para el bloqueo optimista: si el UPDATE
     * viaja recien al cerrar la transaccion, la OptimisticLockException
     * salta DESPUES del metodo, donde ya no se puede traducir a un mensaje
     * entendible — el usuario veria un RollbackException crudo. Con el
     * flush explicito la excepcion ocurre dentro del metodo y se puede
     * atrapar.
     */
    public void sincronizar() {
        em.flush();
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
     * TODO el stock consignado por un comercio, en todos los depósitos de
     * Rabbit. Es la vista por defecto al operar en nombre de un comercio:
     * "qué mercadería mía hay guardada, y dónde".
     *
     * @param idComercio comercio dueño de la mercadería consignada
     * @return sus ítems, ordenados por depósito
     */
    public List<ItemInventario> listarItemsPorComercio(Long idComercio) {
        return em.createQuery(
                "SELECT i FROM ItemInventario i WHERE i.idComercio = :idComercio "
                        + "ORDER BY i.deposito.id, i.id",
                ItemInventario.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Ítems de un comercio puntual dentro de un depósito puntual. Misma
     * idea que arriba, pero acotado a un depósito — el filtro opcional de
     * la pantalla de reserva.
     *
     * @param idComercio comercio dueño de la mercadería consignada
     * @param idDeposito depósito de Rabbit donde está guardada
     * @return los ítems de ese comercio en ese depósito
     */
    public List<ItemInventario> listarItemsPorComercioYDeposito(Long idComercio, Long idDeposito) {
        return em.createQuery(
                "SELECT i FROM ItemInventario i "
                        + "WHERE i.idComercio = :idComercio AND i.deposito.id = :idDeposito ORDER BY i.id",
                ItemInventario.class)
                .setParameter("idComercio", idComercio)
                .setParameter("idDeposito", idDeposito)
                .getResultList();
    }

    /**
     * Indica si ese comercio ya tiene cargado ese producto en ese depósito
     * (evita cargar el mismo producto dos veces como filas separadas).
     *
     * El comercio entra en la comparación a propósito: "Coca-Cola de
     * Kiosco El Sol" y "Coca-Cola de Pepe El Pollo" en el mismo depósito
     * son dos consignaciones distintas y legítimas, y deben poder convivir
     * como dos filas.
     */
    public boolean existeProductoEnDeposito(String producto, Long idDeposito, Long idComercio, Long idExcluir) {
        String jpql = "SELECT COUNT(i) FROM ItemInventario i "
                + "WHERE i.producto = :producto AND i.deposito.id = :idDeposito AND i.idComercio = :idComercio"
                + (idExcluir != null ? " AND i.id <> :idExcluir" : "");
        var query = em.createQuery(jpql, Long.class)
                .setParameter("producto", producto)
                .setParameter("idDeposito", idDeposito)
                .setParameter("idComercio", idComercio);
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

    // --- Reservas de stock (IReservaStock) ---

    public ReservaStock guardarReserva(ReservaStock reserva) {
        em.persist(reserva);
        return reserva;
    }

    public ReservaStock actualizarReserva(ReservaStock reserva) {
        return em.merge(reserva);
    }

    public ReservaStock buscarReservaPorId(Long id) {
        return em.find(ReservaStock.class, id);
    }

    /**
     * Reservas de un item que siguen marcadas VIGENTE, sin importar si ya
     * vencieron. Sirve para auditar cuanto stock hay comprometido.
     *
     * @param idItem ID del item de inventario
     * @return reservas en estado VIGENTE de ese item
     */
    public List<ReservaStock> listarReservasVigentesDeItem(Long idItem) {
        return em.createQuery(
                "SELECT r FROM ReservaStock r WHERE r.item.id = :idItem AND r.estado = :estado "
                        + "ORDER BY r.fechaCreacion",
                ReservaStock.class)
                .setParameter("idItem", idItem)
                .setParameter("estado", EstadoReserva.VIGENTE)
                .getResultList();
    }

    /**
     * HISTORIAL: todas las reservas que cumplen los criterios recibidos,
     * de la mas reciente a la mas vieja.
     *
     * Cada fila de reservas_stock queda para siempre con su estado final
     * (CONFIRMADA / LIBERADA / EXPIRADA / DEVUELTA), asi que la tabla YA
     * es el registro historico — esta consulta solo lo hace consultable.
     *
     * El JPQL se arma sumando unicamente las condiciones que vengan
     * completas en el filtro, en vez de tener una consulta distinta por
     * cada combinacion. Los valores se pasan siempre como parametros
     * nombrados: nunca se concatena entrada del usuario dentro del JPQL.
     *
     * @param filtro criterios opcionales; ninguno es obligatorio
     * @return las reservas que matchean, mas recientes primero
     */
    public List<ReservaStock> listarHistorial(FiltroHistorialDTO filtro) {
        StringBuilder jpql = new StringBuilder("SELECT r FROM ReservaStock r WHERE 1 = 1");

        if (filtro.idComercio != null) {
            jpql.append(" AND r.idComercio = :idComercio");
        }
        if (filtro.idDeposito != null) {
            jpql.append(" AND r.item.deposito.id = :idDeposito");
        }
        if (filtro.estado != null && !filtro.estado.isBlank()) {
            jpql.append(" AND r.estado = :estado");
        }
        if (filtro.producto != null && !filtro.producto.isBlank()) {
            jpql.append(" AND LOWER(r.producto) LIKE :producto");
        }
        if (filtro.desde != null) {
            jpql.append(" AND r.fechaCreacion >= :desde");
        }
        if (filtro.hasta != null) {
            jpql.append(" AND r.fechaCreacion <= :hasta");
        }
        jpql.append(" ORDER BY r.fechaCreacion DESC, r.id DESC");

        var query = em.createQuery(jpql.toString(), ReservaStock.class);

        if (filtro.idComercio != null) {
            query.setParameter("idComercio", filtro.idComercio);
        }
        if (filtro.idDeposito != null) {
            query.setParameter("idDeposito", filtro.idDeposito);
        }
        if (filtro.estado != null && !filtro.estado.isBlank()) {
            query.setParameter("estado", EstadoReserva.valueOf(filtro.estado));
        }
        if (filtro.producto != null && !filtro.producto.isBlank()) {
            query.setParameter("producto", "%" + filtro.producto.trim().toLowerCase() + "%");
        }
        if (filtro.desde != null) {
            query.setParameter("desde", filtro.desde.atStartOfDay());
        }
        if (filtro.hasta != null) {
            // Hasta el final del dia elegido, si no un filtro "hasta hoy"
            // dejaria afuera todo lo de hoy.
            query.setParameter("hasta", filtro.hasta.atTime(LocalTime.MAX));
        }
        return query.getResultList();
    }

    /**
     * Reservas que siguen marcadas VIGENTE pero cuyo plazo ya paso. Es la
     * consulta que usa el barredor automatico (BarredorDeReservas) para
     * liberar el stock que quedo comprometido por reservas abandonadas.
     *
     * El filtro por fecha se hace en la consulta y no en memoria para no
     * traer toda la tabla en cada pasada del barredor.
     *
     * @param ahora instante contra el que se compara el vencimiento
     * @return reservas vencidas pendientes de liberar
     */
    public List<ReservaStock> listarReservasVencidas(LocalDateTime ahora) {
        return em.createQuery(
                "SELECT r FROM ReservaStock r WHERE r.estado = :estado AND r.fechaExpiracion < :ahora",
                ReservaStock.class)
                .setParameter("estado", EstadoReserva.VIGENTE)
                .setParameter("ahora", ahora)
                .getResultList();
    }
}
