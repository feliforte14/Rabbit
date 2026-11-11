package com.rabbit.ruteo.negocio;

/**
 * Punto de partida de todos los camiones: el depósito de Rabbit marcado como
 * "base de salida" en Depósitos y stock. La hoja de ruta del día arranca ahí
 * (ver OrdenDeRecorrido) y desde ahí se va a buscar lo que esté en un punto
 * de picking ajeno. Sin ninguno marcado, la salida figura igual en la hoja
 * pero sin dirección ni link a Maps: el primer tramo arranca desde donde esté
 * el repartidor.
 */

import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import java.util.List;

final class DepositoRabbit {

    static final String NOMBRE_POR_DEFECTO = "Depósito Rabbit";

    private DepositoRabbit() {
    }

    /** La parada de salida del día, sin pedidos asociados y sin ubicar en el mapa. */
    static ParadaDTO salida(List<DepositoDTO> depositos) {
        DepositoDTO base = depositos.stream().filter(DepositoDTO::isBaseDeSalida).findFirst().orElse(null);
        ParadaDTO salida = new ParadaDTO();
        salida.tipo = ParadaDTO.Tipo.SALIDA;
        if (base == null) {
            salida.descripcion = NOMBRE_POR_DEFECTO;
            return salida;
        }
        salida.direccion = base.direccion + ", " + base.localidad;
        salida.codigoPostal = base.codigoPostal;
        salida.descripcion = base.getEtiqueta() + " — " + salida.direccion;
        return salida;
    }
}
