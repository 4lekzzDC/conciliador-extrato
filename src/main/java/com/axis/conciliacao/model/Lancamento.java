package com.axis.conciliacao.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Lancamento {
    private LocalDate data;
    private BigDecimal valor;
    private String tipo;      // "RECEBIMENTO" | "PAGAMENTO"
    private String descricao; // histórico/descrição
    private String origem;    // "RAZAO" | "EXTRATO"

    // Usado pela UI para mostrar detalhes de divergência (tooltip)
    private String observacao;

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
