package com.rabbit.pedidos.negocio;

/**
 * Después de guardar instrucciones de entrega (AFTER_SUCCESS), le pide al gateway de IA las indicaciones para el
 * repartidor. Corre aparte (EtiquetadorEnSegundoPlano): quien las dejó no espera a la IA, y si la IA falla el
 * repartidor ve el texto como siempre.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class EtiquetadoDeInstrucciones {

    @Inject
    private EtiquetadorEnSegundoPlano etiquetador;

    public void alModificar(@Observes(during = TransactionPhase.AFTER_SUCCESS) InstruccionesModificadas evento) {
        etiquetador.etiquetar(evento.idPedido(), evento.instrucciones());
    }
}
