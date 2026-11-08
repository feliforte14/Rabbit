package com.rabbit.pedidos.negocio;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicadorEstadosPedidoTest {

    @Mock private BandejaDeEstados bandeja;
    @Mock private EnvioDeEstadosPendientes envio;
    @InjectMocks private PublicadorEstadosPedido publicador;

    @Test
    void registraElAvisoEnLaBandejaYDespuesDelCommitManda() {
        EstadoPedidoCambiado evento = new EstadoPedidoCambiado(1L, 3L, EstadoPedido.PENDIENTE, LocalDateTime.now());

        publicador.registrarCambioDeEstado(evento);
        publicador.publicarCambioDeEstado(evento);

        var orden = inOrder(bandeja, envio);
        orden.verify(bandeja).registrar(evento);
        orden.verify(envio).enviarPendientes();
    }
}
