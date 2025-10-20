package com.axis.conciliacao.controller;

import com.axis.conciliacao.model.ResultadoConciliacao;
import com.axis.conciliacao.service.ConciliacaoService;
import com.axis.conciliacao.utils.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.InputStream;

/**
 * Controller responsável pela camada de interface web (Thymeleaf).
 * Permite o upload dos arquivos Razão e Extrato e realiza a conciliação financeira.
 */
@Controller
public class UiController {

    private static final Logger log = LoggerFactory.getLogger(UiController.class);

    private final ExcelReader excelReader;
    private final ConciliacaoService conciliacaoService;

    public UiController(ExcelReader excelReader, ConciliacaoService conciliacaoService) {
        this.excelReader = excelReader;
        this.conciliacaoService = conciliacaoService;
    }

    /**
     * Página inicial com o formulário de upload.
     */
    @GetMapping("/ui")
    public ModelAndView form() {
        return new ModelAndView("ui_form");
    }

    /**
     * Endpoint responsável por processar os arquivos e exibir o resultado da conciliação.
     */
    @PostMapping("/ui/processar")
    public ModelAndView processar(@RequestParam("razao") MultipartFile razao,
                                  @RequestParam("extrato") MultipartFile extrato,
                                  @RequestParam(name = "mostrarOk", defaultValue = "true") boolean mostrarOk) {

        ModelAndView mv = new ModelAndView();

        // Validação básica dos arquivos enviados
        String erroValidacao = validarArquivos(razao, extrato);
        if (erroValidacao != null) {
            mv.setViewName("ui_error");
            mv.addObject("mensagem", erroValidacao);
            return mv;
        }

        try (
            InputStream inRazao = razao.getInputStream();
            InputStream inExtrato = extrato.getInputStream()
        ) {
            // Lê os dados dos dois arquivos Excel
            var dadosRazao = excelReader.lerRazao(inRazao);
            var dadosExtrato = excelReader.lerExtrato(inExtrato);

            // Processa a conciliação
            ResultadoConciliacao resultado = conciliacaoService.conciliar(dadosRazao, dadosExtrato);

            // Retorna o resultado para o template de exibição
            mv.setViewName("ui_result_modes");
            mv.addObject("resultadoConciliacao", resultado);
            mv.addObject("mostrarOk", mostrarOk);
            return mv;

        } catch (Exception e) {
            log.error("Falha ao processar conciliacao: razao={}, extrato={}, msg={}",
                    safeName(razao), safeName(extrato), e.getMessage(), e);

            mv.setViewName("ui_error");
            mv.addObject("mensagem",
                "Ocorreu um erro ao processar os arquivos. Verifique o formato e tente novamente.");
            return mv;
        }
    }

    private String validarArquivos(MultipartFile razao, MultipartFile extrato) {
        if (razao == null || razao.isEmpty()) {
            return "Arquivo Razão não enviado ou vazio.";
        }
        if (extrato == null || extrato.isEmpty()) {
            return "Arquivo Extrato não enviado ou vazio.";
        }
        if (!isExcelFile(razao)) {
            return "Arquivo Razão não é um Excel válido (.xls ou .xlsx).";
        }
        if (!isExcelFile(extrato)) {
            return "Arquivo Extrato não é um Excel válido (.xls ou .xlsx).";
        }
        return null;
    }

    private boolean isExcelFile(MultipartFile f) {
        try {
            String name = safeName(f);
            String lower = name == null ? "" : name.toLowerCase();
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return true;
            String ct = f.getContentType();
            if (ct == null) return false;
            ct = ct.toLowerCase();
            return ct.contains("spreadsheetml") || ct.contains("excel");
        } catch (Exception ignore) {
            return false;
        }
    }

    private String safeName(MultipartFile f) {
        if (f == null) return null;
        try { return f.getOriginalFilename(); } catch (Exception e) { return null; }
    }
}
