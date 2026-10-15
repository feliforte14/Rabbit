package com.rabbit.repartidores.dto;

import com.rabbit.repartidores.datos.model.CargaAsignada;
import com.rabbit.repartidores.datos.model.EstadoRepartidor;
import com.rabbit.repartidores.datos.model.Repartidor;
import com.rabbit.repartidores.negocio.CapacidadRepartidor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// DTO de salida: lo que la vista necesita mostrar de un repartidor, con
// la capacidad de su vehículo y lo que lleva ahora (ADR-032).
public class RepartidorDTO {

    public Long id;
    public String nombre;
    public String telefono;
    public String estado;
    public Long idPedidoActual;
    public Long idZona;
    public BigDecimal capacidadKg;
    public Integer capacidadBultos;
    // Lo que lleva ahora: suma de sus CargaAsignada.
    public BigDecimal cargaKg = BigDecimal.ZERO;
    public int cargaBultos;
    public List<Long> idsPedidosEnCurso = new ArrayList<>();

    public static RepartidorDTO desde(Repartidor r) {
        return desde(r, List.of());
    }

    /** Con las cargas de ESTE repartidor (las que no son suyas se ignoran). */
    public static RepartidorDTO desde(Repartidor r, List<CargaAsignada> cargas) {
        RepartidorDTO dto = new RepartidorDTO();
        dto.id = r.getId();
        dto.nombre = r.getNombre();
        dto.telefono = r.getTelefono();
        dto.estado = r.getEstado() != null ? r.getEstado().name() : null;
        dto.idPedidoActual = r.getIdPedidoActual();
        dto.idZona = r.getIdZona();
        dto.capacidadKg = r.getCapacidadKg();
        dto.capacidadBultos = r.getCapacidadBultos();
        for (CargaAsignada c : cargas) {
            if (r.getId().equals(c.getIdRepartidor())) {
                dto.sumar(c.getIdPedido(), c.getPesoKg(), c.getBultos());
            }
        }
        // Asignado antes de que existieran las cargas: un pedido de carga desconocida.
        if (dto.idsPedidosEnCurso.isEmpty() && r.getEstado() == EstadoRepartidor.OCUPADO
                && r.getIdPedidoActual() != null) {
            dto.idsPedidosEnCurso.add(r.getIdPedidoActual());
        }
        return dto;
    }

    /** Suma un pedido a lo que lleva (lo usa también el despacho por zona, en memoria). */
    public void sumar(Long idPedido, BigDecimal pesoKg, int bultos) {
        idsPedidosEnCurso.add(idPedido);
        cargaBultos += bultos;
        if (pesoKg != null) {
            cargaKg = cargaKg.add(pesoKg);
        }
        estado = EstadoRepartidor.OCUPADO.name();
    }

    /** Si le entra un pedido más de ese peso y esos bultos (ver CapacidadRepartidor). */
    public boolean entra(BigDecimal pesoKg, int bultos) {
        return CapacidadRepartidor.entra(capacidadKg, capacidadBultos, cargaKg, cargaBultos,
                idsPedidosEnCurso.size(), pesoKg, bultos);
    }

    /** Si le queda lugar para algo más. */
    public boolean isConLugar() {
        return CapacidadRepartidor.tieneLugar(capacidadKg, capacidadBultos, cargaKg, cargaBultos,
                idsPedidosEnCurso.size());
    }

    public boolean isConCapacidad() {
        return capacidadKg != null || capacidadBultos != null;
    }

    public int getPedidosEnCurso() { return idsPedidosEnCurso.size(); }

    /** "#12, #15": los pedidos que lleva, para las tablas. */
    public String getPedidosEnCursoTexto() {
        return idsPedidosEnCurso.stream().map(id -> "#" + id).collect(java.util.stream.Collectors.joining(", "));
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getEstado() { return estado; }
    public Long getIdPedidoActual() { return idPedidoActual; }
    public Long getIdZona() { return idZona; }
    public BigDecimal getCapacidadKg() { return capacidadKg; }
    public Integer getCapacidadBultos() { return capacidadBultos; }
    public BigDecimal getCargaKg() { return cargaKg; }
    public int getCargaBultos() { return cargaBultos; }
    public List<Long> getIdsPedidosEnCurso() { return idsPedidosEnCurso; }
}
