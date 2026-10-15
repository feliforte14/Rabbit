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
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.util.List;

@Named
@RequestScoped
public class MapaBean {

    @Inject
    private IGestionRepartidores repartidores;

    private List<UbicacionDTO> ubicaciones;

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
