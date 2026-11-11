package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — inicio.xhtml: lo que el personal
 * tiene que mirar primero (ADR-054). No decide nada: cuenta lo que ya existe
 * (pedidos y liquidaciones) y arma una lista de lo que pide atención, cada
 * ítem con el enlace a la pantalla donde se resuelve.
 */

import com.rabbit.inventario.negocio.IRecepcionDeMercaderia;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pagos.negocio.ILiquidaciones;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Named
@ViewScoped
public class InicioBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(InicioBean.class.getName());

    /** Un renglón de "Requiere atención". nivel: "critico", "aviso" u "ok". */
    public record Atencion(String texto, String nivel, String destino) {
        public String getTexto() { return texto; }
        public String getNivel() { return nivel; }
        public String getDestino() { return destino; }
    }

    @Inject
    private ISeguimientoPedido pedidos;

    @Inject
    private ILiquidaciones liquidaciones;

    @Inject
    private IRecepcionDeMercaderia recepciones;

    private int porConfirmar;
    private int enEspera;
    private int enCamino;
    private int listosParaRetirar;
    private int devueltos;
    private int entregados;
    private int cobrosPorResolver;
    private final List<Atencion> atencion = new ArrayList<>();

    @PostConstruct
    public void iniciar() {
        for (PedidoDTO p : pedidos.listarTodos()) {
            switch (p.estado) {
                case "PENDIENTE" -> {
                    if (p.enEspera) {
                        enEspera++;
                    } else {
                        porConfirmar++;
                    }
                }
                case "CONFIRMADO" -> {
                    if (p.isRetiroEnDeposito()) {
                        listosParaRetirar++;
                    }
                }
                case "EN_CAMINO" -> enCamino++;
                case "DEVUELTO_AL_COMERCIO" -> devueltos++;
                case "ENTREGADO" -> entregados++;
                default -> { }
            }
        }
        // El mes anterior es el que se liquidó el día 1: si algo no se cobró, hay que verlo.
        try {
            for (LiquidacionDTO l : liquidaciones.listarDelPeriodo(YearMonth.now().minusMonths(1))) {
                if ("PENDIENTE".equals(l.estado) || "RECHAZADA".equals(l.estado)) {
                    cobrosPorResolver++;
                }
            }
        } catch (RuntimeException e) {
            LOG.warning("[Inicio] No se pudieron leer las liquidaciones: " + e.getMessage());
        }

        long recepcionesEsperadas = 0;
        try {
            recepcionesEsperadas = recepciones.contarAnunciadas();
        } catch (RuntimeException e) {
            LOG.warning("[Inicio] No se pudieron leer las recepciones: " + e.getMessage());
        }
        if (recepcionesEsperadas > 0) {
            atencion.add(new Atencion(recepcionesEsperadas + " orden(es) de recepción esperando la mercadería",
                    "aviso", "/personal/recepciones"));
        }
        if (enEspera > 0) {
            atencion.add(new Atencion(enEspera + " pedido(s) en lista de espera: nadie tiene lugar para llevarlos",
                    "critico", "/personal/pedidos"));
        }
        if (cobrosPorResolver > 0) {
            atencion.add(new Atencion(cobrosPorResolver + " liquidación(es) del mes pasado sin cobrar",
                    "critico", "/personal/liquidaciones"));
        }
        if (devueltos > 0) {
            atencion.add(new Atencion(devueltos + " pedido(s) devuelto(s) al comercio", "aviso", "/personal/pedidos"));
        }
        if (porConfirmar > 0) {
            atencion.add(new Atencion(porConfirmar + " pedido(s) esperando confirmación", "aviso", "/personal/pedidos"));
        }
        if (listosParaRetirar > 0) {
            atencion.add(new Atencion(listosParaRetirar + " retiro(s) en depósito esperando a quien los busque",
                    "aviso", "/personal/pedidos"));
        }
        if (atencion.isEmpty()) {
            atencion.add(new Atencion("Todo en orden: no hay nada pendiente de resolver", "ok", null));
        }
    }

    public int getPorConfirmar() { return porConfirmar; }
    public int getEnEspera() { return enEspera; }
    public int getEnCamino() { return enCamino; }
    public int getListosParaRetirar() { return listosParaRetirar; }
    public int getDevueltos() { return devueltos; }
    public int getEntregados() { return entregados; }
    public List<Atencion> getAtencion() { return atencion; }
}
