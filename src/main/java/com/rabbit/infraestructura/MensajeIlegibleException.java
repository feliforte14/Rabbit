package com.rabbit.infraestructura;

/**
 * Un consumidor JMS no pudo interpretar un mensaje (no es TextMessage, el JSON
 * está roto, falta un campo). Se relanza para que la entrega se deshaga: el
 * broker la reintenta unas veces y, si sigue fallando, el mensaje pasa a la
 * cola de mensajes muertos (DLQ) en vez de perderse. En la DLQ queda guardado
 * con su contenido, y se puede inspeccionar y reprocesar (docs/integraciones/
 * MENSAJERIA-ASINCRONICA.md).
 */
public class MensajeIlegibleException extends RuntimeException {

    public MensajeIlegibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public MensajeIlegibleException(String mensaje) {
        super(mensaje);
    }
}
