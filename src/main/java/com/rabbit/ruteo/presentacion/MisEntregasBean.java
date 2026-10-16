package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — la pantalla del repartidor
 * (mis-entregas.xhtml): sus entregas en curso con la hoja de ruta (pueden
 * ser varias si su vehículo tiene capacidad, ADR-032), los botones para
 * marcar el retiro y la entrega con la foto del remito (ADR-033), la
 * ubicación que manda su celular mientras reparte (ADR-035) y su
 * historial.
 *
 * No recibe el ID del repartidor: IRuteo, IGestionPedidos e
 * IGestionRepartidores lo sacan de la identidad autenticada, así que un
 * repartidor no puede ver ni mover los pedidos de otro.
 */

import com.rabbit.infraestructura.ArchivosSubidos;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class MisEntregasBean implements Serializable {

    // Tope de lo que se lee del archivo subido. El celular achica la foto
    // antes de mandarla (remito.js); la validación real está en Pedidos.
    private static final int TAMANO_MAXIMO_FOTO = 5 * 1024 * 1024;

    @Inject
    private IRuteo ruteo;

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private IGestionRepartidores repartidores;

    // Los motivos de devolución que se ofrecen; con "Otro" hay que contarlo.
    private static final List<String> MOTIVOS_DEVOLUCION = List.of(
            "Cliente ausente", "Dirección incorrecta o incompleta", "El cliente rechazó el pedido",
            "No entra en el vehículo", "Otro");

    private List<HojaDeRutaDTO> entregas;
    private List<HojaDeRutaDTO> historial;
    private RecorridoDTO recorrido;

    // Ventana "Entregar pedido": una sola para todas las entregas en camino
    // (h:inputFile no funciona dentro de ui:repeat); el botón de cada
    // tarjeta le pone el pedido.
    private Long idPedidoAEntregar;
    private transient Part fotoRemito;

    // Ventana "Devolver a depósito", mismo esquema.
    private Long idPedidoADevolver;
    private String motivoDevolucion;
    private String detalleDevolucion;

    // Los manda ubicacion.js por ajax (ver compartirUbicacion).
    private Double latitud;
    private Double longitud;

    @PostConstruct
    public void cargar() {
        try {
            entregas = ruteo.entregasEnCursoDelRepartidor();
            historial = ruteo.historialDelRepartidor();
            recorrido = ruteo.recorridoRecomendado(entregas);
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            // Cuenta REPARTIDOR sin repartidor asociado.
            entregas = List.of();
            historial = List.of();
            recorrido = new RecorridoDTO();
            Mensajes.error(e.getMessage());
        }
    }

    // Retiró el pedido: CONFIRMADO → EN_CAMINO.
    public void retirar(Long idPedido) {
        ejecutar(() -> gestion.despacharPedido(idPedido), "Pedido " + idPedido + " retirado: ya está en camino");
    }

    // Lo entregó: EN_CAMINO → ENTREGADO, con la foto del remito firmado.
    public void entregar() {
        Long idPedido = idPedidoAEntregar;
        byte[] foto;
        try {
            foto = ArchivosSubidos.leer(fotoRemito, TAMANO_MAXIMO_FOTO);
        } catch (IOException e) {
            Mensajes.error("No se pudo leer la foto. Probá sacarla de nuevo.");
            return;
        } finally {
            fotoRemito = null;
        }
        if (idPedido == null) {
            Mensajes.error("Elegí qué pedido entregaste");
            return;
        }
        if (foto == null) {
            Mensajes.error("La foto del remito no puede superar los 5 MB");
            return;
        }
        ejecutar(() -> gestion.registrarEntrega(idPedido, foto), "Entrega del pedido " + idPedido + " registrada");
        idPedidoAEntregar = null;
    }

    // No lo pudo entregar: vuelve al depósito y se le asigna a otro (ADR-037).
    public void devolver() {
        Long idPedido = idPedidoADevolver;
        if (idPedido == null) {
            Mensajes.error("Elegí qué pedido devolvés");
            return;
        }
        if (motivoDevolucion == null || !MOTIVOS_DEVOLUCION.contains(motivoDevolucion)) {
            Mensajes.error("Elegí por qué no se pudo entregar");
            return;
        }
        String detalle = detalleDevolucion == null ? "" : detalleDevolucion.trim();
        if ("Otro".equals(motivoDevolucion) && detalle.isEmpty()) {
            Mensajes.error("Contá qué pasó");
            return;
        }
        String motivo = detalle.isEmpty() ? motivoDevolucion
                : ("Otro".equals(motivoDevolucion) ? detalle : motivoDevolucion + ": " + detalle);
        ejecutar(() -> gestion.devolverADeposito(idPedido, motivo),
                "Pedido " + idPedido + " devuelto al depósito. Rabbit se lo asigna a otro repartidor.");
        idPedidoADevolver = null;
        motivoDevolucion = null;
        detalleDevolucion = null;
    }

    private void ejecutar(Runnable accion, String exito) {
        try {
            accion.run();
            Mensajes.info(exito);
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo registrar el cambio. Intentá de nuevo.");
        }
    }

    // Lo llama ubicacion.js cada tanto mientras la pantalla está abierta y
    // hay entregas en curso. Si falla (por ejemplo, ya no tiene entregas),
    // no se avisa: es un dato de fondo, no una acción del repartidor.
    public void compartirUbicacion() {
        if (latitud == null || longitud == null) {
            return;
        }
        try {
            repartidores.registrarUbicacion(latitud, longitud);
        } catch (com.rabbit.repartidores.negocio.ValidacionException | EJBException e) {
            // Se reintenta en el próximo envío.
        }
    }

    /** Si hay que mandar la ubicación: solo con alguna entrega ya retirada o por retirar. */
    public boolean isCompartirUbicacion() {
        return entregas != null && !entregas.isEmpty();
    }

    public List<HojaDeRutaDTO> getEntregas() { return entregas; }
    public List<HojaDeRutaDTO> getHistorial() { return historial; }
    public RecorridoDTO getRecorrido() { return recorrido; }
    public List<String> getMotivosDevolucion() { return MOTIVOS_DEVOLUCION; }
    public Long getIdPedidoADevolver() { return idPedidoADevolver; }
    public void setIdPedidoADevolver(Long idPedidoADevolver) { this.idPedidoADevolver = idPedidoADevolver; }
    public String getMotivoDevolucion() { return motivoDevolucion; }
    public void setMotivoDevolucion(String motivoDevolucion) { this.motivoDevolucion = motivoDevolucion; }
    public String getDetalleDevolucion() { return detalleDevolucion; }
    public void setDetalleDevolucion(String detalleDevolucion) { this.detalleDevolucion = detalleDevolucion; }
    public Long getIdPedidoAEntregar() { return idPedidoAEntregar; }
    public void setIdPedidoAEntregar(Long idPedidoAEntregar) { this.idPedidoAEntregar = idPedidoAEntregar; }
    public Part getFotoRemito() { return fotoRemito; }
    public void setFotoRemito(Part fotoRemito) { this.fotoRemito = fotoRemito; }
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
}
