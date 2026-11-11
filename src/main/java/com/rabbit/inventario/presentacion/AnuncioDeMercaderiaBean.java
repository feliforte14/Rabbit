package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — la sección "Avisar mercadería" de comercio/mi-stock.xhtml
 * (ADR-058): el comercio anuncia qué manda a un depósito de Rabbit y ve el estado de sus órdenes, con
 * lo que llegó de cada línea y las diferencias.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.inventario.dto.DatosLineaRecepcionDTO;
import com.rabbit.inventario.dto.DatosOrdenDeRecepcionDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.OrdenDeRecepcionDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.IRecepcionDeMercaderia;
import com.rabbit.inventario.negocio.ValidacionException;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Named
@ViewScoped
public class AnuncioDeMercaderiaBean implements Serializable {

    private static final int FILAS_INICIALES = 3;

    /** Una fila del formulario: los números llegan como texto/Integer/BigDecimal para poder mostrar un error claro. */
    public static class Fila implements Serializable {
        private String producto;
        private Integer cantidad;
        private BigDecimal pesoKg;

        public String getProducto() { return producto; }
        public void setProducto(String producto) { this.producto = producto; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public BigDecimal getPesoKg() { return pesoKg; }
        public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
    }

    @Inject
    private IRecepcionDeMercaderia recepciones;

    @Inject
    private IConsultaStock stock;

    private List<DepositoDTO> depositos = List.of();
    private transient List<OrdenDeRecepcionDTO> ordenes;
    private Long idDeposito;
    private String notas;
    private List<Fila> filas = new ArrayList<>();

    @PostConstruct
    public void iniciar() {
        depositos = stock.listarDepositos();
        for (int i = 0; i < FILAS_INICIALES; i++) {
            filas.add(new Fila());
        }
    }

    public void agregarFila() {
        filas.add(new Fila());
    }

    public void anunciar() {
        DatosOrdenDeRecepcionDTO datos = new DatosOrdenDeRecepcionDTO();
        datos.idDeposito = idDeposito;
        datos.notas = notas;
        for (Fila f : filas) {
            datos.lineas.add(new DatosLineaRecepcionDTO(f.producto, f.cantidad == null ? 0 : f.cantidad, f.pesoKg));
        }
        try {
            Long id = recepciones.anunciar(datos);
            Mensajes.info("Orden " + id + " anunciada: el depósito la va a registrar cuando llegue la mercadería");
            idDeposito = null;
            notas = null;
            filas = new ArrayList<>();
            iniciar();
            ordenes = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo anunciar la orden. Intentá de nuevo.");
        }
    }

    public void cancelar(Long idOrden) {
        try {
            recepciones.cancelar(idOrden);
            Mensajes.info("Orden " + idOrden + " cancelada");
            ordenes = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo cancelar la orden. Intentá de nuevo.");
        }
    }

    public List<OrdenDeRecepcionDTO> getOrdenes() {
        if (ordenes == null) {
            ordenes = recepciones.listarDelComercioActual();
        }
        return ordenes;
    }

    public List<DepositoDTO> getDepositos() { return depositos; }
    public Long getIdDeposito() { return idDeposito; }
    public void setIdDeposito(Long idDeposito) { this.idDeposito = idDeposito; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public List<Fila> getFilas() { return filas; }
}
