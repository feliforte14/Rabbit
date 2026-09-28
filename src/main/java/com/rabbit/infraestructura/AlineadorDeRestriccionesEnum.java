package com.rabbit.infraestructura;

/**
 * INFRAESTRUCTURA — mantiene las restricciones CHECK de las columnas enum
 * alineadas con los valores actuales de cada enum.
 *
 * EL PROBLEMA
 * Hibernate crea cada columna @Enumerated(EnumType.STRING) con un CHECK que
 * lista los valores que el enum tenía EN ESE MOMENTO, por ejemplo
 *   estado varchar(255) check (estado in ('PENDIENTE','CONFIRMADO','CANCELADO'))
 * Con hibernate.hbm2ddl.auto=update, Hibernate agrega tablas y columnas
 * nuevas pero NUNCA actualiza ese CHECK. Resultado: cuando se sumaron
 * EN_CAMINO y ENTREGADO a EstadoPedido, la base rechazaba despachar un
 * pedido con "violates check constraint pedidos_estado_check". Hibernate 7
 * no tiene ninguna opción para no generar el CHECK (ni columnDefinition,
 * ni un AttributeConverter, ni @JdbcTypeCode lo evitan).
 *
 * LA SOLUCIÓN
 * Al desplegar (@Startup), este Singleton reemplaza el CHECK de cada
 * columna enum por uno con los valores actuales del enum. Así la base
 * sigue validando los valores (no se pierde integridad) y agregar un
 * valor a un enum no vuelve a romper nada. Corre después de que Hibernate
 * ya creó/actualizó el esquema: el contenedor inicia la unidad de
 * persistencia antes que los EJB que la usan.
 *
 * Cada columna va en su propia transacción (BMT, UserTransaction): si una
 * falla —por ejemplo porque hay filas con un valor que ya no existe en el
 * enum—, se loguea y las demás se alinean igual. Nunca impide el deploy.
 *
 * Al sumar una columna enum nueva a una entidad, agregarla en COLUMNAS.
 * En un proyecto real esto lo resolverían migraciones versionadas
 * (Flyway/Liquibase) en vez de hbm2ddl=update — ver docs/DECISIONES.md.
 */

import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.pagos.datos.model.EstadoCobro;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.repartidores.datos.model.EstadoRepartidor;
import com.rabbit.seguridad.datos.model.Rol;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class AlineadorDeRestriccionesEnum {

    private static final Logger LOG = Logger.getLogger(AlineadorDeRestriccionesEnum.class.getName());

    // Tabla y columna tal como quedan en PostgreSQL (sin comillas, en
    // minúsculas: medioPago -> mediopago) y el enum que las respalda.
    private record ColumnaEnum(String tabla, String columna, Class<? extends Enum<?>> tipo) {}

    private static final List<ColumnaEnum> COLUMNAS = List.of(
            new ColumnaEnum("pedidos", "estado", EstadoPedido.class),
            new ColumnaEnum("pedidos", "origen", OrigenPedido.class),
            new ColumnaEnum("pedidos", "mediopago", MedioPago.class),
            new ColumnaEnum("pedidos_externos", "origen", OrigenPedido.class),
            new ColumnaEnum("pedidos_externos", "mediopago", MedioPago.class),
            new ColumnaEnum("reservas_stock", "estado", EstadoReserva.class),
            new ColumnaEnum("usuarios", "rol", Rol.class),
            new ColumnaEnum("cobros", "estado", EstadoCobro.class),
            new ColumnaEnum("cobros", "mediopago", MedioPago.class),
            new ColumnaEnum("repartidores", "estado", EstadoRepartidor.class));

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    @Resource
    private UserTransaction tx;

    @PostConstruct
    public void alinear() {
        long ok = COLUMNAS.stream().filter(this::alinear).count();
        LOG.info("[Esquema] Restricciones CHECK de columnas enum alineadas: " + ok + "/" + COLUMNAS.size());
    }

    private boolean alinear(ColumnaEnum c) {
        // Mismo nombre que le pone PostgreSQL al CHECK que genera Hibernate.
        String restriccion = c.tabla() + "_" + c.columna() + "_check";
        String valores = Arrays.stream(c.tipo().getEnumConstants())
                .map(v -> "'" + v.name() + "'")
                .collect(Collectors.joining(","));
        try {
            tx.begin();
            em.joinTransaction();
            em.createNativeQuery("ALTER TABLE " + c.tabla() + " DROP CONSTRAINT IF EXISTS " + restriccion)
                    .executeUpdate();
            em.createNativeQuery("ALTER TABLE " + c.tabla() + " ADD CONSTRAINT " + restriccion
                    + " CHECK (" + c.columna() + " IN (" + valores + "))")
                    .executeUpdate();
            tx.commit();
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Esquema] No se pudo alinear " + restriccion + " con " + c.tipo().getSimpleName(), e);
            try {
                tx.rollback();
            } catch (Exception ignorada) {
                // La transacción ya estaba terminada: no queda nada que deshacer.
            }
            return false;
        }
    }
}
