package com.axis.conciliacao.controller;

import com.axis.conciliacao.model.ResultadoConciliacao;
import com.axis.conciliacao.service.ConciliacaoService;
import com.axis.conciliacao.utils.ExcelReader;
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
            mv.addObject("resultadoConciliacao", resultado); // ✅ Corrigido
            mv.addObject("mostrarOk", mostrarOk);
            return mv;

        } catch (Exception e) {
            e.printStackTrace();

            mv.setViewName("ui_error");
            mv.addObject("mensagem",
                "❌ Ocorreu um erro ao processar os arquivos. Verifique o formato e tente novamente.<br><br>"
                + "<small>Detalhes técnicos: " + e.getMessage() + "</small>");
            return mv;
        }
    }
}
