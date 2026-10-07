package com.rabbit.infraestructura;

/**
 * Al desplegar, borra de la base la columna usuarios.passwordhash.
 *
 * La tabla "usuarios" guardaba un hash SHA-256 sin salt de cada contraseña
 * que nadie usaba para autenticar (lo hace el ApplicationRealm de WildFly).
 * Una credencial redundante y débil solo suma riesgo si la base se
 * filtra, así que la entidad dejó de mapearla. hbm2ddl "update" nunca
 * borra columnas: sin esto, los hashes viejos quedarían guardados para
 * siempre. Es idempotente (IF EXISTS): después del primer despliegue no
 * hace nada.
 *
 * Mismo esquema que AlineadorDeRestriccionesEnum: @Singleton @Startup con
 * transacción manejada a mano, porque corre una sola vez al arrancar.
 */

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class LimpiezaDeCredenciales {

    private static final Logger LOG = Logger.getLogger(LimpiezaDeCredenciales.class.getName());

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    @Resource
    private UserTransaction tx;

    @PostConstruct
    public void limpiar() {
        try {
            tx.begin();
            em.joinTransaction();
            em.createNativeQuery("ALTER TABLE IF EXISTS usuarios DROP COLUMN IF EXISTS passwordhash").executeUpdate();
            tx.commit();
            LOG.info("[Esquema] usuarios sin columna de contraseñas (las valida solo el realm)");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Esquema] No se pudo quitar usuarios.passwordhash", e);
            try {
                tx.rollback();
            } catch (Exception ignorada) {
                // La transacción ya estaba terminada: no queda nada que deshacer.
            }
        }
    }
}
