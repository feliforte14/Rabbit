package com.rabbit.inventario.dto;

import java.util.ArrayList;
import java.util.List;

/** Lo que el comercio anuncia (ADR-058): a qué depósito manda y qué manda. */
public class DatosOrdenDeRecepcionDTO {
    public Long idDeposito;
    public String notas;
    public List<DatosLineaRecepcionDTO> lineas = new ArrayList<>();
}
