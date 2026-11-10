package com.rabbit.notificaciones.negocio;

/**
 * Canal de mail de las notificaciones: cada aviso al comercio, además de quedar en su portal, se le manda al
 * email que cargó al darse de alta. Y cada aviso a quien recibe el pedido (ADR-040), al email que mandó el ERP.
 * Cada mail lleva dos versiones, texto plano y HTML (MailHtml).
 *
 * DOS TIEMPOS, como la bandeja de estados (ADR-063):
 * 1. DENTRO de la transacción del aviso se guarda el mail en la bandeja (BandejaDeCorreos): si el aviso se
 *    deshace, el mail también, y no depende de que el servidor de correo esté arriba.
 * 2. DESPUÉS del commit (AFTER_SUCCESS) se manda lo pendiente (EnvioDeCorreosPendientes). Un servidor de correo
 *    caído o lento no puede deshacer ni frenar el aviso: el mail queda pendiente y se reintenta cada minuto,
 *    hasta 24 horas. Antes, un mail que fallaba solo dejaba un log y se perdía. Timeouts de 5 s.
 *
 * Se configura con system properties (como el banco y el circuit breaker), con login y TLS para un correo real
 * como Gmail (ADR-047): ver ConfiguracionSmtp. Sin servidor configurado no se registra ni se intenta: un entorno
 * de desarrollo sin correo funciona igual, con los avisos en el portal.
 */

import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Correo;
import com.rabbit.pedidos.negocio.EntregaModificada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.logging.Logger;

@ApplicationScoped
public class AvisosPorMail {

    private static final Logger LOG = Logger.getLogger(AvisosPorMail.class.getName());

    @Inject
    private BandejaDeCorreos bandeja;

    @Inject
    private EnvioDeCorreosPendientes envio;

    // ---- 1. Dentro de la transacción: se guarda el mail junto con el aviso ----

    // El email ya viene resuelto: después del commit no se puede consultar la base.
    public void registrarAviso(@Observes AvisoRegistrado aviso) {
        registrar(aviso.email(), aviso.idPedido(), "Pedido #" + aviso.idPedido(),
                aviso.texto() + ".\n\nEste aviso también está en tu portal de Rabbit.", null, null, null);
    }

    // ADR-040: el aviso al cliente final, mismo canal y mismas reglas.
    public void registrarAvisoDestinatario(@Observes AvisoDestinatarioRegistrado aviso) {
        registrar(aviso.email(), aviso.idPedido(), aviso.asunto(), aviso.texto(), aviso.enlace(),
                aviso.codigoSeguimiento(), aviso.codigoEntrega());
    }

    // Quien recibe cambió su entrega desde el seguimiento: se le muestra lo que quedó guardado. El evento ya
    // trae todo y no se registra en el historial de avisos porque no es un cambio de estado.
    public void registrarCambioDeEntrega(@Observes EntregaModificada cambio) {
        TextosAlDestinatario.CambioDeEntrega datos = new TextosAlDestinatario.CambioDeEntrega(cambio.nombre(),
                cambio.comercio(), cambio.codigoSeguimiento(),
                AvisoDestinatarioService.enlaceSeguimiento(cambio.codigoSeguimiento()), cambio.fechaPrometida(),
                LocalDate.now(), cambio.instrucciones(), cambio.nombreAutorizado());
        registrar(cambio.email(), cambio.idPedido(), TextosAlDestinatario.asuntoCambioDeEntrega(datos),
                TextosAlDestinatario.cuerpoCambioDeEntrega(datos), datos.enlace(), datos.codigo(), null);
    }

    private void registrar(String destino, Long idPedido, String titulo, String texto, String enlace,
                           String codigoSeguimiento, String codigoEntrega) {
        // Sin servidor de correo o sin email no hay nada que mandar (y nada que reintentar).
        if (destino == null || ConfiguracionSmtp.desde(System.getProperties()) == null) {
            return;
        }
        bandeja.registrar(new Correo(idPedido, destino, titulo, texto, enlace, codigoSeguimiento, codigoEntrega));
    }

    // ---- 2. Después del commit: se manda lo pendiente ----

    public void enviarTrasAviso(@Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoRegistrado aviso) {
        enviarPendientes();
    }

    public void enviarTrasAvisoDestinatario(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoDestinatarioRegistrado aviso) {
        enviarPendientes();
    }

    public void enviarTrasCambioDeEntrega(@Observes(during = TransactionPhase.AFTER_SUCCESS) EntregaModificada cambio) {
        enviarPendientes();
    }

    private void enviarPendientes() {
        try {
            envio.enviarPendientes();
        } catch (RuntimeException e) {
            // El aviso ya está guardado y el mail queda pendiente: el timer lo reintenta.
            LOG.warning("[Notificaciones][Mail] No se pudo intentar el envío ahora: " + e.getMessage());
        }
    }
}
