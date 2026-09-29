package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.inventario.dto.DatosDepositoDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.ValidacionException;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class DepositoBean implements Serializable {

    // Contrato de lectura/escritura de depósitos e ítems (IConsultaStock
    // agrupa ambos, ver esa interfaz): esta pantalla solo necesita la parte
    // de depósitos.
    @Inject
    private IConsultaStock service;

    private List<DepositoDTO> depositos;
    private DatosDepositoDTO nuevoDeposito = new DatosDepositoDTO();

    // @PostConstruct: corre una sola vez al crear el Bean, así la tabla ya
    // llega llena en el primer render de depositos.xhtml.
    @PostConstruct
    public void cargar() {
        depositos = service.listarDepositos();
    }

    // Alta de un depósito nuevo a partir de nuevoDeposito (bindeado al
    // formulario). No pide comercio ni punto de picking: un depósito es
    // infraestructura propia de Rabbit (ver clase Deposito).
    public void registrar() {
        try {
            service.registrarDeposito(nuevoDeposito);
            Mensajes.info("Depósito registrado correctamente");
            nuevoDeposito = new DatosDepositoDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public List<DepositoDTO> getDepositos() { return depositos; }
    public DatosDepositoDTO getNuevoDeposito() { return nuevoDeposito; }
    public void setNuevoDeposito(DatosDepositoDTO nuevoDeposito) { this.nuevoDeposito = nuevoDeposito; }
}
