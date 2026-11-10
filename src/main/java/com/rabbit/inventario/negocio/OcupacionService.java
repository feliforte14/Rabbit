package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — ocupación diaria del depósito (EJB @Stateless, componente Inventario, ADR-059).
 *
 * Una foto por día de cuántas unidades de cada categoría de tamaño guarda cada comercio (disponibles y
 * reservadas), y la suma de unidades-día de un mes. La foto la saca FotoDiariaDeOcupacion a las 23:55.
 *
 * "Un día tiene foto para un comercio" cuando ese comercio tiene alguna fila ese día. Por eso la foto escribe
 * las TRES categorías (con cero si no hay) para todo comercio que tiene stock o que lo tenía en la foto
 * anterior: así un día en que el stock bajó a cero se distingue de un día sin foto, que se completa con lo último
 * que se sabía (ver UnidadesDia).
 */

import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.OcupacionDiaria;
import com.rabbit.inventario.dto.OcupacionMensualDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@PermitAll
public class OcupacionService implements IOcupacionDeDeposito {

    private static final Logger LOG = Logger.getLogger(OcupacionService.class.getName());

    private static final CategoriaDeTamano[] COBRABLES = {
            CategoriaDeTamano.PEQUENO, CategoriaDeTamano.MEDIANO, CategoriaDeTamano.GRANDE};

    @Inject
    private InventarioRepository repository;

    @Inject
    private IContextoUsuario contextoUsuario;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void tomarFoto(LocalDate dia) {
        if (dia == null || dia.isAfter(LocalDate.now())) {
            throw new ValidacionException("No se puede sacar la foto de un día que todavía no llegó");
        }
        List<ItemInventario> items = repository.listarTodosLosItems();
        // Los productos de antes de que se llevara la antigüedad empiezan a contar desde acá: nadie recibe un
        // recargo retroactivo por lo que ya estaba guardado.
        for (ItemInventario item : items) {
            if (item.getIdComercio() != null && item.getFechaUltimoMovimiento() == null) {
                item.setFechaUltimoMovimiento(dia);
            }
        }
        Map<Long, Map<CategoriaDeTamano, Long>> actual = unidadesActuales(items, dia, Clase.TOTAL);
        Map<Long, Map<CategoriaDeTamano, Long>> gratis = unidadesActuales(items, dia, Clase.GRATIS);
        Map<Long, Map<CategoriaDeTamano, Long>> conRecargo = unidadesActuales(items, dia, Clase.CON_RECARGO);

        // Los comercios que tenían stock en la foto anterior siguen en la foto aunque ahora estén en cero.
        Set<Long> comercios = new HashSet<>(actual.keySet());
        for (OcupacionDiaria previa : repository.listarUltimaOcupacionAntesDe(dia, null)) {
            comercios.add(previa.getIdComercio());
        }
        repository.borrarOcupacionDelDia(dia);
        for (Long idComercio : comercios) {
            for (CategoriaDeTamano categoria : COBRABLES) {
                repository.guardarOcupacion(new OcupacionDiaria(idComercio, dia, categoria,
                        actual.getOrDefault(idComercio, Map.of()).getOrDefault(categoria, 0L),
                        gratis.getOrDefault(idComercio, Map.of()).getOrDefault(categoria, 0L),
                        conRecargo.getOrDefault(idComercio, Map.of()).getOrDefault(categoria, 0L)));
            }
        }
        LOG.info("[Inventario] Foto de ocupación del " + dia + ": " + comercios.size() + " comercio(s)");
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Map<Long, OcupacionMensualDTO> ocupacionDelMes(YearMonth mes) {
        List<ItemInventario> items = repository.listarTodosLosItems();
        LocalDate hoy = LocalDate.now();
        Map<Long, Map<CategoriaDeTamano, Long>> enVivo = unidadesActuales(items, hoy, Clase.TOTAL);
        Map<Long, Map<CategoriaDeTamano, Long>> enVivoGratis = unidadesActuales(items, hoy, Clase.GRATIS);
        Map<Long, Map<CategoriaDeTamano, Long>> enVivoRecargo = unidadesActuales(items, hoy, Clase.CON_RECARGO);
        Map<Long, Integer> sinMedidas = productosSinMedidas(items);

        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        List<OcupacionDiaria> filas = repository.listarOcupacion(desde, hasta, null);
        List<OcupacionDiaria> anteriores = repository.listarUltimaOcupacionAntesDe(desde, null);
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> fotos = agruparPorComercio(filas, OcupacionDiaria::getUnidades);
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> fotosGratis = agruparPorComercio(filas, OcupacionDiaria::getUnidadesGratis);
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> fotosRecargo = agruparPorComercio(filas, OcupacionDiaria::getUnidadesConRecargo);
        Map<Long, Map<CategoriaDeTamano, Long>> previas = previasPorComercio(anteriores, OcupacionDiaria::getUnidades);
        Map<Long, Map<CategoriaDeTamano, Long>> previasGratis = previasPorComercio(anteriores, OcupacionDiaria::getUnidadesGratis);
        Map<Long, Map<CategoriaDeTamano, Long>> previasRecargo = previasPorComercio(anteriores, OcupacionDiaria::getUnidadesConRecargo);

        Set<Long> comercios = new HashSet<>(enVivo.keySet());
        comercios.addAll(fotos.keySet());
        comercios.addAll(previas.keySet());
        Map<Long, OcupacionMensualDTO> resultado = new HashMap<>();
        for (Long idComercio : comercios) {
            resultado.put(idComercio, armar(mes,
                    new Serie(fotos.getOrDefault(idComercio, Map.of()), previas.get(idComercio), enVivo.get(idComercio)),
                    new Serie(fotosGratis.getOrDefault(idComercio, Map.of()), previasGratis.get(idComercio),
                            enVivoGratis.get(idComercio)),
                    new Serie(fotosRecargo.getOrDefault(idComercio, Map.of()), previasRecargo.get(idComercio),
                            enVivoRecargo.get(idComercio)),
                    sinMedidas.getOrDefault(idComercio, 0)));
        }
        return resultado;
    }

    @Override
    @RolesAllowed("COMERCIO")
    public OcupacionMensualDTO ocupacionDelMesDelComercioActual(YearMonth mes) {
        Long idComercio = contextoUsuario.idComercioActual();
        List<ItemInventario> items = repository.listarItemsPorComercio(idComercio);
        LocalDate hoy = LocalDate.now();
        LocalDate desde = mes.atDay(1);
        List<OcupacionDiaria> filas = repository.listarOcupacion(desde, mes.atEndOfMonth(), idComercio);
        List<OcupacionDiaria> anteriores = repository.listarUltimaOcupacionAntesDe(desde, idComercio);
        return armar(mes,
                serieDelComercio(idComercio, filas, anteriores, items, hoy, Clase.TOTAL, OcupacionDiaria::getUnidades),
                serieDelComercio(idComercio, filas, anteriores, items, hoy, Clase.GRATIS, OcupacionDiaria::getUnidadesGratis),
                serieDelComercio(idComercio, filas, anteriores, items, hoy, Clase.CON_RECARGO,
                        OcupacionDiaria::getUnidadesConRecargo),
                productosSinMedidas(items).getOrDefault(idComercio, 0));
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public LocalDate fechaDeLaUltimaFoto() {
        return repository.fechaDeLaUltimaOcupacion();
    }

    /** Qué unidades se cuentan: todas, las que están en sus días gratis o las que son stock antiguo. */
    enum Clase { TOTAL, GRATIS, CON_RECARGO }

    /** Los datos de un mismo tipo de unidades: las fotos del mes, la última foto anterior y el stock de hoy. */
    private record Serie(Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos, Map<CategoriaDeTamano, Long> previa,
                         Map<CategoriaDeTamano, Long> enVivo) {
    }

    private static Serie serieDelComercio(Long idComercio, List<OcupacionDiaria> filas, List<OcupacionDiaria> anteriores,
                                          List<ItemInventario> items, LocalDate hoy, Clase clase,
                                          ToLongFunction<OcupacionDiaria> valor) {
        return new Serie(agruparPorComercio(filas, valor).getOrDefault(idComercio, Map.of()),
                previasPorComercio(anteriores, valor).get(idComercio),
                unidadesActuales(items, hoy, clase).get(idComercio));
    }

    private static OcupacionMensualDTO armar(YearMonth mes, Serie total, Serie gratis, Serie conRecargo, int sinMedidas) {
        LocalDate hoy = LocalDate.now();
        Map<CategoriaDeTamano, Long> t = UnidadesDia.sumar(mes, hoy, total.fotos(), total.previa(), total.enVivo());
        Map<CategoriaDeTamano, Long> g = UnidadesDia.sumar(mes, hoy, gratis.fotos(), gratis.previa(), gratis.enVivo());
        Map<CategoriaDeTamano, Long> r = UnidadesDia.sumar(mes, hoy, conRecargo.fotos(), conRecargo.previa(), conRecargo.enVivo());
        return new OcupacionMensualDTO(t.get(CategoriaDeTamano.PEQUENO), t.get(CategoriaDeTamano.MEDIANO),
                t.get(CategoriaDeTamano.GRANDE), sinMedidas,
                g.get(CategoriaDeTamano.PEQUENO), g.get(CategoriaDeTamano.MEDIANO), g.get(CategoriaDeTamano.GRANDE),
                r.get(CategoriaDeTamano.PEQUENO), r.get(CategoriaDeTamano.MEDIANO), r.get(CategoriaDeTamano.GRANDE));
    }

    // Unidades (disponibles + reservadas) por comercio y categoría, tal como está el stock ahora.
    static Map<Long, Map<CategoriaDeTamano, Long>> unidadesActuales(List<ItemInventario> items) {
        return unidadesActuales(items, LocalDate.now(), Clase.TOTAL);
    }

    // Lo mismo, pero solo de la clase pedida (todas, las gratis o las de stock antiguo) en ese día.
    static Map<Long, Map<CategoriaDeTamano, Long>> unidadesActuales(List<ItemInventario> items, LocalDate dia, Clase clase) {
        Map<Long, Map<CategoriaDeTamano, Long>> porComercio = new HashMap<>();
        for (ItemInventario i : items) {
            if (i.getIdComercio() == null) {
                continue;
            }
            // cantidadDisponible ya incluye lo reservado (se descuenta recién al confirmar la reserva).
            long unidades = i.getCantidadDisponible();
            if (unidades <= 0) {
                continue;
            }
            boolean cuenta = switch (clase) {
                case TOTAL -> true;
                case GRATIS -> PoliticaDeAlmacenamiento.esGratis(i, dia);
                case CON_RECARGO -> PoliticaDeAlmacenamiento.esAntiguo(i, dia);
            };
            if (!cuenta) {
                continue;
            }
            porComercio.computeIfAbsent(i.getIdComercio(), id -> new EnumMap<>(CategoriaDeTamano.class))
                    .merge(UnidadesDia.cobrable(i.getCategoria()), unidades, Long::sum);
        }
        return porComercio;
    }

    private static Map<Long, Integer> productosSinMedidas(List<ItemInventario> items) {
        Map<Long, Integer> cuenta = new HashMap<>();
        for (ItemInventario i : items) {
            if (i.getIdComercio() != null && i.getCantidadDisponible() > 0
                    && !CategoriaDeTamano.tieneMedidas(i.getAltoCm(), i.getAnchoCm(), i.getLargoCm())) {
                cuenta.merge(i.getIdComercio(), 1, Integer::sum);
            }
        }
        return cuenta;
    }

    private static Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> agruparPorComercio(
            List<OcupacionDiaria> filas, ToLongFunction<OcupacionDiaria> valor) {
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> r = new HashMap<>();
        for (OcupacionDiaria f : filas) {
            r.computeIfAbsent(f.getIdComercio(), id -> new TreeMap<>())
                    .computeIfAbsent(f.getFecha(), d -> new EnumMap<>(CategoriaDeTamano.class))
                    .merge(f.getCategoria(), valor.applyAsLong(f), Long::sum);
        }
        return r;
    }

    private static Map<Long, Map<CategoriaDeTamano, Long>> previasPorComercio(List<OcupacionDiaria> filas,
                                                                              ToLongFunction<OcupacionDiaria> valor) {
        Map<Long, Map<CategoriaDeTamano, Long>> r = new HashMap<>();
        for (OcupacionDiaria f : filas) {
            r.computeIfAbsent(f.getIdComercio(), id -> new EnumMap<>(CategoriaDeTamano.class))
                    .merge(f.getCategoria(), valor.applyAsLong(f), Long::sum);
        }
        return r;
    }
}
