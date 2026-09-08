package com.rabbit.inventario.dto;

/**
 * DTO de entrada: los criterios con los que se filtra el historial de
 * reservas (ver historial.xhtml e InventarioRepository.listarHistorial).
 *
 * Todos los campos son opcionales — en null significan "no filtrar por
 * esto". El repositorio arma el JPQL sumando solo las condiciones que
 * vengan completas, en vez de tener una consulta distinta por cada
 * combinacion posible.
 */

import java.time.LocalDate;

public class FiltroHistorialDTO {

    /** Comercio dueño del stock reservado. */
    public Long idComercio;

    /** Deposito donde estaba el stock. */
    public Long idDeposito;

    /** Nombre del estado (VIGENTE, CONFIRMADA, ...). Null = todos. */
    public String estado;

    /** Busqueda parcial e insensible a mayusculas sobre el producto. */
    public String producto;

    /** Acota por fecha de creacion de la reserva (inclusive). */
    public LocalDate desde;

    /** Idem, extremo superior; se compara hasta el final de ese dia. */
    public LocalDate hasta;

    /** true si no hay ningun criterio cargado. */
    public boolean estaVacio() {
        return idComercio == null && idDeposito == null
                && (estado == null || estado.isBlank())
                && (producto == null || producto.isBlank())
                && desde == null && hasta == null;
    }

    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdDeposito() { return idDeposito; }
    public void setIdDeposito(Long idDeposito) { this.idDeposito = idDeposito; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
}
