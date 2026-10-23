package com.rabbit.pedidos.presentacion.rest;

/**
 * Cuerpo de POST /api/v1/pedidos-externos: el contrato público de la API
 * (ver docs/integraciones/openapi.yaml), separado de DatosPedidoExternoDTO, que es el
 * DTO del formulario JSF.
 *
 * Separarlos permite dos cosas: que la API no tenga idComercio (el
 * comercio sale de la cuenta del ERP, ver PedidoService) y que lleve las
 * reglas de formato de Bean Validation sin que JSF las aplique también a
 * la pantalla. Las reglas de negocio siguen en PedidoService, que valida
 * igual: esto solo corta antes, con un 400 que dice qué campo está mal.
 */

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@PedidoExternoRequest.ConDireccionSiEsADomicilio
public class PedidoExternoRequest {

    public OrigenPedido origen = OrigenPedido.STOCK_CONSIGNADO;

    // Obligatorio con origen PUNTO_PICKING (lo valida PedidoService).
    public Long idPuntoPicking;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Size(max = 100, message = "Un pedido admite hasta 100 líneas")
    public List<@Valid @NotNull(message = "Una línea del pedido está vacía") Linea> lineas = new ArrayList<>();

    @NotNull(message = "Falta el importe del pedido")
    @DecimalMin(value = "0.01", message = "El importe del pedido debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El importe admite hasta 10 enteros y 2 decimales")
    public BigDecimal importe;

    @NotNull(message = "Falta el medio de pago (PREPAGO o CONTRA_ENTREGA)")
    public MedioPago medioPago;

    // Cómo lo recibe quien compró (ADR-053). Si no viene, DOMICILIO (como siempre).
    // RETIRO_EN_DEPOSITO: sin dirección, lo retira en el depósito mostrando el código.
    public TipoEntrega tipoEntrega;

    // Cuántas cajas o paquetes forman el pedido: una etiqueta con QR por bulto (ADR-056). Por defecto, 1.
    @Min(value = 1, message = "La cantidad de bultos tiene que ser al menos 1")
    @Max(value = 99, message = "Un pedido admite hasta 99 bultos")
    public Integer bultos;

    // Obligatoria a domicilio (ver ConDireccionSiEsADomicilio); en un retiro se ignora.
    @Size(max = 200, message = "La dirección de entrega no puede superar los 200 caracteres")
    public String direccionEntrega;

    // Opcional: si no viene, se busca en la dirección.
    @Size(max = 8, message = "El código postal tiene 4 dígitos o es un CPA de 8 caracteres")
    public String codigoPostalEntrega;

    // Solo PUNTO_PICKING, opcional: kilos totales del pedido. Con stock
    // consignado lo calcula Rabbit con el peso de los ítems.
    @DecimalMin(value = "0.001", message = "El peso del pedido debe ser mayor a cero")
    @Digits(integer = 7, fraction = 3, message = "El peso admite hasta 7 enteros y 3 decimales")
    public BigDecimal pesoKg;

    // Quién recibe (ADR-039). El email es obligatorio desde el 18/10/2026:
    // es por donde Rabbit le avisa al cliente cada novedad. Nombre y
    // teléfono siguen opcionales.
    @Size(max = 100, message = "El nombre de quien recibe no puede superar los 100 caracteres")
    public String nombreDestinatario;

    @Size(max = 30, message = "El teléfono de quien recibe no puede superar los 30 caracteres")
    public String telefonoDestinatario;

    @NotBlank(message = "Falta el email de quien recibe: es obligatorio porque Rabbit le avisa por mail cada novedad del pedido")
    @Email(message = "El email de quien recibe no es válido")
    @Size(max = 120, message = "El email de quien recibe no puede superar los 120 caracteres")
    public String emailDestinatario;

    /** A domicilio la dirección es obligatoria; en un retiro en depósito no hace falta. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = DireccionSiEsADomicilioValidator.class)
    public @interface ConDireccionSiEsADomicilio {
        String message() default "Falta la dirección de entrega";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class DireccionSiEsADomicilioValidator
            implements ConstraintValidator<ConDireccionSiEsADomicilio, PedidoExternoRequest> {
        @Override
        public boolean isValid(PedidoExternoRequest pedido, ConstraintValidatorContext contexto) {
            if (pedido == null || pedido.tipoEntrega == TipoEntrega.RETIRO_EN_DEPOSITO
                    || (pedido.direccionEntrega != null && !pedido.direccionEntrega.isBlank())) {
                return true;
            }
            // El error se informa en el campo (direccionEntrega), no en el pedido entero.
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("direccionEntrega").addConstraintViolation();
            return false;
        }
    }

    public static class Linea {
        // STOCK_CONSIGNADO: el ítem consignado en Rabbit.
        public Long idItem;

        // PUNTO_PICKING: qué se retira (descripción libre).
        @Size(max = 200, message = "La descripción del producto no puede superar los 200 caracteres")
        public String producto;

        @Positive(message = "La cantidad debe ser mayor a cero")
        public int cantidad;
    }

    /**
     * Saca los espacios de más de los datos de quien recibe antes de validar
     * el formato: " ana@correo.com " es el mismo email (el negocio también lo
     * normaliza, ver Destinatarios) y no tiene que dar 400. Vacío = no vino.
     */
    void normalizar() {
        nombreDestinatario = limpiar(nombreDestinatario);
        telefonoDestinatario = limpiar(telefonoDestinatario);
        emailDestinatario = limpiar(emailDestinatario);
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    DatosPedidoExternoDTO aDatos() {
        DatosPedidoExternoDTO datos = new DatosPedidoExternoDTO();
        datos.origen = origen;
        datos.idPuntoPicking = idPuntoPicking;
        datos.importe = importe;
        datos.medioPago = medioPago;
        datos.tipoEntrega = tipoEntrega;
        datos.bultos = bultos;
        datos.direccionEntrega = direccionEntrega;
        datos.codigoPostalEntrega = codigoPostalEntrega;
        datos.pesoKg = pesoKg;
        datos.nombreDestinatario = nombreDestinatario;
        datos.telefonoDestinatario = telefonoDestinatario;
        datos.emailDestinatario = emailDestinatario;
        for (Linea linea : lineas) {
            DatosLineaPedidoDTO dato = new DatosLineaPedidoDTO();
            dato.idItem = linea.idItem;
            dato.producto = linea.producto;
            dato.cantidad = linea.cantidad;
            datos.lineas.add(dato);
        }
        return datos;
    }
}
