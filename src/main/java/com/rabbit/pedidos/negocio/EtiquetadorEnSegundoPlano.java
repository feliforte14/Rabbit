package com.rabbit.pedidos.negocio;

/**
 * Llama al gateway de IA (integracion.ia) y guarda las indicaciones que contesta. Es asincrónico: el pedido HTTP de
 * quien dejó las instrucciones ya terminó. Una transacción propia, porque la del pedido ya se confirmó. Sin respuesta
 * de la IA (fallback), no se guarda nada. Si las instrucciones cambiaron mientras tanto, la respuesta se descarta.
 */

import com.rabbit.integracion.ia.IEtiquetadorIa;
import com.rabbit.integracion.ia.IndicacionDeEntrega;
import com.rabbit.pedidos.datos.PedidoRepository;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@Stateless
public class EtiquetadorEnSegundoPlano {

    private static final Logger LOG = Logger.getLogger(EtiquetadorEnSegundoPlano.class.getName());

    @Inject
    private IEtiquetadorIa gateway;

    @Inject
    private PedidoRepository repository;

    @Asynchronous
    public void etiquetar(Long idPedido, String instrucciones) {
        gateway.etiquetar(instrucciones).ifPresent(indicaciones -> {
            int guardadas = repository.guardarIndicaciones(idPedido, instrucciones,
                    IndicacionDeEntrega.guardar(indicaciones));
            LOG.info("[IA] Instrucciones del pedido " + idPedido + " etiquetadas: " + indicaciones.size()
                    + " indicaciones" + (guardadas == 0 ? " (descartadas: el texto cambió)" : ""));
        });
    }
}
