package com.rabbit.pedidos.datos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Al eliminar un comercio se borran sus pedidos con todo lo que cuelga de ellos, con sentencias fijas. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoRepositoryTest {

    @Mock private EntityManager em;
    @Mock private TypedQuery<Pedido> consultaPedidos;
    @Mock private TypedQuery<PedidoExterno> consultaExternos;
    @Mock private Query borrado;
    @InjectMocks private PedidoRepository repository;

    @BeforeEach
    void preparar() {
        when(em.createQuery(anyString(), eq(Pedido.class))).thenReturn(consultaPedidos);
        when(em.createQuery(anyString(), eq(PedidoExterno.class))).thenReturn(consultaExternos);
        when(consultaPedidos.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(consultaPedidos);
        when(consultaExternos.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(consultaExternos);
        when(em.createQuery(anyString())).thenReturn(borrado);
        when(borrado.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(borrado);
    }

    @Test
    void borraLoQueCuelgaDeCadaPedidoConUnaSentenciaFijaPorEntidad() {
        Pedido uno = mock(Pedido.class);
        Pedido dos = mock(Pedido.class);
        when(uno.getId()).thenReturn(1L);
        when(dos.getId()).thenReturn(2L);
        when(consultaPedidos.getResultList()).thenReturn(List.of(uno, dos));
        when(consultaExternos.getResultList()).thenReturn(List.of(mock(PedidoExterno.class)));

        long[] borrados = repository.eliminarPedidosDeComercio(9L);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em, times(4)).createQuery(jpql.capture());
        assertArrayEquals(new String[] {
                "DELETE FROM RemitoEntrega x WHERE x.idPedido IN :ids",
                "DELETE FROM FotoIntentoEntrega x WHERE x.idPedido IN :ids",
                "DELETE FROM IntentoEntrega x WHERE x.idPedido IN :ids",
                "DELETE FROM CalificacionEntrega x WHERE x.idPedido IN :ids"}, jpql.getAllValues().toArray());
        verify(borrado, times(4)).setParameter("ids", List.of(1L, 2L));
        verify(borrado, times(4)).executeUpdate();
        verify(em).remove(uno);
        verify(em).remove(dos);
        assertArrayEquals(new long[] {2, 1}, borrados);
    }

    @Test
    void sinPedidosNoBorraNadaDeLoQueCuelga() {
        when(consultaPedidos.getResultList()).thenReturn(List.of());
        when(consultaExternos.getResultList()).thenReturn(List.of());

        long[] borrados = repository.eliminarPedidosDeComercio(9L);

        verify(borrado, never()).executeUpdate();
        assertArrayEquals(new long[] {0, 0}, borrados);
    }

    @Test
    void buscaLosRetirosListosAntesDelLimiteSoloConfirmadosYDeRetiro() {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> consultaIds = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(consultaIds);
        when(consultaIds.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(consultaIds);
        when(consultaIds.getResultList()).thenReturn(List.of(7L, 9L));
        java.time.LocalDateTime limite = java.time.LocalDateTime.of(2026, 10, 19, 4, 10);

        List<Long> ids = repository.listarIdsDeRetirosListosAntesDe(limite);

        assertEquals(List.of(7L, 9L), ids);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(jpql.capture(), eq(Long.class));
        // Solo retiros CONFIRMADOS, más viejos que el límite: un retiro ya entregado o cancelado no vence.
        assertTrue(jpql.getValue().contains("p.estado = :estado"));
        assertTrue(jpql.getValue().contains("p.tipoEntrega = :tipo"));
        assertTrue(jpql.getValue().contains("p.fechaActualizacion < :limite"));
        verify(consultaIds).setParameter("estado", com.rabbit.pedidos.datos.model.EstadoPedido.CONFIRMADO);
        verify(consultaIds).setParameter("tipo", com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO);
        verify(consultaIds).setParameter("limite", limite);
    }
}
