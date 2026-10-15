package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — liquidación mensual a comercios (ADR-034), EJB
 * @Stateless.
 *
 * Una vez por mes cerrado, a cada comercio activo se le cobra el servicio
 * de Rabbit: un abono fijo, más un cargo por pedido entregado y otro por
 * kilo entregado (ver TarifaMensual). Los pedidos y sus kilos los informa
 * Pedidos (ISeguimientoPedido.resumenEntregas); el cobro va por el banco
 * legado, igual que el de los pedidos PREPAGO.
 *
 * NOT_SUPPORTED: no abre una transacción propia. Cada comercio se crea y se
 * cobra en transacciones separadas (CobroDeLiquidaciones), así un banco
 * caído o un comercio con problemas no deshace a los demás.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.pagos.datos.LiquidacionRepository;
import com.rabbit.pagos.datos.model.EstadoLiquidacion;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pedidos.dto.ResumenEntregasDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LiquidacionService implements ILiquidaciones {

    private static final Logger LOG = Logger.getLogger(LiquidacionService.class.getName());

    @Inject
    private LiquidacionRepository repository;

    @Inject
    private CobroDeLiquidaciones cobros;

    @Inject
    private ISeguimientoPedido pedidos;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IContextoUsuario contextoUsuario;

    @Override
    @RolesAllowed("ADMINISTRADOR")
    public List<LiquidacionDTO> liquidarMes(YearMonth periodo) {
        if (periodo == null) {
            throw new ValidacionException("Elegí el mes a liquidar");
        }
        if (!periodo.isBefore(YearMonth.now())) {
            throw new ValidacionException("El mes " + periodo + " todavía no terminó: solo se puede ver la vista previa");
        }
        TarifaMensual tarifa = TarifaMensual.vigente();
        for (LiquidacionDTO calculada : calcular(periodo, tarifa)) {
            try {
                Long id = cobros.crear(calculada.idComercio, calculada.periodo, calculada.cantidadEnvios,
                        calculada.kilos, tarifa);
                if (id != null) {
                    cobros.cobrar(id);
                }
            } catch (EJBException | ValidacionException e) {
                LOG.log(Level.WARNING, "[Pagos] No se pudo liquidar " + periodo + " al comercio " + calculada.idComercio, e);
            }
        }
        return listarDelPeriodo(periodo);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<LiquidacionDTO> previsualizar(YearMonth periodo) {
        if (periodo == null) {
            throw new ValidacionException("Elegí el mes");
        }
        return calcular(periodo, TarifaMensual.vigente());
    }

    // Una por comercio activo, con lo que entregó en el mes (o nada: el
    // abono se cobra igual).
    private List<LiquidacionDTO> calcular(YearMonth periodo, TarifaMensual tarifa) {
        Map<Long, ResumenEntregasDTO> entregas = pedidos
                .resumenEntregas(periodo.atDay(1).atStartOfDay(), periodo.plusMonths(1).atDay(1).atStartOfDay())
                .stream().collect(Collectors.toMap(ResumenEntregasDTO::idComercio, Function.identity()));
        List<LiquidacionDTO> resultado = new ArrayList<>();
        for (ComercioDTO comercio : comercios.listarTodos()) {
            if (!comercio.isActivo()) {
                continue;
            }
            ResumenEntregasDTO resumen = entregas.get(comercio.getId());
            int cantidad = resumen != null ? resumen.cantidad() : 0;
            BigDecimal kilos = resumen != null ? resumen.kilos() : BigDecimal.ZERO;
            LiquidacionDTO dto = new LiquidacionDTO();
            dto.idComercio = comercio.getId();
            dto.periodo = periodo.toString();
            dto.cantidadEnvios = cantidad;
            dto.kilos = kilos;
            dto.abono = tarifa.abonoMensual();
            dto.cargoEnvios = tarifa.cargoEnvios(cantidad);
            dto.cargoKilos = tarifa.cargoKilos(kilos);
            dto.total = tarifa.total(cantidad, kilos);
            dto.estado = "VISTA_PREVIA";
            resultado.add(dto);
        }
        return resultado;
    }

    @Override
    @RolesAllowed("ADMINISTRADOR")
    public LiquidacionDTO reintentarCobro(Long idLiquidacion) {
        return LiquidacionDTO.desde(cobros.cobrar(idLiquidacion));
    }

    @Override
    @RolesAllowed("ADMINISTRADOR")
    public void reintentarPendientes() {
        for (Long id : repository.listarIdsPendientes()) {
            try {
                if (cobros.cobrar(id).getEstado() == EstadoLiquidacion.PENDIENTE) {
                    // El banco sigue sin responder: no tiene sentido seguir
                    // probando con las demás ahora.
                    return;
                }
            } catch (EJBException | ValidacionException e) {
                LOG.log(Level.WARNING, "[Pagos] No se pudo reintentar la liquidación " + id, e);
            }
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<LiquidacionDTO> listarDelPeriodo(YearMonth periodo) {
        return repository.listarDelPeriodo(periodo.toString()).stream()
                .map(LiquidacionDTO::desde).collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<String> listarPeriodos() {
        return repository.listarPeriodos();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed("COMERCIO")
    public List<LiquidacionDTO> listarDelComercioActual() {
        return repository.listarDeComercio(contextoUsuario.idComercioActual()).stream()
                .map(LiquidacionDTO::desde).collect(Collectors.toList());
    }

    // Sin rol: la tarifa la ve el personal y cada comercio. Este WildFly
    // rechaza todo método sin permiso declarado.
    @Override
    @PermitAll
    public TarifaMensual tarifaVigente() {
        return TarifaMensual.vigente();
    }
}
