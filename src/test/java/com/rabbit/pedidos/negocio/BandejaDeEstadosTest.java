package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.datos.EventoEstadoRepository;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.EventoEstadoPedido;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.event.Observes;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La bandeja de salida de los avisos de estado (outbox) y su publicador. */
@ExtendWith(MockitoExtension.class)
class BandejaDeEstadosTest {

    private static final LocalDateTime CAMBIO = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Mock private EventoEstadoRepository repository;
    @InjectMocks private BandejaDeEstados bandeja;

    @Test
    void registrarGuardaElAvisoPendienteConTodosSusDatos() {
        bandeja.registrar(new EstadoPedidoCambiado(100L, 3L, EstadoPedido.ENTREGADO, CAMBIO));

        ArgumentCaptor<EventoEstadoPedido> guardado = ArgumentCaptor.forClass(EventoEstadoPedido.class);
        verify(repository).guardar(guardado.capture());
        EventoEstadoPedido fila = guardado.getValue();
        assertEquals(100L, fila.getIdPedido());
        assertEquals(3L, fila.getIdComercio());
        assertEquals("ENTREGADO", fila.getEstado());
        assertEquals(CAMBIO, fila.getFechaCambio());
        assertNotNull(fila.getCreadoEn());
        assertNull(fila.getPublicadoEn(), "recién guardado: todavía no se publicó");
        assertEquals(0, fila.getIntentosFallidos());
    }

    @Test
    void pendientesDevuelveLosEventosEnElOrdenDelRepositorio() {
        EventoEstadoPedido a = fila(1L, 100L, "PENDIENTE");
        EventoEstadoPedido b = fila(2L, 100L, "CONFIRMADO");
        when(repository.listarPendientes(50)).thenReturn(List.of(a, b));

        List<BandejaDeEstados.Pendiente> pendientes = bandeja.pendientes(50);

        assertEquals(2, pendientes.size());
        assertEquals(1L, pendientes.get(0).id());
        assertEquals(EstadoPedido.PENDIENTE, pendientes.get(0).evento().estado());
        assertEquals(EstadoPedido.CONFIRMADO, pendientes.get(1).evento().estado());
        assertEquals(CAMBIO, pendientes.get(1).evento().fechaCambio());
    }

    @Test
    void marcarPublicadoYRegistrarFalloDelegan() {
        bandeja.marcarPublicado(5L);
        bandeja.registrarFallo(5L);
        bandeja.limpiarPublicadosAnterioresA(CAMBIO);

        verify(repository).marcarPublicado(org.mockito.ArgumentMatchers.eq(5L), any(LocalDateTime.class));
        verify(repository).sumarIntentoFallido(5L);
        verify(repository).borrarPublicadosAnterioresA(CAMBIO);
    }

    @Test
    void laLecturaYLasMarcasCorrenEnTransaccionPropia_yElRegistroSeSumaALaDelCambio() throws Exception {
        assertEquals(TransactionAttributeType.REQUIRED, atributo(BandejaDeEstados.class, "registrar", EstadoPedidoCambiado.class));
        assertEquals(TransactionAttributeType.REQUIRES_NEW, atributo(BandejaDeEstados.class, "pendientes", int.class));
        assertEquals(TransactionAttributeType.REQUIRES_NEW, atributo(BandejaDeEstados.class, "marcarPublicado", Long.class));
        assertEquals(TransactionAttributeType.REQUIRES_NEW, atributo(BandejaDeEstados.class, "registrarFallo", Long.class));
    }

    @Test
    void elPublicadorRegistraEnLaTransaccionDelCambioYEnviaRecienDespuesDelCommit() throws Exception {
        Method registrar = PublicadorEstadosPedido.class.getMethod("registrarCambioDeEstado", EstadoPedidoCambiado.class);
        Method publicar = PublicadorEstadosPedido.class.getMethod("publicarCambioDeEstado", EstadoPedidoCambiado.class);

        assertEquals(TransactionAttributeType.REQUIRED, registrar.getAnnotation(TransactionAttribute.class).value());
        assertEquals(TransactionAttributeType.NOT_SUPPORTED, publicar.getAnnotation(TransactionAttribute.class).value());
        // El registro observa durante la transacción; el envío, solo si el commit salió bien.
        assertEquals(TransactionPhase.IN_PROGRESS, registrar.getParameters()[0].getAnnotation(Observes.class).during());
        assertEquals(TransactionPhase.AFTER_SUCCESS, publicar.getParameters()[0].getAnnotation(Observes.class).during());
        // Y si no hay transacción, primero se guarda y después se manda.
        int prioridadRegistro = registrar.getParameters()[0].getAnnotation(jakarta.annotation.Priority.class).value();
        int prioridadEnvio = publicar.getParameters()[0].getAnnotation(jakarta.annotation.Priority.class).value();
        assertEquals(true, prioridadRegistro < prioridadEnvio);
    }

    private static TransactionAttributeType atributo(Class<?> clase, String metodo, Class<?>... tipos) throws Exception {
        return clase.getMethod(metodo, tipos).getAnnotation(TransactionAttribute.class).value();
    }

    private static EventoEstadoPedido fila(Long id, Long idPedido, String estado) {
        EventoEstadoPedido f = new EventoEstadoPedido();
        try {
            var campo = EventoEstadoPedido.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(f, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        f.setIdPedido(idPedido);
        f.setIdComercio(3L);
        f.setEstado(estado);
        f.setFechaCambio(CAMBIO);
        return f;
    }
}
