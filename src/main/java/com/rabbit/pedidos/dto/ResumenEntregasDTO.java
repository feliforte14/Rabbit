package com.rabbit.pedidos.dto;

import java.math.BigDecimal;

/**
 * DTO de salida: cuántos pedidos le entregó Rabbit a un comercio en un
 * período y cuántos kilos sumaron (los de peso desconocido no suman). Lo
 * usa la liquidación mensual de Pagos (ADR-034).
 */
public record ResumenEntregasDTO(Long idComercio, int cantidad, BigDecimal kilos) {
}
