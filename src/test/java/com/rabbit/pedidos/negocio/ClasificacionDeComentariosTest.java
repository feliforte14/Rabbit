package com.rabbit.pedidos.negocio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.integracion.ia.ClasificacionDeComentario;
import com.rabbit.integracion.ia.ClasificacionDeComentario.Sentimiento;
import com.rabbit.integracion.ia.ClasificacionDeComentario.Tema;
import com.rabbit.integracion.ia.IClasificadorIa;
import com.rabbit.pedidos.datos.PedidoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClasificacionDeComentariosTest {

    @Mock private IClasificadorIa gateway;
    @Mock private PedidoRepository repository;
    @InjectMocks private ClasificadorEnSegundoPlano segundoPlano;

    @Test
    void guardaLoQueContestaLaIa() {
        when(gateway.clasificar("Llegó tarde"))
                .thenReturn(Optional.of(new ClasificacionDeComentario(Sentimiento.NEGATIVO, Tema.PUNTUALIDAD)));

        segundoPlano.clasificar(5L, "Llegó tarde");

        verify(repository).guardarClasificacion(5L, "NEGATIVO", "PUNTUALIDAD");
    }

    @Test
    void sinRespuestaDeLaIaNoSeGuardaNadaYLaCalificacionQuedaIgual() {
        when(gateway.clasificar("Todo bien")).thenReturn(Optional.empty());

        segundoPlano.clasificar(6L, "Todo bien");

        verify(repository, never()).guardarClasificacion(any(), any(), any());
    }

    @Test
    void elObservadorLePasaElComentarioAlSegundoPlano() throws Exception {
        ClasificadorEnSegundoPlano falso = org.mockito.Mockito.mock(ClasificadorEnSegundoPlano.class);
        ClasificacionDeComentarios observador = new ClasificacionDeComentarios();
        var campo = ClasificacionDeComentarios.class.getDeclaredField("clasificador");
        campo.setAccessible(true);
        campo.set(observador, falso);

        observador.alCalificar(new ComentarioCalificado(9L, "Muy amable"));

        verify(falso).clasificar(9L, "Muy amable");
    }
}
