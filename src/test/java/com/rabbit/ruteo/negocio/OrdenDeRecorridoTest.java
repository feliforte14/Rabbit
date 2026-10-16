package com.rabbit.ruteo.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OrdenDeRecorridoTest {

    private static HojaDeRutaDTO hoja(long id, String estado, String direccion, String cp, String... retiros) {
        HojaDeRutaDTO h = new HojaDeRutaDTO();
        h.idPedido = id;
        h.estado = estado;
        h.direccionEntrega = direccion;
        h.codigoPostalEntrega = cp;
        h.paradasRetiro = new ArrayList<>();
        for (String retiro : retiros) {
            String[] partes = retiro.split("\\|");
            h.paradasRetiro.add(new ParadaDTO(ParadaDTO.Tipo.RETIRO, partes[0], partes[0],
                    partes.length > 1 ? partes[1] : null, id));
        }
        return h;
    }

    private static List<String> direcciones(RecorridoDTO r) {
        return r.paradas.stream().map(ParadaDTO::getDireccion).collect(Collectors.toList());
    }

    @Test
    void primeroLosRetirosPendientesSinRepetirYDespuesLasEntregas() {
        RecorridoDTO r = OrdenDeRecorrido.recomendar(List.of(
                hoja(1, "CONFIRMADO", "Entrega A", "1400", "Deposito|1100"),
                hoja(2, "CONFIRMADO", "Entrega B", "1200", "Deposito|1100"),
                hoja(3, "EN_CAMINO", "Entrega C", "1150", "Otro deposito|1300")));
        assertEquals(List.of("Deposito", "Entrega C", "Entrega B", "Entrega A"), direcciones(r));
        assertEquals(List.of(1L, 2L), r.paradas.get(0).pedidos);
    }

    @Test
    void lasEntregasSeEncadenanPorCercaniaDeCodigoPostal() {
        List<ParadaDTO> paradas = new ArrayList<>(List.of(
                new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "a", "a", "1490", 1L),
                new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "b", "b", "1010", 2L),
                new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "c", "c", "1300", 3L),
                new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "d", "d", null, 4L)));
        List<String> desdeElCentro = OrdenDeRecorrido.encadenar(paradas, 1280).stream()
                .map(ParadaDTO::getDireccion).collect(Collectors.toList());
        assertEquals(List.of("c", "a", "b", "d"), desdeElCentro);
        List<String> sinPartida = OrdenDeRecorrido.encadenar(paradas, null).stream()
                .map(ParadaDTO::getDireccion).collect(Collectors.toList());
        assertEquals(List.of("b", "c", "a", "d"), sinPartida);
    }

    @Test
    void variosPedidosALaMismaDireccionSonUnaSolaParada() {
        RecorridoDTO r = OrdenDeRecorrido.recomendar(List.of(
                hoja(1, "EN_CAMINO", "Av. Corrientes 1234", "1043"),
                hoja(2, "EN_CAMINO", "av. corrientes  1234", "1043")));
        assertEquals(1, r.paradas.size());
        assertEquals("#1, #2", r.paradas.get(0).getPedidosTexto());
    }

    @Test
    void losPedidosSinDireccionQuedanAfueraYSeAvisan() {
        RecorridoDTO r = OrdenDeRecorrido.recomendar(List.of(
                hoja(1, "EN_CAMINO", null, null),
                hoja(2, "EN_CAMINO", "Entrega", "1200")));
        assertEquals(List.of("Entrega"), direcciones(r));
        assertEquals(List.of(1L), r.sinDireccion);
    }

    @Test
    void hastaNueveParadasEsUnSoloLinkQueArrancaDondeEstaElRepartidor() {
        List<String> links = EnlaceMapa.tramos(paradas(9));
        assertEquals(1, links.size());
        assertFalse(links.get(0).contains("origin="));
        assertTrue(links.get(0).contains("destination=Parada+9"));
        assertEquals(8, links.get(0).split("waypoints=")[1].split("%7C").length);
    }

    @Test
    void masDeNueveParadasSePartenEnTramosEncadenados() {
        List<String> links = EnlaceMapa.tramos(paradas(20));
        assertEquals(3, links.size());
        assertTrue(links.get(1).contains("origin=Parada+9%2C"));
        assertTrue(links.get(1).contains("destination=Parada+18%2C"));
        assertTrue(links.get(2).contains("origin=Parada+18%2C"));
        assertTrue(links.get(2).contains("destination=Parada+20%2C"));
        assertTrue(links.get(2).contains("waypoints=Parada+19%2C"));
    }

    @Test
    void sinParadasNoHayLinks() {
        assertTrue(EnlaceMapa.tramos(List.of()).isEmpty());
        assertTrue(OrdenDeRecorrido.recomendar(List.of()).isVacio());
    }

    private static List<String> paradas(int cantidad) {
        return IntStream.rangeClosed(1, cantidad).mapToObj(i -> "Parada " + i).collect(Collectors.toList());
    }
}
