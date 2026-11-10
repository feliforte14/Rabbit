package com.rabbit.pedidos.negocio;

/**
 * Después de guardar una calificación con comentario (AFTER_SUCCESS), le pide al gateway de IA que lo clasifique.
 * Corre aparte (ClasificadorEnSegundoPlano): quien califica no espera a la IA, y si la IA falla la calificación ya
 * quedó guardada.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClasificacionDeComentarios {

    @Inject
    private ClasificadorEnSegundoPlano clasificador;

    public void alCalificar(@Observes(during = TransactionPhase.AFTER_SUCCESS) ComentarioCalificado evento) {
        clasificador.clasificar(evento.idCalificacion(), evento.comentario());
    }
}
