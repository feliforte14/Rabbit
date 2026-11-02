package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.UserTransaction;
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

/** El alineador arma DDL con texto (los identificadores no se parametrizan): tiene que armar lo esperado y nada más. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlineadorDeRestriccionesEnumTest {

    @Mock private EntityManager em;
    @Mock private UserTransaction tx;
    @Mock private Query consulta;
    @InjectMocks private AlineadorDeRestriccionesEnum alineador;

    @BeforeEach
    void preparar() {
        when(em.createNativeQuery(anyString())).thenReturn(consulta);
    }

    @Test
    void porCadaColumnaBorraYVuelveAArmarElCheckConLosValoresDelEnum() throws Exception {
        alineador.alinear();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em, atLeastOnce()).createNativeQuery(sql.capture());
        List<String> sentencias = sql.getAllValues();
        assertEquals(38, sentencias.size(), "19 columnas, dos sentencias cada una");
        assertTrue(sentencias.contains("ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS pedidos_estado_check"));
        String agregado = sentencias.stream()
                .filter(s -> s.startsWith("ALTER TABLE pedidos ADD CONSTRAINT pedidos_estado_check")).findFirst().orElseThrow();
        assertTrue(agregado.contains("CHECK (estado IN ('") && agregado.contains("'PENDIENTE'"), agregado);
        // Nada que no sea un identificador o un valor de enum entre comillas simples.
        sentencias.forEach(s -> assertTrue(s.matches("ALTER TABLE [a-z_]+ (DROP CONSTRAINT IF EXISTS [a-z_]+|ADD CONSTRAINT [a-z_]+ CHECK \\([a-z_]+ IN \\(('[A-Z0-9_]+',?)+\\)\\))"), s));
        verify(tx, times(19)).commit();
    }

    @Test
    void siUnaColumnaFallaDeshaceSuTransaccionYSigueConLasDemas() throws Exception {
        doThrow(new IllegalStateException("la tabla no existe")).when(consulta).executeUpdate();

        alineador.alinear();

        verify(tx, times(19)).rollback();
        verify(tx, times(0)).commit();
    }

    private enum Valores { PENDIENTE, en_camino }

    @Test
    void soloAceptaIdentificadoresYValoresDeEnumSeguros() {
        AlineadorDeRestriccionesEnum.validarIdentificador("pedidos_externos");
        for (String malo : new String[] {"pedidos; DROP TABLE usuarios", "Pedidos", "1pedidos", "", "a b", "a'b", null}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> AlineadorDeRestriccionesEnum.validarIdentificador(malo), String.valueOf(malo));
        }
        assertEquals("'PENDIENTE'", AlineadorDeRestriccionesEnum.literal(Valores.PENDIENTE));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> AlineadorDeRestriccionesEnum.literal(Valores.en_camino));
    }
}
