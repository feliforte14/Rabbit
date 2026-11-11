package com.rabbit.notificaciones.datos.model;

/**
 * Entidad JPA de la tabla "correos_pendientes": la bandeja de salida (outbox) de los mails de aviso.
 *
 * El mail se guarda en la MISMA transacción que el aviso: si el aviso se deshace, el mail también. Después un
 * envío aparte lo manda y lo marca ENVIADO. Si el servidor de correo está caído, queda PENDIENTE y se reintenta
 * cada minuto. Pasadas 24 horas sin poder mandarlo se DESCARTA: un "llega hoy" que llega al otro día confunde.
 * Un destinatario que el servidor rechaza de forma definitiva (dirección inexistente) se descarta en el acto.
 *
 * Guarda el contenido del mail (email, texto, enlace y códigos), que son datos personales: los ENVIADOS se
 * borran al día siguiente y los DESCARTADOS a los 7 días. Nunca se loguean completos.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "correos_pendientes",
        indexes = @Index(name = "ix_correos_pendientes_estado", columnList = "estado"))
public class CorreoPendiente {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String DESCARTADO = "DESCARTADO";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idPedido;

    @Column(length = 254)
    private String destino;

    @Column(length = 200)
    private String titulo;

    @Column(length = 4000)
    private String texto;

    @Column(length = 500)
    private String enlace;

    @Column(length = 30)
    private String codigoSeguimiento;

    @Column(length = 10)
    private String codigoEntrega;

    @Column(length = 12)
    private String estado = PENDIENTE;

    private LocalDateTime creadoEn;

    // Cuándo se mandó o se descartó. Nulo mientras está pendiente.
    private LocalDateTime resueltoEn;

    // Envíos que fallaron (servidor caído, por ejemplo).
    private int intentosFallidos;

    // Por qué se descartó.
    @Column(length = 200)
    private String motivo;

    public Long getId() { return id; }

    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public String getEnlace() { return enlace; }
    public void setEnlace(String enlace) { this.enlace = enlace; }

    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public void setCodigoSeguimiento(String codigoSeguimiento) { this.codigoSeguimiento = codigoSeguimiento; }

    public String getCodigoEntrega() { return codigoEntrega; }
    public void setCodigoEntrega(String codigoEntrega) { this.codigoEntrega = codigoEntrega; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getResueltoEn() { return resueltoEn; }
    public void setResueltoEn(LocalDateTime resueltoEn) { this.resueltoEn = resueltoEn; }

    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
