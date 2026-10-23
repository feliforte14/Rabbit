package com.rabbit.pedidos.negocio;

/**
 * Cuántos bultos (cajas, paquetes) forman un pedido: es la cantidad de etiquetas con QR que se
 * imprimen (ADR-056). No es la cantidad de unidades de producto: un pedido de 40 remeras
 * puede ir en 2 cajas. Si el ERP no lo informa, es 1.
 */
public final class Bultos {

    public static final int MAXIMO = 99;

    private Bultos() {
    }

    /** @return la cantidad informada, o 1 si no vino @throws ValidacionException si no está entre 1 y 99 */
    public static int validar(Integer informados) {
        if (informados == null) {
            return 1;
        }
        if (informados < 1 || informados > MAXIMO) {
            throw new ValidacionException("La cantidad de bultos tiene que estar entre 1 y " + MAXIMO);
        }
        return informados;
    }
}
