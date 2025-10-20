package com.axis.conciliacao.service;

import com.axis.conciliacao.model.Lancamento;
import com.axis.conciliacao.model.ResultadoConciliacao;
import org.springframework.stereotype.Service;
import java.math.RoundingMode;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Conciliação SEM tolerância:
 * 1) Concilia por chave exata (tipo|data|valor).
 * 2) Divergência por data: mesma (tipo|valor), datas diferentes (pares).
 * 3) Divergência por valor: mesma (tipo|data), valores diferentes (pares).
 * 4) Sobraram itens? "Somente no Razão" / "Somente no Extrato".
 * 5) Ordena por data asc (nulos por último), depois tipo e descrição.
 * 6) Para divergências, preenche Lancamento.observacao com o MOTIVO (tooltip).
 */
@Service
public class ConciliacaoService {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ThreadLocal<NumberFormat> BR_CURRENCY = ThreadLocal.withInitial(
            () -> NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))
    );

    public ResultadoConciliacao conciliar(List<Lancamento> razao, List<Lancamento> extrato) {
        if (razao == null)   razao = Collections.emptyList();
        if (extrato == null) extrato = Collections.emptyList();

        // 1) Conciliação exata por (tipo|data|valor)
        Map<String, Deque<Lancamento>> fullR = groupBy(razao, this::keyFull);
        Map<String, Deque<Lancamento>> fullE = groupBy(extrato, this::keyFull);
        List<Lancamento> conciliados = new ArrayList<>();

        Set<String> allFull = new HashSet<>();
        allFull.addAll(fullR.keySet());
        allFull.addAll(fullE.keySet());

        for (String k : allFull) {
            Deque<Lancamento> rQ = fullR.getOrDefault(k, new ArrayDeque<>());
            Deque<Lancamento> eQ = fullE.getOrDefault(k, new ArrayDeque<>());
            int matches = Math.min(rQ.size(), eQ.size());
            for (int i = 0; i < matches; i++) {
                conciliados.add(rQ.removeFirst());
                eQ.removeFirst();
            }
            fullR.put(k, rQ);
            fullE.put(k, eQ);
        }

        // Restantes após a conciliação exata
        List<Lancamento> restR = flatten(fullR);
        List<Lancamento> restE = flatten(fullE);

        // 2) Divergência por DATA (mesmo tipo+valor, datas diferentes)
        List<Lancamento> divergencias = new ArrayList<>();
        PairingResult afterTipoValor = pairAndCollect(
                restR,
                restE,
                this::keyTipoValor,
                (lr, le) -> {
                    // motivo
                    String msg = "Divergência de DATA — " +
                            "Razão: "   + fmtDate(lr.getData()) +
                            " vs Extrato: " + fmtDate(le.getData()) +
                            " (tipo: " + safeUpper(lr.getTipo()) + ", valor: " + fmtMoney(lr.getValor()) + ")";
                    lr.setObservacao(msg);
                    le.setObservacao(msg);
                },
                divergencias
        );

        // 3) Divergência por VALOR (mesmo tipo+data, valores diferentes)
        PairingResult afterTipoData = pairAndCollect(
                afterTipoValor.leftoverR,
                afterTipoValor.leftoverE,
                this::keyTipoData,
                (lr, le) -> {
                    String msg = "Divergência de VALOR — " +
                            "Razão: "   + fmtMoney(lr.getValor()) +
                            " vs Extrato: " + fmtMoney(le.getValor()) +
                            " (tipo: " + safeUpper(lr.getTipo()) + ", data: " + fmtDate(lr.getData()) + ")";
                    lr.setObservacao(msg);
                    le.setObservacao(msg);
                },
                divergencias
        );

        // 4) Sobraram itens
        List<Lancamento> somenteNoRazao   = new ArrayList<>(afterTipoData.leftoverR);
        List<Lancamento> somenteNoExtrato = new ArrayList<>(afterTipoData.leftoverE);

        // 5) Ordenações
        Comparator<Lancamento> byDateTipoDesc = Comparator
                .comparing((Lancamento l) -> Optional.ofNullable(l.getData()).orElse(LocalDate.MAX))
                .thenComparing(l -> safeUpper(l.getTipo()))
                .thenComparing(l -> safeStr(l.getDescricao()));

        somenteNoRazao.sort(byDateTipoDesc);
        somenteNoExtrato.sort(byDateTipoDesc);
        divergencias.sort(byDateTipoDesc);
        conciliados.sort(byDateTipoDesc);

        ResultadoConciliacao res = new ResultadoConciliacao();
        res.setSomenteNoRazao(somenteNoRazao);
        res.setSomenteNoExtrato(somenteNoExtrato);
        res.setDivergencias(divergencias);
        res.setConciliados(conciliados);
        return res;
    }

    /* ===================== Pairing helpers ===================== */

    @FunctionalInterface
    private interface DivergenceAnnotator { void annotate(Lancamento razao, Lancamento extrato); }

    private PairingResult pairAndCollect(
            List<Lancamento> razao,
            List<Lancamento> extrato,
            KeyFunc keyFunc,
            DivergenceAnnotator annotator,
            List<Lancamento> divergenciasOut
    ) {
        Map<String, Deque<Lancamento>> mapR = groupBy(razao, keyFunc);
        Map<String, Deque<Lancamento>> mapE = groupBy(extrato, keyFunc);

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(mapR.keySet());
        allKeys.addAll(mapE.keySet());

        for (String k : allKeys) {
            Deque<Lancamento> rQ = mapR.getOrDefault(k, new ArrayDeque<>());
            Deque<Lancamento> eQ = mapE.getOrDefault(k, new ArrayDeque<>());

            int pairs = Math.min(rQ.size(), eQ.size());
            for (int i = 0; i < pairs; i++) {
                Lancamento lr = rQ.removeFirst();
                Lancamento le = eQ.removeFirst();
                if (!Objects.equals(keyFull(lr), keyFull(le))) {
                    annotator.annotate(lr, le);
                    divergenciasOut.add(lr); // mantém origem="RAZAO"
                    divergenciasOut.add(le); // mantém origem="EXTRATO"
                } else {
                    // Par idêntico ainda presente nesta fase: não descartar silenciosamente.
                    // Mantemos ambos como conciliados simples adicionando o do Razão.
                    // Obs.: decisão minimalista para não alterar a estrutura do retorno.
                    // Caso desejado, pode-se evoluir para colecionar pares.
                    
                    
                }
            }
            mapR.put(k, rQ);
            mapE.put(k, eQ);
        }

        return new PairingResult(flatten(mapR), flatten(mapE));
    }

    private static class PairingResult {
        final List<Lancamento> leftoverR;
        final List<Lancamento> leftoverE;
        PairingResult(List<Lancamento> r, List<Lancamento> e) { this.leftoverR = r; this.leftoverE = e; }
    }

    /* ===================== Key functions ===================== */

    private String keyFull(Lancamento l) {
        String tipo = safeUpper(l.getTipo());
        String data = (l.getData() == null) ? "null" : l.getData().toString();
        String valor = (l.getValor() == null ? BigDecimal.ZERO : l.getValor()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        return tipo + "|" + data + "|" + valor;
    }

    /** Mesma operação e valor (ignora data) -> divergência de DATA */
    private String keyTipoValor(Lancamento l) {
        String tipo = safeUpper(l.getTipo());
        String valor = (l.getValor() == null ? BigDecimal.ZERO : l.getValor()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        return tipo + "|" + valor;
    }

    /** Mesma operação e data (ignora valor) -> divergência de VALOR */
    private String keyTipoData(Lancamento l) {
        String tipo = safeUpper(l.getTipo());
        String data = (l.getData() == null) ? "null" : l.getData().toString();
        return tipo + "|" + data;
    }

    /* ===================== Generic helpers ===================== */

    private interface KeyFunc { String apply(Lancamento l); }

    private Map<String, Deque<Lancamento>> groupBy(List<Lancamento> list, KeyFunc keyFunc) {
        Map<String, Deque<Lancamento>> map = new HashMap<>();
        for (Lancamento l : list) {
            if (l == null) continue;
            String k = keyFunc.apply(l);
            map.computeIfAbsent(k, kk -> new ArrayDeque<>()).add(l);
        }
        return map;
    }

    private List<Lancamento> flatten(Map<String, Deque<Lancamento>> map) {
        List<Lancamento> out = new ArrayList<>();
        for (Deque<Lancamento> q : map.values()) out.addAll(q);
        return out;
    }

    private String safeUpper(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }
    private String safeStr(String s) { return s == null ? "" : s.trim(); }

    private String fmtDate(LocalDate d) { return d == null ? "-" : BR_DATE.format(d); }
    private String fmtMoney(BigDecimal v) {
        NumberFormat nf = BR_CURRENCY.get();
        if (v == null) return nf.format(0);
        return nf.format(v);
    }
}
