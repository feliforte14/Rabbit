package com.rabbit.pedidos.dto;

/**
 * DTO de entrada: lo que carga el repartidor cuando no pudo entregar un
 * pedido (ADR-041, ADR-045). Solo el motivo es siempre obligatorio; el
 * comentario y las fotos lo son según el motivo (ver EvidenciaDeVisita).
 *
 * latitud/longitud/precisionMetros: los que dio el celular al abrir la
 * ventana, o null si no los dio (sin permiso, sin señal, computadora).
 */

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import java.util.ArrayList;
import java.util.List;

public class EntregaFallidaDTO {

    public MotivoEntregaFallida motivo;
    public String comentario;
    // Hasta 3 imágenes (JPEG, PNG o WEBP), ya leídas.
    public List<byte[]> fotos = new ArrayList<>();
    public Double latitud;
    public Double longitud;
    public Double precisionMetros;

    public EntregaFallidaDTO() {}

    public EntregaFallidaDTO(MotivoEntregaFallida motivo, String comentario) {
        this.motivo = motivo;
        this.comentario = comentario;
    }
}
