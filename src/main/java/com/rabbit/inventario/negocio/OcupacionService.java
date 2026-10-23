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
        Map<Long, Map<CategoriaDeTamano, Long>> actual = unidadesActuales(repository.listarTodosLosItems());

        // Los comercios que tenían stock en la foto anterior siguen en la foto aunque ahora estén en cero.
        Set<Long> comercios = new HashSet<>(actual.keySet());
        for (OcupacionDiaria previa : repository.listarUltimaOcupacionAntesDe(dia, null)) {
            comercios.add(previa.getIdComercio());
        }
        repository.borrarOcupacionDelDia(dia);
        for (Long idComercio : comercios) {
            Map<CategoriaDeTamano, Long> delComercio = actual.getOrDefault(idComercio, Map.of());
            for (CategoriaDeTamano categoria : COBRABLES) {
                repository.guardarOcupacion(new OcupacionDiaria(idComercio, dia, categoria,
                        delComercio.getOrDefault(categoria, 0L)));
            }
        }
        LOG.info("[Inventario] Foto de ocupación del " + dia + ": " + comercios.size() + " comercio(s)");
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Map<Long, OcupacionMensualDTO> ocupacionDelMes(YearMonth mes) {
        List<ItemInventario> items = repository.listarTodosLosItems();
        Map<Long, Map<CategoriaDeTamano, Long>> enVivo = unidadesActuales(items);
        Map<Long, Integer> sinMedidas = productosSinMedidas(items);

        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> fotos = agruparPorComercio(
                repository.listarOcupacion(desde, hasta, null));
        Map<Long, Map<CategoriaDeTamano, Long>> previas = previasPorComercio(repository.listarUltimaOcupacionAntesDe(desde, null));

        Set<Long> comercios = new HashSet<>(enVivo.keySet());
        comercios.addAll(fotos.keySet());
        comercios.addAll(previas.keySet());
        Map<Long, OcupacionMensualDTO> resultado = new HashMap<>();
        for (Long idComercio : comercios) {
            resultado.put(idComercio, armar(mes, fotos.getOrDefault(idComercio, Map.of()), previas.get(idComercio),
                    enVivo.get(idComercio), sinMedidas.getOrDefault(idComercio, 0)));
        }
        return resultado;
    }

    @Override
    @RolesAllowed("COMERCIO")
    public OcupacionMensualDTO ocupacionDelMesDelComercioActual(YearMonth mes) {
        Long idComercio = contextoUsuario.idComercioActual();
        List<ItemInventario> items = repository.listarItemsPorComercio(idComercio);
        LocalDate desde = mes.atDay(1);
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = agruparPorComercio(
                repository.listarOcupacion(desde, mes.atEndOfMonth(), idComercio)).getOrDefault(idComercio, Map.of());
        Map<Long, Map<CategoriaDeTamano, Long>> previas = previasPorComercio(
                repository.listarUltimaOcupacionAntesDe(desde, idComercio));
        return armar(mes, fotos, previas.get(idComercio), unidadesActuales(items).get(idComercio),
                productosSinMedidas(items).getOrDefault(idComercio, 0));
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public LocalDate fechaDeLaUltimaFoto() {
        return repository.fechaDeLaUltimaOcupacion();
    }

    private static OcupacionMensualDTO armar(YearMonth mes, Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos,
                                             Map<CategoriaDeTamano, Long> previa, Map<CategoriaDeTamano, Long> enVivo,
                                             int sinMedidas) {
        Map<CategoriaDeTamano, Long> suma = UnidadesDia.sumar(mes, LocalDate.now(), fotos, previa, enVivo);
        return new OcupacionMensualDTO(suma.get(CategoriaDeTamano.PEQUENO), suma.get(CategoriaDeTamano.MEDIANO),
                suma.get(CategoriaDeTamano.GRANDE), sinMedidas);
    }

    // Unidades (disponibles + reservadas) por comercio y categoría, tal como está el stock ahora.
    static Map<Long, Map<CategoriaDeTamano, Long>> unidadesActuales(List<ItemInventario> items) {
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

    private static Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> agruparPorComercio(List<OcupacionDiaria> filas) {
        Map<Long, Map<LocalDate, Map<CategoriaDeTamano, Long>>> r = new HashMap<>();
        for (OcupacionDiaria f : filas) {
            r.computeIfAbsent(f.getIdComercio(), id -> new TreeMap<>())
                    .computeIfAbsent(f.getFecha(), d -> new EnumMap<>(CategoriaDeTamano.class))
                    .merge(f.getCategoria(), f.getUnidades(), Long::sum);
        }
        return r;
    }

    private static Map<Long, Map<CategoriaDeTamano, Long>> previasPorComercio(List<OcupacionDiaria> filas) {
        Map<Long, Map<CategoriaDeTamano, Long>> r = new HashMap<>();
        for (OcupacionDiaria f : filas) {
            r.computeIfAbsent(f.getIdComercio(), id -> new EnumMap<>(CategoriaDeTamano.class))
                    .merge(f.getCategoria(), f.getUnidades(), Long::sum);
        }
        return r;
    }
}
