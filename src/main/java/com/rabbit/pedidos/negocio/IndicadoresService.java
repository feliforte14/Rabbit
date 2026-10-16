package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — tablero de indicadores de entrega (EJB @Stateless,
 * componente Pedidos, ADR-044).
 *
 * Aparte de PedidoService porque no cambia nada: solo lee los pedidos del
 * período y se los pasa a CalculoDeIndicadores. Stateless por el mismo
 * motivo que el resto: cada consulta recibe todo lo que necesita.
 *
 * SEGURIDAD: el personal ve toda la red (o un comercio); un COMERCIO, solo
 * lo suyo, con el comercio sacado de la identidad autenticada.
 */

import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.dto.IndicadoresDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDate;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class IndicadoresService implements IIndicadores {

    @Inject
    private PedidoRepository repository;

    @Inject
    private IContextoUsuario contextoUsuario;

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public IndicadoresDTO calcular(int dias, Long idComercio) {
        return calcularPara(dias, idComercio);
    }

    @Override
    @RolesAllowed("COMERCIO")
    public IndicadoresDTO calcularDelComercioActual(int dias) {
        return calcularPara(dias, contextoUsuario.idComercioActual());
    }

    private IndicadoresDTO calcularPara(int dias, Long idComercio) {
        if (!PERIODOS.contains(dias)) {
            throw new ValidacionException("El período tiene que ser de " + PERIODOS + " días");
        }
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(dias - 1L);
        return CalculoDeIndicadores.calcular(
                repository.listarRecibidosDesde(desde.atStartOfDay(), idComercio), desde, hasta,
                repository.contarEnEspera(idComercio));
    }
}
