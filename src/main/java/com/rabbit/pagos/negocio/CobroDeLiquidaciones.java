package com.rabbit.pagos.negocio;

/**
 * Las dos escrituras de la liquidación mensual (ADR-034), cada una en su
 * propia transacción (REQUIRES_NEW), para que LiquidacionService las llame
 * comercio por comercio: si falla uno, los demás quedan hechos.
 *
 *   1. crear: guarda la liquidación de un comercio, PENDIENTE.
 *   2. cobrar: se la pide al banco (SOAP, con el circuit breaker) y guarda
 *      el resultado.
 *
 * Van separadas a propósito: el banco es un sistema externo y su llamada
 * no se deshace con un rollback. Si la liquidación se guardara en la misma
 * transacción que el cobro y algo fallara después de que el banco aprobó,
 * quedaría cobrada sin registro. Así, la liquidación ya existe antes de
 * llamar al banco.
 */

import com.rabbit.integracion.banco.IBancoClient;
import com.rabbit.integracion.banco.ResultadoAutorizacion;
import com.rabbit.pagos.datos.LiquidacionRepository;
import com.rabbit.pagos.datos.model.EstadoLiquidacion;
import com.rabbit.pagos.datos.model.Liquidacion;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Stateless
@RolesAllowed("ADMINISTRADOR")
public class CobroDeLiquidaciones {

    private static final Logger LOG = Logger.getLogger(CobroDeLiquidaciones.class.getName());

    @Inject
    private LiquidacionRepository repository;

    @Inject
    private IBancoClient banco;

    /** @return el ID de la liquidación nueva, o null si ese comercio ya tenía la de ese mes */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long crear(Long idComercio, String periodo, int cantidadEnvios, BigDecimal kilos, TarifaMensual tarifa,
                      long unidadesDiaPequeno, long unidadesDiaMediano, long unidadesDiaGrande) {
        if (repository.existe(idComercio, periodo)) {
            return null;
        }
        Liquidacion l = new Liquidacion();
        l.setIdComercio(idComercio);
        l.setPeriodo(periodo);
        l.setCantidadEnvios(cantidadEnvios);
        l.setKilos(kilos);
        l.setAbono(tarifa.abonoMensual());
        l.setCargoEnvios(tarifa.cargoEnvios(cantidadEnvios));
        l.setCargoKilos(tarifa.cargoKilos(kilos));
        l.setUnidadesDia(unidadesDiaPequeno, unidadesDiaMediano, unidadesDiaGrande);
        l.setCargoAlmacenamiento(tarifa.cargoAlmacenamiento(unidadesDiaPequeno, unidadesDiaMediano, unidadesDiaGrande));
        l.setTotal(tarifa.total(cantidadEnvios, kilos, unidadesDiaPequeno, unidadesDiaMediano, unidadesDiaGrande));
        l.setEstado(EstadoLiquidacion.PENDIENTE);
        l.setMotivo("Todavía no se pidió el cobro al banco");
        l.setFechaGeneracion(LocalDateTime.now());
        repository.guardar(l);
        return l.getId();
    }

    /** Pide el cobro al banco si la liquidación no está cobrada. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Liquidacion cobrar(Long idLiquidacion) {
        Liquidacion l = repository.buscarParaActualizar(idLiquidacion);
        if (l == null) {
            throw new ValidacionException("Liquidación no encontrada: " + idLiquidacion);
        }
        if (l.getEstado() == EstadoLiquidacion.COBRADA) {
            return l;
        }
        l.setIntentos(l.getIntentos() + 1);
        ResultadoAutorizacion resultado = banco.autorizarReferencia("LIQ-" + l.getId(), l.getTotal());
        switch (resultado.getEstado()) {
            case APROBADO:
                l.setEstado(EstadoLiquidacion.COBRADA);
                l.setCodigoAutorizacion(resultado.getCodigoAutorizacion());
                l.setFechaCobro(LocalDateTime.now());
                l.setMotivo(null);
                break;
            case RECHAZADO:
                l.setEstado(EstadoLiquidacion.RECHAZADA);
                l.setMotivo("El banco rechazó el cobro: " + resultado.getMotivo());
                break;
            default:
                // Queda PENDIENTE: el timer diario la vuelve a intentar.
                l.setMotivo("El banco no respondió: se reintenta solo");
                break;
        }
        LOG.info("[Pagos] Liquidación " + l.getId() + " (comercio " + l.getIdComercio() + ", " + l.getPeriodo()
                + ", $" + l.getTotal() + "): " + l.getEstado());
        return l;
    }
}
