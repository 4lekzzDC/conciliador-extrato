package com.axis.conciliacao.model;

import java.util.ArrayList;
import java.util.List;

public class ResultadoConciliacao {

    private List<Lancamento> somenteNoRazao;
    private List<Lancamento> somenteNoExtrato;
    private List<Lancamento> conciliados;
    private List<Lancamento> divergencias;

    public ResultadoConciliacao() {
        this.somenteNoRazao = new ArrayList<>();
        this.somenteNoExtrato = new ArrayList<>();
        this.conciliados = new ArrayList<>();
        this.divergencias = new ArrayList<>();
    }

    public ResultadoConciliacao(List<Lancamento> somenteNoRazao,
                                List<Lancamento> somenteNoExtrato,
                                List<Lancamento> conciliados,
                                List<Lancamento> divergencias) {
        this.somenteNoRazao   = nonNull(somenteNoRazao);
        this.somenteNoExtrato = nonNull(somenteNoExtrato);
        this.conciliados      = nonNull(conciliados);
        this.divergencias     = nonNull(divergencias);
    }

    private static <T> List<T> nonNull(List<T> in) {
        return in == null ? new ArrayList<>() : in;
    }

    public List<Lancamento> getSomenteNoRazao() {
        return somenteNoRazao;
    }

    public void setSomenteNoRazao(List<Lancamento> somenteNoRazao) {
        this.somenteNoRazao = nonNull(somenteNoRazao);
    }

    public List<Lancamento> getSomenteNoExtrato() {
        return somenteNoExtrato;
    }

    public void setSomenteNoExtrato(List<Lancamento> somenteNoExtrato) {
        this.somenteNoExtrato = nonNull(somenteNoExtrato);
    }

    public List<Lancamento> getConciliados() {
        return conciliados;
    }

    public void setConciliados(List<Lancamento> conciliados) {
        this.conciliados = nonNull(conciliados);
    }

    public List<Lancamento> getDivergencias() {
        return divergencias;
    }

    public void setDivergencias(List<Lancamento> divergencias) {
        this.divergencias = nonNull(divergencias);
    }
}
