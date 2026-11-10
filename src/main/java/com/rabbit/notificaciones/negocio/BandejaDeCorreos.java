package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — bandeja de salida (outbox) de los mails de aviso.
 *
 * POR QUÉ EXISTE
 * Antes, AvisosPorMail mandaba el mail después del commit y, si el servidor de correo estaba caído o lento, lo
 * perdía (solo un warning en el log). El aviso quedaba en el portal, pero el cliente final no se enteraba.
 *
 * Ahora el mail se guarda junto con el aviso (registrar, que se suma a su transacción) y el envío es aparte
 * (EnvioDeCorreosPendientes): lo que no salió queda pendiente y se reintenta. Es la misma idea que la bandeja de
 * los cambios de estado (BandejaDeEstados), con un límite de vigencia: pasadas 24 horas un mail ya no sirve.
 *
 * Cada método de lectura y de marca abre su propia transacción (REQUIRES_NEW): quien envía corre sin
 * transacción y no debe mezclarse con una ajena.
 */

import com.rabbit.notificaciones.datos.CorreoPendienteRepository;
import com.rabbit.notificaciones.datos.model.CorreoPendiente;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

@Stateless
public class BandejaDeCorreos {

    /** Lo que hace falta para armar el mail: el destino y lo que se le muestra. */
    public record Correo(Long idPedido, String destino, String titulo, String texto, String enlace,
                         String codigoSeguimiento, String codigoEntrega) {
    }

    /** Un mail pendiente, sin la entidad (viaja fuera de la transacción). */
    public record Pendiente(Long id, Correo correo) {
    }

    @Inject
    private CorreoPendienteRepository repository;

    /** Se suma a la transacción del aviso: si ésta se deshace, el mail también. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void registrar(Correo correo) {
        CorreoPendiente fila = new CorreoPendiente();
        fila.setIdPedido(correo.idPedido());
        fila.setDestino(correo.destino());
        fila.setTitulo(recortar(correo.titulo(), 200));
        fila.setTexto(recortar(correo.texto(), 4000));
        fila.setEnlace(recortar(correo.enlace(), 500));
        fila.setCodigoSeguimiento(recortar(correo.codigoSeguimiento(), 30));
        fila.setCodigoEntrega(recortar(correo.codigoEntrega(), 10));
        fila.setCreadoEn(LocalDateTime.now());
        repository.guardar(fila);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<Pendiente> pendientes(int maximo) {
        return repository.listarPendientes(maximo).stream()
                .map(f -> new Pendiente(f.getId(), new Correo(f.getIdPedido(), f.getDestino(), f.getTitulo(),
                        f.getTexto(), f.getEnlace(), f.getCodigoSeguimiento(), f.getCodigoEntrega())))
                .toList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marcarEnviado(Long id) {
        repository.marcarEnviado(id, LocalDateTime.now());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarFallo(Long id) {
        repository.sumarIntentoFallido(id);
    }

    /** Lo saca de la cola sin mandarlo (el servidor rechazó el destinatario de forma definitiva). */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void descartar(Long id, String motivo) {
        repository.descartar(id, LocalDateTime.now(), motivo);
    }

    /** Descarta lo que lleva demasiado esperando: devuelve cuántos. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int descartarVencidos(LocalDateTime creadosAntesDe) {
        return repository.descartarPendientesCreadosAntesDe(creadosAntesDe, LocalDateTime.now(),
                "No se pudo mandar a tiempo");
    }

    /** Borra los enviados y los descartados viejos: devuelve cuántos. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int limpiar(LocalDateTime enviadosAntesDe, LocalDateTime descartadosAntesDe) {
        return repository.borrarResueltosAntesDe(CorreoPendiente.ENVIADO, enviadosAntesDe)
                + repository.borrarResueltosAntesDe(CorreoPendiente.DESCARTADO, descartadosAntesDe);
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }
}
