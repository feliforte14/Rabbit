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
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.dto.EntregaFallidaDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.IntentosDeEntrega;
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

    // Motivos fijos (ADR-041); con "Otro" hay que contarlo.
    private static final List<MotivoEntregaFallida> MOTIVOS_DEVOLUCION = List.of(MotivoEntregaFallida.values());

    private List<HojaDeRutaDTO> entregas;
    private List<HojaDeRutaDTO> historial;
    private RecorridoDTO recorrido;

    // Ventana "Entregar pedido": una sola para todas las entregas en camino
    // (h:inputFile no funciona dentro de ui:repeat); el botón de cada
    // tarjeta le pone el pedido.
    private Long idPedidoAEntregar;
    private transient Part fotoRemito;

    // Ventana "No se pudo entregar", mismo esquema. Con la evidencia de la
    // visita (ADR-045): comentario, hasta 3 fotos y la ubicación que tomó
    // el celular al abrir la ventana (entrega-fallida.js).
    private Long idPedidoADevolver;
    private MotivoEntregaFallida motivoDevolucion;
    private String detalleDevolucion;
    private transient Part foto1;
    private transient Part foto2;
    private transient Part foto3;
    private Double latitudVisita;
    private Double longitudVisita;
    private Double precisionVisita;

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

    // No lo pudo entregar: vuelve al depósito y se le asigna a otro
    // (ADR-037), o, si se agotaron las visitas, vuelve al comercio (ADR-041).
    // Queda registrada la visita con su evidencia (ADR-045).
    public void devolver() {
        Long idPedido = idPedidoADevolver;
        EntregaFallidaDTO datos = new EntregaFallidaDTO(motivoDevolucion, detalleDevolucion);
        datos.latitud = latitudVisita;
        datos.longitud = longitudVisita;
        datos.precisionMetros = precisionVisita;
        try {
            for (Part foto : new Part[] { foto1, foto2, foto3 }) {
                byte[] leida = ArchivosSubidos.leer(foto, TAMANO_MAXIMO_FOTO);
                if (leida == null) {
                    Mensajes.error("Cada foto puede pesar hasta 5 MB");
                    return;
                }
                datos.fotos.add(leida);
            }
        } catch (IOException e) {
            Mensajes.error("No se pudieron leer las fotos. Probá sacarlas de nuevo.");
            return;
        } finally {
            foto1 = null;
            foto2 = null;
            foto3 = null;
        }
        if (idPedido == null) {
            Mensajes.error("Elegí qué pedido no pudiste entregar");
            return;
        }
        if (datos.motivo == null) {
            Mensajes.error("Elegí por qué no se pudo entregar");
            return;
        }
        if (ejecutar(() -> gestion.devolverADeposito(idPedido, datos),
                "Pedido " + idPedido + " registrado como no entregado. Llevalo de vuelta adonde lo retiraste:"
                        + " Rabbit le avisa al comercio y al cliente.")) {
            idPedidoADevolver = null;
            motivoDevolucion = null;
            detalleDevolucion = null;
            latitudVisita = null;
            longitudVisita = null;
            precisionVisita = null;
        }
    }

    /** @return si salió bien */
    private boolean ejecutar(Runnable accion, String exito) {
        try {
            accion.run();
            Mensajes.info(exito);
            cargar();
            return true;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo registrar el cambio. Intentá de nuevo.");
        }
        return false;
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
    public List<MotivoEntregaFallida> getMotivosDevolucion() { return MOTIVOS_DEVOLUCION; }
    /** Visitas antes de devolver el pedido al comercio (ADR-041). */
    public int getMaximoIntentos() { return IntentosDeEntrega.maximo(); }
    public Long getIdPedidoADevolver() { return idPedidoADevolver; }
    public void setIdPedidoADevolver(Long idPedidoADevolver) { this.idPedidoADevolver = idPedidoADevolver; }
    public MotivoEntregaFallida getMotivoDevolucion() { return motivoDevolucion; }
    public void setMotivoDevolucion(MotivoEntregaFallida motivoDevolucion) { this.motivoDevolucion = motivoDevolucion; }
    public String getDetalleDevolucion() { return detalleDevolucion; }
    public void setDetalleDevolucion(String detalleDevolucion) { this.detalleDevolucion = detalleDevolucion; }
    public Part getFoto1() { return foto1; }
    public void setFoto1(Part foto1) { this.foto1 = foto1; }
    public Part getFoto2() { return foto2; }
    public void setFoto2(Part foto2) { this.foto2 = foto2; }
    public Part getFoto3() { return foto3; }
    public void setFoto3(Part foto3) { this.foto3 = foto3; }
    public Double getLatitudVisita() { return latitudVisita; }
    public void setLatitudVisita(Double latitudVisita) { this.latitudVisita = latitudVisita; }
    public Double getLongitudVisita() { return longitudVisita; }
    public void setLongitudVisita(Double longitudVisita) { this.longitudVisita = longitudVisita; }
    public Double getPrecisionVisita() { return precisionVisita; }
    public void setPrecisionVisita(Double precisionVisita) { this.precisionVisita = precisionVisita; }
    public Long getIdPedidoAEntregar() { return idPedidoAEntregar; }
    public void setIdPedidoAEntregar(Long idPedidoAEntregar) { this.idPedidoAEntregar = idPedidoAEntregar; }
    public Part getFotoRemito() { return fotoRemito; }
    public void setFotoRemito(Part fotoRemito) { this.fotoRemito = fotoRemito; }
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
}
