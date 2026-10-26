package com.rabbit.repartidores.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — mapa.xhtml: dónde están los
 * repartidores que tienen entregas en curso (ADR-035), para el personal.
 *
 * La página pide los datos de nuevo cada 30 segundos por ajax (ver
 * mapa.js): cada pedido vuelve a consultar, no hay nada guardado entre uno
 * y otro.
 */

import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.util.ArrayList;
import java.util.List;

@Named
@RequestScoped
public class MapaBean {

    @Inject
    private IGestionRepartidores repartidores;

    @Inject
    private IRuteo ruteo;

    private List<UbicacionDTO> ubicaciones;
    private List<Avance> avances;

    /** Una fila de "Avance de la hoja de ruta": dónde va cada repartidor en su recorrido. */
    public static class Avance {
        public final String nombre;
        public final String hora;
        public final String proximaParada;
        public final int paradasPendientes;

        Avance(String nombre, String hora, String proximaParada, int paradasPendientes) {
            this.nombre = nombre;
            this.hora = hora;
            this.proximaParada = proximaParada;
            this.paradasPendientes = paradasPendientes;
        }

        public String getNombre() { return nombre; }
        public String getHora() { return hora; }
        public String getProximaParada() { return proximaParada; }
        public int getParadasPendientes() { return paradasPendientes; }
    }

    /**
     * Qué le falta a cada repartidor que está en el mapa: la próxima parada de su hoja de ruta y cuántas
     * quedan. Si todavía no retiró nada, la próxima es el retiro y no la salida del depósito.
     */
    public List<Avance> getAvances() {
        if (avances == null) {
            avances = new ArrayList<>();
            for (UbicacionDTO u : ubicaciones()) {
                RecorridoDTO recorrido = ruteo.recorridoDeRepartidor(u.getIdRepartidor());
                List<ParadaDTO> pendientes = recorrido.getParadas().stream().filter(p -> !p.isSalida()).toList();
                avances.add(new Avance(u.getNombre(), u.getHora(),
                        pendientes.isEmpty() ? "Sin paradas pendientes"
                                : (pendientes.get(0).isRetiro() ? "Retiro: " : "Entrega: ") + pendientes.get(0).getDescripcion(),
                        pendientes.size()));
            }
        }
        return avances;
    }

    private List<UbicacionDTO> ubicaciones() {
        if (ubicaciones == null) {
            ubicaciones = repartidores.listarUbicacionesRecientes();
        }
        return ubicaciones;
    }

    /** No hace nada: el ajax solo vuelve a pintar los datos (ver mapa.xhtml). */
    public void actualizar() {
    }

    /** [{lat, lng, titulo, detalle}] para mapa.js. */
    public String getPuntosJson() {
        JsonArrayBuilder puntos = Json.createArrayBuilder();
        for (UbicacionDTO u : ubicaciones()) {
            puntos.add(Json.createObjectBuilder()
                    .add("lat", u.getLatitud())
                    .add("lng", u.getLongitud())
                    .add("titulo", u.getNombre())
                    .add("detalle", u.getPedidosEnCurso() + (u.getPedidosEnCurso() == 1 ? " pedido" : " pedidos")
                            + " en curso · ubicación de las " + u.getHora()));
        }
        ParadaDTO deposito = ruteo.depositoDeSalida();
        if (deposito.isUbicada()) {
            puntos.add(Json.createObjectBuilder()
                    .add("lat", deposito.getLatitud())
                    .add("lng", deposito.getLongitud())
                    .add("titulo", deposito.getDescripcion())
                    .add("detalle", "Salida de los camiones")
                    .add("color", "#1d4ed8"));
        }
        return puntos.build().toString();
    }

    public String getResumen() {
        int cantidad = ubicaciones().size();
        return cantidad == 0
                ? "Ningún repartidor con entregas en curso está compartiendo su ubicación."
                : cantidad + (cantidad == 1 ? " repartidor" : " repartidores") + " en el mapa.";
    }

    public List<UbicacionDTO> getUbicaciones() { return ubicaciones(); }
}
