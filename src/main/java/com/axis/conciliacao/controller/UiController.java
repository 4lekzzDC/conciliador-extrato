package com.axis.conciliacao.ui;

import com.axis.conciliacao.model.Lancamento;
import com.axis.conciliacao.model.ResultadoConciliacao;
import com.axis.conciliacao.service.ConciliacaoService;
import com.axis.conciliacao.utils.ExcelReader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Controller
public class UiController {

    private final ExcelReader excelReader;
    private final ConciliacaoService conciliacaoService;

    public UiController(ExcelReader excelReader, ConciliacaoService conciliacaoService) {
        this.excelReader = excelReader;
        this.conciliacaoService = conciliacaoService;
    }

    @GetMapping("/ui")
    public String form(Model model) {
        // valores padrão da UI
        model.addAttribute("modo", "duascolunas");
        model.addAttribute("mostrarOk", true);

        // listas vazias para evitar NullPointer nos templates
        model.addAttribute("somRazao", Collections.emptyList());
        model.addAttribute("somExtrato", Collections.emptyList());
        model.addAttribute("diverg", Collections.emptyList());
        model.addAttribute("conc", Collections.emptyList());

        // contadores
        model.addAttribute("qSomRazao", 0);
        model.addAttribute("qSomExtrato", 0);
        model.addAttribute("qDiverg", 0);
        model.addAttribute("qConc", 0);

        return "ui_form";
    }

    @PostMapping("/ui/processar")
    public String processar(@RequestParam("razao") MultipartFile razao,
                            @RequestParam("extrato") MultipartFile extrato,
                            @RequestParam(name = "modo", defaultValue = "duascolunas") String modo,
                            @RequestParam(name = "mostrarOk", defaultValue = "true") boolean mostrarOk,
                            Model model) {
        try {
            var lancRazao = excelReader.lerRazao(razao);
            var lancExtrato = excelReader.lerExtrato(extrato);

            ResultadoConciliacao res = conciliacaoService.conciliar(lancRazao, lancExtrato);
            if (res == null) res = new ResultadoConciliacao();

            List<Lancamento> somRazao = nn(res.getSomenteNoRazao());
            List<Lancamento> somExtrato = nn(res.getSomenteNoExtrato());
            List<Lancamento> diverg = nn(res.getDivergencias());
            List<Lancamento> conc = nn(res.getConciliados());

            model.addAttribute("modo", modo);
            model.addAttribute("mostrarOk", mostrarOk);

            // listas já normalizadas (nunca nulas)
            model.addAttribute("somRazao", somRazao);
            model.addAttribute("somExtrato", somExtrato);
            model.addAttribute("diverg", diverg);
            model.addAttribute("conc", conc);

            // contadores prontos para o template
            model.addAttribute("qSomRazao", somRazao.size());
            model.addAttribute("qSomExtrato", somExtrato.size());
            model.addAttribute("qDiverg", diverg.size());
            model.addAttribute("qConc", conc.size());

            return "ui_result_modes";
        } catch (Exception e) {
            // mensagem amigável e volta para o form
            model.addAttribute("erro",
                    "Falha ao processar os arquivos: " + e.getMessage());
            model.addAttribute("modo", "duascolunas");
            model.addAttribute("mostrarOk", true);
            model.addAttribute("somRazao", Collections.emptyList());
            model.addAttribute("somExtrato", Collections.emptyList());
            model.addAttribute("diverg", Collections.emptyList());
            model.addAttribute("conc", Collections.emptyList());
            model.addAttribute("qSomRazao", 0);
            model.addAttribute("qSomExtrato", 0);
            model.addAttribute("qDiverg", 0);
            model.addAttribute("qConc", 0);
            return "ui_form";
        }
    }

    private static <T> List<T> nn(List<T> list) {
        return Objects.requireNonNullElse(list, Collections.emptyList());
    }
}
