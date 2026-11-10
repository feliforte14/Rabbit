package com.rabbit.pedidos.negocio;

/**
 * Evento CDI: quien recibe el pedido cambió su entrega desde el seguimiento (reprogramó, dejó instrucciones
 * o autorizó a otra persona). Lo dispara PedidoService.modificarEntrega y lo observa el canal de mail
 * (AvisosPorMail) recién cuando el cambio ya se guardó, para avisarle qué quedó registrado.
 *
 * Lleva TODO lo que el mail necesita, ya leído dentro de la transacción: después del commit no se puede
 * consultar la base ni llamar a otros servicios (la transacción ya terminó). Es lo que quedó guardado, no lo
 * que se mandó. Los textos son de quien recibe y no van al log.
 */

import java.time.LocalDate;

public record EntregaModificada(Long idPedido, String email, String nombre, String comercio, String codigoSeguimiento,
                                LocalDate fechaPrometida, String instrucciones, String nombreAutorizado) {
}
