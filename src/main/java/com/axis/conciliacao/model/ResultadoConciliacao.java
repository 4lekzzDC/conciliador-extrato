package com.axis.conciliacao.model;

import java.util.ArrayList;
import java.util.List;

public class ResultadoConciliacao {

    private List<Lancamento> somenteNoRazao = new ArrayList<>();
    private List<Lancamento> somenteNoExtrato = new ArrayList<>();
    private List<Lancamento> conciliados = new ArrayList<>();
    private List<Lancamento> divergencias = new ArrayList<>();

    public List<Lancamento> getSomenteNoRazao() {
        return somenteNoRazao;
    }

    public void setSomenteNoRazao(List<Lancamento> somenteNoRazao) {
        this.somenteNoRazao = (somenteNoRazao != null) ? somenteNoRazao : new ArrayList<>();
    }

    public List<Lancamento> getSomenteNoExtrato() {
        return somenteNoExtrato;
    }

    public void setSomenteNoExtrato(List<Lancamento> somenteNoExtrato) {
        this.somenteNoExtrato = (somenteNoExtrato != null) ? somenteNoExtrato : new ArrayList<>();
    }

    public List<Lancamento> getConciliados() {
        return conciliados;
    }

    public void setConciliados(List<Lancamento> conciliados) {
        this.conciliados = (conciliados != null) ? conciliados : new ArrayList<>();
    }

    public List<Lancamento> getDivergencias() {
        return divergencias;
    }

    public void setDivergencias(List<Lancamento> divergencias) {
        this.divergencias = (divergencias != null) ? divergencias : new ArrayList<>();
    }
}
