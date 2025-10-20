package com.axis.conciliacao.utils;

import com.axis.conciliacao.model.Lancamento;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.NumberToTextConverter;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Leitor de arquivos Excel (POI) para Razão e Extrato.
 * Regras:
 *  - Razão: Débito = RECEBIMENTO, Crédito = PAGAMENTO.
 *  - Extrato: Valor Receber = RECEBIMENTO, Valor Pagar = PAGAMENTO.
 * Detecção por cabeçalho (robusto a mudanças de colunas).
 */
public class ExcelReader {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /* =========================================================
       API pública
       ========================================================= */

    /** Lê o arquivo do RAZÃO a partir de um InputStream. */
    public List<Lancamento> lerRazao(InputStream in) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);

            // Procura a linha do cabeçalho (entre as 20 primeiras por segurança)
            int headerRowIdx = findHeaderRow(sheet, 0, 20,
                    Arrays.asList("data"),
                    Arrays.asList("hist"),
                    Arrays.asList("debito", "débito"),
                    Arrays.asList("credito", "crédito")
            );

            if (headerRowIdx < 0) {
                throw new IOException("Cabeçalho do Razão não encontrado (procure por: Data, Histórico, Débito, Crédito).");
            }

            // Mapeia colunas pelo texto do cabeçalho
            Row header = sheet.getRow(headerRowIdx);
            Map<String, Integer> cols = mapColumns(sheet, headerRowIdx);

            // Tenta localizar colunas de interesse (por nome aproximado)
            int colData = findCol(cols, "data");
            int colHistPrimeira = findColApprox(header, headerRowIdx, "hist"); // pega a primeira do "histórico"
            // O histórico costuma estar mesclado/espalhado; por padrão concatena de colHistPrimeira até +7
            int colHistUltima = Math.min(colHistPrimeira + 7, header.getLastCellNum() - 1);

            int colDebito = findCol(cols, "debito", "débito");
            int colCredito = findCol(cols, "credito", "crédito");

            // Itera linhas de dados
            List<Lancamento> out = new ArrayList<>();
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                LocalDate data = getDate(row.getCell(colData));
                // Se não houver data, pode ser somatória/rodapé
                if (data == null) continue;

                // Concatena histórico de várias colunas (células mescladas muitas vezes preenchem só a primeira)
                String historico = concatRange(row, colHistPrimeira, colHistUltima);

                BigDecimal valDebito = getNumber(row.getCell(colDebito));
                BigDecimal valCredito = getNumber(row.getCell(colCredito));

                // Débito -> RECEBIMENTO
                if (valDebito != null && valDebito.abs().compareTo(BigDecimal.ZERO) > 0) {
                    out.add(buildLanc(data, valDebito.abs(), "RECEBIMENTO", "RAZAO", historico));
                }
                // Crédito -> PAGAMENTO
                if (valCredito != null && valCredito.abs().compareTo(BigDecimal.ZERO) > 0) {
                    out.add(buildLanc(data, valCredito.abs(), "PAGAMENTO", "RAZAO", historico));
                }
            }
            return out;
        }
    }

    /** Lê o arquivo do EXTRATO a partir de um InputStream. */
    public List<Lancamento> lerExtrato(InputStream in) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);

            // Cabeçalho do extrato na PRIMEIRA linha (linha 0)
            int headerRowIdx = findHeaderRow(sheet, 0, 5,
                    Arrays.asList("data"),
                    Arrays.asList("fornecedor", "cliente", "hist", "descricao", "descrição"),
                    Arrays.asList("valor receber", "a receber", "receber"),
                    Arrays.asList("valor pagar", "a pagar", "pagar")
            );
            if (headerRowIdx < 0) {
                throw new IOException("Cabeçalho do Extrato não encontrado (procure por: Data, Fornecedor/Cliente, Valor Receber, Valor Pagar).");
            }

            Row header = sheet.getRow(headerRowIdx);
            Map<String, Integer> cols = mapColumns(sheet, headerRowIdx);

            int colData = findCol(cols, "data");
            int colDesc = findCol(cols, "fornecedor", "cliente", "hist", "descricao", "descrição");
            int colReceber = findCol(cols, "valor receber", "a receber", "receber");
            int colPagar = findCol(cols, "valor pagar", "a pagar", "pagar");

            List<Lancamento> out = new ArrayList<>();
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                LocalDate data = getDate(row.getCell(colData));
                if (data == null) continue;

                String desc = getString(row.getCell(colDesc));
                BigDecimal valReceber = getNumber(row.getCell(colReceber));
                BigDecimal valPagar = getNumber(row.getCell(colPagar));

                // Valor Receber -> RECEBIMENTO
                if (valReceber != null && valReceber.abs().compareTo(BigDecimal.ZERO) > 0) {
                    out.add(buildLanc(data, valReceber.abs(), "RECEBIMENTO", "EXTRATO", desc));
                }
                // Valor Pagar -> PAGAMENTO
                if (valPagar != null && valPagar.abs().compareTo(BigDecimal.ZERO) > 0) {
                    out.add(buildLanc(data, valPagar.abs(), "PAGAMENTO", "EXTRATO", desc));
                }
            }
            return out;
        }
    }

    /* =========================================================
       Helpers principais
       ========================================================= */

    private Lancamento buildLanc(LocalDate data, BigDecimal valor, String tipo, String origem, String descricao) {
        Lancamento l = new Lancamento();
        l.setData(data);
        l.setValor(valor);
        l.setTipo(tipo);
        l.setOrigem(origem);
        l.setDescricao(descricao == null ? "" : descricao.trim());
        return l;
    }

    /** Procura a linha do cabeçalho que contenha (em qualquer ordem) todos os grupos de palavras exigidos. */
    private int findHeaderRow(Sheet sheet, int from, int to, List<String>... requiredGroups) {
        int last = Math.min(sheet.getLastRowNum(), to);
        for (int r = from; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Set<String> tokens = new HashSet<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String txt = normalize(getString(row.getCell(c)));
                if (!txt.isEmpty()) tokens.add(txt);
            }
            if (matchesAllGroups(tokens, requiredGroups)) {
                return r;
            }
        }
        return -1;
    }

    private boolean matchesAllGroups(Set<String> tokens, List<String>... groups) {
        for (List<String> group : groups) {
            boolean found = false;
            for (String want : group) {
                String w = normalize(want);
                if (tokens.stream().anyMatch(t -> t.contains(w))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Mapeia coluna -> nome normalizado do cabeçalho. */
    private Map<String, Integer> mapColumns(Row header) {
        Map<String, Integer> out = new HashMap<>();
        if (header == null) return out;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String key = normalize(getString(header.getCell(c)));
            if (!key.isEmpty()) out.put(key, c);
        }
        return out;
    }

    /** Variante que considera merges e preserva a ordem esquerda→direita. */
    private Map<String, Integer> mapColumns(Sheet sheet, int headerRowIdx) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (sheet == null) return out;
        Row header = sheet.getRow(headerRowIdx);
        if (header == null) return out;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String txt = getHeaderTextAt(sheet, headerRowIdx, c);
            String key = normalize(txt);
            if (!key.isEmpty()) out.putIfAbsent(key, c);
        }
        return out;
    }

    private String getHeaderTextAt(Sheet sheet, int rowIdx, int colIdx) {
        if (sheet == null) return "";
        Row row = sheet.getRow(rowIdx);
        if (row == null) return "";
        Cell cell = row.getCell(colIdx);
        String base = getString(cell);
        if (base != null && !base.trim().isEmpty()) return base;
        int mergedCount = sheet.getNumMergedRegions();
        for (int i = 0; i < mergedCount; i++) {
            CellRangeAddress rng = sheet.getMergedRegion(i);
            if (rng != null && rng.isInRange(rowIdx, colIdx)) {
                Row topRow = sheet.getRow(rng.getFirstRow());
                if (topRow == null) return "";
                Cell topLeft = topRow.getCell(rng.getFirstColumn());
                return getString(topLeft);
            }
        }
        return "";
    }

    /** Encontra a coluna cujo cabeçalho contenha qualquer um dos termos fornecidos. */
    private int findCol(Map<String, Integer> cols, String... wants) {
        for (String want : wants) {
            String w = normalize(want);
            // busca por contain para ser tolerante a "valor receber"
            List<String> keys = cols.keySet().stream()
                    .filter(k -> k.contains(w))
                    .collect(Collectors.toList());
            if (!keys.isEmpty()) return cols.get(keys.get(0));
        }
        return -1;
    }

    /** Variante: varre o header para achar a primeira coluna que contenha o termo (considera merges). */
    private int findColApprox(Row header, int headerRowIdx, String wantTerm) {
        String w = normalize(wantTerm);
        if (header == null) return -1;
        Sheet sheet = header.getSheet();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String key = normalize(getHeaderTextAt(sheet, headerRowIdx, c));
            if (key.contains(w)) return c;
        }
        return -1;
    }

    /** Sobrecarga que recebe o sheet diretamente. */
    private int findColApprox(Sheet sheet, int headerRowIdx, String wantTerm) {
        if (sheet == null) return -1;
        Row header = sheet.getRow(headerRowIdx);
        return findColApprox(header, headerRowIdx, wantTerm);
    }

    private String concatRange(Row row, int fromCol, int toCol) {
        if (row == null || fromCol < 0 || toCol < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int c = fromCol; c <= toCol; c++) {
            String part = getString(row.getCell(c));
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(part);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    /* =========================================================
       Leitura de célula
       ========================================================= */

    private String getString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return clean(cell.getStringCellValue());
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date d = cell.getDateCellValue();
                        if (d != null) return BR_DATE.format(d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                        return "";
                    }
                    // número -> texto (para preservar "1234,00" depois convertemos se necessário)
                    return NumberToTextConverter.toText(cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return clean(cell.getStringCellValue());
                    } catch (Exception e) {
                        try {
                            return NumberToTextConverter.toText(cell.getNumericCellValue());
                        } catch (Exception ex) {
                            return clean(cell.getCellFormula());
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception ex) {
            return "";
        }
    }

    private LocalDate getDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d == null ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String s = getString(cell);
            if (s.isEmpty()) return null;
            // tenta dd/MM/yyyy
            try {
                return LocalDate.parse(s.trim(), BR_DATE);
            } catch (Exception ignore) {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal getNumber(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String s = getString(cell);
            if (s.isEmpty()) return null;
            // Normaliza números no formato BR: 1.234,56 -> 1234.56
            s = s.replaceAll("\\s+", "");
            s = s.replace(".", "").replace(",", ".");
            return new BigDecimal(s);
        } catch (Exception ex) {
            return null;
        }
    }

    /* =========================================================
       Utilitários
       ========================================================= */

    private String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private String normalize(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
