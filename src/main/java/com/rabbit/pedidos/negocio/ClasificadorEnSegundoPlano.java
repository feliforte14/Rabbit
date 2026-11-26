package com.rabbit.pedidos.negocio;

/**
 * Llama al gateway de IA (integracion.ia) y guarda lo que contesta. Es asincrónico: el pedido HTTP de quien
 * califica ya terminó. Una transacción propia, porque la del pedido ya se confirmó. Sin respuesta de la IA
 * (fallback), no se guarda nada y el comentario queda sin clasificar.
 */

import com.rabbit.integracion.ia.IClasificadorIa;
import com.rabbit.pedidos.datos.PedidoRepository;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@Stateless
public class ClasificadorEnSegundoPlano {

    private static final Logger LOG = Logger.getLogger(ClasificadorEnSegundoPlano.class.getName());

    @Inject
    private IClasificadorIa gateway;

    @Inject
    private PedidoRepository repository;

    @Asynchronous
    public void clasificar(Long idCalificacion, String comentario) {
        gateway.clasificar(comentario).ifPresent(c -> {
            repository.guardarClasificacion(idCalificacion, c.sentimiento().name(), c.tema().name());
            LOG.info("[IA] Comentario de la calificación " + idCalificacion + " clasificado: " + c.sentimiento()
                    + " / " + c.tema());
        });
    }
}
