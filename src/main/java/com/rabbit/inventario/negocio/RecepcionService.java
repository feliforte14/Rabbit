package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — órdenes de recepción de mercadería (EJB @Stateless, componente Inventario, ADR-058).
 *
 * El comercio anuncia lo que va a mandar; el depósito registra lo que llegó. Recién al registrar la
 * recepción se toca el stock: se suma lo que llegó, no lo que se anunció. Si el comercio ya tenía ese
 * producto en ese depósito se le suma a esa fila (mismo criterio que existeProductoEnDeposito: una fila
 * por producto, depósito y comercio); si no, se crea.
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.EstadoOrdenDeRecepcion;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.LineaDeRecepcion;
import com.rabbit.inventario.datos.model.OrdenDeRecepcion;
import com.rabbit.inventario.dto.DatosLineaRecepcionDTO;
import com.rabbit.inventario.dto.DatosOrdenDeRecepcionDTO;
import com.rabbit.inventario.dto.LineaRecibidaDTO;
import com.rabbit.inventario.dto.OrdenDeRecepcionDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@PermitAll
public class RecepcionService implements IRecepcionDeMercaderia {

    private static final Logger LOG = Logger.getLogger(RecepcionService.class.getName());

    @Inject
    private InventarioRepository repository;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IContextoUsuario contextoUsuario;

    @Resource
    private SessionContext contexto;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("COMERCIO")
    public Long anunciar(DatosOrdenDeRecepcionDTO datos) {
        Long idComercio = contextoUsuario.idComercioActual();
        if (datos == null || datos.idDeposito == null) {
            throw new ValidacionException("Elegí a qué depósito vas a mandar la mercadería");
        }
        Deposito deposito = repository.buscarDepositoPorId(datos.idDeposito);
        if (deposito == null) {
            throw new ValidacionException("Depósito no encontrado: " + datos.idDeposito);
        }
        if (!comercios.validarComercioActivo(idComercio)) {
            throw new ValidacionException("El comercio no existe o está dado de baja: no puede anunciar mercadería.");
        }
        List<DatosLineaRecepcionDTO> lineas = ReglaDeRecepcion.validarAnuncio(datos.lineas);
        // Lo que no entra ni en la categoría más grande no se acepta en consignación (ADR-059); las medidas
        // todavía no se conocen, así que acá solo se puede juzgar por el peso.
        for (DatosLineaRecepcionDTO l : lineas) {
            if (CategoriaDeTamano.de(l.pesoKg, null, null, null) == CategoriaDeTamano.FUERA_DE_RANGO) {
                throw new ValidacionException("\"" + l.producto + "\" pesa más de lo que se acepta en consignación: se cotiza aparte");
            }
        }
        String notas = ReglaDeRecepcion.limpiarNotas(datos.notas);

        OrdenDeRecepcion orden = new OrdenDeRecepcion();
        orden.setIdComercio(idComercio);
        orden.setDeposito(deposito);
        orden.setNotas(notas);
        orden.setFechaAnuncio(LocalDateTime.now());
        for (DatosLineaRecepcionDTO l : lineas) {
            LineaDeRecepcion linea = new LineaDeRecepcion();
            linea.setProducto(l.producto);
            linea.setCantidadEsperada(l.cantidad);
            linea.setPesoKg(l.pesoKg);
            orden.agregarLinea(linea);
        }
        Long id = repository.guardarOrden(orden).getId();
        LOG.info("[Inventario] Comercio " + idComercio + " anunció la orden de recepción " + id
                + " (" + lineas.size() + " producto(s)) para el depósito " + deposito.getId());
        return id;
    }

    @Override
    @RolesAllowed("COMERCIO")
    public List<OrdenDeRecepcionDTO> listarDelComercioActual() {
        return repository.listarOrdenesDeComercio(contextoUsuario.idComercioActual()).stream()
                .map(OrdenDeRecepcionDTO::desde).collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"COMERCIO", "ADMINISTRADOR", "OPERADOR"})
    public void cancelar(Long idOrden) {
        OrdenDeRecepcion orden = ordenParaActualizarOFallar(idOrden);
        // Un comercio solo cancela lo suyo; se responde igual que si no existiera.
        if (contexto.isCallerInRole("COMERCIO") && !orden.getIdComercio().equals(contextoUsuario.idComercioActual())) {
            throw new ValidacionException("Orden de recepción no encontrada: " + idOrden);
        }
        exigirAnunciada(orden, "cancelar");
        orden.setEstado(EstadoOrdenDeRecepcion.CANCELADA);
        LOG.info("[Inventario] Orden de recepción " + idOrden + " cancelada");
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<OrdenDeRecepcionDTO> listarTodas() {
        return repository.listarOrdenes().stream().map(OrdenDeRecepcionDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public OrdenDeRecepcionDTO obtener(Long idOrden) {
        OrdenDeRecepcion orden = repository.buscarOrdenPorId(idOrden);
        if (orden == null) {
            throw new ValidacionException("Orden de recepción no encontrada: " + idOrden);
        }
        return OrdenDeRecepcionDTO.desde(orden);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public OrdenDeRecepcionDTO registrarRecepcion(Long idOrden, List<LineaRecibidaDTO> recibidas) {
        OrdenDeRecepcion orden = ordenParaActualizarOFallar(idOrden);
        exigirAnunciada(orden, "registrar la recepción de");

        // Lo informado, por línea de la orden; una línea que no es de esta orden es un error, no se ignora.
        Map<Long, Integer> porLinea = new HashMap<>();
        if (recibidas != null) {
            for (LineaRecibidaDTO r : recibidas) {
                if (r == null || r.idLinea == null || porLinea.put(r.idLinea, r.cantidadRecibida) != null) {
                    throw new ValidacionException("La recepción tiene líneas repetidas o inválidas");
                }
            }
        }
        List<Integer> esperadas = new ArrayList<>();
        List<Integer> informadas = new ArrayList<>();
        for (LineaDeRecepcion l : orden.getLineas()) {
            esperadas.add(l.getCantidadEsperada());
            informadas.add(porLinea.remove(l.getId()));
        }
        if (!porLinea.isEmpty()) {
            throw new ValidacionException("La recepción incluye líneas que no son de esta orden");
        }
        ReglaDeRecepcion.Resultado resultado = ReglaDeRecepcion.resolver(esperadas, informadas);

        // Se suma al stock solo lo que llegó.
        Long idDeposito = orden.getDeposito().getId();
        for (int i = 0; i < orden.getLineas().size(); i++) {
            LineaDeRecepcion linea = orden.getLineas().get(i);
            int llegaron = informadas.get(i);
            linea.setCantidadRecibida(llegaron);
            if (llegaron > 0) {
                sumarAlStock(orden, linea, idDeposito, llegaron);
            }
        }
        orden.setEstado(resultado.estado());
        orden.setFechaRecepcion(LocalDateTime.now());
        orden.setRecibidaPor(contexto.getCallerPrincipal().getName());
        try {
            repository.sincronizar();
        } catch (OptimisticLockException e) {
            // Alguien reservó o confirmó de este stock mientras se registraba: se vuelve a intentar.
            throw new ValidacionException("El stock cambió mientras se registraba la recepción. Volvé a intentarlo.");
        }
        LOG.info("[Inventario] Recepción de la orden " + idOrden + ": " + resultado.estado() + " ("
                + resultado.totalRecibido() + " de " + resultado.totalEsperado() + " unidades)");
        return OrdenDeRecepcionDTO.desde(orden);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public long contarAnunciadas() {
        return repository.contarOrdenes(EstadoOrdenDeRecepcion.ANUNCIADA, null);
    }

    private void sumarAlStock(OrdenDeRecepcion orden, LineaDeRecepcion linea, Long idDeposito, int llegaron) {
        ItemInventario existente = repository.buscarItemDelComercio(linea.getProducto(), idDeposito, orden.getIdComercio());
        if (existente != null) {
            existente.setCantidadDisponible(existente.getCantidadDisponible() + llegaron);
            repository.actualizarItem(existente);
            return;
        }
        ItemInventario item = new ItemInventario();
        item.setProducto(linea.getProducto());
        item.setCantidadDisponible(llegaron);
        item.setCantidadReservada(0);
        item.setDeposito(orden.getDeposito());
        item.setIdComercio(orden.getIdComercio());
        item.setPesoKg(linea.getPesoKg());
        repository.guardarItem(item);
    }

    private OrdenDeRecepcion ordenParaActualizarOFallar(Long idOrden) {
        OrdenDeRecepcion orden = repository.buscarOrdenParaActualizar(idOrden);
        if (orden == null) {
            throw new ValidacionException("Orden de recepción no encontrada: " + idOrden);
        }
        return orden;
    }

    private static void exigirAnunciada(OrdenDeRecepcion orden, String accion) {
        if (orden.getEstado() != EstadoOrdenDeRecepcion.ANUNCIADA) {
            throw new ValidacionException("No se puede " + accion + " la orden " + orden.getId()
                    + ": está " + orden.getEstado() + " y solo se opera sobre las ANUNCIADAS");
        }
    }
}
